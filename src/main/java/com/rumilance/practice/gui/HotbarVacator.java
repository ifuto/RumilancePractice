package com.rumilance.practice.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Empties a player's hotbar (slots 0-8) for as long as a NARENA menu is open, and hands the
 * stashed items back when the menu chain ends (user spec 2026-10-04: 「GUI開いた際はホットバー空にする」).
 *
 * <p>The items are never destroyed: they are cloned into a per-player stash and restored into
 * the SAME slots afterwards. Restoration is deliberately conservative — it only fills slots
 * that are currently EMPTY, so an item a menu handed out meanwhile (the leave-queue dye, a kit
 * editor stash, ...) is never overwritten and never duplicated.</p>
 *
 * <p>Menus that already own the player's own inventory rows ({@link BottomInventoryClickHandler}
 * — the KIT SELECT chip panel and the kit/preset editors) are skipped: they stash and repaint
 * those rows themselves, and double-stashing would restore the wrong snapshot.</p>
 */
public final class HotbarVacator {

    /** Vanilla hotbar width (inventory slots 0-8). */
    public static final int HOTBAR_SIZE = 9;

    private final Map<UUID, ItemStack[]> stash = new ConcurrentHashMap<>();
    /**
     * Off switch used by the GUI snapshot tool: rendering a screen must not empty the
     * operator's hotbar when nothing is actually being opened.
     */
    private volatile boolean suspended;

    /** True while this player's hotbar is parked in the stash. */
    public boolean isVacated(UUID playerId) {
        return playerId != null && stash.containsKey(playerId);
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    /**
     * Parks the hotbar. Idempotent: a menu chain (screen A → screen B) keeps the FIRST
     * snapshot, so returning always restores what the player actually held before the chain.
     */
    public void vacate(Player player) {
        if (suspended || player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (stash.containsKey(id)) {
            return;
        }
        ItemStack[] saved = new ItemStack[HOTBAR_SIZE];
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack held = player.getInventory().getItem(i);
            saved[i] = held == null || held.getType().isAir() ? null : held.clone();
            player.getInventory().setItem(i, null);
        }
        stash.put(id, saved);
    }

    /**
     * Puts the stashed items back into slots that are still empty, then forgets the stash.
     * Safe to call when nothing was stashed (no-op).
     */
    public void restore(Player player) {
        if (player == null) {
            return;
        }
        ItemStack[] saved = stash.remove(player.getUniqueId());
        if (saved == null || !player.isOnline()) {
            return;
        }
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack item = saved[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack current = player.getInventory().getItem(i);
            if (current == null || current.getType().isAir()) {
                player.getInventory().setItem(i, item);
            }
        }
    }

    /** Forgets a player's stash without restoring it (the player left / was sent elsewhere). */
    public void drop(UUID playerId) {
        if (playerId != null) {
            stash.remove(playerId);
        }
    }

    /** Emergency cleanup on plugin shutdown. */
    public void dropAll() {
        stash.clear();
    }
}
