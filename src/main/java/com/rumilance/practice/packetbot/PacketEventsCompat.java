package com.rumilance.practice.packetbot;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.UUID;

/**
 * PacketEvents (io.github.retrooper.packetevents) coexistence for fake players.
 *
 * <p>PacketEvents verifies on every {@code PlayerJoinEvent} that it can resolve a {@code User}
 * from the player's network channel; if it cannot, it kicks the player with
 * "PacketEvents failed to inject into a channel". Our fake players never perform a network
 * handshake, so no PE user is ever created from traffic.
 *
 * <p>The fix: pre-register a PE {@code User} for the bot's channel before
 * {@code placeNewPlayer} fires the join event, on <em>every</em> PE version. PE's
 * {@code getUser(player)} consults the UUID→channel map first (no reflection), so the kick
 * branch becomes unreachable. PE 2.8.0+ whitelists {@code EmbeddedChannel} in its reflection
 * fallback, but production (Paper 1.21.11 + PE 2.13.0) still kicked bots through that
 * fallback, so the fallback is not trusted. Quit-time cleanup removes the entries.
 *
 * <p>1.76.37 adds full observability: every stage of the pre-registration is logged, the
 * entry is read back through PE's own API, and after {@code placeNewPlayer} we run PE's own
 * join-check resolution ({@code PlayerManager.getUser(player)}) and log the verdict — the
 * exact function PE's kick branch calls.
 *
 * <p>All PacketEvents access is reflective, loaded through <em>PacketEvents' own class
 * loader</em> (Bukkit plugins are sibling class loaders; {@code Class.forName} from this
 * plugin would never see them), and optional: without PacketEvents (or if its API shape
 * changed) everything here is a silent no-op.
 */
public final class PacketEventsCompat implements Listener {

    private static volatile Plugin host;

    private PacketEventsCompat() {
    }

    /** Registers the quit-time cleanup listener. Idempotent. */
    public static void register(Plugin plugin) {
        host = plugin;
        plugin.getServer().getPluginManager().registerEvents(new PacketEventsCompat(), plugin);
        log().info("[PacketEventsCompat] armed (pre-registration + quit cleanup active)");
    }

