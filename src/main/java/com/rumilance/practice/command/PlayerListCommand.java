package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.RealPlayers;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code /player} — the online player list.
 *
 * <p>Deliberately written against {@link CommandSender} rather than {@link Player}: the whole
 * point of the command is that the <b>console</b> can run it, so nothing here may assume a
 * player executor (no {@code sender.sendMessage} to an audience, no world of its own, no
 * cooldown map keyed on a UUID). Console output goes through {@link MessageService}, which
 * renders the same MiniMessage strings it sends to players and strips them to plain text for
 * the console logger.</p>
 *
 * <p>Bots are excluded via {@link RealPlayers#online()}, so the count matches what an actual
 * human would see in the tab list.</p>
 */
public final class PlayerListCommand implements CommandExecutor {

    private final MessageService messages;
    /** Null when no state manager is wired: the state column is then simply omitted. */
    private final PlayerStateManager stateManager;

    public PlayerListCommand(MessageService messages, PlayerStateManager stateManager) {
        this.messages = messages;
        this.stateManager = stateManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        List<Player> online = sortedOnline();

        if (online.isEmpty()) {
            messages.send(sender, "player-list.empty");
            return true;
        }

        messages.send(sender, "player-list.header",
                MessageService.tags("count", String.valueOf(online.size())));
        for (Player player : online) {
            World world = player.getWorld();
            messages.send(sender, "player-list.line",
                    MessageService.tags(
                            "name", player.getName(),
                            "state", state(player),
                            "ping", String.valueOf(ping(player)),
                            "world", world == null ? "-" : world.getName()));
        }
        return true;
    }

    private String state(Player player) {
        if (stateManager == null) {
            return "-";
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        return state == null ? PlayerState.IDLE.name() : state.name();
    }

    /**
     * Ping in milliseconds. {@code Player#getPing} is the modern name; the legacy spigot build
     * only exposes it through the CraftPlayer handle, so fall back to 0 rather than reflect.
     */
    private static int ping(Player player) {
        try {
            return player.getPing();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The players the command prints, name-sorted and with bots filtered out. */
    private static List<Player> sortedOnline() {
        List<Player> online = new ArrayList<>(RealPlayers.online());
        online.sort(Comparator.comparing(player -> player.getName().toLowerCase(Locale.ROOT)));
        return online;
    }
}
