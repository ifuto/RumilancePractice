package com.rumilance.practice.command;

import com.rumilance.practice.combat.KnockbackTuning;
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

/**
 * Operator knobs for the knockback coefficients ({@link KnockbackTuning}) — usable in game,
 * from the server console, and from the shield-web management site's console tab.
 *
 * <p>Only multipliers on Paper's final knockback vector: the vanilla calculation (sprint /
 * enchant strength, netherite knockback resistance, explosion knockback resistance, grounded
 * hop) keeps running underneath, untouched.</p>
 */
public final class KnockbackFactorCommand implements CommandExecutor, TabCompleter {

    private final KnockbackTuning tuning;

    public KnockbackFactorCommand(KnockbackTuning tuning) {
        this.tuning = tuning;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player && !sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(Component.text(
                    "Knockback係数: horizontal=" + fmt(tuning.horizontal())
                            + " vertical=" + fmt(tuning.vertical())
                            + (tuning.isNeutral() ? "（バニラ通り）" : ""), NamedTextColor.AQUA));
            sender.sendMessage(Component.text(
                    "使い方: /" + label + " h <0.0-4.0>  /" + label + " v <0.0-4.0>  /" + label + " reset",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(
                    "バニラの計算そのままに、最終ノックバックへ掛かる倍率だけを変えます（耐衝撃属性も有効のまま）。",
                    NamedTextColor.GRAY));
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "h", "horizontal", "x" -> setFactor(sender, label, args, true);
            case "v", "vertical", "y" -> setFactor(sender, label, args, false);
            case "reset", "clear" -> {
                tuning.resetOverrides();
                sender.sendMessage(Component.text(
                        "Knockback係数を初期値（config.yml）へ戻しました: horizontal="
                                + fmt(tuning.horizontal()) + " vertical=" + fmt(tuning.vertical()),
                        NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "使い方: /" + label + " <h|v|reset>", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void setFactor(CommandSender sender, String label, String[] args, boolean horizontalAxis) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "使い方: /" + label + " " + (horizontalAxis ? "h" : "v") + " <0.0-4.0>",
                    NamedTextColor.YELLOW));
            return;
        }
        double value;
        try {
            value = KnockbackTuning.parseFactor(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }
        if (horizontalAxis) {
            tuning.setHorizontal(value);
        } else {
            tuning.setVertical(value);
        }
        sender.sendMessage(Component.text(
                "Knockback係数を更新: horizontal=" + fmt(tuning.horizontal())
                        + " vertical=" + fmt(tuning.vertical()) + "（即時反映・永続）",
                NamedTextColor.GREEN));
    }

    private static String fmt(double value) {
        return value == Math.floor(value)
                ? String.format(java.util.Locale.US, "%.1f", value)
                : String.format(java.util.Locale.US, "%.3f", value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("h", "v", "reset", "status")) {
                if (option.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && ("h".equalsIgnoreCase(args[0]) || "v".equalsIgnoreCase(args[0]))) {
            out.add("1.0");
        }
        return out;
    }
}
