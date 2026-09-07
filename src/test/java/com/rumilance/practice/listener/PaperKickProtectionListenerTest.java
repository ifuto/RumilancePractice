package com.rumilance.practice.listener;

import net.kyori.adventure.text.Component;
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

    private final PaperKickProtectionListener listener = new PaperKickProtectionListener();

    @ParameterizedTest
    @EnumSource(value = Cause.class, names = {"TIMEOUT", "FLYING_PLAYER", "FLYING_VEHICLE"})
    void cancelsAutomaticKicksRegardlessOfReasonText(Cause cause) {
        Component reason = Component.text("カスタムのキック理由");
        Component leaveMessage = Component.text("original leave message");
        PlayerKickEvent event = kick(cause, reason, leaveMessage);

        listener.onKick(event);

        assertTrue(event.isCancelled());
        assertSame(reason, event.reason());
        assertSame(leaveMessage, event.leaveMessage());
    }

    @ParameterizedTest
    @EnumSource(value = Cause.class, names = {"TIMEOUT", "FLYING_PLAYER", "FLYING_VEHICLE"},
            mode = EnumSource.Mode.EXCLUDE)
    void otherCausesAreNotCancelledEvenWithMatchingReasonText(Cause cause) {
        for (Component reason : new Component[]{
                Component.text("Timed out"),
                Component.translatable("disconnect.timeout"),
                Component.text("Flying is not enabled on this server"),
                Component.translatable("multiplayer.disconnect.flying")
        }) {
            PlayerKickEvent event = kick(cause, reason, Component.empty());

            listener.onKick(event);

            assertFalse(event.isCancelled(), () -> "Must preserve " + cause + " kicks");
            assertSame(reason, event.reason());
        }
    }

    @ParameterizedTest
    @EnumSource(Cause.class)
    void neverReEnablesAnAlreadyCancelledKick(Cause cause) {
        PlayerKickEvent event = kick(cause, Component.empty(), Component.empty());
        event.setCancelled(true);

        listener.onKick(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void protectionRunsBeforeMonitorsAndSkipsCancelledEvents() throws NoSuchMethodException {
        EventHandler handler = PaperKickProtectionListener.class
                .getMethod("onKick", PlayerKickEvent.class).getAnnotation(EventHandler.class);

        assertNotNull(handler);
        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    @Test
    void quitMessageMonitorSkipsCancelledKicks() throws NoSuchMethodException {
        EventHandler handler = SessionBootstrapListener.class
                .getMethod("onKick", PlayerKickEvent.class).getAnnotation(EventHandler.class);

        assertNotNull(handler);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    private static PlayerKickEvent kick(Cause cause, Component reason, Component leaveMessage) {
        // No player/server is needed: protection must use only the cause, never change flight
        // abilities or other player state. Any accidental player access fails this test.
        return new PlayerKickEvent(null, reason, leaveMessage, cause);
    }
}
