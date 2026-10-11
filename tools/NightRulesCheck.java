import com.zeropointsix.eraser.eternalnight.CityLayout;
import com.zeropointsix.eraser.eternalnight.NightRules;
import java.util.Arrays;

public final class NightRulesCheck {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        long[] ticks = {-1, 0, 23999, 24000, 72000, 144000, Long.MAX_VALUE};
        double[] expected = {.02, .02, .02, .10, .26, .50, .50};
        for (int i = 0; i < ticks.length; i++) {
            check(Math.abs(NightRules.entryProbability(ticks[i], .02, .08, .5) - expected[i]) < 1e-9,
                    "insomnia probability boundary " + ticks[i]);
        }
        check(!NightRules.roll(0, 0), "zero chance");
        check(NightRules.roll(1, Math.nextDown(1.0)), "certain chance");
        check(!NightRules.roll(.01, .01), "strict probability boundary");
        NightRules.Doors doors = new NightRules.Doors(new long[0]);
        for (int i = 1; i <= 100; i++) {
            var result = doors.open(i, 100, .01, .5);
            check(result.counted() && result.count() == i, "unique count " + i);
            check(result.exit() == (i == 100), "exact guarantee " + i);
            check(!doors.open(i, 100, .01, 0).counted(), "repeat never rerolls " + i);
        }
        check(new NightRules.Doors(doors.save()).count() == 100, "save/reload");
        check(new NightRules.Doors(new long[]{2, 2, 3}).count() == 2, "corrupt duplicate data");
        check(new NightRules.Doors(new long[0]).count() == 0, "visit reset");
        check(new NightRules.Doors(new long[0]).open(2, 100, .01, 0).exit(), "early random exit");
        NightRules.Doors largeHistory = new NightRules.Doors(new long[0]);
        for (int i = 0; i < 10001; i++) largeHistory.open(i, 100, 0, .5);
        check(new NightRules.Doors(largeHistory.save()).count() == 10001, "written histories are always readable");
        int beforeInvalid = largeHistory.count();
        try {
            largeHistory.open(20000, 100, .01, Double.NaN);
            throw new AssertionError("invalid sample accepted");
        } catch (IllegalArgumentException expectedFailure) {
            check(largeHistory.count() == beforeInvalid, "invalid input cannot mutate history");
        }
        for (int count = 0; count <= 110; count++) {
            check((NightRules.milestone(count) != 0) == Arrays.asList(25, 50, 75, 90).contains(count), "milestone");
        }
        int[] districts = new int[3];
        for (int x = -1024; x < 1024; x += 3) {
            for (int z = -1024; z < 1024; z += 7) {
                int y = CityLayout.surface(x, z);
                check(y >= 64 && y <= 88, "24 block elevation envelope");
                check(Math.abs(y - CityLayout.surface(x + 1, z)) <= 1, "walkable east slope");
                check(Math.abs(y - CityLayout.surface(x, z + 1)) <= 1, "walkable south slope");
            }
        }
        for (int x = -128; x < 128; x++) for (int z = -128; z < 128; z++) {
            districts[CityLayout.district(x * 128, z * 128).ordinal()]++;
            check(CityLayout.surface(x * 64 + 17, z * 64 + 17)
                    == CityLayout.surface(x * 64 + 63, z * 64 + 63), "flat building block");
        }
        check(Math.abs(districts[0] / 65536.0 - .55) < .02, "55% residential");
        check(Math.abs(districts[1] / 65536.0 - .15) < .02, "15% commercial");
        check(Math.abs(districts[2] / 65536.0 - .30) < .02, "30% industrial");
        check(CityLayout.region(-1, 0) != CityLayout.region(0, 0), "negative region floor division");
        check(CityLayout.region(0, 0) == CityLayout.region(511, 511), "512 region boundary");
        System.out.println("NightRulesCheck PASS assertions=" + assertions);
    }
}
