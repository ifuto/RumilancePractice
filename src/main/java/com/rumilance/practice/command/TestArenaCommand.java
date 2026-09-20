package com.rumilance.practice.command;

import com.rumilance.practice.testarena.SmoothTerrainGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Admin entry point for the generated smooth terrain test arena. */
public final class TestArenaCommand implements CommandExecutor, TabCompleter {

    private final SmoothTerrainGenerator generator;

    public TestArenaCommand(SmoothTerrainGenerator generator) {
        this.generator = generator;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only: run /testarena spawnde in the test world.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text(
                    "/testarena spawnde [radius] | /testarena cancel", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("cancel")) {
            generator.cancel(player.getUniqueId());
            player.sendMessage(Component.text("Test terrain generation cancelled.", NamedTextColor.YELLOW));
            return true;
        }
        if (!sub.equals("spawnde")) {
            player.sendMessage(Component.text(
                    "Unknown testarena action. Use /testarena spawnde.", NamedTextColor.RED));
            return true;
        }
        if (generator.isRunning(player.getUniqueId())) {
            player.sendMessage(Component.text(
                    "A test terrain is already being generated for you.", NamedTextColor.YELLOW));
            return true;
        }
        int radius = SmoothTerrainGenerator.DEFAULT_RADIUS;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage(Component.text("Radius must be a number between 8 and 48.", NamedTextColor.RED));
                return true;
            }
        }
        if (radius < 8 || radius > SmoothTerrainGenerator.MAX_RADIUS) {
            player.sendMessage(Component.text("Radius must be between 8 and 48.", NamedTextColor.RED));
            return true;
        }
        long seed = ThreadLocalRandom.current().nextLong();
        player.sendMessage(Component.text(
                "Generating a smooth test terrain (height range 5 blocks)...", NamedTextColor.AQUA));
        generator.generate(player, radius, seed, result -> player.sendMessage(Component.text(
                "Test terrain ready: " + result.columns() + " columns, "
                        + "height " + result.minimumHeight() + ".." + result.maximumHeight()
                        + " (range " + result.heightRange() + "), seed " + result.seed() + ".",
                NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(args[0], "spawnde", "cancel");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawnde")) {
            return TabCompletions.filter(args[1], "16", "24", "32", "48");
        }
        return List.of();
    }
}
