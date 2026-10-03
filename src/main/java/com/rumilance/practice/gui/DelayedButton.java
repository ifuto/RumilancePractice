package com.rumilance.practice.gui;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 木時差式ボタン — the delayed wooden button.
 *
 * <p>A click does not fire the action immediately. It <em>presses</em> the button: the wooden
 * click-on sound plays and the tile is genuinely TAKEN off its slot onto the player's cursor —
 * the real stack, the slot visibly empties, exactly like a vanilla pickup (a press used to put
 * a cloned ghost on the cursor while the tile stayed put, which read as a dupe). After
 * {@link #PRESS_TICKS} (0.2s) the button pops back: the tile returns to its slot, the cursor
 * empties with the click-off sound, and only then does the real action run — a category list
 * opens, a setting flips, a sub-screen appears. It makes every menu navigation feel like
 * operating a real switch instead of teleporting between screens.</p>
 *
 * <p>Menus opt in by prefixing the tile's action with {@link #PREFIX}; {@link GuiListener}
 * intercepts it centrally, unwraps the action and re-dispatches it after the release, so no
 * menu has to know about the timing (and menus with a plain action keep the old instant
 * behaviour). Pressing twice inside the window is swallowed: the button is already down.</p>
 */
public final class DelayedButton {

    /** Action prefix marking a tile as a delayed button. */
    public static final String PREFIX = "delay:";
    /** 0.2 seconds at 20 TPS — the requested press/release gap. */
    public static final long PRESS_TICKS = 4L;
    /** Sound of a wooden button going down / coming back up. */
    private static final Sound PRESS = Sound.BLOCK_WOODEN_BUTTON_CLICK_ON;
    private static final Sound RELEASE = Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF;
    /** Volume of both clicks. */
    private static final float VOLUME = 0.8f;
    /** Pressing sounds sharper (1.35); the release is the plain vanilla pitch (1.0). */
    private static final float PRESS_PITCH = 1.35f;
    private static final float RELEASE_PITCH = 1.0f;

    /** One live press: the tile taken off its slot, kept so it can be put back. */
    private record PendingPress(Inventory inventory, int slot, ItemStack taken, long pressedAt) {
    }

    /** Players with a button currently held down, so a second press cannot re-trigger it. */
    private static final Map<UUID, PendingPress> PENDING = new ConcurrentHashMap<>();

    private DelayedButton() {
    }

    /** Marks an action as a delayed button action. */
    public static String wrap(String action) {
        return PREFIX + action;
    }

    public static boolean isDelayed(String action) {
        return action != null && action.startsWith(PREFIX);
    }

    /** The action to run once the button pops back up. */
    public static String unwrap(String action) {
        return isDelayed(action) ? action.substring(PREFIX.length()) : action;
    }

    /** True while this player's button is down (used to clear the cursor on close). */
    public static boolean isPressing(UUID playerId) {
        return PENDING.containsKey(playerId);
    }

    /**
     * Forgets a pending press and puts the taken tile back into its slot (idempotent — the
     * release path restores first, then removes the pending entry). The inventory may already
     * be closed; writing into a closed container is harmless and keeps the tile from vanishing.
     */
    public static void cancel(UUID playerId) {
        PendingPress pending = PENDING.remove(playerId);
        restore(pending);
    }

    private static void restore(PendingPress pending) {
        if (pending == null || pending.taken() == null || pending.inventory() == null) {
            return;
        }
        try {
            int size = pending.inventory().getSize();
            if (pending.slot() >= 0 && pending.slot() < size) {
                pending.inventory().setItem(pending.slot(), pending.taken());
            }
        } catch (Throwable ignored) {
            // A closed/foreign inventory must never break the release pipeline.
        }
    }

    /**
     * Presses the button: the tile is taken off its slot onto the cursor (the slot empties),
     * click-on sound, then {@link #PRESS_TICKS} later the tile pops back into its slot, the
     * cursor empties with the click-off sound and {@code onComplete} runs.
     *
     * @param clickedInventory the inventory the tile was clicked in (its slot is emptied)
     * @param slot             the slot the tile was taken from
     * @param action           the unwrapped action handed back to {@code onComplete}
     * @param onComplete       runs on the main thread after the release
     * @return false when the press was refused (already pressed, offline player, no plugin)
     */
    public static boolean press(Plugin plugin, Player player, Inventory clickedInventory, int slot,
                                String action, Consumer<String> onComplete) {
        if (plugin == null || player == null || !player.isOnline() || action == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        PendingPress live = PENDING.get(id);
        if (live != null && now - live.pressedAt() < PRESS_TICKS * 50L) {
            return false; // still down: swallow the second press
        }
        // TAKE the real tile: the actual stack moves slot → cursor, nothing is cloned.
        ItemStack taken = null;
        try {
            ItemStack inSlot = clickedInventory == null ? null : clickedInventory.getItem(slot);
            if (inSlot != null && !inSlot.getType().isAir()) {
                taken = inSlot;
                clickedInventory.setItem(slot, null);
                player.setItemOnCursor(taken);
            }
        } catch (Throwable ignored) {
            // A missing cursor/slot (spectator edge, another plugin) must not kill the action;
            // fall back to a press without a visual take.
            taken = null;
            try {
                player.setItemOnCursor(null);
            } catch (Throwable ignoredToo) {
            }
        }
        PendingPress pending = new PendingPress(clickedInventory, slot, taken, now);
        PENDING.put(id, pending);
        try {
            player.playSound(player.getLocation(), PRESS, VOLUME, PRESS_PITCH);
        } catch (Throwable ignored) {
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingPress released = PENDING.remove(id);
            if (!player.isOnline()) {
                // Offline mid-press: the tile stays in the (server-side) inventory, cursor dies
                // with the connection — nothing to restore on screen.
                return;
            }
            restore(released);
            try {
                player.setItemOnCursor(null);
                player.playSound(player.getLocation(), RELEASE, VOLUME, RELEASE_PITCH);
            } catch (Throwable ignored) {
            }
            onComplete.accept(action);
        }, PRESS_TICKS);
        return true;
    }
}