    /**
     * Pre-register a PE user for the bot's channel. Must run before {@code placeNewPlayer}
     * (which fires {@code PlayerJoinEvent} synchronously). No-op when PE is absent.
     * Every failure is logged, never thrown.
     */
    public static void preRegister(FakePlayerConnection connection, GameProfile profile) {
        String bot = profile != null ? profile.name() : "?";
        if (connection == null || profile == null) {
            return;
        }
        Channel channel = connection.channel();
        if (channel == null) {
            log().warning("[PacketEventsCompat] preRegister " + bot + ": connection channel is null — giving up");
            return;
        }
        ClassLoader peLoader = packetEventsClassLoader();
        if (peLoader == null) {
            log().info("[PacketEventsCompat] preRegister " + bot + ": no enabled PacketEvents plugin — nothing to do");
            return;
        }
        try {
            Object api = forName(peLoader, "com.github.retrooper.packetevents.PacketEvents")
                    .getMethod("getAPI").invoke(null);
            if (api == null) {
                log().warning("[PacketEventsCompat] preRegister " + bot + ": PacketEvents.getAPI() is null (PE not initialised?)");
                return;
            }
            Object protocolManager = invoke(api, "getProtocolManager", new Class<?>[0]);
            if (protocolManager == null) {
                log().warning("[PacketEventsCompat] preRegister " + bot + ": getProtocolManager() missing/failing on "
                        + api.getClass().getName());
                return;
            }
            Object clientVersion = serverClientVersion(api);
            Class<?> userProfileClass = forName(peLoader, "com.github.retrooper.packetevents.protocol.player.UserProfile");
            Object userProfile = userProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(profile.id(), profile.name());
            Class<?> connectionStateClass = forName(peLoader, "com.github.retrooper.packetevents.protocol.ConnectionState");
            Object play = Enum.valueOf(connectionStateClass.asSubclass(Enum.class), "PLAY");
            Class<?> userClass = forName(peLoader, "com.github.retrooper.packetevents.protocol.player.User");
            Object user = null;
            for (Constructor<?> constructor : userClass.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 4 && params[1] == connectionStateClass
                        && userProfileClass.isAssignableFrom(params[3])) {
                    user = constructor.newInstance(channel, play, clientVersion, userProfile);
                    break;
                }
            }
            if (user == null) {
                log().warning("[PacketEventsCompat] preRegister " + bot + ": no 4-arg (Object,ConnectionState,*,UserProfile) "
                        + "constructor on " + userClass.getName() + " — PE API drift");
                return;
            }
            invoke(protocolManager, "setUser", new Class<?>[]{Object.class, userClass}, channel, user);
            invoke(protocolManager, "setChannel", new Class<?>[]{UUID.class, Object.class}, profile.id(), channel);

            // Read the entries back through PE's own API. If this fails, nothing on our side
            // was wrong — PE's map simply did not retain the entry.
            Object readBackChannel = invoke(protocolManager, "getChannel", new Class<?>[]{UUID.class}, profile.id());
            Object readBackUser = readBackChannel == null
                    ? null
                    : invoke(protocolManager, "getUser", new Class<?>[]{Object.class}, readBackChannel);
            if (readBackUser == null || readBackChannel != channel) {
                log().warning("[PacketEventsCompat] preRegister " + bot + ": VERIFICATION FAILED — read-back channel="
                        + describe(readBackChannel) + " (expected " + describe(channel) + "), user="
                        + describe(readBackUser) + " | api=" + describe(api) + " mgr=" + describe(protocolManager));
            } else {
                log().info("[PacketEventsCompat] preRegister " + bot + ": OK — user+channel registered and read back "
                        + "(api=" + describe(api) + ")");
            }
        } catch (Throwable t) {
            log().log(java.util.logging.Level.WARNING, "[PacketEventsCompat] preRegister " + bot + " FAILED: " + t, t);
        }
    }

    /**
     * Run PE's <em>own</em> join-check resolution after {@code placeNewPlayer} has fired the
     * join event: {@code PlayerManager.getUser(player)} — the exact call PE's kick branch
     * makes. Logging its verdict tells us whether the bot is safe on this server.
     */
    public static void verifyJoinCheck(UUID botUuid, String botName) {
        ClassLoader peLoader = packetEventsClassLoader();
        if (peLoader == null) {
            return; // no PE: nothing to verify
        }
        Player player = Bukkit.getPlayer(botUuid);
        if (player == null) {
            log().warning("[PacketEventsCompat] verify " + botName + ": no Bukkit Player for " + botUuid);
            return;
        }
        try {
            Object api = forName(peLoader, "com.github.retrooper.packetevents.PacketEvents")
                    .getMethod("getAPI").invoke(null);
            if (api == null) {
                return;
            }
            Object playerManager = invoke(api, "getPlayerManager", new Class<?>[0]);
            if (playerManager == null) {
                log().warning("[PacketEventsCompat] verify " + botName + ": getPlayerManager() missing on " + describe(api));
                return;
            }
            Object user = invoke(playerManager, "getUser", new Class<?>[]{Object.class}, player);
            if (user != null) {
                log().info("[PacketEventsCompat] verify " + botName + ": OK — PE resolves the bot's user ("
                        + user.getClass().getSimpleName() + "); kick branch unreachable");
            } else {
                // Diagnostics: uuid->channel map hit? fallback reflection result?
                Object protocolManager = invoke(api, "getProtocolManager", new Class<?>[0]);
                Object mapChannel = protocolManager == null
                        ? null
                        : invoke(protocolManager, "getChannel", new Class<?>[]{UUID.class}, botUuid);
                log().warning("[PacketEventsCompat] verify " + botName + ": PE user IS NULL — PE will kick the bot. "
                        + "uuid->channel map: " + describe(mapChannel)
                        + (mapChannel == null ? " (pre-registration did not land in PE's map!)" : "")
                        + " | playerManager=" + describe(playerManager) + " api=" + describe(api));
            }
        } catch (Throwable t) {
            log().log(java.util.logging.Level.WARNING, "[PacketEventsCompat] verify " + botName + " FAILED: " + t, t);
        }
    }

    /** Remove the pre-registered user when one of our bots leaves. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Object channel = botChannel(player);
        if (channel == null) {
            return; // not one of our fake players
        }
        ClassLoader peLoader = packetEventsClassLoader();
        if (peLoader == null) {
            return;
        }
        try {
            Object api = forName(peLoader, "com.github.retrooper.packetevents.PacketEvents")
                    .getMethod("getAPI").invoke(null);
            if (api != null) {
                Object protocolManager = invoke(api, "getProtocolManager", new Class<?>[0]);
                if (protocolManager != null) {
                    invoke(protocolManager, "removeUser", new Class<?>[]{Object.class}, channel);
                    invoke(protocolManager, "removeChannelById", new Class<?>[]{UUID.class}, player.getUniqueId());
                    invoke(protocolManager, "removeChannel", new Class<?>[]{Object.class}, channel);
                }
            }
            if (channel instanceof Channel ch && ch.isOpen()) {
                ch.close();
            }
        } catch (Throwable t) {
            log().log(java.util.logging.Level.WARNING, "[PacketEventsCompat] quit cleanup " + player.getName() + " failed: " + t, t);
        }
    }

    // --------------------------------------------------------------- internals

    /** Paper API's {@code Plugin#getLogger()} returns a {@link java.util.logging.Logger}. */
    private static java.util.logging.Logger log() {
        Plugin p = host;
        return p != null ? p.getLogger() : FALLBACK_LOGGER;
    }

    /** Safety net for calls before {@link #register(Plugin)}. */
    private static final java.util.logging.Logger FALLBACK_LOGGER =
            java.util.logging.Logger.getLogger("RumilancePractice.PacketEventsCompat");

    private static String describe(Object o) {
        if (o == null) {
            return "null";
        }
        return o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }

    private static Object botChannel(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            if (!(handle instanceof PacketBot bot)) {
                return null;
            }
            // ServerPlayer.connection (public) holds our FakePlayerConnection; Connection.channel
            // (public) holds the EmbeddedChannel.
            Object connection = bot.connection;
            return connection instanceof net.minecraft.network.Connection conn ? conn.channel : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * PacketEvents' class loader, found through the registered plugin. Bukkit plugins are
     * siblings — this plugin's own {@code Class.forName} can never see PE's classes.
     */
    private static ClassLoader packetEventsClassLoader() {
        Plugin p = host;
        if (p == null) {
            return null;
        }
        try {
            var pluginManager = p.getServer().getPluginManager();
            for (Plugin candidate : pluginManager.getPlugins()) {
                String name = candidate.getDescription().getName().toLowerCase(Locale.ROOT);
                if (name.equals("packetevents") && candidate.isEnabled()) {
                    return candidate.getClass().getClassLoader();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?> forName(ClassLoader loader, String name) throws ReflectiveOperationException {
        return Class.forName(name, true, loader);
    }

    private static Object serverClientVersion(Object api) {
        try {
            Object serverManager = invoke(api, "getServerManager", new Class<?>[0]);
            if (serverManager == null) {
                return null;
            }
            Object serverVersion = invoke(serverManager, "getVersion", new Class<?>[0]);
            return serverVersion == null ? null : invoke(serverVersion, "toClientVersion", new Class<?>[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * {@code method.invoke(target, args)} with explicit declared parameter types, so the lookup
     * succeeds for interface default methods on the runtime implementation class.
     */
    private static Object invoke(Object target, String name, Class<?>[] paramTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name, paramTypes).invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
