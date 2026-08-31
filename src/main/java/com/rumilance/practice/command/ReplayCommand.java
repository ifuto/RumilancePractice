package com.rumilance.practice.command;

import com.rumilance.practice.replay.ReplayArchive;
import com.rumilance.practice.replay.ReplayService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.rumilance.practice.command.TabCompletions;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replay viewer entry + transport controls.
 *
 * <p>Access is rank-gated: default players cannot view replays ({@code ReplayArchive.recent}
 * returns nothing for non-VIP+). VIP+ players get the last 3 matches, up to 15 minutes each;
 * recordings are purged two days after the match. {@code /replay} on its own lists the viewer's
 * available replays; {@code /replay watch <index>} starts one; {@code /replay <control>} drives
 * playback (also available via the creative hotbar items).</p>
 */
public final class ReplayCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS =
            List.of("list", "watch", "pause", "play", "rewind", "forward", "speed", "restart", "stop");

    private final ReplayService replayService;
    private final ReplayArchive archive;
    private final com.rumilance.practice.rank.RankService rankService;

    public ReplayCommand(ReplayService replayService, ReplayArchive archive,
                         com.rumilance.practice.rank.RankService rankService) {
        this.replayService = replayService;
        this.archive = archive;
        this.rankService = rankService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        boolean vip = rankService != null && rankService.isVipPlusOrAbove(player);

        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pause", "play" -> {
                requireReplaying(player);
                replayService.togglePause(player);
            }
            case "rewind" -> {
                requireReplaying(player);
                replayService.rewind(player);
            }
            case "forward" -> {
                requireReplaying(player);
                replayService.forward(player);
            }
            case "speed" -> {
                requireReplaying(player);
                replayService.cycleSpeed(player);
            }
            case "restart" -> {
                requireReplaying(player);
                replayService.restart(player);
            }
            case "stop" -> {
                if (replayService.isReplaying(player.getUniqueId())) {
                    replayService.stop(player);
                }
            }
            case "watch" -> {
                if (!vip) {
                    player.sendMessage(Component.text("Replays are a VIP+ feature.", NamedTextColor.RED));
                    return true;
                }
                watchReplay(player, args);
            }
            case "list", "replay" -> listReplays(player, vip);
            default -> player.sendMessage(Component.text(
                    "/replay [list|watch <n>|pause|rewind|forward|speed|restart|stop]", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void requireReplaying(Player player) {
        if (!replayService.isReplaying(player.getUniqueId())) {
            player.sendMessage(Component.text("再生中のリプレイがありません。/replay で一覧を開いてください。", NamedTextColor.RED));
        }
    }

    private void listReplays(Player player, boolean vip) {
        if (!vip) {
            player.sendMessage(Component.text("Replays are a VIP+ feature (last 3 matches, up to 15 min).",
                    NamedTextColor.RED));
            return;
        }
        List<ReplayArchive.RecordedMatch> recent = archive.recent(player.getUniqueId(), true);
        if (recent.isEmpty()) {
            player.sendMessage(Component.text("リプレイがありません。直近の試合が2日間保存されます。", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("─ リプレイ (直近" + recent.size() + "試合・2日で削除) ─",
                NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));
        for (int i = 0; i < recent.size(); i++) {
            ReplayArchive.RecordedMatch m = recent.get(i);
            long mins = m.durationSeconds() / 60;
            long secs = m.durationSeconds() % 60;
            Component line = Component.text("[" + (i + 1) + "] ", NamedTextColor.YELLOW)
                    .append(Component.text(m.kit() + " (" + m.mode() + ") ", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%d:%02d", mins, secs), NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/replay watch " + (i + 1)))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            Component.text("クリックで再生", NamedTextColor.GREEN)));
            player.sendMessage(line);
        }
    }

    private void watchReplay(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使い方: /replay watch <番号>", NamedTextColor.YELLOW));
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("番号を指定してください。", NamedTextColor.RED));
            return;
        }
        List<ReplayArchive.RecordedMatch> recent =
                new ArrayList<>(archive.recent(player.getUniqueId(), true));
        if (index < 1 || index > recent.size()) {
            player.sendMessage(Component.text("その番号のリプレイはありません。", NamedTextColor.RED));
            return;
        }
        replayService.startArchive(player, recent.get(index - 1));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(TabCompletions.current(args), SUBS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("watch") && sender instanceof Player player) {
            int count = archive.recent(player.getUniqueId(),
                    rankService != null && rankService.isVipPlusOrAbove(player)).size();
            List<String> nums = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                nums.add(String.valueOf(i));
            }
            return TabCompletions.filter(TabCompletions.current(args), nums);
        }
        return List.of();
    }
}
