package com.rumilance.practice.practice;

import com.rumilance.practice.kit.KitService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /botadmin <botkit> <arenakit>} — binds a bot fight kit to the arena kit whose arenas
 * host the fight (BOT fights are NOT practice rooms: once bound, clicking the bot mode takes
 * the fight straight into that arena kit's arenas). {@code off} removes the binding, no
 * arguments lists the active bindings.
 */
public final class BotAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "rumilance.admin";

    private final PracticeService practiceService;
    private final KitService kitService;

    public BotAdminCommand(PracticeService practiceService, KitService kitService) {
        this.practiceService = practiceService;
        this.kitService = kitService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            Map<String, String> bindings = practiceService.botArenaKitBindings();
            if (bindings.isEmpty()) {
                sender.sendMessage(Component.text(
                        "BOT↔アリーナの紐づけはありません。/botadmin <botkit> <arenakit>", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("BOT↔アリーナ紐づけ:", NamedTextColor.GOLD));
            bindings.forEach((botKit, arenaKit) -> sender.sendMessage(Component.text(
                    " - " + botKit + " → " + arenaKit, NamedTextColor.YELLOW)));
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(Component.text(
                    "Usage: /botadmin <botkit> <arenakit|off>", NamedTextColor.YELLOW));
            return true;
        }
        String botKit = args[0];
        if (!kitExists(botKit)) {
            sender.sendMessage(Component.text(
                    "BOTキット '" + botKit + "' が見つかりません。", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 2 && "off".equalsIgnoreCase(args[1])) {
            if (practiceService.clearBotArenaKit(botKit)) {
                sender.sendMessage(Component.text(
                        botKit + " のアリーナ紐づけを解除しました（Prac部屋/キット自身のアリーナに戻ります）。",
                        NamedTextColor.YELLOW));
            } else {
                sender.sendMessage(Component.text(
                        botKit + " には紐づけがありません。", NamedTextColor.RED));
            }
            return true;
        }
        String arenaKit = args[1];
        if (arenaKit.equalsIgnoreCase(botKit)) {
            sender.sendMessage(Component.text(
                    "同じキット同士は紐づけできません（そのままでOK）。", NamedTextColor.RED));
            return true;
        }
        if (!kitExists(arenaKit)) {
            sender.sendMessage(Component.text(
                    "アリーナキット '" + arenaKit + "' が見つかりません。", NamedTextColor.RED));
            return true;
        }
        java.util.List<String> arenas = kitService.get(arenaKit)
                .map(kit -> kit.arenas() == null ? java.util.List.<String>of() : kit.arenas())
                .orElse(java.util.List.of());
        if (arenas.isEmpty()) {
            sender.sendMessage(Component.text(
                    arenaKit + " にはアリーナが1つも設定されていません。先にアリーナを登録してください。",
                    NamedTextColor.RED));
            return true;
        }
        practiceService.setBotArenaKit(botKit, arenaKit);
        sender.sendMessage(Component.text(
                "紐づけました: " + botKit + " → " + arenaKit + "（アリーナ" + arenas.size()
                        + "件。このBOT対戦はそのアリーナで開催されます）", NamedTextColor.GREEN));
        return true;
    }

    private boolean kitExists(String name) {
        return kitService != null && kitService.get(name).isPresent();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        List<String> kits = kitService == null ? List.of()
                : kitService.all().stream()
                        .map(com.rumilance.practice.model.KitDefinition::name)
                        .collect(Collectors.toCollection(ArrayList::new));
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return kits.stream().filter(k -> k.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>(kits);
            options.add("off");
            return options.stream().filter(k -> k.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
