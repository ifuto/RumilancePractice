package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.CustomShieldAdminGui;
import com.rumilance.practice.hiddenrank.HiddenRankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * OP-only hidden-rank management: {@code /urank}. Hidden ranks never appear in any display —
 * they silently grant perks. Currently the only hidden rank is {@code custom_shield}.
 *
 * <pre>
 * /urank custom_shield &lt;player&gt;   grant the hidden custom_shield rank
 * /urank remove &lt;player&gt;          remove the hidden rank
 * /urank shield &lt;player&gt; &lt;cmd&gt;    assign the shield Custom Model Data
 * /urank gui                      open the Custom Model Data assignment screen
 * /urank list                     list holders
 * </pre>
 */
public final class HiddenRankCommand implements CommandExecutor, TabCompleter {

    private final HiddenRankService hiddenRanks;
    private final CustomShieldAdminGui adminGui;

    public HiddenRankCommand(HiddenRankService hiddenRanks, CustomShieldAdminGui adminGui) {
        this.hiddenRanks = hiddenRanks;
        this.adminGui = adminGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("OP only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                if (sender instanceof Player player) {
                    adminGui.open(player);
                } else {
                    sender.sendMessage(Component.text("Console cannot open GUIs.", NamedTextColor.RED));
                }
            }
            case "list" -> {
                var holders = hiddenRanks.customShieldHolders();
                if (holders.isEmpty()) {
                    sender.sendMessage(Component.text("No hidden custom_shield holders.", NamedTextColor.YELLOW));
                    return true;
                }
                for (UUID uuid : holders) {
                    sender.sendMessage(Component.text(
                            hiddenRanks.lastName(uuid) + "  cmd=" + hiddenRanks.shieldModelData(uuid),
                            NamedTextColor.AQUA));
                }
            }
            case "custom_shield", "customshield", "shield_rank" -> {
                if (args.length < 2) {
                    usage(sender);
                    return true;
                }
                OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
                UUID uuid;
                String name;
                if (target != null) {
                    uuid = target.getUniqueId();
                    name = target.getName();
                } else {
                    sender.sendMessage(Component.text("Player must be online.", NamedTextColor.RED));
                    return true;
                }
                hiddenRanks.setCustomShield(uuid, name, true);
                sender.sendMessage(Component.text(
                        "Hidden rank custom_shield granted to " + name + ".", NamedTextColor.GREEN));
                sender.sendMessage(Component.text(
                        "Assign a model data with /urank shield " + name + " <cmd> or /urank gui",
                        NamedTextColor.GRAY));
            }
            case "shield", "cmdata", "cmd" -> {
                if (args.length < 3) {
                    usage(sender);
                    return true;
                }
                OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player must be online.", NamedTextColor.RED));
                    return true;
                }
                int cmd;
                try {
                    cmd = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Model data must be a number.", NamedTextColor.RED));
                    return true;
                }
                hiddenRanks.setShieldModelData(target.getUniqueId(), cmd);
                sender.sendMessage(Component.text(
                        target.getName() + " shield model data = " + cmd, NamedTextColor.GREEN));
            }
            case "remove", "delete" -> {
                if (args.length < 2) {
                    usage(sender);
                    return true;
                }
                OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player must be online.", NamedTextColor.RED));
                    return true;
                }
                hiddenRanks.setCustomShield(target.getUniqueId(), target.getName(), false);
                sender.sendMessage(Component.text(
                        "Hidden rank removed from " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "/urank custom_shield <player> | remove <player> | shield <player> <cmd> | gui | list",
                NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("custom_shield", "remove", "shield", "gui", "list"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("gui") && !args[0].equalsIgnoreCase("list")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted()
                .toList();
    }
}
