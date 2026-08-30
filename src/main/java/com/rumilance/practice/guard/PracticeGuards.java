package com.rumilance.practice.guard;

import com.rumilance.practice.kit.KitLayoutContents;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Central guard clauses for practice-plugin invariants. Keeps listener/GUI code thin and
 * lets JUnit exercise the same branching matrix without a running server.
 */
public final class PracticeGuards {

    private static final Set<String> VIP_PLUS_TRIM_MATERIALS = Set.of(
            "quartz", "gold", "diamond", "amethyst"
    );
    private static final Set<String> VIP_PLUS_TRIM_PATTERNS = Set.of(
            "silence", "snout"
    );

    private PracticeGuards() {
    }

    // --- Kit layout ---

    public static boolean hasValidEditorLayout(ItemStack[] layout) {
        return layout != null && layout.length >= 41;
    }

    /**
     * True when {@code edited} contains exactly the same non-placeholder items as {@code baseline}
     * (rearrange-only saves).
     */
    public static boolean kitLayoutUnchanged(ItemStack[] baseline, ItemStack[] edited) {
        if (baseline == null || edited == null) {
            return false;
        }
        if (!hasValidEditorLayout(edited)) {
            return false;
        }
        ItemStack[] scrubbed = edited.clone();
        KitLayoutContents.stripPlaceholders(scrubbed);
        return KitLayoutContents.sameContentsIgnoringCosmetics(baseline, scrubbed);
    }

