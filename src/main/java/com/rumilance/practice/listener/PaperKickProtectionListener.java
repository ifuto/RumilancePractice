package com.rumilance.practice.listener;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Cancels Paper's automatic connection kicks for players who are already in the game:
 * <ul>
 *   <li>{@link PlayerKickEvent.Cause#TIMEOUT} — the keep-alive "Timed out" kick
 *       ({@code paper.playerconnection.keepalive}, 30s by default). A lag spike or a frozen
 *       client no longer throws the player out of a fight; a dead TCP connection is still
 *       closed by Netty (read timeout / channel inactive), which is a quit, not a kick.</li>
 *   <li>{@link PlayerKickEvent.Cause#FLYING_PLAYER} / {@link PlayerKickEvent.Cause#FLYING_VEHICLE}
 *       — vanilla's "Flying is not enabled on this server" floating check. Wind-charge /
 *       mace / pearl chains and ping-induced rubber-banding trip it on legit players.</li>
 * </ul>
 *
 * <p>Decisions are made on Paper's typed cause only, never on the reason text: an admin or
 * another plugin may kick with the same wording, and the vanilla message is translatable.
 * Bans, {@code /kick}, idle kicks and every other cause pass through untouched. Nothing here
 * grants flight, changes game modes or touches {@code server.properties}.</p>
 *
 * <p>After a cancelled floating kick Paper keeps counting the same "above ground" ticks and
 * would fire the kick (plus a console warning) again on the very next tick. To keep that to
 * one attempt per {@code max-flying-ticks} window we best-effort reset the counter through
 * reflection on the Mojang-mapped server internals; when the field is not found the kick is
 * still cancelled and a single warning is logged.</p>
 *
 * <p>Both toggles live under {@code connection.*} in {@code config.yml} and are read live,
 * so {@code /rumireload} applies them without a restart.</p>
 */
public final class PaperKickProtectionListener implements Listener {

    public static final String TIMEOUT_KEY = "connection.suppress-timeout-kick";
    public static final String FLYING_KEY = "connection.suppress-flying-kick";

    private final Supplier<ConfigurationSection> config;
    private final Logger logger;
    private volatile boolean reflectionWarned;

    /**
     * @param config supplier of the live {@code config.yml} root (re-read on every kick so a
     *               reload takes effect immediately); {@code null} sections mean "defaults"
     * @param logger plugin logger for the one-time reflection warning, may be {@code null}
     */
    public PaperKickProtectionListener(Supplier<ConfigurationSection> config, Logger logger) {
        this.config = config == null ? () -> null : config;
        this.logger = logger;
    }

    /** Always-on protection with the defaults (both kicks suppressed). */
    public PaperKickProtectionListener() {
        this(null, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        if (!shouldCancel(event.getCause())) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() != PlayerKickEvent.Cause.TIMEOUT) {
            resetFloatingCounter(event.getPlayer());
        }
    }

    /** Pure decision: which Paper causes are suppressed under the current configuration. */
    public boolean shouldCancel(PlayerKickEvent.Cause cause) {
        if (cause == null) {
            return false;
        }
        return switch (cause) {
            case TIMEOUT -> suppressTimeout();
            case FLYING_PLAYER, FLYING_VEHICLE -> suppressFlying();
            default -> false;
        };
    }

    public boolean suppressTimeout() {
        return flag(TIMEOUT_KEY);
    }

    public boolean suppressFlying() {
        return flag(FLYING_KEY);
    }

    private boolean flag(String key) {
        ConfigurationSection section = config.get();
        return section == null || section.getBoolean(key, true);
    }

    // --- floating counter reset (best effort, Mojang-mapped Paper internals) ---------------

    private static final String[] COUNTER_FIELDS = {"aboveGroundTickCount", "aboveGroundVehicleTickCount"};
    private static volatile Method getHandle;
    private static volatile Field connectionField;
    private static volatile Field[] counterFields;
    private static volatile boolean reflectionBroken;

    private void resetFloatingCounter(Player player) {
        if (player == null || reflectionBroken) {
            return;
        }
        try {
            Object handle = handleOf(player);
            if (handle == null) {
                return;
            }
            Object connection = connectionOf(handle);
            if (connection == null) {
                return;
            }
            for (Field field : countersOf(connection)) {
                field.setInt(connection, 0);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionBroken = true;
            if (!reflectionWarned && logger != null) {
                reflectionWarned = true;
                logger.warning("[N Arena] Could not reset Paper's floating-kick counter ("
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "). Flying kicks are still cancelled, but Paper may log"
                        + " 'was kicked for floating too long' repeatedly for floating players.");
            }
        }
    }

    private static Object handleOf(Player player) throws ReflectiveOperationException {
        Method method = getHandle;
        if (method == null) {
            method = player.getClass().getMethod("getHandle");
            method.setAccessible(true);
            getHandle = method;
        }
        return method.invoke(player);
    }

    private static Object connectionOf(Object handle) throws ReflectiveOperationException {
        Field field = connectionField;
        if (field == null) {
            field = findField(handle.getClass(), "connection");
            connectionField = field;
        }
        return field.get(handle);
    }

    private static Field[] countersOf(Object connection) throws ReflectiveOperationException {
        Field[] fields = counterFields;
        if (fields == null) {
            fields = new Field[COUNTER_FIELDS.length];
            for (int i = 0; i < COUNTER_FIELDS.length; i++) {
                fields[i] = findField(connection.getClass(), COUNTER_FIELDS[i]);
            }
            counterFields = fields;
        }
        return fields;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }
}
