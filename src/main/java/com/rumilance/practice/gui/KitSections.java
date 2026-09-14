package com.rumilance.practice.gui;

import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitCategory;
import com.rumilance.practice.model.KitDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Main Kits / Sub Kits split shared by every kit picker. Row 1 lists Main Kits under
 * the Wild armor trim smithing template (the nature one), row 2 lists Sub Kits under the
 * Bolt armor trim smithing template (the bolted one), and later grid rows continue the
 * Main line-up. {@link #ordered} is the flat main-then-sub source for paginated or
 * cycling pickers.
 */
public final class KitSections {

    /** The 7-column content grid holds the header column plus 6 kits per row. */
    public static final int PER_ROW = 6;
    /** Grid cell right after row 1 (header + 6 main kits). */
    public static final int ROW1_END = PER_ROW + 1;
    /** Grid cell right after row 2 (sub header + 6 sub kits). */
    public static final int ROW2_END = ROW1_END + PER_ROW + 1;

    private KitSections() {
    }

    /** Main kits first, then sub kits — the display order for flat/cycling pickers. */
    public static List<KitDefinition> ordered(KitService kitService) {
        List<KitDefinition> out = new ArrayList<>(kitService.enabled(KitCategory.MAIN));
        out.addAll(kitService.enabled(KitCategory.SUB));
        return out;
    }

    /**
     * Header tile for a section row. The icons are the vanilla smithing templates
     * themselves: the Wild armor trim template (nature) for Main, the Bolt armor trim
     * template (bolted) for Sub.
     */
    public static ItemStack header(KitCategory category, int count, String hint) {
        boolean main = category == KitCategory.MAIN;
        return ItemBuilder.of(main ? Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE
                                   : Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE)
                .name(Component.text(main ? "Main Kits" : "Sub Kits",
                                main ? UiTheme.SUCCESS : UiTheme.SECONDARY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(count + " kits"),
                        hint == null || hint.isBlank() ? UiTheme.blank() : UiTheme.hint(hint)
                )
                .action("decorate")
                .build();
    }
}
