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

public final class KitSelectGui extends AbstractGui {

    private final KitService kitService;
    private DuelRequestGui duelRequestGui;

    public KitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_SELECT, 6, true);
        this.kitService = kitService;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void openFor(Player player, GuiSession parent) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Select Kit", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        int index = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (index >= 28) {
                break;
            }
            Material mat = Material.matchMaterial(kit.icon());
            ItemStack icon = new ItemStack(mat == null ? Material.DIAMOND_SWORD : mat);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(kit.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "pick:" + kit.name());
            icon.setItemMeta(meta);
            inventory.setItem(GuiSlots.slot(1 + index / 7, 1 + index % 7), icon);
            index++;
        }
        String selectedName = session.selectedKit() == null ? "まだ選択されていません" : session.selectedKit();
        inventory.setItem(GuiSlots.slot(2, 4), GuiDecorator.button(Material.BOOK,
                Component.text(selectedName, NamedTextColor.AQUA), "selected"));
        inventory.setItem(GuiSlots.slot(5, 1), GuiDecorator.button(Material.BARRIER,
                Component.text("戻る", NamedTextColor.RED), "back"));
        inventory.setItem(GuiSlots.slot(5, 7), GuiDecorator.button(Material.EMERALD,
                Component.text("選択", NamedTextColor.GREEN), "confirm"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action.startsWith("pick:")) {
            session.put("temp_kit", action.substring(5));
            sounds.play(player, "kit-select");
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if ("confirm".equals(action)) {
            String temp = session.get("temp_kit", String.class);
            if (temp != null) {
                session.setSelectedKit(temp);
            }
            sounds.play(player, "select");
            returnToDuel(player, session);
        }
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        String kit = session.selectedKit();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean ranked = session.ranked();
        player.closeInventory();
        duelRequestGui.openFor(player, target, ranked);
        registry.get(player.getUniqueId()).ifPresent(s -> {
            s.setSelectedKit(kit);
            s.setSelectedMap(map);
            s.setBestOf(bestOf);
        });
    }
}
