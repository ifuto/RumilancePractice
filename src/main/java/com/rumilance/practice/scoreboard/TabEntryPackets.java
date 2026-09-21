package com.rumilance.practice.scoreboard;

import com.mojang.authlib.GameProfile;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Filler rows of the fight TAB grid, sent as the server's own player-info packets.
 *
 * <p>A row the client does not know about cannot be shown: the blank gap rows and the column
 * headers (see {@link TabFightListService}) are therefore fake player-list entries, exactly like
 * the slots TAB's layout feature fills. They are sent per viewer with an explicit list order so
 * each one lands on a known row, carry no skin and never exist server-side — no entity, no
 * collision, no server player.</p>
 *
 * <p>The packets are built with the same classes the server itself uses
 * ({@code ClientboundPlayerInfoUpdatePacket}, {@code ClientboundPlayerInfoRemovePacket} via
 * {@link CraftPlayer#getHandle()}, the pattern already used for the packet bots), so there is no
 * third-party packet plugin in the path and no API drift to survive:
 * <ul>
 *   <li>ADD_PLAYER carries the whole entry (profile, listed flag, ping, display name, order),</li>
 *   <li>later style/position changes travel as UPDATE_DISPLAY_NAME + UPDATE_LIST_ORDER,</li>
 *   <li>leaving the layout removes the entries again.</li>
 * </ul>
 */
final class TabEntryPackets {

    /** Ping drawn on filler rows: one bar. A negative value would draw the "no connection" icon. */
    private static final int FILLER_LATENCY = 1000;
    /**
     * Profile name of a filler entry. The client renders the display name and the list order
     * decides the position, so the name is never used — TAB sends an empty one for the same
     * reason (it must not leak into chat completion as a fake player).
     */
    private static final String FILLER_NAME = "";

    private static volatile Boolean available;

    private TabEntryPackets() {
    }

    /** True when the server classes exist (Paper 1.21.9+; guarded for any other runtime). */
    static boolean available() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        try {
            Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            available = true;
        } catch (Throwable missing) {
            available = false;
        }
        return Boolean.TRUE.equals(available);
    }

    /** Adds one filler row to {@code viewer}'s player list at {@code order}. */
    static void add(Player viewer, UUID id, Component display, int order) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                id,
                new GameProfile(id, FILLER_NAME),
                true,
                FILLER_LATENCY,
                GameType.SURVIVAL,
                PaperAdventure.asVanilla(display),
                false,
                order,
                null);
        // Same action set the server uses for a joining player, so every field of the entry is
        // registered with the client in one packet.
        send(viewer, new ClientboundPlayerInfoUpdatePacket(
                EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class), entry));
    }

    /** Re-styles and moves an existing filler row. */
    static void update(Player viewer, UUID id, Component display, int order) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                id,
                null,
                false,
                0,
                GameType.SURVIVAL,
                PaperAdventure.asVanilla(display),
                false,
                order,
                null);
        send(viewer, new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER), entry));
    }

    /** Lists (or hides) one real entry from {@code viewer}'s player list. */
    static void setListed(Player viewer, UUID id, boolean listed) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                id,
                null,
                listed,
                0,
                GameType.SURVIVAL,
                null,
                false,
                0,
                null);
        send(viewer, new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), entry));
    }

    /** Removes filler rows from {@code viewer}'s player list. */
    static void remove(Player viewer, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        send(viewer, new ClientboundPlayerInfoRemovePacket(List.copyOf(ids)));
    }

    private static void send(Player viewer, Packet<?> packet) {
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }
}
