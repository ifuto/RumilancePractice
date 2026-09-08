package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.tier.TierService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /tier} — shows the player's auto skill tier from real PvP: their best-kit ranked
 * ELO is ranked against every eligible player on the server (20+ ranked matches) and the
 * resulting rarity percentile maps to HT5..HT1 / LT5..LT1 (HT1 = top 0.1%, "1 in 1000").
 * {@code /tier bands} lists the band table. Self-view only.
 */
public final class TierCommand implements CommandExecutor, TabCompleter {

    private final TierService tierService;
    private final MessageService messageService;

    public TierCommand(TierService tierService, MessageService messageService) {
        this.tierService = tierService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("In-game only.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("bands")) {
            showBands(player);
            return true;
        }
        var standing = tierService.standingOf(player.getUniqueId());
        player.sendMessage(messageService.render(player, "tier.header"));
        if (standing.isEmpty()) {
            player.sendMessage(messageService.render(player, "tier.unranked"));
            player.sendMessage(messageService.render(player, "tier.unranked-progress",
                    MessageService.tags("n", String.valueOf(tierService.minMatches()))));
            player.sendMessage(messageService.render(player, "tier.hint"));
            return true;
        }
        TierService.Standing s = standing.get();
        NamedTextColor color = NamedTextColor.NAMES.value(s.tier().color().name().toLowerCase(Locale.ROOT));
        if (color == null) {
            color = NamedTextColor.WHITE;
        }
        player.sendMessage(messageService.render(player, "tier.current")
                .append(Component.text(s.tier().label(), color)));
        player.sendMessage(messageService.render(player, "tier.rank", MessageService.tags(
                "rank", String.valueOf(s.rank()),
                "population", String.valueOf(s.population()),
                "pct", String.format(Locale.ROOT, "%.2f", s.percentile() * 100.0d))));
        player.sendMessage(messageService.render(player, "tier.stats", MessageService.tags(
                "kit", s.topKit(),
                "elo", String.valueOf(s.bestElo()),
                "matches", String.valueOf(s.matches()))));
        player.sendMessage(messageService.render(player, "tier.hint"));
        return true;
    }

    private void showBands(Player player) {
        player.sendMessage(messageService.render(player, "tier.bands-title"));
        String[] labels = {"HT1", "HT2", "HT3", "HT4", "HT5", "LT1", "LT2", "LT3", "LT4", "LT5"};
        String[] shares = {"0.1", "0.3", "1", "3", "10", "20", "35", "50", "70", "100"};
        for (int i = 0; i < labels.length; i++) {
            String prev = i == 0 ? "0" : shares[i - 1];
            player.sendMessage(Component.text("  " + labels[i] + ": top " + prev + "–" + shares[i] + "%",
                    NamedTextColor.GRAY));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && "bands".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("bands");
        }
        return List.of();
    }
}
