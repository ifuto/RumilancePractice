package com.rumilance.practice.match;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.rumilance.practice.util.ItemKeys;

/**
 * "Golden Head" mechanic popular on practice servers: when a player eats a golden apple that
 * carries the {@code golden_head} PDC marker (typically given by kits or crafted from a player
 * head during long matches), they receive the standard golden-apple effects plus an extra
 * absorption/regeneration boost. The marker lets servers distinguish the cosmetic golden head
 * item from a regular golden apple without relying on its display name.
 */
public final class GoldenHeadListener implements Listener {

    private static final int EXTRA_ABSORPTION_HEARTS = 2;
    private static final int REGEN_AMPLIFIER = 1;
    private static final int REGEN_DURATION_SECONDS = 10;

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;

    public GoldenHeadListener(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = plugin;
        this.matchRegistry = matchRegistry;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.GOLDEN_APPLE && item.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }
        // Only active inside a match (so lobby/FFA food is untouched unless the FFA mode opts in later).
        if (matchRegistry.byPlayer(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        if (!isGoldenHead(item)) {
            return;
        }
        Player player = event.getPlayer();
        // Schedule one tick later so the vanilla consumption effects apply first, then we stack ours.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    REGEN_DURATION_SECONDS * 20,
                    REGEN_AMPLIFIER,
                    false,
                    true,
                    true));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.ABSORPTION,
                    120 * 20,
                    EXTRA_ABSORPTION_HEARTS - 1,
                    false,
                    true,
                    true));
        });
    }

    public static boolean isGoldenHead(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.goldenHead(), PersistentDataType.BYTE);
    }
}
