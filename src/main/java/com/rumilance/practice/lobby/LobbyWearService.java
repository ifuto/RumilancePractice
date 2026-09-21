package com.rumilance.practice.lobby;

import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Lobby wear: the cursed leather boots everyone gets in the hub, plus the elytra that
 * VIP/VIP+ can glide with.
 *
 * <p>Both pieces carry a {@link ItemKeys#lobbyWear()} tag, so this service only ever
 * touches its own items — real kit armour is never overwritten (a slot is only filled
 * when it is empty) and never stripped. A repeating reconciler keeps the truth: lobby
 * players wear them, everybody else does not. On top of that every teleport strips them
 * immediately, because a match must never start with hub cosmetics equipped.</p>
 *
 * <p>VIP glides with a speed cap, VIP+ without one; the cap is applied by clamping the
 * velocity while the player is gliding inside the lobby.</p>
 */
public final class LobbyWearService implements Listener {

    /** PDC values marking our two pieces. */
    private static final String BOOTS = "boots";
    private static final String ELYTRA = "elytra";
    /** Speed cap for the VIP elytra, in blocks per tick. VIP+ is uncapped. */
    private static final double VIP_GLIDE_CAP = 0.85d;
    /** Reconcile + clamp interval in ticks (5x per second). */
    private static final long INTERVAL_TICKS = 4L;

    private final Plugin plugin;
    private final PlayerStateManager stateManager;
    private final RankService rankService;

    public LobbyWearService(Plugin plugin, PlayerStateManager stateManager, RankService rankService) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.rankService = rankService;
    }

    /** Starts the reconciler / glide-clamp loop. */
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (isInLobby(player)) {
                    equip(player);
                    clampVipGlide(player);
                } else {
                    strip(player);
                }
            } catch (Throwable t) {
                // One broken player (bad equipment, a vanished rank row) must not stop the loop.
            }
        }
    }

    /** Hub-ish states: the wear belongs to all of them, not just {@code LOBBY}. */
    private boolean isInLobby(Player player) {
        return stateManager != null
                && PracticeGuards.lobbyProtectedStates(stateManager.getState(player.getUniqueId()));
    }

    /** Fills the boots slot (everyone) and the chest slot (VIP and VIP+) when they are free. */
    public void equip(Player player) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return;
        }
        if (isEmpty(equipment.getBoots())) {
            equipment.setBoots(boots());
        }
        if (hasElytra(player) && isEmpty(equipment.getChestplate())) {
            equipment.setChestplate(elytra());
        }
    }

    /** Removes our boots / elytra — and only ours. */
    public void strip(Player player) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return;
        }
        if (isLobbyWear(equipment.getBoots())) {
            equipment.setBoots(null);
        }
        if (isLobbyWear(equipment.getChestplate())) {
            equipment.setChestplate(null);
        }
    }

    /**
     * TP 時は絶対剥がす: any teleport drops the hub cosmetics instantly, before the player
     * lands wherever they are going. The reconciler puts them back if the destination
     * turns out to be the lobby again.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        strip(player);
        // A hub-to-hub move (e.g. /lobby while already in the hub) must not flash bare feet:
        // put the wear back next tick when the player is still in a lobby state.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isInLobby(player)) {
                equip(player);
            }
        }, 1L);
    }

    /** VIP glides with a speed cap; VIP+ keeps vanilla elytra speed. */
    private void clampVipGlide(Player player) {
        if (!player.isGliding() || rankService == null) {
            return;
        }
        if (!rankService.isVipOrAbove(player) || rankService.isVipPlusOrAbove(player)) {
            return;
        }
        Vector velocity = player.getVelocity();
        if (velocity.lengthSquared() > VIP_GLIDE_CAP * VIP_GLIDE_CAP) {
            player.setVelocity(velocity.normalize().multiply(VIP_GLIDE_CAP));
        }
    }

    /** True for VIP and VIP+ (a null rank service means no elytra for anyone). */
    private boolean hasElytra(Player player) {
        return rankService != null && rankService.isVipOrAbove(player);
    }

    public static boolean isLobbyWear(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(ItemKeys.lobbyWear(), PersistentDataType.STRING);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /** Curse-of-binding leather boots: only this service (or a teleport) takes them off. */
    private static ItemStack boots() {
        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Lobby Boots", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(ItemKeys.lobbyWear(), PersistentDataType.STRING, BOOTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The hub elytra. Bound as well, so a teleport is the only way it comes off. */
    private static ItemStack elytra() {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Lobby Elytra", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(ItemKeys.lobbyWear(), PersistentDataType.STRING, ELYTRA);
            item.setItemMeta(meta);
        }
        return item;
    }
}
