package com.rumilance.practice.scoreboard;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.rumilance.practice.session.MatchSession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Left TAB column = fighting, right = spectating. Uses {@link Player#setPlayerListOrder(int)}
 * plus ProtocolLib listed dummy rows to pad the first column to 20 when ProtocolLib is present.
 */
public final class TabFightListService {

    private static final int FIGHTER_COLUMN_SIZE = 20;

    private static final String BLANK_PAD_PROFILE = " ";

    private final Plugin plugin;
    private ProtocolManager protocolManager;
    private boolean protocolReady;
    private final Map<UUID, List<UUID>> padsByViewer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastPadCount = new ConcurrentHashMap<>();

    public TabFightListService(Plugin plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            return;
        }
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            protocolReady = true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[TAB] ProtocolLib pad unavailable", t);
        }
    }

    public void apply(MatchSession session, java.util.Collection<? extends Player> online) {
        if (session == null) {
            return;
        }
        List<Player> fighters = new ArrayList<>();
        List<Player> specs = new ArrayList<>();
        for (Player player : online) {
            boolean participant = session.participants().contains(player.getUniqueId());
            if (player.getGameMode() == GameMode.SPECTATOR) {
                specs.add(player);
            } else if (participant) {
                fighters.add(player);
            }
        }
        int fightIndex = 0;
        for (Player fighter : fighters) {
            fighter.setPlayerListOrder(fightIndex++);
        }
        int specIndex = 0;
        for (Player spec : specs) {
            spec.setPlayerListOrder(FIGHTER_COLUMN_SIZE + specIndex++);
        }
        for (Player viewer : online) {
            if (session.participants().contains(viewer.getUniqueId())
                    || viewer.getGameMode() == GameMode.SPECTATOR) {
                padLeftColumn(viewer, fighters.size());
            }
        }
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        player.setPlayerListOrder(0);
        lastPadCount.remove(player.getUniqueId());
        removePads(player);
    }

    private void padLeftColumn(Player viewer, int fighterCount) {
        int missing = Math.max(0, FIGHTER_COLUMN_SIZE - fighterCount);
        Integer previous = lastPadCount.get(viewer.getUniqueId());
        if (previous != null && previous == missing) {
            return;
        }
        removePads(viewer);
        lastPadCount.put(viewer.getUniqueId(), missing);
        if (missing == 0 || !protocolReady || protocolManager == null) {
            return;
        }
        List<UUID> ids = new ArrayList<>(missing);
        List<PlayerInfoData> data = new ArrayList<>(missing);
        for (int i = 0; i < missing; i++) {
            int order = fighterCount + i;
            UUID id = UUID.nameUUIDFromBytes(
                    ("rp-tab-pad-" + viewer.getUniqueId() + "-" + i).getBytes(StandardCharsets.UTF_8));
            ids.add(id);
            WrappedGameProfile profile = new WrappedGameProfile(id, BLANK_PAD_PROFILE);
            data.add(createInfo(id, profile, blankPadDisplay(), order));
        }
        if (sendUpdate(viewer, data, true)) {
            padsByViewer.put(viewer.getUniqueId(), ids);
        }
    }

    private void removePads(Player viewer) {
        List<UUID> ids = padsByViewer.remove(viewer.getUniqueId());
        if (ids == null || ids.isEmpty() || !protocolReady || protocolManager == null) {
            return;
        }
        List<PlayerInfoData> data = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            WrappedGameProfile profile = new WrappedGameProfile(id, BLANK_PAD_PROFILE);
            data.add(createInfo(id, profile, blankPadDisplay(), 0));
        }
        sendUpdate(viewer, data, false);
    }

    private boolean sendUpdate(Player viewer, List<PlayerInfoData> data, boolean add) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
            EnumSet<EnumWrappers.PlayerInfoAction> actions = EnumSet.noneOf(EnumWrappers.PlayerInfoAction.class);
            actions.add(EnumWrappers.PlayerInfoAction.UPDATE_LISTED);
            if (add) {
                actions.add(EnumWrappers.PlayerInfoAction.ADD_PLAYER);
                try {
                    actions.add(EnumWrappers.PlayerInfoAction.valueOf("UPDATE_DISPLAY_NAME"));
                } catch (IllegalArgumentException ignored) {
                    // older ProtocolLib
                }
                try {
                    actions.add(EnumWrappers.PlayerInfoAction.valueOf("UPDATE_LIST_ORDER"));
                } catch (IllegalArgumentException ignored) {
                    // older ProtocolLib
                }
            }
            packet.getPlayerInfoActions().write(0, actions);
            packet.getPlayerInfoDataLists().write(1, data);
            protocolManager.sendServerPacket(viewer, packet);
            if (!add) {
                PacketContainer remove = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                remove.getUUIDLists().write(0, data.stream().map(PlayerInfoData::getProfileId).toList());
                protocolManager.sendServerPacket(viewer, remove);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[TAB] dummy pad send failed", t);
            protocolReady = false;
            return false;
        }
    }

    private static PlayerInfoData createInfo(UUID id, WrappedGameProfile profile,
                                             WrappedChatComponent display, int listOrder) {
        try {
            Constructor<PlayerInfoData> ctor = PlayerInfoData.class.getConstructor(
                    UUID.class, int.class, boolean.class, EnumWrappers.NativeGameMode.class,
                    WrappedGameProfile.class, WrappedChatComponent.class, int.class,
                    Class.forName("com.comphenix.protocol.wrappers.WrappedRemoteChatSessionData"));
            return ctor.newInstance(id, 0, true, EnumWrappers.NativeGameMode.SPECTATOR, profile, display,
                    listOrder, null);
        } catch (ReflectiveOperationException ignored) {
            return new PlayerInfoData(id, 0, true, EnumWrappers.NativeGameMode.SPECTATOR, profile, display);
        }
    }

    private static WrappedChatComponent blankPadDisplay() {
        return WrappedChatComponent.fromText("");
    }
}
