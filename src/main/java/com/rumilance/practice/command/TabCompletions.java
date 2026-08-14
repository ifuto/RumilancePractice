package com.rumilance.practice.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Small, dependency-free helper for building tab completions. Every method returns the subset
 * of candidates whose lowercase form starts with the lowercase current argument, so commands
 * only need to declare which completions apply at each argument index. Also filters online
 * player names out of the sender themselves, since players can never duel/spec themselves.
 */
public final class TabCompletions {

    private TabCompletions() {
    }

    /** Filters {@code candidates} to those starting with the current argument (case-insensitive). */
    public static List<String> filter(String current, Collection<String> candidates) {
        String prefix = current == null ? "" : current.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(s -> s != null && s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }

    /** Varargs convenience overload of {@link #filter(String, Collection)}. */
    public static List<String> filter(String current, String... candidates) {
        return filter(current, List.of(candidates));
    }

    /** @return online player names, excluding {@code self} (and the console). */
    public static List<String> onlinePlayers(Player self) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> self == null || !n.equalsIgnoreCase(self.getName()))
                .collect(Collectors.toList());
    }

    /**
     * If {@code sender} is a player, the names of every other online player who has a pending
     * incoming or outgoing duel request with them (used by /accept and /deny). Falls back to
     * all online players so the command still hints names after a request has expired.
     */
    public static List<String> duelPeers(CommandSender sender, Function<Player, Stream<String>> peerLookup) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        List<String> peers = peerLookup.apply(player).collect(Collectors.toList());
        return peers.isEmpty() ? onlinePlayers(player) : peers;
    }

    /** Returns the first {@code limit} completions (vanilla clients never render more than ~a screen). */
    public static List<String> limit(List<String> completions, int limit) {
        if (completions.size() <= limit) {
            return completions;
        }
        return new ArrayList<>(completions.subList(0, limit));
    }

    /** @return the argument currently being typed, or an empty string when there is none. */
    public static String current(String[] args) {
        return args.length == 0 ? "" : args[args.length - 1];
    }
}
