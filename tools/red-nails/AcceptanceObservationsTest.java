package com.zeropointsix.rednailstest;

/** Offline checks for QA arithmetic only, not a substitute for native gameplay evidence. */
public final class AcceptanceObservationsTest {
    private static int checks;
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) {
        for (int air = -15; air <= 300; air++) {
            check(AcceptanceObservations.validAirChange(air, air - 4), "normal total decrement " + air);
            check(!AcceptanceObservations.validAirChange(air, air - 3), "missing vanilla decrement " + air);
            check(!AcceptanceObservations.validAirChange(air, air - 1), "missing extra decrement " + air);
        }
        check(AcceptanceObservations.validAirChange(-19, -3), "vanilla reset before grab");
        check(AcceptanceObservations.validAirChange(-19, -1), "grab reset before vanilla");
        check(!AcceptanceObservations.validAirChange(-19, 0), "do not combine two reset operations");
        check(AcceptanceObservations.validAirChange(-16, 0), "either reset order reaches zero");
        check(!AcceptanceObservations.validAirChange(-16, -20), "reset is mandatory");

        var motion = new AcceptanceObservations.BoatHistory();
        for (int tick = 10; tick <= 13; tick++) motion.observe("boat-a", tick, 0.5, 74.5, tick * 0.2, true);
        check(motion.movingAtDismount(14), "continuous native samples cross immediate dismount");
        check(!motion.movingAtDismount(15), "stale motion cannot prove a later dismount");
        motion.observe("boat-a", 14, 0.5, 74.5, 2.6, true);
        check(!motion.movingAtDismount(15), "stopped boat cannot pass using historical distance");
        for (int tick = 15; tick <= 18; tick++) motion.observe("boat-a", tick, 0.5, 74.5, tick * 0.2, true);
        motion.observe("boat-a", 19, 0.5, 74.5, 3.8, false);
        check(!motion.movingAtDismount(20), "released input breaks continuous driving");
        for (int tick = 20; tick <= 24; tick++) motion.observe("boat-a", tick, 0.5, 74.5, tick * 0.2, true);
        motion.observe("boat-b", 25, 0.5, 74.5, 5, true);
        check(!motion.movingAtDismount(26), "different boat cannot inherit movement");
        for (int tick = 26; tick <= 30; tick++) motion.observe("boat-b", tick, 0.5, 74.5, tick * 0.2, true);
        motion.observe("boat-b", 32, 0.5, 74.5, 6.4, true);
        check(!motion.movingAtDismount(33), "missing client sample breaks continuity");

        double warmup = 0.19360567402424297, nativeMean = 0.2246166817395102;
        check(!AcceptanceObservations.speedRateMatches(nativeMean, warmup, 39), "historical mixed-window comparison fails");
        check(AcceptanceObservations.speedRateMatches(nativeMean, nativeMean, 39), "aligned historical clocks agree");
        check(AcceptanceObservations.maintainsWarmupSpeed(nativeMean, warmup, 39), "faster natural motion is not slowdown");
        check(!AcceptanceObservations.speedRateMatches(0.17, nativeMean, 39), "server slowdown still fails aligned displacement");
        check(!AcceptanceObservations.maintainsWarmupSpeed(0.15, warmup, 39), "both clocks agreeing on slower input cannot pass");
        check(AcceptanceObservations.maintainsWarmupSpeed(warmup, warmup, 39), "unchanged native speed passes");
        check(AcceptanceObservations.speedRateMatches(112, 100, 100), "existing cadence allowance boundary retained");
        check(!AcceptanceObservations.speedRateMatches(113, 100, 100), "cadence above existing allowance fails");
        check(!AcceptanceObservations.speedRateMatches(87, 100, 100), "slow client clock fails");
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!AcceptanceObservations.speedRateMatches(invalid, warmup, 39), "invalid measured speed fails");
            check(!AcceptanceObservations.speedRateMatches(warmup, invalid, 39), "invalid reference fails");
            check(!AcceptanceObservations.maintainsWarmupSpeed(invalid, warmup, 39), "invalid warmup comparison fails");
            check(!AcceptanceObservations.maintainsWarmupSpeed(warmup, invalid, 39), "invalid warmup fails");
        }
        check(!AcceptanceObservations.speedRateMatches(0.2, 0, 39), "zero reference fails");
        check(!AcceptanceObservations.maintainsWarmupSpeed(0.2, 0, 39), "zero warmup fails");
        check(!AcceptanceObservations.speedRateMatches(0.2, 0.2, 0), "empty window fails");
        check(!AcceptanceObservations.maintainsWarmupSpeed(0.2, 0.2, 0), "empty warmup comparison fails");
        check(AcceptanceObservations.requiresRearCatch("swimmer"), "swimmer natural catch remains mandatory");
        check(!AcceptanceObservations.requiresRearCatch("boat"), "mature rear boat is diagnostic under adjudicated scope");
        check(!AcceptanceObservations.requiresRearCatch("calibration"), "speed calibration is not a catch claim");
        boolean unknownRejected = false;
        try { AcceptanceObservations.requiresRearCatch("front"); }
        catch (IllegalArgumentException expected) { unknownRejected = true; }
        check(unknownRejected, "unknown speed scenarios cannot silently waive catch acceptance");
        System.out.println("QA_OBSERVATION_CHECKS_PASSED=" + checks + "; gameplay/native not exercised");
    }
}
