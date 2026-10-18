package com.zeropointsix.keepertest;

import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Bounded, main-thread-only diagnostics; never part of a readiness decision. */
public final class WarmupTrace {
    public enum Stage { TICK_START, TICK_END, RENDER_START, RENDER_END, PRESENTED }

    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private final long[] epoch, monotonic, cpu;
    private final byte[] stages, phases, baselines;
    private int size;
    private long dropped;
    private boolean written;

    public WarmupTrace() { this(65_536); }

    WarmupTrace(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        epoch = new long[capacity];
        monotonic = new long[capacity];
        cpu = new long[capacity];
        stages = new byte[capacity];
        phases = new byte[capacity];
        baselines = new byte[capacity];
    }

    public void record(Stage stage, String phase, boolean baselineSaved) {
        if (size == epoch.length || written) {
            if (!written) dropped++;
            return;
        }
        long cpuTime = -1;
        // Unsupported or disabled CPU clocks remain explicitly unknown, never zero.
        try {
            if (THREADS.isCurrentThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled()) {
                cpuTime = THREADS.getCurrentThreadCpuTime();
            }
        } catch (UnsupportedOperationException | SecurityException ignored) { }
        record(stage, phase, baselineSaved, System.currentTimeMillis(), System.nanoTime(), cpuTime);
    }

    void record(Stage stage, String phase, boolean baselineSaved, long wall, long nano, long threadCpu) {
        if (written) return;
        if (size == epoch.length) { dropped++; return; }
        epoch[size] = wall;
        monotonic[size] = nano;
        cpu[size] = threadCpu;
        stages[size] = (byte) stage.ordinal();
        phases[size] = (byte) (phase.equals("baseline") ? 1 : phase.equals("shoot") ? 2 : 0);
        baselines[size] = (byte) (baselineSaved ? 1 : 0);
        size++;
    }

    /** Call only after failure or after the measured shutter interval has ended. */
    public void write(Path results) throws Exception {
        if (written) return;
        Stage[] names = Stage.values();
        try (BufferedWriter out = Files.newBufferedWriter(results.resolve("warmup-stages.csv"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            out.write("epoch_ms,monotonic_ns,thread_cpu_ns,stage,phase,baseline_saved\n");
            for (int i = 0; i < size; i++) {
                out.write(epoch[i] + "," + monotonic[i] + "," + cpu[i] + ","
                        + names[stages[i]].name() + ","
                        + (phases[i] == 1 ? "baseline" : phases[i] == 2 ? "shoot" : "other")
                        + "," + (baselines[i] != 0) + "\n");
            }
        }
        Files.writeString(results.resolve("warmup-stages-meta.txt"),
                "capacity=" + epoch.length + "\nrecorded=" + size + "\ndropped=" + dropped
                        + "\nthread_cpu_unknown=-1\nreadiness_effect=none\n",
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        written = true;
    }
}
