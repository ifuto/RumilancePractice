package com.rumilance.practice.practice;

import com.rumilance.practice.kit.KitService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /botadmin} — two binding forms, told apart by the first argument:
 *
 * <ol>
 *   <li><b>/botadmin &lt;SWORD|MACE|CRYSTAL|NETHERITE_POT|CART&gt; &lt;kit|off&gt;</b> — the
 *   LOADOUT binding the admin actually expects: that bot mode fights with the named server
 *   kit (applied to the player at match start; the bot wears the matching gear). Same store
 *   as {@code /practice bindkit}, so the two commands are interchangeable.</li>
 *   <li><b>/botadmin &lt;botkit&gt; &lt;arenakit|off&gt;</b> — the VENUE binding: fights that
 *   run with the named bot kit are hosted in the arena kit's arenas (instead of the kit's
 *   own). Same kit on both sides is allowed and is an explicit no-op.</li>
 * </ol>
 * No arguments lists both binding tables.
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
            Map<String, String> modeBindings = practiceService.botModeKitsView();
            Map<String, String> arenaBindings = practiceService.botArenaKitBindings();
            if (modeBindings.isEmpty() && arenaBindings.isEmpty()) {
                sender.sendMessage(Component.text(
                        "紐づけはありません。/botadmin <SWORD|MACE|CRYSTAL|NETHERITE_POT|CART> <kit>",
                        NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("BOT戦の紐づけ:", NamedTextColor.GOLD));
            modeBindings.forEach((mode, kit) -> sender.sendMessage(Component.text(
                    " - " + mode + " BOT → キット '" + kit + "'（装備）", NamedTextColor.YELLOW)));
            arenaBindings.forEach((botKit, arenaKit) -> sender.sendMessage(Component.text(
                    " - " + botKit + " → " + arenaKit + "（開催アリーナ）", NamedTextColor.YELLOW)));
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(Component.text(
                    "Usage: /botadmin <SWORD|MACE|CRYSTAL|NETHERITE_POT|CART|botkit> <kit|arenakit|off>",
                    NamedTextColor.YELLOW));
            return true;
        }

        // ---- form 1: <BOT MODE> <kit|off> — the loadout binding ----
        PracticeType mode = modeOrNull(args[0]);
        if (mode != null && mode.botMode()) {
            if ("off".equalsIgnoreCase(args[1]) || "clear".equalsIgnoreCase(args[1])) {
                boolean had = practiceService.botKitFor(mode) != null;
                practiceService.bindBotKit(mode, null);
                sender.sendMessage(Component.text(had
                        ? mode + " BOT のキット紐づけを解除しました（モード標準装備に戻ります）。"
                        : mode + " BOT にはキット紐づけがありません。",
                        had ? NamedTextColor.YELLOW : NamedTextColor.RED));
                return true;
            }
            String kitName = args[1];
            if (!kitExists(kitName)) {
                sender.sendMessage(Component.text(
                        "キット '" + kitName + "' が見つかりません。", NamedTextColor.RED));
                return true;
            }
            practiceService.bindBotKit(mode, kitName);
            sender.sendMessage(Component.text(
                    "紐づけました: " + mode + " BOT → キット '" + kitName
                            + "'（試合開始時にこのキットを適用。開催地はキットのアリーナ、未設定なら練習部屋）",
                    NamedTextColor.GREEN));
            return true;
        }

        // ---- form 2: <botkit> <arenakit|off> — the venue binding ----
        String botKit = args[0];
        if (!kitExists(botKit)) {
            sender.sendMessage(Component.text(
                    "'" + botKit + "' はBOTモードでもキットでもありません。BOTモード: SWORD, MACE, "
                            + "CRYSTAL, NETHERITE_POT, CART", NamedTextColor.RED));
            return true;
        }
        if ("off".equalsIgnoreCase(args[1])) {
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
        // Same kit is ALLOWED (e.g. /botadmin mace mace): an explicit no-op binding — the
        // fight loadout stays the bot kit and the venue is the kit's own arenas.
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
        boolean same = arenaKit.equalsIgnoreCase(botKit);
        sender.sendMessage(Component.text(
                "紐づけました: " + botKit + " → " + arenaKit + "（アリーナ" + arenas.size()
                        + "件。このBOT対戦はそのアリーナで開催されます"
                        + (same ? "。同一キットなので装備もこのキットのままです" : "") + "）",
                NamedTextColor.GREEN));
        return true;
    }

    /** {@code SWORD}/{@code MACE}/… as a bot mode, or {@code null} when it is not one. */
    private static PracticeType modeOrNull(String raw) {
        try {
            PracticeType type = PracticeType.parse(raw);
            return type.botMode() ? type : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean kitExists(String name) {
        return kitService != null && kitService.get(name).isPresent();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        List<String> kits = kitService == null ? List.of()
                : kitService.all().stream()
                        .map(com.rumilance.practice.model.KitDefinition::name)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toCollection(ArrayList::new));
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                    "SWORD", "MACE", "CRYSTAL", "NETHERITE_POT", "CART"));
            options.addAll(kits);
            return filter(args[0], options);
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>(kits);
            options.add("off");
            return filter(args[1], options);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .toList();
    }
}
