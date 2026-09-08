package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.tier.TierService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /tier} — shows the player's skill tiers <b>per kit</b> from real PvP: within one
 * kit, players with 20+ ranked matches are ranked by that kit's ELO and the resulting
 * rarity percentile maps to the tierlist ladder (HT1 &gt; LT1 &gt; HT2 &gt; … &gt; LT5;
 * HT1 = top 0.1%, "1 in 1000"). {@code /tier bands} lists the band table. Kits without a
 * placement yet simply don't appear. Self-view only.
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
        Map<String, TierService.Standing> standings = tierService.standingsOf(player.getUniqueId());
        player.sendMessage(messageService.render(player, "tier.header"));
        if (standings.isEmpty()) {
            player.sendMessage(messageService.render(player, "tier.unranked"));
            player.sendMessage(messageService.render(player, "tier.unranked-progress",
                    MessageService.tags("n", String.valueOf(tierService.minMatches()))));
            player.sendMessage(messageService.render(player, "tier.hint"));
            return true;
        }
        List<TierService.Standing> ordered = new ArrayList<>(standings.values());
        ordered.sort(Comparator.comparing((TierService.Standing s) -> s.tier().ordinal())
                .thenComparing(TierService.Standing::kit));
        for (TierService.Standing s : ordered) {
            player.sendMessage(renderKitLine(player, s));
        }
        player.sendMessage(messageService.render(player, "tier.hint"));
        return true;
    }

    /** One kit line: kit name, colored tier label, rarity, ELO and match count. */
    private Component renderKitLine(Player player, TierService.Standing s) {
        NamedTextColor color = NamedTextColor.NAMES.value(s.tier().color().name().toLowerCase(Locale.ROOT));
        if (color == null) {
            color = NamedTextColor.WHITE;
        }
        TagResolver tierTag = TagResolver.resolver("tier",
                Tag.inserting(Component.text(s.tier().label(), color)));
        TagResolver[] tags = MessageService.tags(
                "kit", s.kit(),
                "rank", String.valueOf(s.rank()),
                "population", String.valueOf(s.population()),
                "pct", String.format(Locale.ROOT, "%.2f", s.percentile() * 100.0d),
                "elo", String.valueOf(s.elo()),
                "matches", String.valueOf(s.matches()));
        TagResolver[] all = Arrays.copyOf(tags, tags.length + 1);
        all[tags.length] = tierTag;
        return messageService.render(player, "tier.kit-line", all);
    }

    private void showBands(Player player) {
        player.sendMessage(messageService.render(player, "tier.bands-title"));
        String[] labels = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5"};
        String[] shares = {"0.1", "0.3", "1", "3", "10", "20", "35", "50", "70", "100"};
        for (int i = 0; i < labels.length; i++) {
            String prev = i == 0 ? "0" : shares[i - 1];
            player.sendMessage(Component.text("  " + labels[i] + ": top " + prev + "\u2013" + shares[i] + "%",
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
