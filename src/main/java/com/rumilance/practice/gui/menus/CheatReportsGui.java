package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiFrame;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * The "Cheat / Alt / Reports" section of {@code /admin}.
 *
 * <p>Three review queues side by side, a column apart: chat reports filed by players through the
 * click-to-report hover, the alt-detection flag list, and the match reports that carry a replay.
 * Destinations are injected, so a queue that is not wired yet simply does nothing rather than
 * throwing.</p>
 */
public final class CheatReportsGui extends AbstractGui {

    private Consumer<Player> openChatReports = NOOP;
    private Consumer<Player> openAltFlags = NOOP;
    private Consumer<Player> openMatchReports = NOOP;
    private Consumer<Player> onBack = NOOP;

    private static final Consumer<Player> NOOP = player -> { };

    public CheatReportsGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.CHEAT_REPORTS, 6, false);
    }

    public void setOpenChatReports(Consumer<Player> opener) {
        this.openChatReports = orNoop(opener);
    }

    public void setOpenAltFlags(Consumer<Player> opener) {
        this.openAltFlags = orNoop(opener);
    }

    public void setOpenMatchReports(Consumer<Player> opener) {
        this.openMatchReports = orNoop(opener);
    }

    public void setOnBack(Consumer<Player> onBack) {
        this.onBack = orNoop(onBack);
    }

    @Override
    protected GuiFrame.Theme theme() {
        return GuiFrame.Theme.RED;
    }

    @Override
    protected Material titleIcon() {
        return Material.SPYGLASS;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Cheat / Alt / Reports", UiTheme.HEADER);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        inventory.setItem(GuiSlots.slot(2, 1), tile(Material.WRITTEN_BOOK, "Chat Reports",
                "Messages players flagged with click-to-report.", "chat_reports"));
        inventory.setItem(GuiSlots.slot(2, 4), tile(Material.REDSTONE_TORCH, "Alt Flags",
                "Suspected alt pairs and their evidence score.", "alt_flags"));
        inventory.setItem(GuiSlots.slot(2, 7), tile(Material.WRITABLE_BOOK, "Match Reports",
                "Cheat reports with a replay attached.", "match_reports"));
        inventory.setItem(GuiSlots.slot(5, 4), ItemBuilder.of(UiTheme.BACK)
                .name(Component.text("Back", UiTheme.WARNING))
                .action("back")
                .build());
    }

    private org.bukkit.inventory.ItemStack tile(Material material, String name, String lore,
                                                String action) {
        return ItemBuilder.of(material)
                .name(Component.text(name, UiTheme.HEADER))
                .lore(UiTheme.divider(),
                        UiTheme.line(lore),
                        UiTheme.blank(),
                        UiTheme.hint("Click to open"))
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("back".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if ("back".equals(action)) {
                onBack.accept(player);
            }
            return;
        }
        Consumer<Player> target = switch (action == null ? "" : action) {
            case "chat_reports" -> openChatReports;
            case "alt_flags" -> openAltFlags;
            case "match_reports" -> openMatchReports;
            default -> null;
        };
        if (target == null) {
            return;
        }
        sounds.play(player, "gui-click");
        player.closeInventory();
        target.accept(player);
    }

    private static Consumer<Player> orNoop(Consumer<Player> opener) {
        return opener == null ? NOOP : opener;
    }
}
