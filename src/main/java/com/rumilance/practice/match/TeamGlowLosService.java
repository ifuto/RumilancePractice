package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.settings.SettingsService;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Team glow without wallhack ESP. Vanilla {@code setGlowing(true)} always outlines through
 * walls; this service keeps the server flag off and only pushes glowing metadata to viewers
 * that currently have line-of-sight (ProtocolLib). Without ProtocolLib, glow is fully off.
 *
 * <p>Runs for every match that has RED/BLUE colours (1v1 and party), not only
 * {@link MatchSession#isTeamMatch()}.</p>
 */
public final class TeamGlowLosService {

    private static final byte GLOW_BIT = 0x40;

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;
    private final SettingsService settingsService;
    private final Map<UUID, Set<UUID>> glowVisible = new ConcurrentHashMap<>();
    private ProtocolManager protocolManager;
    private BukkitTask tickTask;
    private boolean enabled;

    public TeamGlowLosService(Plugin plugin, MatchRegistry matchRegistry, SettingsService settingsService) {
        this.plugin = plugin;
        this.matchRegistry = matchRegistry;
        this.settingsService = settingsService;
    }

    public void start() {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().info("[TeamGlow] ProtocolLib missing - outline glow disabled (anti-ESP).");
            enabled = false;
            return;
        }
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLos, 10L, 4L);
            enabled = true;
            plugin.getLogger().info("[TeamGlow] LOS-only glow active (ProtocolLib).");
        } catch (Throwable t) {
            enabled = false;
            plugin.getLogger().log(Level.WARNING, "[TeamGlow] Failed to hook ProtocolLib; glow disabled.", t);
        }
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.setGlowing(false);
            clearViewerGlows(online);
            TeamGlowColors.clear(online);
        }
        glowVisible.clear();
        enabled = false;
    }

    public void clearEntityGlow(Player player) {
        if (player == null) {
            return;
        }
        player.setGlowing(false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            sendGlowFlag(viewer, player, false);
            TeamGlowColors.clearTarget(viewer, player);
        }
        glowVisible.remove(player.getUniqueId());
    }

    private void tickLos() {
        if (!enabled) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!settingsService.get(viewer).teamGlow()) {
                clearViewerGlows(viewer);
                continue;
            }
            MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
            if (session == null || !session.isTeamMatch() || session.participants().size() < 2) {
                clearViewerGlows(viewer);
                continue;
            }
            Set<UUID> visible = glowVisible.computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            Set<UUID> next = ConcurrentHashMap.newKeySet();
            for (UUID id : session.participants()) {
                if (id.equals(viewer.getUniqueId())) {
                    continue;
                }
                Player target = Bukkit.getPlayer(id);
                if (target == null || !target.isOnline()) {
                    continue;
                }
                if (target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    sendGlowFlag(viewer, target, false);
                    continue;
                }
                // Never leave vanilla glowing on (that is always ESP).
                target.setGlowing(false);
                boolean los = canSeeGlow(viewer, target);
                if (los) {
                    next.add(id);
                    TeamGlowColors.apply(viewer, target, session);
                }
                if (MatchTeamVisuals.fightTeamOf(viewer.getScoreboard(), target) == null) {
                    continue;
                }
                if (los != visible.contains(id)) {
                    sendGlowFlag(viewer, target, los);
                    if (!los) {
                        TeamGlowColors.clearTarget(viewer, target);
                    }
                } else if (los) {
                    sendGlowFlag(viewer, target, true);
                }
            }
            for (UUID was : visible) {
                if (!next.contains(was)) {
                    Player target = Bukkit.getPlayer(was);
                    if (target != null) {
                        sendGlowFlag(viewer, target, false);
                        TeamGlowColors.clearTarget(viewer, target);
                    }
                }
            }
            visible.clear();
            visible.addAll(next);
        }
    }

    private void clearViewerGlows(Player viewer) {
        Set<UUID> visible = glowVisible.remove(viewer.getUniqueId());
        if (visible != null) {
            for (UUID id : visible) {
                Player target = Bukkit.getPlayer(id);
                if (target != null) {
                    sendGlowFlag(viewer, target, false);
                    TeamGlowColors.clearTarget(viewer, target);
                }
            }
        }
        TeamGlowColors.clear(viewer);
    }

    private static boolean canSeeGlow(Player viewer, Player target) {
        if (!viewer.canSee(target) || viewer.getWorld() != target.getWorld()) {
            return false;
        }
        if (viewer.getLocation().distanceSquared(target.getLocation()) > 48 * 48) {
            return false;
        }
        return viewer.hasLineOfSight(target);
    }

    private void sendGlowFlag(Player viewer, Player target, boolean glow) {
        if (!enabled || protocolManager == null || viewer == null || target == null || !viewer.isOnline()) {
            return;
        }
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, target.getEntityId());
            byte flags = entityFlagByte(target, glow);
            WrappedDataValue flagValue = new WrappedDataValue(
                    0, WrappedDataWatcher.Registry.get(Byte.class), flags);
            packet.getDataValueCollectionModifier().write(0, List.of(flagValue));
            protocolManager.sendServerPacket(viewer, packet);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[TeamGlow] send failed", t);
        }
    }

    private static byte entityFlagByte(Player target, boolean glow) {
        byte flags = 0;
        if (target.isSneaking()) {
            flags |= 0x02;
        }
        if (target.isSprinting()) {
            flags |= 0x08;
        }
        if (target.isSwimming()) {
            flags |= 0x10;
        }
        if (target.isInvisible()) {
            flags |= 0x20;
        }
        if (glow) {
            flags |= GLOW_BIT;
        }
        if (target.isGliding()) {
            flags |= (byte) 0x80;
        }
        return flags;
    }
}
