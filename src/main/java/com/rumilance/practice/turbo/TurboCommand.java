package com.rumilance.practice.turbo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /turbo on|off|status} — Windows 10 system-level optimization through standard tools.
 *
 * <p>{@code on}/{@code off} run off the server main thread and touch only Windows power-plan
 * settings (never the Minecraft server process itself). The first {@code on}/{@code off} triggers
 * exactly one Windows UAC prompt; approve it once and the whole recipe runs. See
 * {@link WindowsOptimizationService} for the full safety/reversibility model.</p>
 */
public final class TurboCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final WindowsOptimizationService service;

    public TurboCommand(JavaPlugin plugin, WindowsOptimizationService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "on", "apply", "start" -> runApply(sender);
            case "off", "revert", "stop" -> runRevert(sender);
            case "status", "state", "info" -> runStatus(sender);
            case "help" -> help(sender, label);
            default -> usage(sender, label);
        }
        return true;
    }

    private void runApply(CommandSender sender) {
        if (!WindowsOptimizationService.isWindows()) {
            sender.sendMessage(Component.text(
                    "このサーバは Windows ではないため、システム最適化 (turbo) は利用できません。",
                    NamedTextColor.RED));
            sender.sendMessage(Component.text(
                    "Linux の場合は config.yml の JVM 起動引数 (ZGC/Aikar フラグ等) と "
                            + "docs/system-level-optimization-design.md の手動チューニングを参照してください。",
                    NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(
                "Turbo最適化を開始します… (初回は Windows の UAC ダイアログが表示されます。承認してください)",
                NamedTextColor.GREEN));
        service.applyAsync().whenComplete((result, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage(Component.text(
                            "内部エラーで Turbo最適化を完了できませんでした: " + error.getMessage(),
                            NamedTextColor.RED));
                    plugin.getLogger().warning("turbo: apply failed: " + error);
                    return;
                }
                for (String line : result.lines()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
                }
            });
        });
    }

    private void runRevert(CommandSender sender) {
        if (!WindowsOptimizationService.isWindows()) {
            sender.sendMessage(Component.text(
                    "このサーバは Windows ではないため、システム最適化 (turbo) は利用できません。",
                    NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text(
                "Turbo最適化を解除します… (初回は Windows の UAC ダイアログが表示される場合があります)",
                NamedTextColor.GREEN));
        service.revertAsync().whenComplete((result, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage(Component.text(
                            "内部エラーで Turbo解除を完了できませんでした: " + error.getMessage(),
                            NamedTextColor.RED));
                    plugin.getLogger().warning("turbo: revert failed: " + error);
                    return;
                }
                for (String line : result.lines()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
                }
            });
        });
    }

    private void runStatus(CommandSender sender) {
        service.statusAsync().whenComplete((report, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage(Component.text("ステータス取得に失敗しました。", NamedTextColor.RED));
                    return;
                }
                if (!report.windows()) {
                    sender.sendMessage(Component.text("OS: 非 Windows — turbo は利用できません。",
                            NamedTextColor.GRAY));
                    return;
                }
                sender.sendMessage(Component.text("==== Turbo / システム最適化 ====", NamedTextColor.AQUA));
                sender.sendMessage(Component.text("OS: Windows", NamedTextColor.WHITE));
                sender.sendMessage(Component.text("管理者権限: " + adminWord(report),
                        NamedTextColor.WHITE));
                sender.sendMessage(Component.text("Turbo適用中: " + (report.applied() ? "はい" : "いいえ"),
                        report.applied() ? NamedTextColor.GREEN : NamedTextColor.WHITE));
                sender.sendMessage(Component.text("省電力スロットリング(EcoQoS): " + (report.ecoThrottled() ? "有効" : "無効"),
                        report.ecoThrottled() ? NamedTextColor.GREEN : NamedTextColor.WHITE));
                sender.sendMessage(Component.text("現在のアクティブプラン: " + safe(report.activeScheme()),
                        NamedTextColor.WHITE));
                if (report.applied()) {
                    sender.sendMessage(Component.text("Turboプラン: " + safe(report.appliedScheme()),
                            NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("適用前プラン: " + safe(report.backupScheme()),
                            NamedTextColor.WHITE));
                    sender.sendMessage(Component.text("アイドル無効化: " + (report.idleWasSet() ? "有効" : "無効"),
                            NamedTextColor.WHITE));
                }
                if (report.note() != null) {
                    sender.sendMessage(Component.text(report.note(), NamedTextColor.GRAY));
                }
                if (!report.applied()) {
                    sender.sendMessage(Component.text("開始: /turbo on 、解除: /turbo off", NamedTextColor.GRAY));
                }
            });
        });
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("使い方: /" + label + " on|off|status", NamedTextColor.YELLOW));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== Turbo 使い方 ====", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/" + label + " on      - Windows 電源プランを最適化 (P-state固定・コアパーキング解除)。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/" + label + " off     - 元の電源プランへ戻して解除。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/" + label + " status  - 現在の状態 (プラン/省電力) を表示。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
                "自動切替: turbo.auto.enabled: true なら「最後の人が抜けたら省電力化、人が来たら即フルパワー復帰」。",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("危険性・推奨事項は config.yml の turbo: セクションを参照。", NamedTextColor.GRAY));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "(不明)" : value;
    }

    private static String adminWord(WindowsOptimizationService.StateReport report) {
        if (report.privileged()) {
            return "あり (サーバーコンソールが管理者)";
        }
        // Server JVM is not elevated; privileged work runs in the resident helper (UAC once).
        return report.applied() ? "ヘルパー経由 (サーバーJVMは非管理者)" : "ヘルパー経由 (UAC昇格を使用)";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            return Arrays.stream(new String[]{"on", "off", "status", "help"})
                    .filter(opt -> opt.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
