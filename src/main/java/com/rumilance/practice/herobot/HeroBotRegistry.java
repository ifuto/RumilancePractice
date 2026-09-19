package com.rumilance.practice.herobot;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The {@code /player}-style bot directory: named HeroBot fake players, spawned and removed
 * with the reference's flow ({@code PlayerList#placeNewPlayer} over a dead connection).
 *
 * <p>The Quantum map addresses bots by the persistent {@code quantum_bot} tag. The first
 * profile keeps the reference name {@code quantumbot}; additional instances receive unique
 * suffixes and run through the same tagged function path.</p>
 */
public final class HeroBotRegistry {

    private final Plugin plugin;
    private final Map<String, HeroBotPlayer> bots = new LinkedHashMap<>();
    private BukkitTask ticker;
    /** {@code tick-phase: tick-start} のときのドライバ (ServerTickStartEvent)。 */
    private org.bukkit.event.Listener tickListener;
    private final Set<String> spawning = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public HeroBotRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Case-insensitive lookup, matching the reference's {@code PlayerList#getPlayerByName}. */
    public HeroBotPlayer byName(String name) {
        if (name == null) {
            return null;
        }
        HeroBotPlayer direct = this.bots.get(name.toLowerCase(Locale.ROOT));
        if (direct != null && !direct.isRemoved()) {
            return direct;
        }
        return null;
    }

