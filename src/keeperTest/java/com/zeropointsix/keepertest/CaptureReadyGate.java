package com.zeropointsix.keepertest;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/** Cross-JVM, one-shot shutter permission; timestamps use the checked sampler clock domain. */
public final class CaptureReadyGate {
    public static final long MAX_AGE_NANOS = 50_000_000L;
    private static final String VERSION = "keeper-ready-v1,";
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCAL_LOCKS = new ConcurrentHashMap<>();
    private final Path results, ready, claimed;
    private final LongSupplier clock;
    private final ReentrantLock localLock;

    @FunctionalInterface
    public interface Dispatch {
        void start() throws Exception;
    }

    public CaptureReadyGate(Path results) {
        this(results, System::nanoTime);
    }

    public CaptureReadyGate(Path results, LongSupplier clock) {
        this.results = results.toAbsolutePath().normalize();
        this.ready = this.results.resolve("capture.ready");
        this.claimed = this.results.resolve("shutter.claimed");
        this.clock = clock;
        this.localLock = LOCAL_LOCKS.computeIfAbsent(this.results, ignored -> new ReentrantLock());
    }

    private boolean failed() {
        return Files.exists(results.resolve("sampler.failed")) || Files.exists(results.resolve("client.failed"));
    }

    private boolean fresh(long capturedAt) {
        long age = clock.getAsLong() - capturedAt;
        return age >= 0 && age <= MAX_AGE_NANOS;
    }

    private LockHandle lock(boolean blocking) throws IOException {
        if (localLock.isHeldByCurrentThread()) {
            if (blocking) throw new IOException("Nested capture operation");
            return null;
        }
        if (blocking) localLock.lock();
        else if (!localLock.tryLock()) return null;
        FileChannel channel = null;
        try {
            channel = FileChannel.open(results.resolve("capture.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock fileLock = blocking ? channel.lock() : channel.tryLock();
            if (fileLock != null) return new LockHandle(channel, fileLock);
        } catch (OverlappingFileLockException busy) {
            // Another gate in this JVM must not be mistaken for a ready sampler.
        } catch (IOException | RuntimeException failure) {
            try { if (channel != null) channel.close(); }
            finally { localLock.unlock(); }
            throw failure;
        }
        try { if (channel != null) channel.close(); }
        finally { localLock.unlock(); }
        return null;
    }

    private final class LockHandle implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock fileLock;

        private LockHandle(FileChannel channel, FileLock fileLock) {
            this.channel = channel;
            this.fileLock = fileLock;
        }

        @Override
        public void close() throws IOException {
            try { fileLock.release(); }
            finally {
                try { channel.close(); }
                finally { localLock.unlock(); }
            }
        }
    }

    /** Hold this guard across the actual blocking capture, not just its readiness calculation. */
    public Capture beginCapture() throws IOException {
        LockHandle held = lock(true);
        if (held == null) throw new IOException("Sampler capture lock is already held in this JVM");
        try {
            Files.deleteIfExists(ready);
            return new Capture(held);
        } catch (IOException failure) {
            held.close();
            throw failure;
        }
    }

    public final class Capture implements AutoCloseable {
        private final LockHandle held;
        private boolean closed;

        private Capture(LockHandle held) { this.held = held; }

        public void publish(long capturedAt) throws IOException {
            if (closed) throw new IllegalStateException("Capture is already closed");
            if (!fresh(capturedAt) || failed() || Files.exists(claimed)) return;
            Path temporary = results.resolve("capture.ready.tmp");
            Files.writeString(temporary, VERSION + capturedAt + "\n");
            Files.move(temporary, ready, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                held.close();
            }
        }
    }

    public void invalidate() throws IOException {
        try (Capture ignored = beginCapture()) { }
    }

    /** Run only the dispatch start under the lock; never wait for native input completion here. */
    public boolean tryShutter(Dispatch dispatch) throws Exception {
        try (LockHandle held = lock(false)) {
            if (held == null) return false;
            if (failed() || Files.exists(claimed)) {
                Files.deleteIfExists(ready);
                return false;
            }
            long capturedAt;
            try {
                String value = Files.readString(ready).trim();
                if (!value.startsWith(VERSION)) throw new NumberFormatException("Unversioned readiness");
                capturedAt = Long.parseLong(value.substring(VERSION.length()));
            } catch (NoSuchFileException missing) {
                return false;
            } catch (NumberFormatException invalid) {
                Files.deleteIfExists(ready);
                return false;
            }
            Files.deleteIfExists(ready);
            if (!fresh(capturedAt)) return false;
            Files.writeString(claimed, VERSION + capturedAt + "\n", StandardOpenOption.CREATE_NEW);
            // Recheck after filesystem I/O and on the dispatching thread, not the scheduling frame.
            if (failed() || !fresh(capturedAt)) {
                Files.deleteIfExists(claimed);
                return false;
            }
            dispatch.start();
            // Keep the claim even on dispatch failure: native input may already have been sent.
            return true;
        }
    }
}
