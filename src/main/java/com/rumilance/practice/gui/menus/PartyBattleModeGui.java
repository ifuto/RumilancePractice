package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The mockup's "Battle Mode GUI" (2026-10): light-blue ring with the two party battle modes
 * centred — <b>Party Fight</b> (diamond sword, the classic red-vs-blue party battle) and
 * <b>Party FFA</b> (end crystal, the Private FFA: the whole party fights free-for-all in its
 * own {@code party-ffa} zone). Picking a mode returns to the Start Battle screen with the
 * choice applied; the black bottom row marks the cells this screen does not use.
 */
public final class PartyBattleModeGui extends AbstractGui {

    /** Session attribute carrying the picked mode ("fight" / "ffa"). */
    public static final String MODE_KEY = "party-battle-mode";

    private final PartyStartBattleGui startGui;
    private final MessageService messageService;

    public PartyBattleModeGui(GuiSessionRegistry registry, SoundService sounds,
                              PartyStartBattleGui startGui, MessageService messageService) {
        super(registry, sounds, GuiType.PARTY_BATTLE_MODE, 6, true);
        this.startGui = startGui;
        this.messageService = messageService;
    }

    /** Opens the picker showing {@code current} ("fight"/"ffa") as the selected mode. */
    public void openFor(Player player, String current) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put(MODE_KEY, PartyStartBattleGui.normalizeMode(current));
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.IRON_AXE;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.battle-mode-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        String mode = PartyStartBattleGui.normalizeMode(
                session.get(MODE_KEY, String.class));
        boolean ffa = PartyStartBattleGui.MODE_FFA.equals(mode);
        // --- docs/design/gui.json「Battle Mode GUI」1:1 ---
        //   r0 / r4 : light-blue ×9            (上下の帯)
        //   r5      : black ×9 = 存在しないマス
        //   r1-r3   : light-blue は両端 col0/col8 のみ。内側 col1-7 は空気。
        //             (以前は内側まで水色で埋めていた = gui.json と不一致)
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(0, col), pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(4, col), pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(5, col),
                    com.rumilance.practice.gui.GuiMockups.noCell(player, messageService));
        }
        for (int row = 1; row <= 3; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(row, 8), pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            for (int col = 1; col <= 7; col++) {
                inventory.setItem(GuiSlots.slot(row, col), null);
            }
        }

        // --- modes: Party Fight (2,3) and Party FFA (2,5) ---
        inventory.setItem(GuiSlots.slot(2, 3),
                ItemBuilder.of(Material.DIAMOND_SWORD)
                        .name(t(player, "gui.battle-mode-fight")
                                .color(ffa ? UiTheme.VALUE : UiTheme.SUCCESS))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.battle-mode-fight-lore")),
                                UiTheme.blank(),
                                ffa ? UiTheme.hint(line(player, "menu.click"))
                                        : UiTheme.status(line(player, "gui.selected"), UiTheme.SUCCESS))
                        .glint(!ffa)
                        .action("mode:fight").build());
        inventory.setItem(GuiSlots.slot(2, 5),
                ItemBuilder.of(Material.END_CRYSTAL)
                        .name(t(player, "gui.battle-mode-ffa")
                                .color(ffa ? UiTheme.SUCCESS : UiTheme.VALUE))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.battle-mode-ffa-lore")),
                                UiTheme.blank(),
                                ffa ? UiTheme.status(line(player, "gui.selected"), UiTheme.SUCCESS)
                                        : UiTheme.hint(line(player, "menu.click")))
                        .glint(ffa)
                        .action("mode:ffa").build());
    }

    private ItemStack pane(Player player, Material material) {
        return com.rumilance.practice.gui.GuiMockups.deco(player, material, messageService);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            startGui.openFor(player,
                    PartyStartBattleGui.normalizeMode(session.get(MODE_KEY, String.class)));
            return;
        }
        if ("mode:fight".equals(action) || "mode:ffa".equals(action)) {
            String mode = action.substring("mode:".length());
            session.put(MODE_KEY, mode);
            sounds.play(player, "select");
            startGui.openFor(player, mode);
        }
    }
}
