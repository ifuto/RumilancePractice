package com.rumilance.practice.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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
 * click-on sound plays and the tile jumps onto the player's cursor, as if they had picked it
 * up. {@link #PRESS_TICKS} later (0.3s) the button pops back with the click-off sound, the
 * cursor is emptied and only then does the real action run — a category list opens, a setting
 * flips, a sub-screen appears. It makes every menu navigation feel like operating a real
 * switch instead of teleporting between screens.</p>
 *
 * <p>Menus opt in by prefixing the tile's action with {@link #PREFIX}; {@link GuiListener}
 * intercepts it centrally, unwraps the action and re-dispatches it after the release, so no
 * menu has to know about the timing (and menus with a plain action keep the old instant
 * behaviour). Pressing twice inside the window is swallowed: the button is already down.</p>
 */
public final class DelayedButton {

    /** Action prefix marking a tile as a delayed button. */
    public static final String PREFIX = "delay:";
    /** 0.3 seconds at 20 TPS — the requested press/release gap. */
    public static final long PRESS_TICKS = 6L;
    /** Sound of a wooden button going down / coming back up. */
    private static final Sound PRESS = Sound.BLOCK_WOODEN_BUTTON_CLICK_ON;
    private static final Sound RELEASE = Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF;

    /** Players with a button currently held down, so a second press cannot re-trigger it. */
    private static final Map<UUID, Long> PRESSED_AT = new ConcurrentHashMap<>();

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
        return PRESSED_AT.containsKey(playerId);
    }

    /** Forgets a pending press (inventory closed mid-press). The pending task no-ops. */
    public static void cancel(UUID playerId) {
        PRESSED_AT.remove(playerId);
    }

    /**
     * Presses the button: cursor grabs the tile, click-on sound, then {@link #PRESS_TICKS}
     * later the click-off sound and {@code onComplete}.
     *
     * @param visual     the tile the cursor should hold (cloned; never the inventory's stack)
     * @param action     the unwrapped action handed back to {@code onComplete}
     * @param onComplete runs on the main thread after the release
     * @return false when the press was refused (already pressed, offline player, no plugin)
     */
    public static boolean press(Plugin plugin, Player player, ItemStack visual, String action,
                                Consumer<String> onComplete) {
        if (plugin == null || player == null || !player.isOnline() || action == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long pressedAt = PRESSED_AT.get(id);
        if (pressedAt != null && now - pressedAt < PRESS_TICKS * 50L) {
            return false; // still down: swallow the second press
        }
        PRESSED_AT.put(id, now);
        try {
            player.setItemOnCursor(visual == null ? new ItemStack(Material.OAK_BUTTON) : visual.clone());
        } catch (Throwable ignored) {
            // A missing cursor (spectator edge, another plugin) must not kill the action.
        }
        try {
            player.playSound(player.getLocation(), PRESS, 0.8f, 1.0f);
        } catch (Throwable ignored) {
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PRESSED_AT.remove(id);
            if (!player.isOnline()) {
                return;
            }
            try {
                player.setItemOnCursor(null);
                player.playSound(player.getLocation(), RELEASE, 0.8f, 1.0f);
            } catch (Throwable ignored) {
            }
            onComplete.accept(action);
        }, PRESS_TICKS);
        return true;
    }
}
