package com.zeropointsix.gravitytest;

import com.zeropointsix.eraser.item.ItemGuideCatalog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Shared server inventory batches and real client window sizes. */
final class ItemGuideProbeCases {
    record Case(String name, int width, int height, int scale, List<ResourceLocation> items) { }

    static List<Case> cases() {
        List<Case> result = new ArrayList<>();
        result.add(new Case("baseline-samples", 960, 540, 2, ItemCategoryServerProbe.GUIDE_ITEMS));
        var remaining = ItemGuideCatalog.entries().keySet().stream()
                .filter(id -> !ItemCategoryServerProbe.GUIDE_ITEMS.contains(id)).toList();
        for (int start = 0; start < remaining.size(); start += 27) {
            result.add(new Case("catalog-" + (start / 27 + 1), 960, 540, 2,
                    remaining.subList(start, Math.min(remaining.size(), start + 27))));
        }
        var focus = List.of("red_nails", "wind_thunder_wings", "enhancement_pill_pack", "super_fertilizer_bag")
                .stream().map(id -> new ResourceLocation("sound_isolating_eraser", id)).toList();
        for (int[] size : new int[][]{{640, 480, 1}, {640, 480, 2}, {800, 600, 2},
                {1920, 1080, 2}, {1920, 1080, 4}, {1920, 480, 2}, {640, 1080, 2}}) {
            result.add(new Case(size[0] + "x" + size[1] + "-s" + size[2], size[0], size[1], size[2], focus));
        }
        return List.copyOf(result);
    }

    private ItemGuideProbeCases() { }
}