    public Collection<HeroBotPlayer> all() {
        List<HeroBotPlayer> alive = new ArrayList<>();
        for (HeroBotPlayer bot : this.bots.values()) {
            if (!bot.isRemoved()) {
                alive.add(bot);
            }
        }
        return alive;
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, HeroBotPlayer> entry : this.bots.entrySet()) {
            if (!entry.getValue().isRemoved()) {
                names.add(entry.getValue().profileName());
            }
        }
        return names;
    }

    /**
     * Spawns a fake player exactly like HeroBot's {@code createFake}: offline UUID when the name
     * cannot be resolved (Paper has no Mojang session here), the real player's skin when a
     * player with that name is online, {@code placeNewPlayer} through a
     * {@link com.rumilance.practice.packetbot.FakePlayerConnection}, then a teleport and the
     * health/gamemode touches.
     */
    public HeroBotPlayer spawn(String name, Location location, float yaw, float pitch,
                               net.minecraft.world.level.GameType gameType, Player skinTemplate) {
        return this.spawn(name, location, yaw, pitch, gameType, skinTemplate,
                gameType.isCreative() || gameType == net.minecraft.world.level.GameType.SPECTATOR);
    }

    /**
     * Spawns a fake player like HeroBot's {@code createFake}: offline UUID when the name cannot be
     * resolved, the online player's profile when there is one, {@code placeNewPlayer} through a
     * {@link com.rumilance.practice.packetbot.FakePlayerConnection}, then the reference's touches —
     * teleport, health 20, step height 0.6, game mode, spawn position, {@code ping = 0} and the
     * creative/spectator flight flag {@code /playerspawn … in <mode>} computes.
     */
    public HeroBotPlayer spawn(String name, Location location, float yaw, float pitch,
                               net.minecraft.world.level.GameType gameType, Player skinTemplate,
                               boolean flying) {
        HeroBotPlayer existing = byName(name);
        if (existing != null) {
            ((org.bukkit.entity.Player) existing.getBukkitEntity()).teleport(location);
            return existing;
        }
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        UUID uuid = skinTemplate != null
                ? skinTemplate.getUniqueId()
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);
        if (skinTemplate != null) {
            for (var prop : skinTemplate.getPlayerProfile().getProperties()) {
                profile.properties().put(prop.getName(),
                        new Property(prop.getName(), prop.getValue(), prop.getSignature()));
            }
        } else {
            net.minecraft.server.level.ServerPlayer online =
                    server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                profile = online.getGameProfile();
            }
        }

        this.spawning.add(name.toLowerCase(Locale.ROOT));
        HeroBotPlayer bot = new HeroBotPlayer(server, level, profile, ClientInformation.createDefault());
        com.rumilance.practice.packetbot.FakePlayerConnection connection =
                new com.rumilance.practice.packetbot.FakePlayerConnection(PacketFlow.SERVERBOUND);
        // Keep PacketEvents (any version) from kicking the bot on the join event below.
        com.rumilance.practice.packetbot.PacketEventsCompat.preRegister(connection, profile);
        server.getPlayerList().placeNewPlayer(
                connection,
                bot,
                CommonListenerCookie.createInitial(profile, false));
        // Diagnostics: run PE's own join-check resolution and log the verdict (no-op w/o PE).
        com.rumilance.practice.packetbot.PacketEventsCompat.verifyJoinCheck(profile.id(), name);
        // Presence policy: bots are not server players — out of the TAB, out of the count.
        // Quantum's functions use tags rather than a single hard-coded name, so every spawned
        // instance can participate in the same live runtime without replacing another bot.
        bot.addTag("quantum_bot");
        bot.addTag("xlib_bot");
        org.bukkit.entity.Player visibleBot = bot.getBukkitEntity();
        visibleBot.setCustomName(com.rumilance.practice.packetbot.PacketBotFactory.DEFAULT_DISPLAY_NAME);
        visibleBot.setCustomNameVisible(true);
        com.rumilance.practice.packetbot.PacketBot.registerLive(bot,
                com.rumilance.practice.packetbot.PacketBotFactory.DEFAULT_DISPLAY_NAME);
        bot.stopRiding();
        bot.teleportTo(level, location.getX(), location.getY(), location.getZ(),
                Set.of(), yaw, pitch, true);
        bot.setHealth(20.0f);
        bot.unsetRemoved();
        if (bot.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) != null) {
            bot.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT)
                    .setBaseValue(0.6);
        }
        bot.gameMode.changeGameModeForPlayer(gameType);
        bot.getAbilities().flying = flying;
        bot.spawnPos = new net.minecraft.world.phys.Vec3(location.getX(), location.getY(), location.getZ());
        bot.spawnYaw = yaw;
        bot.ping = 0;
        this.spawning.remove(name.toLowerCase(Locale.ROOT));
        this.bots.put(name.toLowerCase(Locale.ROOT), bot);
        this.ensureTicker();
        return bot;
    }

    /** The reference's {@code BotPlayer#isSpawningPlayer} guard, used by {@code /playerspawn}. */
    public boolean isSpawning(String name) {
        return this.spawning.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Removes a fake player (tab list + world + live registry) — the reference's cleanup. */
    public boolean despawn(String name) {
        HeroBotPlayer bot = this.bots.remove(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (bot == null) {
            return false;
        }
        com.rumilance.practice.packetbot.PacketBot.unregisterLive(bot);
        if (bot.owningServer() != null) {
            bot.owningServer().getPlayerList().remove(bot);
        }
        bot.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        return true;
    }

    public void despawnAll() {
        for (String name : List.copyOf(this.bots.keySet())) {
            this.despawn(name);
        }
    }

    /**
     * Drives one server tick for every bot.
     *
     * <p>The reference mod's bots are real fake players on a real (dead) connection, and the
     * server ticks players from <b>their connection</b>: {@code ServerConnectionListener} →
     * {@code Connection#tick} → (TickablePacketListener) {@code ServerGamePacketListenerImpl#tick}
     * → {@code ServerPlayer#doTick}. A bot whose connection was never registered in the connection
     * list is ticked by nobody, so the plugin runs that same call itself — one scheduled task for
     * all bots, started with the first bot and stopped with the last.</p>
     */
    public void ensureTicker() {
        if (this.ticker != null || this.tickListener != null) {
            return;
        }
        // 参照(Fabric mod)の bot は player list の ServerPlayer なので、bot の doTick は
        // `PlayerList.tick()`（= tick の内側、関数タグより前）で走る。Paper に同じ差し込み口は
        // 無いので、既定では「tick の先頭」で回す: サーバー自身の `#minecraft:tick` 関数は
        // これより後に走るため、関数から見た bot の状態が「同じ tick のもの」になる。
        // (bukkit scheduler の heart は tick の *終わり* にあり、関数はその前の状態を見てしまう
        //  = 参照に対して常に 1 tick 遅れる。実測で剣の与hit数が減っていた原因。)
        if (!"scheduler".equalsIgnoreCase(HeroBotSettings.tickPhase)) {
            this.tickListener = new TickStartDriver();
            Bukkit.getPluginManager().registerEvents(this.tickListener, this.plugin);
            return;
        }
        this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickBots, 1L, 1L);
    }

    /** {@code ServerTickStartEvent} 経由のドライバ (= tick の先頭で 1 回)。 */
    public final class TickStartDriver implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void onTickStart(com.destroystokyo.paper.event.server.ServerTickStartEvent event) {
            tickBots();
        }
    }

    private void tickBots() {
        List<HeroBotPlayer> alive = new java.util.ArrayList<>(this.all());
        if (alive.isEmpty()) {
            if (this.ticker != null) {
                this.ticker.cancel();
                this.ticker = null;
            }
            if (this.tickListener != null) {
                org.bukkit.event.HandlerList.unregisterAll(this.tickListener);
                this.tickListener = null;
            }
            return;
        }
        for (HeroBotPlayer bot : alive) {
            if (!bot.isRemoved() && bot.valid) {
                bot.doTick();
            }
        }
    }

    /** True while the per-tick driver is running (diagnostics). */
    public boolean ticking() {
        return this.ticker != null || this.tickListener != null;
    }

    public Plugin plugin() {
        return this.plugin;
    }
}
