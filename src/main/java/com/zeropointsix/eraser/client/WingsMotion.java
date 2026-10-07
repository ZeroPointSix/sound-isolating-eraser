package com.zeropointsix.eraser.client;

/** Per-wearer clock: no render-frame mutations or shared-player pose state. */
public final class WingsMotion {
    private static final double TAU = Math.PI * 2;
    public record Pose(float deployment, float sweep, float phase, float amplitude) {}
    private float deployment;
    private float previousDeployment;
    private float sweep;
    private float previousSweep;
    private float amplitude;
    private float previousAmplitude;
    private double phase;
    private double previousPhase;
    private double frequency = 0.8;

    public WingsMotion(float initialDeployment) {
        deployment = previousDeployment = clamp(initialDeployment);
    }

    public void tick(boolean deployed, int tier, float speedRatio) {
        previousDeployment = deployment;
        previousSweep = sweep;
        previousAmplitude = amplitude;
        previousPhase = phase;
        // Six ticks including endpoints. A reached target must stay still.
        deployment = approach(deployment, deployed ? 1 : 0, 1f / 6f);
        sweep += ((deployed ? clamp(speedRatio) : 0) - sweep) * 0.3f;
        double targetFrequency = tier == 2 ? 4 : tier == 3 ? 1.2 : 0.8;
        frequency += (targetFrequency - frequency) * 0.3;
        phase += TAU * frequency / 20;
        if (phase >= TAU) { phase -= TAU; previousPhase -= TAU; }
        float targetAmplitude = !deployed ? 0 : switch (tier) {
            case 0 -> 1f;
            case 1 -> 0.18f;
            case 2 -> 0.45f;
            default -> 0.05f;
        };
        amplitude += (targetAmplitude - amplitude) * 0.35f;
    }

    public Pose sample(float partialTick) {
        float t = clamp(partialTick);
        float linear = lerp(previousDeployment, deployment, t);
        float eased = linear * linear * (3 - 2 * linear);
        return new Pose(eased, lerp(previousSweep, sweep, t),
                (float) (previousPhase + (phase - previousPhase) * t),
                lerp(previousAmplitude, amplitude, t) * eased);
    }

    public float deployment() { return deployment; }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float approach(float a, float target, float step) {
        if (Math.abs(target - a) <= step + 0.000001f) return target;
        return a < target ? Math.min(target, a + step) : Math.max(target, a - step);
    }
}
