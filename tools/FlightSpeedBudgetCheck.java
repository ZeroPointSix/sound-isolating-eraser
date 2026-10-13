import com.zeropointsix.eraser.wings.FlightSpeedBudget;
import com.zeropointsix.eraser.wings.FlightSpeedTransition;
import java.util.Random;

public final class FlightSpeedBudgetCheck {
    private static int assertions;
    private static final double HOVER = 0.625;

    public static void main(String[] args) {
        FlightSpeedBudget smooth = new FlightSpeedBudget();
        FlightSpeedBudget burst = new FlightSpeedBudget();
        for (int tick = 0; tick < 300; tick++) {
            check(smooth.accept(0.25, HOVER, tick), "legal smooth movement");
            check(burst.accept(tick % 3 == 0 ? 0.75 : 0, HOVER, tick), "legal packet bursts");
        }
        FlightSpeedBudget fast = new FlightSpeedBudget();
        for (int tick = 0; tick < 5; tick++) check(fast.accept(0.75, HOVER, tick), "bounded initial burst");
        check(!fast.accept(0.75, HOVER, 5), "sustained overspeed rejected by sixth tick");
        check(!fast.accept(0.01, HOVER, 5), "rejection does not refill");

        FlightSpeedBudget idle = new FlightSpeedBudget();
        check(idle.accept(0, HOVER, 0), "initial idle");
        check(!idle.accept(1.26, HOVER, 1_000_000), "idle cannot bank unlimited allowance");
        FlightSpeedBudget resume = new FlightSpeedBudget();
        check(resume.accept(1.25, HOVER, 0), "consume initial budget before stowing");
        check(resume.accept(0.75, HOVER, 100), "long stowed interval restores bounded burst allowance");
        check(!resume.accept(0.51, HOVER, 100), "long idle still restores at most two ticks");
        check(!new FlightSpeedBudget().accept(30, 9.25, 0), "storm still rejects 30-block jump");
        FlightSpeedBudget sameTick = new FlightSpeedBudget();
        check(sameTick.accept(0.75, HOVER, 0), "first packet burst");
        check(!sameTick.accept(0.75, HOVER, 0), "same tick cannot refill twice");

        FlightSpeedBudget lower = new FlightSpeedBudget();
        check(lower.accept(0, 10, 0), "high-tier initial allowance");
        check(!lower.accept(1.26, HOVER, 0), "lower tier clamps immediately");
        FlightSpeedBudget raise = new FlightSpeedBudget();
        check(raise.accept(1, HOVER, 0), "use initial allowance");
        check(!raise.accept(0.5, 10, 0), "same-tick tier raise cannot mint allowance");
        check(!raise.accept(0.01, HOVER, 0), "tier cycling does not reset debt");
        FlightSpeedBudget clock = new FlightSpeedBudget();
        check(clock.accept(0, HOVER, 5), "initialize clock");
        check(!clock.accept(0.1, HOVER, 4), "backwards clock cannot mint allowance");
        check(!clock.accept(0.1, HOVER, 5), "returning to old high-water tick cannot refill");
        check(!clock.accept(Double.NaN, HOVER, 3), "invalid old packet is rejected");
        check(!clock.accept(0.1, HOVER, 5), "invalid packet cannot lower clock high-water mark");
        check(clock.accept(0.1, HOVER, 6), "next new server tick refills normally");

        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            check(!new FlightSpeedBudget().accept(invalid, HOVER, 0), "invalid distance");
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE, 0, -1}) {
            check(!new FlightSpeedBudget().accept(0.1, invalid, 0), "invalid allowance");
        }
        FlightSpeedBudget adversarial = new FlightSpeedBudget();
        Random random = new Random(1420);
        double accepted = 0;
        for (int tick = 0; tick < 1000; tick++) {
            for (int packet = 0; packet < 4; packet++) {
                double distance = random.nextDouble() * 1.5;
                if (adversarial.accept(distance, HOVER, tick)) accepted += distance;
            }
            check(accepted <= HOVER * (tick + 2) + 1.0e-7, "cumulative server-tick bound");
        }
        for (double acceleration : new double[]{0.05, 2.5, 20.0}) {
            for (double target : new double[]{5, 30, 60}) {
                FlightSpeedTransition transition = new FlightSpeedTransition();
                transition.change(120, target, acceleration, 100);
                double client = 120;
                double prior = 120;
                for (int tick = 100; tick < 4100; tick++) {
                    double envelope = transition.speed(target, tick);
                    check(envelope <= prior + 1.0e-9, "transition allowance never grows with time");
                    check(envelope + 1.0e-9 >= client, "legal client inertia fits authorized envelope");
                    check(envelope >= target, "transition cannot undershoot target speed");
                    prior = envelope;
                    client += (target - client) * acceleration / 20.0;
                }
                check(prior < target + 0.01, "even minimum acceleration has a finite decay tail");
            }
        }
        FlightSpeedTransition transition = new FlightSpeedTransition();
        transition.change(120, 5, 2.5, 0);
        double inherited = transition.speed(5, 10);
        transition.change(5, 30, 2.5, 10);
        check(Math.abs(transition.speed(30, 10) - Math.max(30, inherited)) < 1.0e-9,
                "changing tier during deceleration inherits only the remaining envelope");
        check(transition.speed(30, 1000) < 30.001, "repeated transition still expires");
        FlightSpeedTransition toggles = new FlightSpeedTransition();
        toggles.change(120, 5, 2.5, 0);
        double tierSpeed = 5;
        for (int tick = 2; tick <= 200; tick += 2) {
            double nextSpeed = tierSpeed == 5 ? 30 : 5;
            toggles.change(tierSpeed, nextSpeed, 2.5, tick);
            tierSpeed = nextSpeed;
        }
        check(toggles.speed(tierSpeed, 200) <= 30.001,
                "lower-tier toggles cannot indefinitely renew the old STORM envelope");
        FlightSpeedBudget transitionBudget = new FlightSpeedBudget();
        check(transitionBudget.accept(18.5, 9.25, 0), "consume storm credit");
        transition.change(120, 5, 2.5, 0);
        check(!transitionBudget.accept(0.1, (transition.speed(5, 0) * 1.5 + 5) / 20, 0),
                "transition does not mint same-tick movement credit");
        System.out.println("WINGS_SPEED_BUDGET_PASSED assertions=" + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
