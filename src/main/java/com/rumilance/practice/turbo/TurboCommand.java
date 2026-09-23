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
    private final JvmGcService jvmGc;

    public TurboCommand(JavaPlugin plugin, WindowsOptimizationService service, JvmGcService jvmGc) {
        this.plugin = plugin;
        this.service = service;
        this.jvmGc = jvmGc;
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
            case "jvm", "gc" -> runJvm(sender);
            case "make-start", "bat" -> writeStartScript(sender);
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

    private void runJvm(CommandSender sender) {
        JvmGcService.Report report = jvmGc.report();
        sender.sendMessage(Component.text("==== JVM / GC 診断 ====", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("JVM: " + report.vmName() + " " + report.vmVersion(),
                NamedTextColor.WHITE));
        sender.sendMessage(Component.text("ヒープ: 使用 " + mega(report.heapUsedBytes())
                        + " / 確保 " + mega(report.heapCommittedBytes())
                        + " / 上限 " + (report.heapMaxBytes() > 0 ? mega(report.heapMaxBytes()) : "未指定"),
                NamedTextColor.WHITE));
        sender.sendMessage(Component.text("論理プロセッサ: " + report.processors(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("GC: " + String.join(", ", report.collectorNames()),
                NamedTextColor.WHITE));
        sender.sendMessage(Component.text("GCモード: " + (report.zgc() ? "ZGC(低停止) " : "")
                        + (report.shenandoah() ? "Shenandoah(低停止)" : ""),
                NamedTextColor.WHITE));
        if (!report.jvmArgs().isEmpty()) {
            sender.sendMessage(Component.text("起動引数: " + String.join(" ", report.jvmArgs()),
                    NamedTextColor.GRAY));
        }
        for (String recommendation : report.recommendations()) {
            sender.sendMessage(Component.text("→ " + recommendation, NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text(
                "低停止GC化には再起動が必要です: /turbo make-start が最適化済み start.bat を plugins/n-arena/ に生成します。",
                NamedTextColor.GRAY));
    }

    private void writeStartScript(CommandSender sender) {
        String text = jvmGc.startScriptText();
        java.io.File dataFolder = com.rumilance.practice.PluginIdentity.dataFolder(plugin);
        java.io.File out = new java.io.File(dataFolder, "start.bat");
        try {
            java.nio.file.Files.writeString(out.toPath(), text, java.nio.charset.StandardCharsets.UTF_8);
            sender.sendMessage(Component.text("最適化済み起動スクリプトを生成しました: "
                    + out.getAbsolutePath(), NamedTextColor.GREEN));
            sender.sendMessage(Component.text(
                    "内容: ZGC(低停止GC) + ヒープ固定(-Xms=-Xmx) + GCスレッド自動割当。",
                    NamedTextColor.WHITE));
            sender.sendMessage(Component.text(
                    "使い方: サーバーを /stop で落とし、上記 start.bat を paper.jar と同じ階層へ置いて"
                            + " jar 名を合わせ、start.bat から再起動してください。",
                    NamedTextColor.GRAY));
        } catch (java.io.IOException e) {
            sender.sendMessage(Component.text("start.bat の書き込みに失敗しました: " + e.getMessage(),
                    NamedTextColor.RED));
        }
    }

    private static String mega(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.0fMB", bytes / 1048576.0);
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("使い方: /" + label + " on|off|status|jvm|make-start", NamedTextColor.YELLOW));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("==== Turbo 使い方 ====", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/" + label + " on      - Windows 電源プランを最適化 (P-state固定・コアパーキング解除)。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/" + label + " off     - 元の電源プランへ戻して解除。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/" + label + " status  - 現在の状態 (プラン/省電力) を表示。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/" + label + " jvm     - GC/ヒープの現状診断と改善提案。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/" + label + " make-start - 低停止GC(ZGC)+ヒープ固定の start.bat を生成。", NamedTextColor.WHITE));
        sender.sendMessage(Component.text(
                "自動切替: turbo.auto.enabled: true なら「最後の人が抜けたら省電力化、人が来たら即フルパワー復帰」。",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "バックグラウンドGC: turbo.gc-background.enabled: true なら、無人在中かつ tick に"
                        + " 余裕があるときだけ System.gc() をワーカースレッドで先行実行し、"
                        + " 試合中の突然のGC停止を減らします。",
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
            return Arrays.stream(new String[]{"on", "off", "status", "jvm", "make-start", "help"})
                    .filter(opt -> opt.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