    /** Caps total totems of undying in a player's inventory (storage + offhand). */
    public static void enforceTotemCap(Player player, int max) {
        if (player == null || max < 0) {
            return;
        }
        int remaining = max;
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType() != Material.TOTEM_OF_UNDYING) {
                continue;
            }
            int amount = stack.getAmount();
            if (amount <= remaining) {
                remaining -= amount;
                continue;
            }
            int keep = remaining;
            remaining = 0;
            if (keep <= 0) {
                storage[i] = null;
            } else {
                stack.setAmount(keep);
            }
        }
        inventory.setStorageContents(storage);
        ItemStack off = inventory.getItemInOffHand();
        if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) {
            int amount = off.getAmount();
            if (amount > remaining) {
                if (remaining <= 0) {
                    inventory.setItemInOffHand(null);
                } else {
                    off.setAmount(remaining);
                    inventory.setItemInOffHand(off);
                }
            }
        }
    }

    // --- Rank ---

    public static boolean effectiveVipOrAbove(
            PlayerRank rank,
            boolean permVip,
            boolean permVipPlus,
            boolean permAdmin
    ) {
        PlayerRank resolved = rank == null ? PlayerRank.NORM : rank;
        if (resolved.isVipOrAbove()) {
            return true;
        }
        if (permAdmin) {
            return true;
        }
        if (permVipPlus) {
            return true;
        }
        return permVip;
    }

    public static boolean effectiveVipPlusOrAbove(
            PlayerRank rank,
            boolean permVipPlus,
            boolean permAdmin
    ) {
        PlayerRank resolved = rank == null ? PlayerRank.NORM : rank;
        if (resolved.isVipPlusOrAbove()) {
            return true;
        }
        if (permAdmin) {
            return true;
        }
        return permVipPlus;
    }

    public static boolean effectiveAdmin(PlayerRank rank, boolean permAdmin) {
        PlayerRank resolved = rank == null ? PlayerRank.NORM : rank;
        return resolved == PlayerRank.ADMIN || permAdmin;
    }

    // --- Smithing trim ---

    public static boolean trimEditorAllowedInState(PlayerState state) {
        if (state == null) {
            return false;
        }
        if (state == PlayerState.LOBBY) {
            return true;
        }
        if (state == PlayerState.OPENING_GUI) {
            return true;
        }
        if (state == PlayerState.IDLE) {
            return true;
        }
        if (state == PlayerState.EDITING_KIT) {
            return true;
        }
        return false;
    }

    public static boolean isTrimmableArmorMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return false;
        }
        if (materialName.endsWith("_HELMET")) {
            return true;
        }
        if (materialName.endsWith("_CHESTPLATE")) {
            return true;
        }
        if (materialName.endsWith("_LEGGINGS")) {
            return true;
        }
        return materialName.endsWith("_BOOTS");
    }

    public static boolean trimMaterialAllowed(boolean vipPlusOrAbove, String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            return false;
        }
        if (vipPlusOrAbove) {
            return true;
        }
        return !VIP_PLUS_TRIM_MATERIALS.contains(materialKey.toLowerCase(Locale.ROOT));
    }

    public static boolean trimPatternAllowed(boolean vipPlusOrAbove, String patternKey) {
        if (patternKey == null || patternKey.isBlank()) {
            return false;
        }
        if (vipPlusOrAbove) {
            return true;
        }
        return !VIP_PLUS_TRIM_PATTERNS.contains(patternKey.toLowerCase(Locale.ROOT));
    }

    public static boolean trimSelectionAllowed(
            boolean vipPlusOrAbove,
            String materialKey,
            String patternKey
    ) {
        if (!trimMaterialAllowed(vipPlusOrAbove, materialKey)) {
            return false;
        }
        return trimPatternAllowed(vipPlusOrAbove, patternKey);
    }

    // --- Queue ---

    public static boolean canEnterQueue(MatchMode mode, boolean alreadyQueued) {
        if (mode == null) {
            return false;
        }
        if (mode == MatchMode.FFA) {
            return false;
        }
        if (alreadyQueued) {
            return false;
        }
        return true;
    }

    public static boolean queueEntriesSamePool(QueueService.QueueEntry a, QueueService.QueueEntry b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.mode() != b.mode()) {
            return false;
        }
        if (!a.kitId().equalsIgnoreCase(b.kitId())) {
            return false;
        }
        PlayerPlatform pa = a.platform() == null ? PlayerPlatform.JAVA : a.platform();
        PlayerPlatform pb = b.platform() == null ? PlayerPlatform.JAVA : b.platform();
        return pa == pb;
    }

    public static boolean canPairInQueue(
            QueueService.QueueEntry a,
            QueueService.QueueEntry b,
            boolean blockSameIp,
            boolean avoidRecent,
            boolean ignoreElo,
            int eloRange,
            UUID recentOpponentOfA,
            UUID recentOpponentOfB
    ) {
        if (a == null || b == null) {
            return false;
        }
        if (a.playerId().equals(b.playerId())) {
            return false;
        }
        if (!queueEntriesSamePool(a, b)) {
            return false;
        }
        if (blockSameIp) {
            if (a.ip() != null && a.ip().equals(b.ip())) {
                return false;
            }
        }
        if (avoidRecent) {
            if (recentOpponentOfA != null && b.playerId().equals(recentOpponentOfA)) {
                return false;
            }
            if (recentOpponentOfB != null && a.playerId().equals(recentOpponentOfB)) {
                return false;
            }
        }
        if (a.mode() == MatchMode.UNRANKED) {
            return true;
        }
        if (ignoreElo) {
            return true;
        }
        if (eloRange < 0) {
            return false;
        }
        return Math.abs(a.elo() - b.elo()) <= eloRange;
    }

    // --- Platform ---

    public static boolean isBedrockName(String name) {
        return name != null && name.startsWith(".");
    }

    // --- Match / duel / border ---

    public static boolean canUseSoloMatchmaking(boolean inParty) {
        return !inParty;
    }

    /**
     * Player states where sending or accepting a duel must be rejected (already committed elsewhere).
     */
    public static boolean canSendOrAcceptDuel(PlayerState state) {
        if (state == null) {
            return false;
        }
        if (state == PlayerState.FIGHTING) {
            return false;
        }
        if (state == PlayerState.COUNTDOWN) {
            return false;
        }
        if (state == PlayerState.PREPARING_MATCH) {
            return false;
        }
        if (state == PlayerState.SPECTATING) {
            return false;
        }
        if (state == PlayerState.FFA) {
            return false;
        }
        if (state == PlayerState.PRACTICE_WAIT) {
            return false;
        }
        if (state == PlayerState.PRACTICE_ACTIVE) {
            return false;
        }
        if (state == PlayerState.EDITING_KIT) {
            return false;
        }
        return true;
    }

    /**
     * Server-side arena soft-wall + per-player border should constrain players during these phases.
     */
    public static boolean arenaBoundsActive(MatchState state) {
        if (state == null) {
            return false;
        }
        if (state == MatchState.ACTIVE) {
            return true;
        }
        if (state == MatchState.COUNTDOWN) {
            return true;
        }
        if (state == MatchState.ENDING) {
            return true;
        }
        return false;
    }

    /**
     * Teammate hits are blocked only when friendly fire is off.
     */
    public static boolean shouldBlockTeammateDamage(boolean teamMatch, boolean teammates, boolean friendlyFire) {
        if (!teamMatch) {
            return false;
        }
        if (!teammates) {
            return false;
        }
        return !friendlyFire;
    }

    /**
     * Computes the per-player {@link org.bukkit.WorldBorder} center X for an arena cuboid.
     */
    public static double matchBorderCenterX(int minX, int maxX) {
        return (minX + maxX + 1) / 2.0;
    }

    /**
     * Computes the per-player {@link org.bukkit.WorldBorder} center Z for an arena cuboid.
     */
    public static double matchBorderCenterZ(int minZ, int maxZ) {
        return (minZ + maxZ + 1) / 2.0;
    }

    /**
     * Vanilla borders are square: cover the larger horizontal dimension (+2 margin).
     */
    public static double matchBorderSize(int minX, int maxX, int minZ, int maxZ) {
        double width = maxX - minX + 1;
        double depth = maxZ - minZ + 1;
        return Math.max(width, depth) + 2;
    }

    // --- Item handling invariants ---

    /**
     * States in which a player must never move items through a <em>vanilla</em> container
     * (chests, barrels, shulkers, hoppers/dispensers inventories...):
     * <ul>
     *   <li>non-combat states ({@link #lobbyProtectedStates(PlayerState)}) — combat kit
     *       items must not exist there at all, so any container access is abuse;</li>
     *   <li>{@code PREPARING_MATCH}/{@code COUNTDOWN}/{@code SPECTATING} — the kit is fixed
     *       and nothing may be stashed or pulled.</li>
     * </ul>
     * Fighting ({@code FIGHTING}/{@code FFA}) is intentionally excluded: kits may place/use
     * blocks, and PvP container rules are governed by kit flags elsewhere.
     */
    public static boolean vanillaContainerItemMoveBlocked(PlayerState state) {
        if (state == null) {
            return true;
        }
        if (lobbyProtectedStates(state)) {
            return true;
        }
        return switch (state) {
            case PREPARING_MATCH, COUNTDOWN, SPECTATING, ENDING -> true;
            default -> false;
        };
    }

    /**
     * States where dropping / picking up loose items is illegal (the lobby inventory is a
     * fixed hotbar and the player must not be carrying kit items at all).
     */
    public static boolean looseItemMoveBlocked(PlayerState state) {
        return lobbyProtectedStates(state)
                || state == PlayerState.PREPARING_MATCH
                || state == PlayerState.COUNTDOWN
                || state == PlayerState.SPECTATING
                || state == PlayerState.ENDING;
    }

    /**
     * Non-combat "hub" states: lobby, menus, queues, duel requests, idle. Matches the
     * protection matrix in {@code LobbyListener#shouldProtect}.
     */
    public static boolean lobbyProtectedStates(PlayerState state) {
        if (state == null) {
            return true;
        }
        return switch (state) {
            case IDLE, LOBBY, OPENING_GUI, QUEUED_RANKED, QUEUED_UNRANKED,
                 REQUESTING_DUEL, EDITING_KIT, PRACTICE_WAIT -> true;
            default -> false;
        };
    }
}
