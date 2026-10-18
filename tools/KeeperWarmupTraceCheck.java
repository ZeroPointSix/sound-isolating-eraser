package com.zeropointsix.keepertest;

import java.nio.file.Files;
import java.nio.file.Path;

/** Recorder boundary checks, not Minecraft E2E or performance acceptance. */
public final class KeeperWarmupTraceCheck {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("keeper-warmup-trace-");
        try {
            WarmupTrace trace = new WarmupTrace(3);
            trace.record(WarmupTrace.Stage.TICK_START, "baseline", false, 100, 200, -1);
            trace.record(WarmupTrace.Stage.RENDER_END, "shoot", true, 110, 300, 20);
            trace.record(WarmupTrace.Stage.PRESENTED, "unknown", true, 120, 400, 30);
            trace.record(WarmupTrace.Stage.TICK_END, "shoot", false, 130, 500, 40);
            try (var paths = Files.list(root)) {
                check(paths.findAny().isEmpty(), "recording performs no file output");
            }
            trace.write(root);
            var rows = Files.readAllLines(root.resolve("warmup-stages.csv"));
            check(rows.size() == 4, "bounded capacity preserves exactly three events");
            check(rows.get(1).equals("100,200,-1,TICK_START,baseline,false"), "unknown CPU and baseline retained");
            check(rows.get(2).equals("110,300,20,RENDER_END,shoot,true"), "clock domains and stage retained");
            check(rows.get(3).equals("120,400,30,PRESENTED,other,true"), "unknown phase explicit");
            String meta = Files.readString(root.resolve("warmup-stages-meta.txt"));
            check(meta.contains("dropped=1\n"), "overflow is reported");
            trace.record(WarmupTrace.Stage.TICK_END, "shoot", false);
            trace.write(root);
            check(Files.readAllLines(root.resolve("warmup-stages.csv")).equals(rows), "write is idempotent and freezes recording");
            try {
                new WarmupTrace(0);
                throw new AssertionError("zero capacity accepted");
            } catch (IllegalArgumentException expected) { checks++; }
            Path live = Files.createDirectory(root.resolve("live"));
            WarmupTrace actual = new WarmupTrace(2);
            actual.record(WarmupTrace.Stage.TICK_START, "baseline", false);
            actual.record(WarmupTrace.Stage.TICK_END, "baseline", false);
            actual.write(live);
            var real = Files.readAllLines(live.resolve("warmup-stages.csv"));
            long first = Long.parseLong(real.get(1).split(",")[1]);
            long second = Long.parseLong(real.get(2).split(",")[1]);
            check(second >= first, "real monotonic clock is retained");
            Path occupied = Files.createDirectory(root.resolve("occupied"));
            Files.writeString(occupied.resolve("warmup-stages.csv"), "prior evidence\n");
            try {
                new WarmupTrace(1).write(occupied);
                throw new AssertionError("prior evidence overwritten");
            } catch (java.nio.file.FileAlreadyExistsException expected) { checks++; }
            check(Files.readString(occupied.resolve("warmup-stages.csv")).equals("prior evidence\n"), "prior evidence preserved");
            System.out.println("KeeperWarmupTraceCheck: " + checks + " checks PASS (recorder only, not visual E2E)");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
