package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.state.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Party (team) match cosmetics only:
 * <ul>
 *   <li>Packet leather armor in team colours when the viewer opts in (default OFF)</li>
 *   <li>Entity glow in team colour when the player opts in (default ON)</li>
 * </ul>
 * Real inventory is never mutated for the leather look.
 */
public final class TeamColoredArmorService {

    private static final Color RED = Color.fromRGB(0xC62828);
    private static final Color BLUE = Color.fromRGB(0x1565C0);

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;
    private final SettingsService settingsService;
    private volatile com.rumilance.practice.spectator.SpectatorService spectatorService;

    public TeamColoredArmorService(Plugin plugin, MatchRegistry matchRegistry, SettingsService settingsService) {
        this.plugin = plugin;
        this.matchRegistry = matchRegistry;
        this.settingsService = settingsService;
    }

    public void setSpectatorService(com.rumilance.practice.spectator.SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    /** Re-apply (or clear) cosmetics for one viewer based on current setting + match. */
    public void refreshViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        MatchSession session = visibleMatch(viewer);
        if (session == null || !session.isTeamMatch()) {
            clearViewer(viewer, session);
            syncGlow(viewer, null);
            return;
        }
        syncGlow(viewer, session);
        if (!settingsService.get(viewer).teamColoredArmor()) {
            clearViewerArmorOnly(viewer, session);
            return;
        }
        applyViewer(viewer, session);
    }

    /** Push team looks to every participant / spectator watching this match. */
    public void refreshMatch(MatchSession session) {
        if (session == null) {
            return;
        }
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                refreshViewer(player);
            }
        }
        if (spectatorService != null) {
            for (UUID specId : spectatorService.spectatorsWatching(session.id())) {
                Player spectator = Bukkit.getPlayer(specId);
                if (spectator != null) {
                    refreshViewer(spectator);
                }
            }
        }
    }

    /** After kit / armor change on {@code changed}: re-send to everyone who can see them. */
    public void refreshTarget(Player changed) {
        if (changed == null) {
            return;
        }
        MatchSession session = matchRegistry.byPlayer(changed.getUniqueId()).orElse(null);
        if (session == null || !session.isTeamMatch()) {
            return;
        }
        syncGlow(changed, session);
        TeamColor color = session.teamColor(changed.getUniqueId());
        for (UUID id : session.participants()) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer == null || viewer.equals(changed) || !settingsService.get(viewer).teamColoredArmor()) {
                continue;
            }
            sendTeamLook(viewer, changed, color);
        }
        if (spectatorService != null) {
            for (UUID specId : spectatorService.spectatorsWatching(session.id())) {
                Player spectator = Bukkit.getPlayer(specId);
                if (spectator != null && !spectator.equals(changed)
                        && settingsService.get(spectator).teamColoredArmor()) {
                    sendTeamLook(spectator, changed, color);
                }
            }
        }
    }

    /** Leaving a match: restore real armor packets and clear glow. */
    public void clearForPlayer(Player player) {
        if (player == null) {
            return;
        }
        clearViewer(player, null);
        player.setGlowing(false);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) || !settingsService.get(online).teamColoredArmor()) {
                continue;
            }
            sendRealArmor(online, player);
        }
    }

    public void scheduleRefreshMatch(MatchSession session) {
        if (session == null) {
            return;
        }
        // Kit / teleport packets overwrite cosmetics - pulse a few times.
        Bukkit.getScheduler().runTask(plugin, () -> refreshMatch(session));
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshMatch(session), 2L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshMatch(session), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshMatch(session), 40L);
    }

    public void scheduleRefreshViewer(Player viewer) {
        if (viewer == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshViewer(viewer));
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshViewer(viewer), 2L);
    }

    public void scheduleRefreshTarget(Player changed) {
        if (changed == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refreshTarget(changed));
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshTarget(changed), 2L);
    }

    private volatile TeamGlowLosService teamGlowLos;

    public void setTeamGlowLosService(TeamGlowLosService teamGlowLos) {
        this.teamGlowLos = teamGlowLos;
    }

    private void syncGlow(Player player, MatchSession session) {
        // Vanilla setGlowing is wallhack ESP - always clear; LOS glow is ProtocolLib-only.
        player.setGlowing(false);
        if (teamGlowLos != null && (session == null || !session.isTeamMatch())) {
            teamGlowLos.clearEntityGlow(player);
        }
    }

    private void applyViewer(Player viewer, MatchSession session) {
        for (UUID id : session.participants()) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || target.equals(viewer)) {
                continue;
            }
            TeamColor color = session.teamColor(id);
            if (color == null) {
                continue;
            }
            sendTeamLook(viewer, target, color);
        }
    }

    private void clearViewerArmorOnly(Player viewer, MatchSession session) {
        if (session == null) {
            return;
        }
        for (UUID id : session.participants()) {
            Player target = Bukkit.getPlayer(id);
            if (target != null) {
                sendRealArmor(viewer, target);
            }
        }
    }

    private void clearViewer(Player viewer, MatchSession session) {
        if (session != null) {
            clearViewerArmorOnly(viewer, session);
            return;
        }
        MatchSession own = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
        if (own != null) {
            clearViewerArmorOnly(viewer, own);
        }
    }

    private MatchSession visibleMatch(Player viewer) {
        MatchSession own = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
        if (own != null) {
            return own;
        }
        if (spectatorService == null) {
            return null;
        }
        return spectatorService.spectatedMatch(viewer.getUniqueId())
                .flatMap(matchRegistry::get)
                .orElse(null);
    }

    private static void sendTeamLook(Player viewer, Player target, TeamColor color) {
        Map<EquipmentSlot, ItemStack> fake = dyedArmor(color);
        viewer.sendEquipmentChange(target, fake);
    }

    private static void sendRealArmor(Player viewer, Player target) {
        viewer.sendEquipmentChange(target, realArmor(target));
    }

    static Map<EquipmentSlot, ItemStack> dyedArmor(TeamColor color) {
        Color dye = color.leatherColor();
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        map.put(EquipmentSlot.HEAD, leather(Material.LEATHER_HELMET, dye));
        map.put(EquipmentSlot.CHEST, leather(Material.LEATHER_CHESTPLATE, dye));
        map.put(EquipmentSlot.LEGS, leather(Material.LEATHER_LEGGINGS, dye));
        map.put(EquipmentSlot.FEET, leather(Material.LEATHER_BOOTS, dye));
        return map;
    }

    static Map<EquipmentSlot, ItemStack> realArmor(Player target) {
        PlayerInventory inv = target.getInventory();
        Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
        map.put(EquipmentSlot.HEAD, copyOrAir(inv.getHelmet()));
        map.put(EquipmentSlot.CHEST, copyOrAir(inv.getChestplate()));
        map.put(EquipmentSlot.LEGS, copyOrAir(inv.getLeggings()));
        map.put(EquipmentSlot.FEET, copyOrAir(inv.getBoots()));
        return map;
    }

    private static ItemStack leather(Material material, Color dye) {
        ItemStack stack = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setColor(dye);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack copyOrAir(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return new ItemStack(Material.AIR);
        }
        return stack.clone();
    }
}
