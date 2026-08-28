package com.rumilance.practice.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Keeps the custom name/lore box and hides the extra vanilla boxes (attributes, weapon
 * stats) that otherwise draw beside the GUI frame.
 */
public final class ItemTooltips {

    private ItemTooltips() {
    }

    public static void hideExtras(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        try {
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                    .hideTooltip(false)
                    .addHiddenComponents(
                            DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            DataComponentTypes.ENCHANTMENTS,
                            DataComponentTypes.STORED_ENCHANTMENTS,
                            DataComponentTypes.UNBREAKABLE,
                            DataComponentTypes.CAN_BREAK,
                            DataComponentTypes.CAN_PLACE_ON,
                            DataComponentTypes.DYED_COLOR,
                            DataComponentTypes.TRIM,
                            DataComponentTypes.DAMAGE,
                            DataComponentTypes.MAX_DAMAGE,
                            DataComponentTypes.WEAPON,
                            DataComponentTypes.TOOL,
                            DataComponentTypes.EQUIPPABLE,
                            DataComponentTypes.POTION_CONTENTS,
                            DataComponentTypes.CHARGED_PROJECTILES
                    )
                    .build());
        } catch (Throwable ignored) {
            // Older Paper: ItemFlags above still hide most extras.
        }
    }
}
