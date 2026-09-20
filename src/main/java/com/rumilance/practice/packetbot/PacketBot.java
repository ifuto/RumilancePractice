package com.rumilance.practice.packetbot;

import com.mojang.authlib.GameProfile;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A carpet-style fake player on Paper: a real {@link ServerPlayer} (real inventory, skin,
 * hitbox, sounds, vanilla damage/totem/death pipeline) wired to a {@link FakePlayerConnection}.
 * Never saved to playerdata, hunger constantly topped up, and death is redirected to the
 * session's callback instead of vanilla's drop/respawn flow.
 *
 * <p><b>Presence policy (multiple bots, always).</b> Bots are per-user sparring partners, so
 * the server routinely runs <i>many</i> of them at once (one per active bot-vs-player session,
 * 10+ in practice). They are deliberately <b>not players of this server</b>: every live bot is
 * tracked in {@link #LIVE}, hidden from every real player's TAB list (a {@code REMOVE_PLAYER}
 * player-info packet, re-applied when a real player joins), excluded from the real online
 * count, and joined/left without any chat line. Spawn sites MUST call {@link #registerLive}
 * (which performs the tab hiding) and teardown sites {@link #unregisterLive}.</p>
 */
public class PacketBot extends ServerPlayer {

    /** Every live fake player, whatever spawned it (practice bots, quantum map bot, …). */
    private static final Map<UUID, PacketBot> LIVE = new java.util.concurrent.ConcurrentHashMap<>();

    private final MinecraftServer owningServer;
    private final String profileName;
    private Runnable deathCallback = () -> { };

    public PacketBot(MinecraftServer server, ServerLevel level, GameProfile profile,
                     ClientInformation information) {
        super(server, level, profile, information);
        this.owningServer = server;
        this.profileName = profile.name();
    }

    public String profileName() {
        return profileName;
    }

    /**
     * True when the Bukkit player is one of this plugin's fake players.
     *
     * <p>Used by the ordinary player-facing services (join bootstrap, lobby reset, …) to leave
     * bots alone: they are real {@link ServerPlayer}s, so a {@code PlayerJoinEvent} fires for them,
     * and the lobby flow would otherwise put them in adventure, teleport them to the lobby spawn
     * and hand them the lobby's infinite Resistance 255 — which makes the Quantum map's own
     * damage pipeline (and every bot-vs-bot fight) silently invulnerable.</p>
     */
    public static boolean isBot(Player player) {
        return player instanceof CraftPlayer craft && craft.getHandle() instanceof PacketBot;
    }

    // ------------------------------------------------------------ live registry

    /**
     * Marks the bot as live and hides it from every real player's TAB list. Call once, right
     * after {@code placeNewPlayer} returns (the broadcast it triggers is what we undo here).
     *
     * @param displayName what the bot should be called in player-facing text (nametag,
     *                    scoreboard, feed) — usually {@code "NARENA BOT"}; may be null
     */
    public static void registerLive(PacketBot bot, String displayName) {
        if (bot == null) {
            return;
        }
        LIVE.put(bot.getUUID(), bot);
        bot.displayName = displayName;
        hideFromTab(bot);
    }

    public static void unregisterLive(PacketBot bot) {
        if (bot == null) {
            return;
        }
        LIVE.remove(bot.getUUID(), bot);
    }

    /** Live fake players (multi-instance: one per active bot session). */
    public static List<PacketBot> liveBots() {
        return List.copyOf(LIVE.values());
    }

    public static PacketBot liveBot(UUID uuid) {
        return uuid == null ? null : LIVE.get(uuid);
    }

    /** Player-facing name for a bot (falls back to its profile name). */
    public static String displayNameOf(Player player) {
        if (!(player instanceof CraftPlayer craft)) {
            return null;
        }
        PacketBot bot = craft.getHandle() instanceof PacketBot p ? p : null;
        return bot != null && bot.displayName != null ? bot.displayName : null;
    }

    private String displayName;

    // ------------------------------------------------------------ tab hiding

    /**
     * Removes the bot's entry from every real player's TAB list. The bot's own entity stays
     * fully visible in-world (it is the opponent) — only the player-list row goes away.
     */
    public static void hideFromTab(PacketBot bot) {
        if (bot == null || !bot.isAlive()) {
            return;
        }
        ClientboundPlayerInfoRemovePacket packet = tabRemovePacket(bot);
        if (packet == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (isBot(viewer) || !(viewer instanceof CraftPlayer craft)) {
                continue;
            }
            try {
                craft.getHandle().connection.send(packet);
            } catch (Throwable ignored) {
                // viewer vanished mid-loop; the next join refresh covers stragglers
            }
        }
    }

    /** Re-applies tab hiding to a freshly joined real player (the server sent them the full list). */
    public static void hideAllFromTab(Player viewer) {
        if (viewer == null || isBot(viewer)) {
            return;
        }
        for (PacketBot bot : liveBots()) {
            if (!bot.isAlive()) {
                LIVE.remove(bot.getUUID(), bot);
                continue;
            }
            ClientboundPlayerInfoRemovePacket packet = tabRemovePacket(bot);
            if (packet == null) {
                continue;
            }
            try {
                ((CraftPlayer) viewer).getHandle().connection.send(packet);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * The 1.21.x tab-removal packet: a single {@code ClientboundPlayerInfoRemovePacket} with
     * the bot's profile UUID. (Pre-1.21 used {@code ClientboundPlayerInfoPacket} with a
     * REMOVE_PLAYER action on a full entry; the protocol split add/update from remove.)
     */
    private static ClientboundPlayerInfoRemovePacket tabRemovePacket(PacketBot bot) {
        try {
            return new ClientboundPlayerInfoRemovePacket(List.of(bot.getUUID()));
        } catch (Throwable t) {
            // NMS signature drift would be fatal for every bot fight; fail soft instead.
            return null;
        }
    }

    /** The player-facing display name (empty when unset). */
    public Component displayNameComponent() {
        return Component.text(displayName == null ? profileName : displayName);
    }

    public MinecraftServer owningServer() {
        return owningServer;
    }

    public void deathCallback(Runnable callback) {
        this.deathCallback = callback;
    }

    /**
     * The real per-tick path for a player on this platform: the server ticks players from their
     * connection ({@code Connection#tick} → {@code ServerGamePacketListenerImpl#tick} →
     * {@code ServerPlayer#doTick}), and a dead-connection bot has no connection to be ticked from,
     * so {@link com.rumilance.practice.herobot.HeroBotRegistry} drives it. {@code ServerPlayer#tick}
     * stays vanilla: nothing in the server's player path dispatches to it.
     */
    @Override
    public void doTick() {
        // 参照 (herobot の BotPlayer) は「クライアントからの hunger パケットが無いだけ」の
        // 普通の fake player で、空腹も満腹度も*普通に減る*。ここで毎tick eat() して満腹に
        // 保つと、food >= 18 が維持されて自然回復が止まらない = 参照では起きる
        // 「満腹度切れ → 自然回復停止 → 死亡」が起きず、BOT vs BOT の勝敗・移動が丸ごと
        // 変わってしまう(実測: Paper 側だけ満腹度が 20 のまま、参照は 20 → 0 まで減る)。
        super.doTick();
    }

    @Override
    public void die(DamageSource source) {
        Runnable callback = deathCallback;
        this.remove(Entity.RemovalReason.KILLED);
        unregisterLive(this);
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
