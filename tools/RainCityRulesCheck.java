import com.zeropointsix.eraser.yuji.city.RainCityRules;

public final class RainCityRulesCheck {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(RainCityRules.day(0) == 0, "arrival day");
        check(RainCityRules.day(11999) == 0, "before first night");
        check(RainCityRules.day(12000) == 1, "first night");
        check(RainCityRules.day(35999) == 1 && RainCityRules.day(36000) == 2, "twenty minute progression");
        int[] days = {0, 1, 2, 3, 5, 7, 10, 15};
        int[] daytime = {30, 60, 85, 110, 160, 210, 285, 300};
        int[] nighttime = {45, 90, 128, 165, 240, 300, 300, 300};
        int[] hordes = {0, 50, 70, 180, 130, 170, 230, 660};
        int[] anger = {0, 1, 1, 2, 3, 4, 5, 8};
        for (int i = 0; i < days.length; i++) {
            int d = days[i];
            check(RainCityRules.cap(d, false, 300) == daytime[i], "day cap " + d);
            check(RainCityRules.cap(d, true, 300) == nighttime[i], "night cap " + d);
            check(RainCityRules.horde(d, 1) == hordes[i], "horde " + d);
            check(RainCityRules.chennu(d) == anger[i], "regional anger " + d);
        }
        check(RainCityRules.cap(0, true, 20) == 20, "configured cap includes night multiplier");
        check(RainCityRules.horde(10, 0) == 0, "disabled waves");
        check(Math.abs(RainCityRules.specialWeight(10) - 4.15) < 1e-9, "special weight");
        check(RainCityRules.strength(1000) == 2, "strength ceiling");
        check(RainCityRules.speed(1000) == 1.15, "speed ceiling");
        check(RainCityRules.layers(1000) == 6, "skin ceiling");
        check(RainCityRules.day(Long.MAX_VALUE) == 1_000_000, "bounded day conversion");
        check(!RainCityRules.night(24000) && RainCityRules.night(36000), "night boundaries");
        System.out.println("RAIN_CITY_RULES_PASS assertions=" + assertions);
    }
}
