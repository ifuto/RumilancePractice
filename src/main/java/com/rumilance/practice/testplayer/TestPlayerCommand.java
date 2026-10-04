package com.rumilance.practice.testplayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /testplayer} — spawns operator smoke-test doubles that can receive (and auto-accept)
 * duel requests, so the whole Battle Menu → Duel Request → match flow can be exercised solo.
 */
public final class TestPlayerCommand implements CommandExecutor, TabCompleter {

    private static final Component USAGE = Component.text(
            "Usage: /testplayer <spawn [name]|despawn <name|all>|list>", NamedTextColor.YELLOW);

    private final TestPlayerService service;

    public TestPlayerCommand(TestPlayerService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only — a test player spawns at your position.",
                    NamedTextColor.RED));
            return true;
        }
        if (!service.available()) {
            sender.sendMessage(Component.text("The fake-player registry is unavailable.",
                    NamedTextColor.RED));
            return true;
        }
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "spawn";
        switch (action) {
            case "spawn" -> spawn(player, args.length > 1 ? args[1] : null);
            case "despawn", "remove", "despawnall" -> despawn(player, args.length > 1 ? args[1] : "all");
            case "list" -> list(player);
            default -> player.sendMessage(USAGE);
        }
        return true;
    }

    private void spawn(Player player, String name) {
        Location at = player.getLocation();
        Player spawned;
        try {
            spawned = service.spawn(at, name);
        } catch (Throwable t) {
            player.sendMessage(Component.text("Spawn failed: " + t.getMessage(), NamedTextColor.RED));
            return;
        }
        if (spawned == null) {
            player.sendMessage(Component.text(
                    name == null ? "Could not spawn a test player." : "That name is already in use.",
                    NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Spawned test player ", NamedTextColor.GREEN)
                .append(Component.text(spawned.getName(), NamedTextColor.AQUA))
                .append(Component.text(
                        " — send it a duel from the Battle Menu; it accepts automatically.",
                        NamedTextColor.GREEN)));
    }

    private void despawn(Player player, String which) {
        if ("all".equalsIgnoreCase(which)) {
            int removed = service.despawnAll();
            player.sendMessage(Component.text("Removed " + removed + " test player(s).",
                    NamedTextColor.GREEN));
            return;
        }
        boolean removed = service.despawn(which);
        player.sendMessage(removed
                ? Component.text("Removed test player " + which + ".", NamedTextColor.GREEN)
                : Component.text("No test player named " + which + ".", NamedTextColor.RED));
    }

    private void list(Player player) {
        List<String> names = service.names();
        if (names.isEmpty()) {
            player.sendMessage(Component.text("No test players online.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("Test players (" + names.size() + "): ", NamedTextColor.GREEN)
                .append(Component.text(String.join(", ", names), NamedTextColor.AQUA)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(args[0], List.of("spawn", "despawn", "list"));
        }
        if (args.length == 2 && "despawn".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>(service.names());
            names.add("all");
            return filter(args[1], names);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
