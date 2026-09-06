package com.rumilance.practice.scoreboard;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * ProtocolLib glue for the blank TAB padding entries. Pads are fake player-info entries:
 * a valid profile name (required by the ADD_PLAYER action) with a blank display name,
 * latency -1 (no ping icon) and a tab-list priority that places them exactly where the
 * column gap belongs. They exist only in the player list — no entity is ever spawned.
 *
 * <p>Every action travels in its own single-action packet, which keeps the per-action
 * {@code getPlayerDataLists()} index unambiguous across ProtocolLib versions.</p>
 *
 * <p>This class must only be touched after {@link #available()} returned true: it is the
 * only place importing ProtocolLib types, so servers without the soft-depend never load
 * it.</p>
 */
final class TabPadPackets {

    private static volatile ProtocolManager manager;
    private static volatile Boolean available;

    private TabPadPackets() {
    }

    /** True when ProtocolLib is present and its manager could be obtained. */
    static boolean available() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
                available = false;
                return false;
            }
            manager = ProtocolLibrary.getProtocolManager();
            available = manager != null;
        } catch (Throwable t) {
            available = false;
        }
        return Boolean.TRUE.equals(available);
    }

    /** Adds one blank pad entry to {@code viewer}'s player list at the given priority. */
    static void addPad(Player viewer, UUID padId, String padName, int priority) {
        ProtocolManager pm = manager;
        WrappedGameProfile profile = new WrappedGameProfile(padId, padName);
        PlayerInfoData data = new PlayerInfoData(padId, -1, true,
                EnumWrappers.NativeGameMode.NOT_SET, profile,
                WrappedChatComponent.fromText(" "), priority, null);
        sendSingle(pm, viewer, EnumWrappers.PlayerInfoAction.ADD_PLAYER, data);
        sendSingle(pm, viewer, EnumWrappers.PlayerInfoAction.UPDATE_LISTED, data);
        sendSingle(pm, viewer, EnumWrappers.PlayerInfoAction.UPDATE_LATENCY, data);
        sendSingle(pm, viewer, EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME, data);
        sendSingle(pm, viewer, EnumWrappers.PlayerInfoAction.UPDATE_LIST_ORDER, data);
    }

    /** Moves an already-added pad to a new priority. */
    static void updatePriority(Player viewer, UUID padId, String padName, int priority) {
        PlayerInfoData data = new PlayerInfoData(padId, -1, true,
                EnumWrappers.NativeGameMode.NOT_SET, new WrappedGameProfile(padId, padName),
                WrappedChatComponent.fromText(" "), priority, null);
        sendSingle(manager, viewer, EnumWrappers.PlayerInfoAction.UPDATE_LIST_ORDER, data);
    }

    /** Removes pad entries from {@code viewer}'s player list. */
    static void removePads(Player viewer, Collection<UUID> padIds) {
        if (padIds.isEmpty()) {
            return;
        }
        List<PlayerInfoData> list = padIds.stream()
                .map(id -> new PlayerInfoData(id, -1, false,
                        EnumWrappers.NativeGameMode.NOT_SET, new WrappedGameProfile(id, "NArenaPad"),
                        null, 0, null))
                .toList();
        sendAction(manager, viewer, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER, list);
    }

    private static void sendSingle(ProtocolManager pm, Player viewer,
                                   EnumWrappers.PlayerInfoAction action, PlayerInfoData data) {
        sendAction(pm, viewer, action, List.of(data));
    }

    /**
     * Single-action PLAYER_INFO packet. 1.20.5+ carries an EnumSet of actions; older
     * ProtocolLib revisions have a single action field and/or keep the data list at index 1,
     * so both spots are tried (same hardened pattern as ReplayNpcService).
     */
    private static void sendAction(ProtocolManager pm, Player viewer,
                                   EnumWrappers.PlayerInfoAction action, List<PlayerInfoData> list) {
        PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
        try {
            packet.getPlayerInfoActions().write(0, EnumSet.of(action));
        } catch (Throwable legacy) {
            packet.getPlayerInfoAction().write(0, action);
        }
        try {
            packet.getPlayerInfoDataLists().write(0, list);
        } catch (Throwable alternateIndex) {
            packet.getPlayerInfoDataLists().write(1, list);
        }
        pm.sendServerPacket(viewer, packet);
    }
}
