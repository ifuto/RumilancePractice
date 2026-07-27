package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ArrowEffectGui extends AbstractGui {

    private final ArrowEffectService arrowEffectService;
    private final SettingsService settingsService;

    public ArrowEffectGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            ArrowEffectService arrowEffectService,
            SettingsService settingsService
    ) {
        super(registry, sounds, GuiType.ARROW_EFFECT, 3, true);
        this.arrowEffectService = arrowEffectService;
        this.settingsService = settingsService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Arrow Effects", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        int i = 0;
        for (String id : arrowEffectService.effectIds()) {
            ArrowEffectService.EffectDef def = arrowEffectService.get(id);
            if (def == null || i >= 7) {
                continue;
            }
            ItemStack icon = new ItemStack(Material.ARROW);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(def.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "fx:" + id);
            icon.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(1, 1 + i), icon);
            i++;
        }
        inventory.setItem(GuiSlots.slot(2, 4), GuiDecorator.button(Material.RED_STAINED_GLASS_PANE,
                Component.text("Close", NamedTextColor.RED), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action.startsWith("fx:")) {
            String id = action.substring(3);
            settingsService.update(settingsService.get(player).withArrowEffect(id));
            sounds.play(player, "select");
            player.sendMessage(Component.text("Arrow effect set to " + id, NamedTextColor.GREEN));
            player.closeInventory();
        }
    }
}
