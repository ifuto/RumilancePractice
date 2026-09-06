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
        if (kit == null) {
            return false;
        }
        // "Bed Explosion" kits are bed-bombing kits: the bed is the weapon, so the rule implies
        // the placement permission even when the kit does not allow general block placing.
        return kit.allowsBlockPlace() || kit.bedExplosion();
    }

    public static boolean mayBreak(KitDefinition kit, Material type, boolean playerPlaced) {
        if (kit == null || isGlass(type)) {
            return false;
        }
        // A bed-bombing kit must be able to clean up (or re-use) its own beds.
        if (kit.bedExplosion() && type != null && type.name().endsWith("_BED")) {
            return true;
        }
        if (kit.breakPlayerPlacedOnly()) {
            return playerPlaced;
        }
        return kit.blockBreak() || kit.isExplicitlyBreakable(type.name());
    }
}
