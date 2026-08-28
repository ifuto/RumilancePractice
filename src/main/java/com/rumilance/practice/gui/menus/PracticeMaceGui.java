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
 * Mace enchant settings: Density / Breach / Wind Burst. Mace is forced unbreakable on apply.
 */
public final class PracticeMaceGui extends AbstractGui {

    private final PracticeService practiceService;

    public PracticeMaceGui(GuiSessionRegistry registry, SoundService sounds, PracticeService practiceService) {
        super(registry, sounds, GuiType.PRACTICE_MACE, 3, true);
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
        return Component.text("Mace Settings", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        PracticeSession prac = practiceService.session(player.getUniqueId()).orElse(null);
        int density = prac == null ? 0 : prac.maceDensity();
        int breach = prac == null ? 0 : prac.maceBreach();
        int wind = prac == null ? 0 : prac.maceWindBurst();
        inventory.setItem(11, ItemBuilder.of(Material.ANVIL)
                .name(Component.text("Density: " + density, NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.hint("Click to cycle 0-5"))
                .action("density")
                .build());
        inventory.setItem(13, ItemBuilder.of(Material.IRON_AXE)
                .name(Component.text("Breach: " + breach, NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.hint("Click to cycle 0-4"))
                .action("breach")
                .build());
        inventory.setItem(15, ItemBuilder.of(Material.WIND_CHARGE)
                .name(Component.text("Wind Burst: " + wind, NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.hint("Click to cycle 0-3"))
                .action("wind")
                .build());
        MenuScaffold.closeButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        practiceService.session(player.getUniqueId()).ifPresent(prac -> {
            switch (action == null ? "" : action) {
                case "density" -> prac.setMaceDensity((prac.maceDensity() + 1) % 6);
                case "breach" -> prac.setMaceBreach((prac.maceBreach() + 1) % 5);
                case "wind" -> prac.setMaceWindBurst((prac.maceWindBurst() + 1) % 4);
                default -> {
                    return;
                }
            }
            practiceService.refreshMaceItem(player, prac);
            refresh(player, session, inventory);
        });
    }
}
