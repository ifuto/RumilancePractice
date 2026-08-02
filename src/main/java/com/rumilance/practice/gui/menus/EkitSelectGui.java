package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * /ekit entry screen: pick a kit (or the red "オリジナルキット" paper at the end),
 * which is shown in the center glass pane, then press the lime "選択" glass.
 */
public final class EkitSelectGui extends AbstractGui {

    private final KitService kitService;
    private EditKitGui editKitGui;
    private OriginalKitGui originalKitGui;

    public EkitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.EKIT_SELECT, 6, false);
        this.kitService = kitService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public void setOriginalKitGui(OriginalKitGui originalKitGui) {
        this.originalKitGui = originalKitGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Kit選択", NamedTextColor.WHITE);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        List<KitDefinition> kits = kitService.enabled();
        int shown = Math.min(kits.size(), 35);
        for (int i = 0; i < shown; i++) {
            inventory.setItem(GuiSlots.slot(1 + i / 9, i % 9), kitIcon(kits.get(i)));
        }
        int originalSlot = Math.min(9 + kits.size(), 44);
        inventory.setItem(originalSlot, originalPaper());

        String selected = session.get("selected", String.class);
        ItemStack center;
        if (selected == null) {
            center = GuiDecorator.decorative(Material.LIME_STAINED_GLASS_PANE, "キットをクリックして選択");
        } else if ("original".equals(selected)) {
            center = originalPaper();
        } else {
            KitDefinition kit = kitService.get(selected).orElse(null);
            center = kit == null ? GuiDecorator.decorative(Material.LIME_STAINED_GLASS_PANE, "キットをクリックして選択") : kitIcon(kit);
        }
        inventory.setItem(GuiSlots.slot(0, 4), center);
        inventory.setItem(GuiSlots.slot(5, 0), GuiDecorator.button(Material.RED_DYE,
                Component.text("戻る", NamedTextColor.RED), "back"));
        inventory.setItem(GuiSlots.slot(5, 4), GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text("選択", NamedTextColor.GREEN), "select"));
        inventory.setItem(GuiSlots.slot(5, 8), GuiDecorator.button(Material.BARRIER,
                Component.text("閉じる", NamedTextColor.GRAY), "close"));
    }

    private ItemStack kitIcon(KitDefinition kit) {
        Material material = Material.matchMaterial(kit.icon());
        ItemStack stack = new ItemStack(material == null ? Material.DIAMOND_SWORD : material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(kit.displayName())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("クリックして選択", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(com.rumilance.practice.util.ItemKeys.guiAction(),
                org.bukkit.persistence.PersistentDataType.STRING, "kit:" + kit.name());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack originalPaper() {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("オリジナルキット", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("クリックして選択", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(com.rumilance.practice.util.ItemKeys.guiAction(),
                org.bukkit.persistence.PersistentDataType.STRING, "original");
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "back", "close" -> player.closeInventory();
            case "select" -> {
                String selected = session.get("selected", String.class);
                if (selected == null) {
                    sounds.play(player, "error");
                    return;
                }
                sounds.play(player, "select");
                player.closeInventory();
                if ("original".equals(selected)) {
                    if (originalKitGui != null) {
                        originalKitGui.open(player);
                    }
                } else if (editKitGui != null) {
                    editKitGui.openKitEditor(player, selected);
                }
            }
            default -> {
                if (action != null && action.startsWith("kit:")) {
                    session.setSelectedKit(action.substring(4));
                    session.put("selected", action.substring(4));
                    render(player, session, inventory);
                } else if ("original".equals(action)) {
                    session.put("selected", "original");
                    render(player, session, inventory);
                }
            }
        }
    }
}
