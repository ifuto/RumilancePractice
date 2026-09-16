package com.rumilance.practice.packetbot;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * A carpet-style fake player on Paper: a real {@link ServerPlayer} (real inventory, skin,
 * hitbox, sounds, vanilla damage/totem/death pipeline) wired to a {@link FakePlayerConnection}.
 * Never saved to playerdata, hunger constantly topped up, and death is redirected to the
 * session's callback instead of vanilla's drop/respawn flow.
 */
public class PacketBot extends ServerPlayer {

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
    public static boolean isBot(org.bukkit.entity.Player player) {
        return player instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft
                && craft.getHandle() instanceof PacketBot;
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
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
