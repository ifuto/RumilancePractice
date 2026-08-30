package com.rumilance.practice.command;

import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.practice.PracticeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Player {@code /prac <name>} join and {@code /prac leave}.
 */
public final class PracCommand implements CommandExecutor, TabCompleter {

    private final PracticeService practiceService;

    public PracCommand(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /prac <name>|leave", NamedTextColor.YELLOW));
            List<PracticeRoom> enabled = practiceService.enabled();
            if (!enabled.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (PracticeRoom room : enabled) {
                    names.add(room.id());
                }
                player.sendMessage(Component.text("Available: " + String.join(", ", names),
                        NamedTextColor.GRAY));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            practiceService.leave(player, true);
            return true;
        }
        // Case-sensitive exact match preferred; fall back to ignore-case for convenience.
        String name = args[0];
        if (practiceService.get(name).isEmpty()) {
            for (PracticeRoom room : practiceService.enabled()) {
                if (room.id().equalsIgnoreCase(name)) {
                    name = room.id();
                    break;
                }
            }
        }
        practiceService.join(player, name);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        options.add("leave");
        for (PracticeRoom room : practiceService.enabled()) {
            options.add(room.id());
        }
        return TabCompletions.filter(TabCompletions.current(args), options);
    }
}
