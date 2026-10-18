import com.zeropointsix.keepertest.CaptureReadyGate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Runs the actual QA gate and sampler state machine. These are not visual E2E tests. */
public final class KeeperReadinessCheck {
    private static final long MS = 1_000_000L;
    private static final long START = 1_000_000_000L;
    private static int passed;

    @FunctionalInterface
    private interface Check { void run(Path directory) throws Exception; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void check(String name, Check check) throws Exception {
        Path directory = Files.createTempDirectory("keeper-ready-");
        try {
            check.run(directory);
            passed++;
            System.out.println("PASS " + name);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void publish(CaptureReadyGate gate, long when) throws Exception {
        try (var capture = gate.beginCapture()) { capture.publish(when); }
    }

    private static void neverDispatch() { throw new AssertionError("Invalid readiness permitted shutter"); }

    private static void await(CountDownLatch latch) throws Exception {
        require(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for test thread");
    }

    private static void writeLease(Path directory, long when) throws Exception {
        Files.writeString(directory.resolve("capture.ready"), "keeper-ready-v1," + when + "\n");
    }

    private static void childCapture(Path directory) throws Exception {
        try (var capture = new CaptureReadyGate(directory).beginCapture()) {
            Files.writeString(directory.resolve("child-capturing"), "locked");
            long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(directory.resolve("release-child"))) {
                require(System.nanoTime() < timeout, "Parent did not release child capture");
                Thread.sleep(1);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("child-capture")) {
            childCapture(Path.of(args[1]));
            return;
        }
        check("fresh lease accepted exactly once", directory -> {
            AtomicLong clock = new AtomicLong(START);
            var gate = new CaptureReadyGate(directory, clock::get);
            publish(gate, START);
            clock.addAndGet(49 * MS);
            AtomicInteger clicks = new AtomicInteger();
            require(gate.tryShutter(clicks::incrementAndGet), "Fresh token rejected");
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Token reused");
            require(clicks.get() == 1 && !Files.exists(directory.resolve("capture.ready")), "Not consumed");
        });
        check("50ms inclusive lease boundary", directory -> {
            AtomicLong clock = new AtomicLong(START);
            var gate = new CaptureReadyGate(directory, clock::get);
            publish(gate, START);
            clock.addAndGet(50 * MS);
            require(gate.tryShutter(() -> { }), "50ms boundary should be valid");
        });
        check("50ms plus 1ns rejected and revoked", directory -> {
            AtomicLong clock = new AtomicLong(START);
            var gate = new CaptureReadyGate(directory, clock::get);
            publish(gate, START);
            clock.addAndGet(50 * MS + 1);
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Expired token accepted");
            require(!Files.exists(directory.resolve("capture.ready")), "Expired token was retained");
        });
        check("missing malformed legacy and future tokens fail closed", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Missing token accepted");
            for (String value : new String[] {"", "not-a-time", "" + START,
                    "keeper-ready-v1,not-a-time", "keeper-ready-v1," + (START + 1)}) {
                Files.writeString(directory.resolve("capture.ready"), value);
                require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Invalid token accepted: " + value);
                require(!Files.exists(directory.resolve("capture.ready")), "Invalid token not revoked");
            }
        });
        check("lease refreshed after each healthy capture", directory -> {
            AtomicLong clock = new AtomicLong(START);
            var gate = new CaptureReadyGate(directory, clock::get);
            publish(gate, START);
            clock.addAndGet(20 * MS);
            try (var capture = gate.beginCapture()) {
                require(!Files.exists(directory.resolve("capture.ready")), "Capture did not revoke first");
                capture.publish(clock.get());
            }
            clock.addAndGet(49 * MS);
            require(gate.tryShutter(() -> { }), "Refreshed lease still used old timestamp");
        });
        check("old or future capture cannot publish", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            publish(gate, START - 50 * MS - 1);
            require(!Files.exists(directory.resolve("capture.ready")), "Old capture published");
            publish(gate, START + 1);
            require(!Files.exists(directory.resolve("capture.ready")), "Future capture published");
        });
        check("sampler and client failure revoke permission", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            for (String marker : new String[] {"sampler.failed", "client.failed"}) {
                publish(gate, START);
                Files.writeString(directory.resolve(marker), "failure");
                require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Failed run dispatched");
                publish(gate, START);
                require(!Files.exists(directory.resolve("capture.ready")), "Failure allowed republish");
                Files.delete(directory.resolve(marker));
            }
        });
        check("capture exception leaves ready absent", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            publish(gate, START);
            try (var ignored = KeeperX11Sampler.capture(gate, () -> {
                throw new IOException("capture failure");
            })) {
                throw new AssertionError("Capture exception hidden");
            } catch (IOException expected) {
                require(expected.getMessage().equals("capture failure"), "Unexpected error");
            }
            require(!Files.exists(directory.resolve("capture.ready")), "Failed capture retained lease");
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Capture failure dispatched");
        });
        check("startup and final invalidation revoke ready", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            publish(gate, START);
            gate.invalidate();
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Invalidated token accepted");
            var capture = gate.beginCapture();
            capture.close();
            try {
                capture.publish(START);
                throw new AssertionError("Closed capture published");
            } catch (IllegalStateException expected) { }
        });
        check("200ms blocked capture denies concurrent shutter at 100ms", directory -> {
            AtomicLong clock = new AtomicLong(START);
            var gate = new CaptureReadyGate(directory, clock::get);
            publish(gate, START);
            CountDownLatch entered = new CountDownLatch(1), finish = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            var future = executor.submit(() -> {
                try (var capture = KeeperX11Sampler.capture(gate, () -> {
                    entered.countDown();
                    await(finish);
                    return new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
                })) { }
                return true;
            });
            try {
                await(entered);
                require(!Files.exists(directory.resolve("capture.ready")), "Old lease survived capture start");
                clock.set(START + 100 * MS);
                require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "100ms blocked capture allowed input");
                // Even a concurrently reintroduced, fresh-looking file cannot bypass the capture lock.
                writeLease(directory, clock.get());
                require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Busy lock bypassed by file");
                clock.set(START + 200 * MS);
            } finally {
                finish.countDown();
                try { future.get(5, TimeUnit.SECONDS); }
                finally { executor.shutdownNow(); }
            }
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Stale lease accepted after unblock");
        });
        check("delayed native worker rechecks current clock", directory -> {
            AtomicLong clock = new AtomicLong(START);
            var gate = new CaptureReadyGate(directory, clock::get);
            publish(gate, START);
            require(Files.exists(directory.resolve("capture.ready")), "Scheduling precondition");
            clock.addAndGet(100 * MS);
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Scheduling-time token used later");
        });
        check("expiry during claim I/O checked before dispatch", directory -> {
            AtomicInteger reads = new AtomicInteger();
            var gate = new CaptureReadyGate(directory, () ->
                    reads.incrementAndGet() < 3 ? START : START + 51 * MS);
            publish(gate, START);
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Post-I/O expiry missed");
            require(!Files.exists(directory.resolve("shutter.claimed")), "Rejected claim prevented recovery");
        });
        check("concurrent shutters consume only one claim", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            var other = new CaptureReadyGate(directory, () -> START);
            publish(gate, START);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger clicks = new AtomicInteger();
            var executor = Executors.newFixedThreadPool(2);
            var first = executor.submit(() -> { await(start); return gate.tryShutter(clicks::incrementAndGet); });
            var second = executor.submit(() -> { await(start); return other.tryShutter(clicks::incrementAndGet); });
            try {
                start.countDown();
                require(first.get(5, TimeUnit.SECONDS) ^ second.get(5, TimeUnit.SECONDS), "Not exactly one accepted");
                publish(gate, START);
                require(!other.tryShutter(KeeperReadinessCheck::neverDispatch), "Next capture rearmed consumed shutter");
                require(clicks.get() == 1, "Duplicate native dispatch");
            } finally { executor.shutdownNow(); }
        });
        check("uncertain native failure cannot dispatch twice", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            publish(gate, START);
            try {
                gate.tryShutter(() -> { throw new IOException("native start failure"); });
                throw new AssertionError("Dispatch exception hidden");
            } catch (IOException expected) {
                require(expected.getMessage().equals("native start failure"), "Unexpected failure");
            }
            publish(gate, START);
            require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Uncertain dispatch retried");
        });
        check("native completion does not hold capture lock", directory -> {
            var gate = new CaptureReadyGate(directory, () -> START);
            publish(gate, START);
            CountDownLatch complete = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                require(gate.tryShutter(() -> executor.submit(() -> {
                    await(complete);
                    return true;
                })), "Fresh dispatch failed");
                try (var capture = gate.beginCapture()) {
                    require(complete.getCount() == 1, "Test did not overlap native completion");
                }
            } finally {
                complete.countDown();
                executor.shutdown();
                require(executor.awaitTermination(5, TimeUnit.SECONDS), "Native completion timed out");
            }
        });
        check("actual second JVM capture lock excludes shutter", directory -> {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    "KeeperReadinessCheck", "child-capture", directory.toString()).inheritIO().start();
            try {
                long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!Files.exists(directory.resolve("child-capturing"))) {
                    require(child.isAlive() && System.nanoTime() < timeout, "Child capture did not start");
                    Thread.sleep(1);
                }
                var gate = new CaptureReadyGate(directory, () -> START);
                writeLease(directory, START);
                require(!gate.tryShutter(KeeperReadinessCheck::neverDispatch), "Cross-process lock bypassed");
                Files.writeString(directory.resolve("release-child"), "release");
                require(child.waitFor(5, TimeUnit.SECONDS) && child.exitValue() == 0, "Child failed");
                publish(gate, START);
                require(gate.tryShutter(() -> { }), "Lock not released by child");
            } finally {
                if (child.isAlive()) {
                    Files.writeString(directory.resolve("release-child"), "release");
                    require(child.waitFor(5, TimeUnit.SECONDS), "Child did not stop");
                }
            }
        });
        check("real sampler keeps 2s warmup and resets on 200ms capture", directory -> {
            var readiness = new KeeperX11Sampler.Readiness();
            for (int i = 0; i < 100; i++) {
                long before = START + i * 20 * MS;
                require(!readiness.observe(before, before + MS), "Ready before 2s");
            }
            require(readiness.observe(START + 2000 * MS, START + 2001 * MS), "Stable sampler never ready");
            require(!readiness.observe(START + 2020 * MS, START + 2220 * MS), "200ms capture retained ready");
            for (int i = 0; i < 101; i++) {
                long before = START + 2240 * MS + i * 20 * MS;
                require(!readiness.observe(before, before + MS), "Stall recovery skipped full warmup");
            }
            require(readiness.observe(START + 4260 * MS, START + 4261 * MS), "Sampler never recovered");
        });
        check("real sampler 50ms gap and latency limits unchanged", directory -> {
            var readiness = new KeeperX11Sampler.Readiness();
            for (int i = 0; i <= 100; i++) readiness.observe(START + i * 20 * MS, START + i * 20 * MS + MS);
            require(readiness.observe(START + 2050 * MS, START + 2100 * MS), "50ms boundary rejected");
            require(!readiness.observe(START + 2100 * MS + 1, START + 2101 * MS), "Gap >50ms accepted");
            for (int i = 0; i <= 110; i++) readiness.observe(START + 2120 * MS + i * 20 * MS,
                    START + 2121 * MS + i * 20 * MS);
            require(!readiness.observe(START + 4340 * MS, START + 4390 * MS + 1), "Latency >50ms accepted");
        });
        System.out.println("Keeper readiness checks: " + passed + "/" + passed + " passed (Java regression, NOT visual E2E)");
    }
}
