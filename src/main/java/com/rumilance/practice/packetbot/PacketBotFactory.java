package com.rumilance.practice.packetbot;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.util.Set;
import java.util.UUID;

/**
 * Spawns carpet-style fake players (verified against fabric-carpet's EntityPlayerMPFake
 * createFake flow): {@code placeNewPlayer} with a {@link FakePlayerConnection}, an immediate
 * teleport to the fight position, then the health/game-mode touches.
 */
public final class PacketBotFactory {

    private PacketBotFactory() {
    }

    public static PacketBotBody spawnCombat(Location location, String botName,
                                            org.bukkit.entity.Player template, double maxHp,
                                            boolean shieldUp) {
        // The bot wears the template player's skin: copy the signed textures property.
        GameProfile profile = new GameProfile(UUID.randomUUID(), botName);
        if (template != null) {
            for (ProfileProperty prop : template.getPlayerProfile().getProperties()) {
                profile.properties().put(prop.getName(),
                        new Property(prop.getName(), prop.getValue(), prop.getSignature()));
            }
        }
        return spawn(location, profile, maxHp);
    }

    /**
     * Skinless fake player — the headless side of a bot match (harness/automation): a normal
     * ServerPlayer with a {@link FakePlayerConnection}, so the practice flow sees a real player
     * while nobody is connected.
     */
    public static PacketBotBody spawnDummy(Location location, String botName, double maxHp) {
        return spawnCombat(location, botName, null, maxHp, false);
    }

    private static PacketBotBody spawn(Location location, GameProfile profile, double maxHp) {
        MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        ClientInformation information = ClientInformation.createDefault();
        PacketBot bot = new PacketBot(server, level, profile, information);
        server.getPlayerList().placeNewPlayer(
                new FakePlayerConnection(PacketFlow.SERVERBOUND),
                bot,
                CommonListenerCookie.createInitial(profile, false));
        bot.stopRiding();
        bot.teleportTo(level, location.getX(), location.getY(), location.getZ(),
                Set.of(), location.getYaw(), location.getPitch(), true);
        bot.setHealth((float) Math.max(1.0f, Math.min(20.0f, maxHp)));
        bot.gameMode.changeGameModeForPlayer(
                net.minecraft.world.level.GameType.SURVIVAL);
        return new PacketBotBody(bot);
    }

    /** Fully deregisters a fake player (tab list + world). */
    public static void despawn(PacketBotBody body) {
        PacketBot bot = body.bot();
        if (bot.owningServer() != null) {
            bot.owningServer().getPlayerList().remove(bot);
        }
        bot.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
}
