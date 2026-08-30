package com.rumilance.practice.command;

import com.rumilance.practice.security.sign.SignProbeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin command that runs the active (DonutSMP-style) client-mod detector against a target player.
 */
public final class SignCheckCommand implements CommandExecutor, TabCompleter {

    private final SignProbeService probeService;

    public SignCheckCommand(SignProbeService probeService) {
        this.probeService = probeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        if (!probeService.isAvailable()) {
            sender.sendMessage(Component.text(
                    "看板プローブは無効です（ProtocolLib 未導入 / config で無効 / 未対応バージョン）。",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("使い方: /signcheck <player>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("プレイヤーが見つかりません: " + args[0], NamedTextColor.RED));
            return true;
        }
        Player requester = sender instanceof Player p ? p : null;
        boolean started = probeService.probe(target, requester);
        if (!started) {
            sender.sendMessage(Component.text("検査を開始できませんでした（config で無効の可能性）。",
                    NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
