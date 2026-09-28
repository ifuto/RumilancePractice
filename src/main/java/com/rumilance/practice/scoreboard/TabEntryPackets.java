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

    /** Ping drawn on filler rows: 0 ms — 埋め行を実プレイヤーと同じ緑バー扱いにする（user request 2026-09-28）。 */
    private static final int FILLER_LATENCY = 0;
    /**
     * Profile name of a filler entry. The client renders the display name and the list order
     * decides the position, so the name is never used — TAB sends an empty one for the same
     * reason (it must not leak into chat completion as a fake player).
     */
    private static final String FILLER_NAME = "";

    /**
     * Optional texture property for the filler head (base64 of the standard unsigned
     * {@code {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/…"}}}} JSON).
     * Set it to a blank/solid-dark skin and the head square in the TAB list blends into the
     * list background — that is the closest to "headless" the vanilla protocol allows
     * (a fully-transparent skin renders BLACK first-layer pixels, never invisible).
     * Empty constant = fall back to the default Steve/Alex head.
     */
    private static volatile String fillerSkinValue = "";

    /** Setter from config ({@code tab-fight.filler-skin-value}): any unsigned texture value. */
    public static void setFillerSkinValue(String value) {
        fillerSkinValue = value == null ? "" : value.trim();
    }

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

    /** The filler profile: blank name; carries the configured (usually blank) skin, if any. */
    private static GameProfile fillerProfile(UUID id) {
        GameProfile profile = new GameProfile(id, FILLER_NAME);
        String value = fillerSkinValue;
        if (!value.isEmpty()) {
            try {
                attachTexture(profile, value);
            } catch (Throwable ignored) {
                // A malformed value must never break the whole layout — just send without skin.
            }
        }
        return profile;
    }

    /**
     * Puts the texture property on the profile. Fully reflective: the current paperweight dev
     * bundle no longer exposes the authlib {@code properties.*} classes on the plugin compile
     * classpath (and older generations shipped constructor shapes 2-arg and 3-arg), so nothing
     * below compile-Bukkit/Paper-NMS must reference them directly. Any mismatch simply leaves
     * the filler head-less (default skin).
     */
    private static void attachTexture(GameProfile profile, String value) throws Exception {
        Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
        Object property;
        try {
            property = propertyClass
                    .getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", value, null);
        } catch (NoSuchMethodException threeArg) {
            property = propertyClass
                    .getConstructor(String.class, String.class)
                    .newInstance("textures", value);
        }
        Object multimap = GameProfile.class.getMethod("getProperties").invoke(profile);
        multimap.getClass().getMethod("put", Object.class, Object.class)
                .invoke(multimap, "textures", property);
    }

    /** Adds one filler row to {@code viewer}'s player list at {@code order}. */
    static void add(Player viewer, UUID id, Component display, int order) {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                id,
                fillerProfile(id),
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
