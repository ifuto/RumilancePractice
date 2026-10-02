package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.tier.TierService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Tier display GUI — shows per-kit skill tiers in a visual card layout.
 * Opened from {@code /tier} with no arguments.
 */
public final class TierGui extends AbstractGui {

    private final TierService tierService;

    public TierGui(GuiSessionRegistry registry, SoundService sounds, TierService tierService) {
        super(registry, sounds, GuiType.TIER, 4, false);
        this.tierService = tierService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.DARK;
    }

    @Override
    protected Material titleIcon() {
        return Material.NETHERITE_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "tier.header").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        Map<String, TierService.Standing> standings = tierService.standingsOf(player.getUniqueId());
        if (standings.isEmpty()) {
            inventory.setItem(GuiSlots.slot(1, 4), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "tier.unranked").color(NamedTextColor.RED))
                    .lore(UiTheme.divider(),
                            UiTheme.line(line(player, "tier.unranked-progress")
                                    .replace("<n>", String.valueOf(tierService.minMatches()))),
                            UiTheme.blank(),
                            UiTheme.hint(line(player, "tier.hint")))
                    .action("none").build());
            MenuScaffold.closeButton(inventory, t(player, "menu.close"));
            return;
        }

        // Sort by tier strength (ordinal 0 = strongest)
        List<Map.Entry<String, TierService.Standing>> sorted = new ArrayList<>(standings.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getValue().tier().ordinal()));

        int col = 1;
        int row = 1;
        for (Map.Entry<String, TierService.Standing> entry : sorted) {
            if (col > 7) { col = 1; row++; }
            if (row > 2) break; // max 14 tiers visible

            TierService.Standing s = entry.getValue();
            Material icon = tierIcon(s.tier());
            String tierLabel = s.tier().label();
            String kitName = entry.getKey();

            inventory.setItem(GuiSlots.slot(row, col), ItemBuilder.of(icon)
                    .name(Component.text(tierLabel, tierColor(s.tier()), TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.labelValue(line(player, "gui.kit"), kitName),
                            UiTheme.labelValue("PT", String.valueOf((int) s.pt())),
                            UiTheme.labelValue(line(player, "gui.ranking"),
                                    s.rank() + " / " + s.population()),
                            UiTheme.labelValue(line(player, "gui.percentile"),
                                    String.format("%.1f%%", s.percentile() * 100.0)),
                            UiTheme.blank(),
                            UiTheme.hint(line(player, "tier.hint"))
                    )
                    .action("none").build());
            col++;
        }

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private static Material tierIcon(TierService.Tier tier) {
        return switch (tier) {
            case HT1, LT1 -> Material.DIAMOND;
            case HT2, LT2 -> Material.GOLD_INGOT;
            case HT3, LT3 -> Material.IRON_INGOT;
            case HT4, LT4 -> Material.COPPER_INGOT;
            case HT5, LT5 -> Material.FLINT;
            case UNRANKED -> Material.BARRIER;
        };
    }

    private static NamedTextColor tierColor(TierService.Tier tier) {
        return switch (tier) {
            case HT1, LT1 -> NamedTextColor.GOLD;
            case HT2, LT2 -> NamedTextColor.YELLOW;
            case HT3, LT3 -> NamedTextColor.LIGHT_PURPLE;
            case HT4 -> NamedTextColor.AQUA;
            case LT4 -> NamedTextColor.GREEN;
            case HT5 -> NamedTextColor.GRAY;
            case LT5 -> NamedTextColor.DARK_GRAY;
            case UNRANKED -> NamedTextColor.DARK_GRAY;
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory,
                            int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
        }
    }
}