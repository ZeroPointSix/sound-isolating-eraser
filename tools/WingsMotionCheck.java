import com.zeropointsix.eraser.client.WingsMotion;

public class WingsMotionCheck {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        WingsMotion still = new WingsMotion(0);
        for (int i = 0; i < 200; i++) {
            still.tick(false, 0, 0);
            check(still.deployment() == 0, "Closed endpoint oscillates");
        }
        WingsMotion wings = new WingsMotion(0);
        float last = 0;
        for (int tick = 0; tick < 6; tick++) {
            wings.tick(true, 0, 0);
            for (int sub = 0; sub <= 10; sub++) {
                float amount = wings.sample(sub / 10f).deployment();
                check(amount >= last - 0.00001f, "Deploy is not monotonic");
                last = amount;
            }
        }
        check(wings.deployment() == 1, "Deploy must take exactly six ticks");
        for (int i = 0; i < 200; i++) {
            wings.tick(true, 0, 0);
            check(wings.sample(0.5f).deployment() == 1, "Open endpoint oscillates");
        }
        for (int tier : new int[] {0, 2, 3, 1, 0}) {
            for (int i = 0; i < 100; i++) {
                float before = wings.sample(1).phase();
                wings.tick(true, tier, tier / 3f);
                float after = wings.sample(0).phase();
                check(Math.abs(Math.sin(before) - Math.sin(after)) < 0.00001,
                        "Tier change resets phase");
                var pose = wings.sample(0.5f);
                check(Float.isFinite(pose.phase()) && pose.sweep() >= 0 && pose.sweep() <= 1,
                        "Invalid interpolated pose");
            }
        }
        for (int tick = 0; tick < 6; tick++) wings.tick(false, 0, 0);
        check(wings.deployment() == 0, "Retract must take six ticks");
        for (int i = 0; i < 10; i++) wings.tick(false, 0, 0);
        check(wings.deployment() == 0, "Retract never settles");
        check(still.deployment() == 0, "Players share pose state");
        System.out.println("WINGS_MOTION_PASSED assertions=" + assertions);
    }
}
