package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.practice.PracticeSession;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Mace bot settings — toggle shield raised (1s stun window after hit when up).
 */
public final class PracticeBotGui extends AbstractGui {

    private final PracticeService practiceService;

    public PracticeBotGui(GuiSessionRegistry registry, SoundService sounds, PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_BOT, 3, true);
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
        return Component.text("Bot Settings", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        PracticeSession prac = practiceService.session(player.getUniqueId()).orElse(null);
        boolean up = prac != null && prac.botShieldRaised();
        inventory.setItem(13, ItemBuilder.of(Material.SHIELD)
                .name(Component.text("Shield: " + (up ? "UP" : "DOWN"),
                                up ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.line("When UP: 1s stun after mace hit"),
                        UiTheme.hint("Click to toggle"))
                .action("toggle_shield")
                .build());
        MenuScaffold.closeButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (!"toggle_shield".equals(action)) {
            return;
        }
        practiceService.session(player.getUniqueId()).ifPresent(prac -> {
            prac.setBotShieldRaised(!prac.botShieldRaised());
            practiceService.applyBotShield(prac);
            practiceService.refreshMaceItem(player, prac);
            refresh(player, session, inventory);
        });
    }
}
