package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.practice.PracticeItems;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.practice.PracticeSession;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Simple ANKER layout picker: Anchor 1st or Glowstone 1st.
 */
public final class PracticeLayoutGui extends AbstractGui {

    private final PracticeService practiceService;

    public PracticeLayoutGui(GuiSessionRegistry registry, SoundService sounds, PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_LAYOUT, 3, true);
        this.practiceService = practiceService;
    }

    public void openFor(Player player, PracticeSession session) {
        GuiSession gui = registry.open(player.getUniqueId(), type(), rows);
        gui.put("practice_id", session.practiceId());
        PracticeGuiOpen.open(this, player, gui);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Practice Layout", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        inventory.setItem(11, ItemBuilder.of(Material.RESPAWN_ANCHOR)
                .name(Component.text("Anchor 1st", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.line("RESPAWN_ANCHOR then GLOWSTONE"),
                        UiTheme.hint("Click to save & use"))
                .action("layout:" + PracticeItems.LAYOUT_ANCHOR_FIRST)
                .build());
        inventory.setItem(15, ItemBuilder.of(Material.GLOWSTONE)
                .name(Component.text("Glowstone 1st", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.line("GLOWSTONE then RESPAWN_ANCHOR"),
                        UiTheme.hint("Click to save & use"))
                .action("layout:" + PracticeItems.LAYOUT_GLOW_FIRST)
                .build());
        MenuScaffold.closeButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || !action.startsWith("layout:")) {
            return;
        }
        String key = action.substring("layout:".length());
        practiceService.session(player.getUniqueId()).ifPresent(prac -> {
            ItemStack[] contents = PracticeItems.defaultLayout(key);
            practiceService.saveLayout(player, prac, key, contents);
            player.closeInventory();
        });
    }
}
