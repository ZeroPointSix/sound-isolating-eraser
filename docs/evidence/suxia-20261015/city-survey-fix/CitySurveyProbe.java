import com.zeropointsix.eraser.eternalnight.CityBuildingAccess;
import com.zeropointsix.eraser.eternalnight.CityLayout;
import com.zeropointsix.eraser.eternalnight.CityTemplates;
import java.util.HashSet;

public class CitySurveyProbe {
    public static void main(String[] args) {
        if (args[0].equals("before")) {
            try {
                CityBuildingAccess.locateBuildings(52L);
            } catch (AssertionError failure) {
                if (!failure.getMessage().startsWith("could not locate every original building class"))
                    throw failure;
                System.out.println("CITY_SEED_52_REPRODUCED: " + failure.getMessage());
                return;
            }
            throw new AssertionError("seed 52 unexpectedly passed before the fix");
        }
        for (long seed : new long[]{0L, 52L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var placements = CityBuildingAccess.locateBuildings(seed);
            var names = new HashSet<String>();
            for (var placement : placements) {
                names.add(placement.name());
                int bx = Math.floorDiv(placement.origin().getX(), 64);
                int bz = Math.floorDiv(placement.origin().getZ(), 64);
                String actual = CityTemplates.CIVIC_LARGE.contains(placement.name())
                        ? CityTemplates.largeCivic(seed, bx, bz)
                        : CityLayout.building(seed, Math.floorDiv(placement.origin().getX(), 16),
                                Math.floorDiv(placement.origin().getZ(), 16));
                if (!placement.name().equals(actual) || Math.abs(bx) > 48 || Math.abs(bz) > 48)
                    throw new AssertionError("invalid placement for seed " + seed);
            }
            if (!names.equals(new HashSet<>(CityTemplates.BUILDINGS))
                    || !placements.equals(CityBuildingAccess.locateBuildings(seed)))
                throw new AssertionError("incomplete or unstable survey for seed " + seed);
            System.out.println("CITY_SURVEY_FIXED_SEED_PASS seed=" + seed + " classes=" + names.size());
        }
        for (long seed = 0; seed < 256; seed++)
            if (CityBuildingAccess.locateBuildings(seed).size() != 12)
                throw new AssertionError("incomplete survey for seed " + seed);
        System.out.println("CITY_SURVEY_256_SEEDS_PASSED");
    }
}
