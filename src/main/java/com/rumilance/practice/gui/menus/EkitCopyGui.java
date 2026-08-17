package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.originalkit.OriginalKitService;
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

/**
 * Official kit list to copy into a new original kit slot.
 */
public final class EkitCopyGui extends AbstractGui {

    private final KitService kitService;
    private final OriginalKitEditGui editGui;
    private final OriginalKitService service;
    private EkitChoiceGui choiceGui;

    public EkitCopyGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService,
                       OriginalKitEditGui editGui, OriginalKitService service) {
        super(registry, sounds, GuiType.EKIT_COPY, 6, false);
        this.kitService = kitService;
        this.editGui = editGui;
        this.service = service;
    }

    public void setChoiceGui(EkitChoiceGui choiceGui) {
        this.choiceGui = choiceGui;
    }

    public void open(Player player, int kitSlot) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("slot", kitSlot);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("公式キットをコピー", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        int i = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (i >= 35) {
                break;
            }
            Material material = Material.matchMaterial(kit.icon());
            ItemStack icon = new ItemStack(material == null ? Material.DIAMOND_SWORD : material);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(kit.prettyDisplayName())
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(Component.text("クリックでコピー", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "kit:" + kit.name());
            icon.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(1 + i / 9, i % 9), icon);
            i++;
        }
        inventory.setItem(GuiSlots.slot(5, 0), GuiDecorator.button(Material.RED_DYE,
                Component.text("戻る", NamedTextColor.RED), "back"));
        inventory.setItem(GuiSlots.slot(5, 8), GuiDecorator.button(Material.BARRIER,
                Component.text("閉じる", NamedTextColor.GRAY), "close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        Integer kitSlot = session.get("slot", Integer.class);
        if (kitSlot == null) {
            return;
        }
        switch (action) {
            case "back" -> {
                service.markNavigating(player.getUniqueId());
                player.closeInventory();
                if (choiceGui != null) {
                    choiceGui.open(player, kitSlot);
                }
            }
            case "close" -> {
                player.closeInventory();
            }
            default -> {
                if (action != null && action.startsWith("kit:")) {
                    String name = action.substring(4);
                    kitService.get(name).ifPresent(kit -> {
                        sounds.play(player, "select");
                        service.markNavigating(player.getUniqueId());
                        player.closeInventory();
                        editGui.open(player, kitSlot, OriginalKitService.layoutFromOfficial(kit));
                    });
                }
            }
        }
    }
}
