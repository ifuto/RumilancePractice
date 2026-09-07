package com.rumilance.practice.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class JoinQuitMessagesTest {

    @Test
    void joinAndQuitKeepRequestedShape() {
        assertEquals("[+] Alice", PlainTextComponentSerializer.plainText().serialize(JoinQuitMessages.join("Alice")));
        assertEquals("[-] Bob", PlainTextComponentSerializer.plainText().serialize(JoinQuitMessages.quit("Bob")));
    }

    @ParameterizedTest
    @EnumSource(value = PlayerKickEvent.Cause.class,
            names = {"TIMEOUT", "FLYING_PLAYER", "FLYING_VEHICLE", "PLUGIN"})
    void cancelledKickDoesNotSuppressLaterNormalQuit(PlayerKickEvent.Cause cause) {
        Player player = player("Alice");
        Component leaveMessage = Component.text("original leave message");
        PlayerKickEvent kick = new PlayerKickEvent(player, Component.empty(), leaveMessage, cause);
        kick.setCancelled(true);

        JoinQuitMessages.apply(kick);

        assertSame(leaveMessage, kick.leaveMessage());
        PlayerQuitEvent quit = quit(player, PlayerQuitEvent.QuitReason.DISCONNECTED);
        JoinQuitMessages.apply(quit);
        assertEquals(JoinQuitMessages.quit("Alice"), quit.quitMessage());
    }

    @Test
    void realKickStillLeavesSilentlyAndSuppressionIsConsumed() {
        Player player = player("Bob");
        PlayerKickEvent kick = new PlayerKickEvent(player, Component.text("Kicked by an admin"),
                Component.text("original leave message"), PlayerKickEvent.Cause.KICK_COMMAND);

        JoinQuitMessages.apply(kick);

        assertNull(kick.leaveMessage());
        PlayerQuitEvent kickedQuit = quit(player, PlayerQuitEvent.QuitReason.KICKED);
        JoinQuitMessages.apply(kickedQuit);
        assertNull(kickedQuit.quitMessage());

        // The same player can reconnect and quit normally; suppression applies only once.
        PlayerQuitEvent normalQuit = quit(player, PlayerQuitEvent.QuitReason.DISCONNECTED);
        JoinQuitMessages.apply(normalQuit);
        assertEquals(JoinQuitMessages.quit("Bob"), normalQuit.quitMessage());
    }

    private static PlayerQuitEvent quit(Player player, PlayerQuitEvent.QuitReason reason) {
        return new PlayerQuitEvent(player, Component.empty(), reason);
    }

    private static Player player(String name) {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    default -> throw new AssertionError("Unexpected player access: " + method.getName());
                });
    }
}
