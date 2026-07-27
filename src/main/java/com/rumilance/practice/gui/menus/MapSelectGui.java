package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class MapSelectGui extends AbstractGui {

    private DuelRequestGui duelRequestGui;

    public MapSelectGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.MAP_SELECT, 3, true);
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
        return Component.text("Select Map", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        inventory.setItem(GuiSlots.slot(1, 2), GuiDecorator.button(Material.END_CRYSTAL,
                Component.text("クリスタルマップ", NamedTextColor.LIGHT_PURPLE), "map:CRYSTAL"));
        inventory.setItem(GuiSlots.slot(1, 3), GuiDecorator.button(Material.GRASS_BLOCK,
                Component.text("平地マップ", NamedTextColor.GREEN), "map:FLAT"));
        inventory.setItem(GuiSlots.slot(1, 4), GuiDecorator.button(Material.ENDER_EYE,
                Component.text("ランダム", NamedTextColor.AQUA), "map:ANY"));
        inventory.setItem(GuiSlots.slot(1, 5), GuiDecorator.button(Material.MOSS_BLOCK,
                Component.text("地形マップ", NamedTextColor.DARK_GREEN), "map:BUMPY"));
        inventory.setItem(GuiSlots.slot(1, 6), GuiDecorator.button(Material.NETHERITE_BLOCK,
                Component.text("ネザライトマップ", NamedTextColor.DARK_GRAY), "map:NETHERITE"));
        inventory.setItem(GuiSlots.slot(2, 1), GuiDecorator.button(Material.RED_STAINED_GLASS_PANE,
                Component.text("戻る", NamedTextColor.RED), "back"));
        inventory.setItem(GuiSlots.slot(2, 7), GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text("選択", NamedTextColor.GREEN), "confirm"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action.startsWith("map:")) {
            session.put("temp_map", action.substring(4));
            sounds.play(player, "gui-click");
            return;
        }
        if ("back".equals(action) || "confirm".equals(action)) {
            if ("confirm".equals(action)) {
                String temp = session.get("temp_map", String.class);
                if (temp != null) {
                    session.setSelectedMap(temp);
                }
                sounds.play(player, "select");
            } else {
                sounds.play(player, "gui-back");
            }
            returnToDuel(player, session);
        }
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        String map = session.selectedMap() == null ? ArenaTerrain.ANY.name() : session.selectedMap();
        String kit = session.selectedKit();
        int bestOf = session.bestOf();
        boolean ranked = session.ranked();
        player.closeInventory();
        duelRequestGui.openFor(player, target, ranked);
        registry.get(player.getUniqueId()).ifPresent(s -> {
            s.setSelectedMap(map);
            s.setSelectedKit(kit);
            s.setBestOf(bestOf);
        });
    }
}
