package com.rumilance.practice.match;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.rumilance.practice.settings.SettingsService;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps packet-only team leather looks in sync when players track each other or change armor.
 */
public final class TeamColoredArmorListener implements Listener {

    private final TeamColoredArmorService service;
    private final SettingsService settingsService;

    public TeamColoredArmorListener(TeamColoredArmorService service, SettingsService settingsService) {
        this.service = service;
        this.settingsService = settingsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player viewer = event.getPlayer();
        if (!settingsService.get(viewer).teamColoredArmor()) {
            return;
        }
        service.refreshViewer(viewer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        service.scheduleRefreshTarget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        service.scheduleRefreshTarget(target);
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            if (!settingsService.get(viewer).teamColoredArmor()) {
                continue;
            }
            service.refreshViewer(viewer);
        }
    }

    /**
     * Party-fight leather flicker fix ("ダメージ受けた瞬間革装備じゃなくなるバグ"): the team
     * leather look is packet-only, but every armor-durability change re-broadcasts the REAL
     * (undamaged→damaged meta) equipment to viewers, overwriting the fake leather for ~2 ticks
     * until the refresh pulses re-send it. Freezing armor durability in team matches removes the
     * overwrite at its source: real items keep their display, packets never fire, and the
     * packet-only team look stays stable. Side effect is intended: armor does not break in
     * practice party fights.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item == null || !service.isInTeamMatch(event.getPlayer())) {
            return;
        }
        String name = item.getType().name();
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || item.getType() == org.bukkit.Material.ELYTRA) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearForPlayer(event.getPlayer());
    }
}
