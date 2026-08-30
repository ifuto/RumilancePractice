package com.rumilance.practice.util;

import com.rumilance.practice.model.KitDefinition;
import org.bukkit.Material;

/** Shared block place/break rules for match and FFA listeners. */
public final class KitBlockRules {

    private KitBlockRules() {
    }

    public static boolean isGlass(Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return name.endsWith("GLASS") || name.endsWith("GLASS_PANE");
    }

    public static boolean mayPlace(KitDefinition kit) {
        return kit != null && kit.allowsBlockPlace();
    }

    public static boolean mayBreak(KitDefinition kit, Material type, boolean playerPlaced) {
        if (kit == null || isGlass(type)) {
            return false;
        }
        if (kit.breakPlayerPlacedOnly()) {
            return playerPlaced;
        }
        return kit.blockBreak() || kit.isExplicitlyBreakable(type.name());
    }
}
