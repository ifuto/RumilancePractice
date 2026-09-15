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
 * <p>The Quantum map addresses its bot by <b>name</b> ({@code tag @a[name=quantumbot] add
 * xlib_bot}), so the runtime always spawns it as {@code quantumbot} — exactly the name the
 * measurement scenario in {@code java-trigger.md} uses.</p>
 */
public final class HeroBotRegistry {

    private final Plugin plugin;
    private final Map<String, HeroBotPlayer> bots = new LinkedHashMap<>();
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
        server.getPlayerList().placeNewPlayer(
                new com.rumilance.practice.packetbot.FakePlayerConnection(PacketFlow.SERVERBOUND),
                bot,
                CommonListenerCookie.createInitial(profile, false));
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
        return bot;
    }

    /** The reference's {@code BotPlayer#isSpawningPlayer} guard, used by {@code /playerspawn}. */
    public boolean isSpawning(String name) {
        return this.spawning.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Removes a fake player (tab list + world) — the reference's disconnect/kill cleanup. */
    public boolean despawn(String name) {
        HeroBotPlayer bot = this.bots.remove(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (bot == null) {
            return false;
        }
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

    public Plugin plugin() {
        return this.plugin;
    }
}
