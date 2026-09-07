package com.rumilance.practice.join;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

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

    @Test
    void cancelledKickDoesNotSuppressTheLaterRealQuit() {
        Player player = player("Alice");
        Component leave = Component.text("leave");
        PlayerKickEvent kick = new PlayerKickEvent(player, Component.empty(), leave, PlayerKickEvent.Cause.TIMEOUT);
        kick.setCancelled(true);

        JoinQuitMessages.apply(kick);

        assertSame(leave, kick.leaveMessage(), "cancelled kick must not be silenced");
        PlayerQuitEvent quit = quit(player, PlayerQuitEvent.QuitReason.DISCONNECTED);
        JoinQuitMessages.apply(quit);
        assertEquals(JoinQuitMessages.quit("Alice"), quit.quitMessage());
    }

    @Test
    void realKickLeavesSilentlyAndSuppressionIsConsumedOnce() {
        Player player = player("Bob");
        PlayerKickEvent kick = new PlayerKickEvent(player, Component.text("Kicked"),
                Component.text("leave"), PlayerKickEvent.Cause.KICK_COMMAND);

        JoinQuitMessages.apply(kick);

        assertNull(kick.leaveMessage());
        PlayerQuitEvent kicked = quit(player, PlayerQuitEvent.QuitReason.KICKED);
        JoinQuitMessages.apply(kicked);
        assertNull(kicked.quitMessage());

        PlayerQuitEvent later = quit(player, PlayerQuitEvent.QuitReason.DISCONNECTED);
        JoinQuitMessages.apply(later);
        assertEquals(JoinQuitMessages.quit("Bob"), later.quitMessage());
    }

    private static PlayerQuitEvent quit(Player player, PlayerQuitEvent.QuitReason reason) {
        return new PlayerQuitEvent(player, Component.text("original"), reason);
    }

    /** Minimal Player stub: only identity is needed by the join/quit message logic. */
    private static Player player(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player(" + name + ")";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
