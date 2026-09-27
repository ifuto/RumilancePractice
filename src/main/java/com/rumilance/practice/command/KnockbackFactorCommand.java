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
import java.util.Locale;
import java.util.Map;

/**
 * Operator knobs for the knockback coefficients ({@link KnockbackTuning}) — usable in game,
 * from the server console, and from the shield-web management site's console tab.
 *
 * <p>KBM 風のプロファイル構成: {@code global} &gt; per-{@code cause} (ATTACK / EXPLOSION / …)
 * &gt; per-{@code kit}。優先度は kit &gt; cause &gt; global。係数は Paper の最終ノックバック
 * ベクトルにだけ掛かり、バニラの計算（耐衝撃属性の軽減含む）は untouched です。</p>
 */
public final class KnockbackFactorCommand implements CommandExecutor, TabCompleter {

    private static final List<String> CAUSES = List.of(
            "ENTITY_ATTACK", "SWEEP_ATTACK", "EXPLOSION", "SHIELD_BLOCK", "DAMAGE", "UNKNOWN");

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
            status(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "h", "horizontal" -> setGlobal(sender, label, args, true);
            case "v", "vertical" -> setGlobal(sender, label, args, false);
            case "cause" -> scopedSet(sender, label, args, false);
            case "kit" -> scopedSet(sender, label, args, true);
            case "reset" -> scopedReset(sender, label, args);
            default -> sender.sendMessage(Component.text(
                    "使い方: /" + label + " <h|v|cause|kit|reset>", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void status(CommandSender sender, String label) {
        KnockbackTuning.Factor g = tuning.global();
        sender.sendMessage(Component.text(
                "Knockback係数（global）: horizontal=" + fmt(g.horizontal())
                        + " vertical=" + fmt(g.vertical())
                        + (tuning.isNeutral() ? "（完全にバニラ通り）" : ""), NamedTextColor.AQUA));
        for (Map.Entry<String, KnockbackTuning.Factor> e : tuning.causeOverridesView().entrySet()) {
            sender.sendMessage(Component.text(
                    "  cause " + e.getKey() + ": " + fmt(e.getValue().horizontal())
                            + " / " + fmt(e.getValue().vertical()), NamedTextColor.GRAY));
        }
        for (Map.Entry<String, KnockbackTuning.Factor> e : tuning.kitOverridesView().entrySet()) {
            sender.sendMessage(Component.text(
                    "  kit " + e.getKey() + ": " + fmt(e.getValue().horizontal())
                            + " / " + fmt(e.getValue().vertical()), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text(
                "優先度: kit > cause > global。適用は Paper の最終ベクトルのみ（計算式はバニラのまま）。",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                "例: /" + label + " h 0.9 ・ /" + label + " v 1.0 ・ /" + label + " cause EXPLOSION h 0.5 v 0.5 ・ /" + label
                        + " kit nodebuff h 1.1 v 0.9 ・ /" + label + " reset kit nodebuff",
                NamedTextColor.YELLOW));
    }

    /** /kbf h|v <value> — global factor. */
    private void setGlobal(CommandSender sender, String label, String[] args, boolean horizontalAxis) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "使い方: /" + label + " " + (horizontalAxis ? "h" : "v") + " <0.0-4.0>",
                    NamedTextColor.YELLOW));
            return;
        }
        final double value;
        try {
            value = KnockbackTuning.parseFactor(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }
        KnockbackTuning.Factor g = tuning.global();
        if (horizontalAxis) {
            tuning.setGlobal(value, g.vertical());
        } else {
            tuning.setGlobal(g.horizontal(), value);
        }
        saved(sender, "global");
    }

    /** /kbf cause <CAUSE> h|v <value> ・ /kbf kit <kit> h|v <value> */
    private void scopedSet(CommandSender sender, String label, String[] args, boolean kit) {
        if (args.length < 4 || (!"h".equalsIgnoreCase(args[2]) && !"v".equalsIgnoreCase(args[2]))) {
            String scope = kit ? "kit <kit名>" : "cause <" + String.join("|", CAUSES) + ">";
            sender.sendMessage(Component.text(
                    "使い方: /" + label + " " + scope + " <h|v> <0.0-4.0>", NamedTextColor.YELLOW));
            return;
        }
        String name = args[1];
        final double value;
        try {
            value = KnockbackTuning.parseFactor(args[3]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }
        boolean horizontalAxis = "h".equalsIgnoreCase(args[2]);
        KnockbackTuning.Factor current = tuning.effective(
                kit ? null : name.toUpperCase(Locale.ROOT), kit ? name : "");
        double h = horizontalAxis ? value : current.horizontal();
        double v = horizontalAxis ? current.vertical() : value;
        if (kit) {
            tuning.setKit(name, h, v);
        } else {
            tuning.setCause(name, h, v);
        }
        saved(sender, (kit ? "kit " + name : "cause " + name.toUpperCase(Locale.ROOT)));
    }

    /** /kbf reset [all|global|cause <CAUSE>|kit <kit>] */
    private void scopedReset(CommandSender sender, String label, String[] args) {
        if (args.length == 1 || "all".equalsIgnoreCase(args[1])) {
            tuning.resetOverrides();
            sender.sendMessage(Component.text(
                    "すべての係数を config.yml の既定に戻しました: h=" + fmt(tuning.horizontal())
                            + " v=" + fmt(tuning.vertical()), NamedTextColor.GREEN));
            return;
        }
        String scope = args[1].toLowerCase(Locale.ROOT);
        boolean removed;
        if ("global".equals(scope)) {
            removed = tuning.clear("global", "");
        } else if (("cause".equals(scope) || "kit".equals(scope)) && args.length >= 3) {
            removed = tuning.clear(scope, args[2]);
        } else {
            sender.sendMessage(Component.text(
                    "使い方: /" + label + " reset [all|global|cause <CAUSE>|kit <kit>]",
                    NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(removed
                        ? "override を解除しました（config 既定に戻ります）: " + scope
                        : "解除する override はありませんでした: " + scope,
                removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    private void saved(CommandSender sender, String scope) {
        KnockbackTuning.Factor g = tuning.global();
        sender.sendMessage(Component.text(
                "Knockback係数を更新（" + scope + "） global h=" + fmt(g.horizontal())
                        + " v=" + fmt(g.vertical()) + "（即時反映・永続）", NamedTextColor.GREEN));
    }

    private static String fmt(double value) {
        return value == Math.floor(value)
                ? String.format(Locale.US, "%.1f", value)
                : String.format(Locale.US, "%.3f", value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        String head = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            for (String option : List.of("h", "v", "cause", "kit", "reset", "status")) {
                if (option.startsWith(head)) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && ("h".equals(head) || "v".equals(head))) {
            out.add("1.0");
        } else if (args.length == 2 && "cause".equals(head)) {
            for (String c : CAUSES) {
                if (c.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(c);
                }
            }
        } else if (args.length == 2 && "kit".equals(head)) {
            out.addAll(tuning.kitOverridesView().keySet());
        } else if (args.length == 2 && "reset".equals(head)) {
            for (String option : List.of("all", "global", "cause", "kit")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        } else if (args.length == 3 && ("cause".equals(head) || "kit".equals(head))) {
            out.add("h");
            out.add("v");
        } else if (args.length == 3 && "reset".equals(head) && args[1].equalsIgnoreCase("kit")) {
            out.addAll(tuning.kitOverridesView().keySet());
        } else if (args.length == 4 && ("cause".equals(head) || "kit".equals(head))) {
            out.add("1.0");
        }
        return out;
    }
}
