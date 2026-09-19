package com.rumilance.practice.packetbot;

import io.netty.channel.Channel;
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
 * <p>PE ≥ 2.8.0 whitelists {@code EmbeddedChannel} in its fallback, but that fallback
 * (reflection over the player's handle) is broken on some Paper 1.21.x builds — production
 * (Paper 1.21.11 + PE 2.13.0) still kicked every bot join through it. So we do NOT rely on
 * the fallback at all: we pre-register a PE {@code User} for the bot's channel before
 * {@code placeNewPlayer} fires the join event. {@code getPlayerManager().getUser(player)}
 * then resolves on <em>any</em> PE version (the UUID→channel map is consulted first, no
 * reflection involved) and the kick branch is never reached. Quit-time cleanup removes the
 * entries.
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
    }

    /**
     * Pre-register a PE user for the bot's channel. Must run before {@code placeNewPlayer}
     * (which fires {@code PlayerJoinEvent} synchronously). No-op when PE is absent or already
     * handles fake channels.
     */
    public static void preRegister(FakePlayerConnection connection, GameProfile profile) {
        if (connection == null || profile == null) {
            return;
        }
        Channel channel = connection.channel();
        if (channel == null) {
            return;
        }
        ClassLoader peLoader = packetEventsClassLoader();
        if (peLoader == null) {
            return;
        }
        try {
            Object api = forName(peLoader, "com.github.retrooper.packetevents.PacketEvents")
                    .getMethod("getAPI").invoke(null);
            if (api == null) {
                return;
            }
            // Always pre-register: relying on PE's own fallback (reflection over the player
            // handle + fake-channel list) is not sufficient — it still kicked bots on
            // Paper 1.21.11 + PE 2.13.0 in production. A pre-registered User makes
            // getUser(player) resolve on every version, so the kick branch is unreachable.
            Object protocolManager = invoke(api, "getProtocolManager", new Class<?>[0]);
            if (protocolManager == null) {
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
                return;
            }
            invoke(protocolManager, "setUser", new Class<?>[]{Object.class, userClass}, channel, user);
            invoke(protocolManager, "setChannel", new Class<?>[]{UUID.class, Object.class}, profile.id(), channel);
        } catch (Throwable ignored) {
            // PE internals drifted — the bot will be subject to PE's default handling.
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
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------------------- internals

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
