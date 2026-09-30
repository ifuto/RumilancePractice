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

    /** {@value} も含む凡例: SHULKER_BOX / WHITE_SHULKER_BOX … BLACK_SHULKER_BOX の17種。 */
    public static boolean isShulkerBox(Material type) {
        return type != null && type.name().endsWith("SHULKER_BOX");
    }

    public static boolean mayPlace(KitDefinition kit) {
        if (kit == null || kit.forceAdventure()) {
            return false;
        }
        // "Bed Explosion" kits are bed-bombing kits: the bed is the weapon, so the rule implies
        // the placement permission even when the kit does not allow general block placing.
        return kit.allowsBlockPlace() || kit.bedExplosion();
    }

    /**
     * ユーザー要望 (2026-09-30): シュルカーボックスは「持ち運び可能なストレージ」として、
     * キットの設置可否やアドベンチャー指定に関係なく常に設置を許可する。破壊は
     * （アリーナ本体保護のため）そのマッチ/FFA 内でプレイヤーが置いたものに限る。
     */
    public static boolean mayPlace(KitDefinition kit, Material type) {
        if (isShulkerBox(type)) {
            return true;
        }
        return mayPlace(kit);
    }

    public static boolean mayBreak(KitDefinition kit, Material type, boolean playerPlaced) {
        if (kit == null || isGlass(type)) {
            return false;
        }
        // シュルカー例外: 設置は常に可能なので、自分が置いたシュルカーは壊せてよい
        // （中身ごとアイテムで回収できるのがこの許可の目的）。アリーナ生成物は対象外。
        if (isShulkerBox(type)) {
            return playerPlaced;
        }
        if (kit.forceAdventure()) {
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
