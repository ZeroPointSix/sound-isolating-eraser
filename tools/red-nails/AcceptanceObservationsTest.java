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
        System.out.println("QA_OBSERVATION_CHECKS_PASSED=" + checks + "; gameplay/native not exercised");
    }
}
