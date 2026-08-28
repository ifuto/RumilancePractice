package com.rumilance.practice.command;

import com.rumilance.practice.combat.KnockbackProfile;
import com.rumilance.practice.combat.KnockbackService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operator knockback presets: {@code /kb off}, {@code /kb sync}, or {@code /kb set <file.json>}.
 */
public final class KbCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE =
            "Usage: /kb off | /kb sync | /kb set <file.json> | /kb list | /kb current | /kb reload";

    private final KnockbackService knockbackService;

    public KbCommand(KnockbackService knockbackService) {
        this.knockbackService = knockbackService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text(USAGE, NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "off", "paper", "none", "passthrough" -> {
                knockbackService.disable();
                sender.sendMessage(Component.text(
                        "Knockback off. Paper vanilla melee, no ping sync.", NamedTextColor.GREEN));
                yield true;
            }
            case "sync", "knockbacksync", "kbsync" -> {
                knockbackService.syncOnly();
                sender.sendMessage(Component.text(
                        "KnockbackSync Y-only. Paper numbers + ping ground sync.", NamedTextColor.GREEN));
                yield true;
            }
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /kb set <off|sync|file.json>  (see /kb list)", NamedTextColor.YELLOW));
                    yield true;
                }
                if (!knockbackService.apply(args[1])) {
                    sender.sendMessage(Component.text(
                            "Preset not found. Use /kb off|sync or put JSON in plugins/RumilancePractice/kb/",
                            NamedTextColor.RED));
                    yield true;
                }
                sendCurrent(sender);
                yield true;
            }
            case "list" -> {
                List<String> names = listedNames();
                sender.sendMessage(Component.text("KB presets: " + String.join(", ", names),
                        NamedTextColor.AQUA));
                yield true;
            }
            case "current" -> {
                sendCurrent(sender);
                yield true;
            }
            case "reload" -> {
                knockbackService.load();
                sender.sendMessage(Component.text("Reloaded.", NamedTextColor.GREEN));
                sendCurrent(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text(USAGE, NamedTextColor.YELLOW));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            return List.of();
        }
        String current = TabCompletions.current(args);
        if (args.length == 1) {
            return TabCompletions.filter(current, "off", "sync", "set", "list", "current", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return TabCompletions.filter(current, listedNames());
        }
        return List.of();
    }

    private void sendCurrent(CommandSender sender) {
        if (!knockbackService.rewriteEnabled() && !knockbackService.syncEnabled()) {
            sender.sendMessage(Component.text(
                    "Active: off (Paper vanilla, no sync)", NamedTextColor.AQUA));
            return;
        }
        if (!knockbackService.rewriteEnabled() && knockbackService.syncEnabled()) {
            sender.sendMessage(Component.text(
                    "Active: sync (Paper melee + KnockbackSync Y)", NamedTextColor.AQUA));
            return;
        }
        sender.sendMessage(Component.text(
                "Active: " + knockbackService.activeName() + " (rewrite+sync)", NamedTextColor.AQUA));
        sender.sendMessage(Component.text(summarize(knockbackService.profile()), NamedTextColor.GRAY));
    }

    private List<String> listedNames() {
        List<String> names = new ArrayList<>();
        names.add(KnockbackService.OFF_NAME);
        names.add("sync");
        names.addAll(knockbackService.listProfiles());
        return names;
    }

    private static String summarize(KnockbackProfile profile) {
        return "H=" + profile.horizontalKb()
                + " V=" + profile.verticalKb()
                + " AirV=" + profile.airVerticalKb()
                + " SprintH=" + profile.sprintKb()
                + " SprintV=" + profile.sprintVerticalKb()
                + " YLim=" + profile.verticalLimit()
                + " Atk=" + profile.attackKnockback()
                + " Friction=" + profile.targetVelocity()
                + " Dir=" + profile.knockbackDirection().name().toLowerCase(Locale.ROOT);
    }
}
