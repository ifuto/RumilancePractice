package com.rumilance.practice.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerKickEvent.Cause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperKickProtectionListenerTest {

    private static final String[] SUPPRESSED = {"TIMEOUT", "FLYING_PLAYER", "FLYING_VEHICLE"};

    private final PaperKickProtectionListener defaults = new PaperKickProtectionListener();

    @ParameterizedTest
    @EnumSource(value = Cause.class, names = {"TIMEOUT", "FLYING_PLAYER", "FLYING_VEHICLE"})
    void cancelsPaperAutomaticKicksRegardlessOfReasonText(Cause cause) {
        Component reason = Component.text("カスタムのキック理由");
        Component leave = Component.text("leave");
        PlayerKickEvent event = kick(cause, reason, leave);

        defaults.onKick(event);

        assertTrue(event.isCancelled());
        assertSame(reason, event.reason());
        assertSame(leave, event.leaveMessage());
    }

    @ParameterizedTest
    @EnumSource(value = Cause.class, names = {"TIMEOUT", "FLYING_PLAYER", "FLYING_VEHICLE"},
            mode = EnumSource.Mode.EXCLUDE)
    void everyOtherCausePassesThroughEvenWithMatchingText(Cause cause) {
        for (Component reason : new Component[]{
                Component.text("Timed out"),
                Component.translatable("disconnect.timeout"),
                Component.text("Flying is not enabled on this server"),
                Component.translatable("multiplayer.disconnect.flying")}) {
            PlayerKickEvent event = kick(cause, reason, Component.empty());

            defaults.onKick(event);

            assertFalse(event.isCancelled(), () -> cause + " must still kick");
            assertFalse(defaults.shouldCancel(cause));
        }
    }

    @Test
    void nullCauseIsNeverCancelled() {
        assertFalse(defaults.shouldCancel(null));
    }

    @Test
    void configTogglesAreReadLive() {
        YamlConfiguration config = new YamlConfiguration();
        PaperKickProtectionListener listener = new PaperKickProtectionListener(() -> config, null);

        // Missing keys default to "on".
        assertTrue(listener.shouldCancel(Cause.TIMEOUT));
        assertTrue(listener.shouldCancel(Cause.FLYING_PLAYER));
        assertTrue(listener.shouldCancel(Cause.FLYING_VEHICLE));

        config.set(PaperKickProtectionListener.TIMEOUT_KEY, false);
        assertFalse(listener.shouldCancel(Cause.TIMEOUT));
        assertTrue(listener.shouldCancel(Cause.FLYING_PLAYER));

        config.set(PaperKickProtectionListener.FLYING_KEY, false);
        assertFalse(listener.shouldCancel(Cause.FLYING_PLAYER));
        assertFalse(listener.shouldCancel(Cause.FLYING_VEHICLE));

        PlayerKickEvent event = kick(Cause.TIMEOUT, Component.empty(), Component.empty());
        listener.onKick(event);
        assertFalse(event.isCancelled(), "disabled toggle must leave the kick alone");
    }

    @Test
    void nullConfigSupplierFallsBackToDefaults() {
        PaperKickProtectionListener listener = new PaperKickProtectionListener(() -> null, null);
        assertTrue(listener.suppressTimeout());
        assertTrue(listener.suppressFlying());
    }

    @Test
    void protectionRunsBeforeMonitorsAndSkipsAlreadyCancelledKicks() throws NoSuchMethodException {
        EventHandler handler = PaperKickProtectionListener.class
                .getMethod("onKick", PlayerKickEvent.class).getAnnotation(EventHandler.class);
        assertNotNull(handler);
        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void quitLineMonitorIgnoresCancelledKicks() throws NoSuchMethodException {
        EventHandler handler = SessionBootstrapListener.class
                .getMethod("onKick", PlayerKickEvent.class).getAnnotation(EventHandler.class);
        assertNotNull(handler);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void bundledConfigShipsBothTogglesOn() throws Exception {
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml resource missing");
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(config.isBoolean(PaperKickProtectionListener.TIMEOUT_KEY));
            assertTrue(config.isBoolean(PaperKickProtectionListener.FLYING_KEY));
            assertTrue(config.getBoolean(PaperKickProtectionListener.TIMEOUT_KEY));
            assertTrue(config.getBoolean(PaperKickProtectionListener.FLYING_KEY));
        }
    }

    private static PlayerKickEvent kick(Cause cause, Component reason, Component leaveMessage) {
        // No player / server: the cause alone must decide. With a null player the floating
        // counter reset is skipped, which also proves the listener never needs a live player
        // to make its decision.
        return new PlayerKickEvent(null, reason, leaveMessage, cause);
    }
}
