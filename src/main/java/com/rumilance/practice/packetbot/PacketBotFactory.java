package com.rumilance.practice.packetbot;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Spawns carpet-style fake players (verified against fabric-carpet's EntityPlayerMPFake
 * createFake flow): {@code placeNewPlayer} with a {@link FakePlayerConnection}, an immediate
 * teleport to the fight position, then the health/game-mode touches.
 *
 * <p>Every spawn registers the bot as live ({@link PacketBot#registerLive}) — that is what
 * hides it from real players' TAB lists — and gives it a <b>unique profile name</b>
 * ({@link BotNames#uniqueProfileName}) under the shared display name, so any number of bot
 * fights can run in parallel without name collisions.</p>
 */
public final class PacketBotFactory {

    /** Default player-facing name of a practice bot (the in-world nametag and any feed text). */
    public static final String DEFAULT_DISPLAY_NAME = "NARENA BOT";

    private PacketBotFactory() {
    }

    public static PacketBotBody spawnCombat(Location location, String botName,
                                            org.bukkit.entity.Player template, double maxHp,
                                            boolean shieldUp) {
        return spawnCombat(location, botName, template, maxHp, shieldUp, DEFAULT_DISPLAY_NAME);
    }

    /**
     * @param botName     legacy hook (ignored for the profile name; the unique name is derived
     *                    from {@code displayName}) — kept so old call sites stay valid
     * @param displayName what the bot is called by players (nametag / feed), e.g. "NARENA BOT"
     */
    public static PacketBotBody spawnCombat(Location location, String botName,
                                            org.bukkit.entity.Player template, double maxHp,
                                            boolean shieldUp, String displayName) {
        // The bot wears the template player's skin: copy the signed textures property.
        UUID profileId = UUID.randomUUID();
        String profileName = BotNames.uniqueProfileName(displayName);
        GameProfile profile = new GameProfile(profileId, profileName);
        // authlib 7.x: GameProfile#properties() は不変なので put すると必ず落ちる。
        // テンプレート玩家のプロパティを持つプロファイルを 3 引数コンストラクタで作る。
        // (GameProfile は record 化されているので getId()/getName() には頼らない)
        if (template instanceof org.bukkit.craftbukkit.entity.CraftPlayer craft) {
            try {
                profile = new GameProfile(profileId, profileName,
                        craft.getHandle().getGameProfile().properties());
            } catch (Throwable authlibDrift) {
                // authlib/Paper 形状ドリフトでも spawn 自体は止めない: スキンレスへフォールバック。
                profile = new GameProfile(profileId, profileName);
            }
        }
        return spawn(location, profile, maxHp, displayName);
    }

    /**
     * Skinless fake player — the headless side of a bot match (harness/automation): a normal
     * ServerPlayer with a {@link FakePlayerConnection}, so the practice flow sees a real player
     * while nobody is connected.
     */
    public static PacketBotBody spawnDummy(Location location, String botName, double maxHp) {
        return spawnCombat(location, botName, null, maxHp, false);
    }

    private static PacketBotBody spawn(Location location, GameProfile profile, double maxHp,
                                       String displayName) {
        MinecraftServer server = ((CraftServer) org.bukkit.Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        ClientInformation information = ClientInformation.createDefault();
        PacketBot bot = new PacketBot(server, level, profile, information);
        FakePlayerConnection connection = new FakePlayerConnection(PacketFlow.SERVERBOUND);
        // Keep PacketEvents (any version) from kicking the bot on the join event below.
        PacketEventsCompat.preRegister(connection, profile);
        server.getPlayerList().placeNewPlayer(
                connection,
                bot,
                CommonListenerCookie.createInitial(profile, false));
        // Diagnostics: run PE's own join-check resolution and log the verdict (no-op w/o PE).
        PacketEventsCompat.verifyJoinCheck(profile.id(),
                displayName == null || displayName.isBlank() ? DEFAULT_DISPLAY_NAME : displayName);
        // Presence policy: bots are not server players — out of the TAB, out of the count.
        PacketBot.registerLive(bot, displayName == null || displayName.isBlank()
                ? DEFAULT_DISPLAY_NAME : displayName);
        // In-world nametag shows the friendly display name, not the unique profile name.
        // Bukkit String API on purpose: the NMS/custom-name component overloads drift across
        // 1.21.x builds (Mojang chat Component vs Adventure vs plain String), but the plain
        // String Bukkit overload exists in every supported version.
        try {
            org.bukkit.entity.Player bukkitBot = bot.getBukkitEntity();
            bukkitBot.setCustomName(displayName == null || displayName.isBlank()
                    ? DEFAULT_DISPLAY_NAME : displayName);
            bukkitBot.setCustomNameVisible(true);
        } catch (Throwable ignored) {
        }
        bot.stopRiding();
        bot.teleportTo(level, location.getX(), location.getY(), location.getZ(),
                Set.of(), location.getYaw(), location.getPitch(), true);
        bot.setHealth((float) Math.max(1.0f, Math.min(20.0f, maxHp)));
        bot.gameMode.changeGameModeForPlayer(
                net.minecraft.world.level.GameType.SURVIVAL);
        return new PacketBotBody(bot);
    }

    /** Fully deregisters a fake player (tab list + world + live registry). */
    public static void despawn(PacketBotBody body) {
        PacketBot bot = body.bot();
        PacketBot.unregisterLive(bot);
        if (bot.owningServer() != null) {
            bot.owningServer().getPlayerList().remove(bot);
        }
        bot.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }
}
