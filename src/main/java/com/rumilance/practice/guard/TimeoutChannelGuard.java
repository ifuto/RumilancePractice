package com.rumilance.practice.guard;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Paper (like vanilla) installs netty's {@code ReadTimeoutHandler(30)} in every connection's
 * channel pipeline under the name {@code "timeout"}; a client that stops sending ANY packet
 * for that long (frozen game, heavy resource-pack reload, map download stall...) has its
 * socket closed with no {@code PlayerKickEvent} fired, so it cannot be cancelled from Bukkit.
 * Pulling the handler out of the pipeline on join disables that drop for good.
 *
 * The keepalive-based "Timed out" kick (cause {@code TIMEOUT}) is already cancelled by
 * {@link KickGuardListener}, and truly dead TCP connections still close on the OS level
 * (failed write of the server-side keepalive), so ghost players do not linger forever.
 *
 * Pure reflection against Mojang-mapped NMS (Paper no longer relocates packages since
 * 1.20.5, and CraftBukkit dropped the versioned package name), so the plugin keeps compiling
 * against paper-api only:
 * {@code CraftPlayer#getHandle() -> ServerPlayer#connection
 * -> ServerCommonPacketListenerImpl#connection -> Connection#channel -> pipeline.remove("timeout")}.
 * Any failure (renamed members, future versions) degrades to one warning instead of breaking
 * joins.
 */
public final class TimeoutChannelGuard {

    private static final String TIMEOUT_HANDLER_NAME = "timeout";

    private final Logger logger;
    private boolean warned;

    public TimeoutChannelGuard(Logger logger) {
        this.logger = logger;
    }

    /** Removes the netty read-timeout handler for one player. No-op if it is already gone. */
    public void disableReadTimeout(Player player) {
        if (player == null) {
            return;
        }
        try {
            Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object gameListener = readField(serverPlayer, "connection");
            Object connection = readField(gameListener, "connection");
            Object channel = readField(connection, "channel");
            Object pipeline = channel.getClass().getMethod("pipeline").invoke(channel);
            Object handler = pipeline.getClass()
                    .getMethod("get", String.class)
                    .invoke(pipeline, TIMEOUT_HANDLER_NAME);
            if (handler != null) {
                pipeline.getClass()
                        .getMethod("remove", String.class)
                        .invoke(pipeline, TIMEOUT_HANDLER_NAME);
            }
        } catch (Throwable ex) {
            if (!warned) {
                warned = true;
                logger.log(Level.WARNING,
                        "[KickGuard] Could not strip the netty read-timeout handler; "
                                + "socket-level \"Timed out\" disconnects stay active. Cause: "
                                + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name + " on " + target.getClass().getName());
        }
        return field.get(target);
    }

    /** Walks the superclass chain; Paper fields move between listener classes across versions. */
    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
