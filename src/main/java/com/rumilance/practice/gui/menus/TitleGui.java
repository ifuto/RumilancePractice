package com.rumilance.practice.gui.menus;

import com.rumilance.practice.cosmetic.KillTitle;
import com.rumilance.practice.cosmetic.TitleService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Cosmetic title picker. Lists the entire title ladder, showing which titles the player has
 * unlocked (glinting, coloured) and which are still locked (greyed out with the requirement
 * shown). Clicking an unlocked title selects it; the active title is marked with an "ACTIVE"
 * badge.
 */
public final class TitleGui extends AbstractGui {

    private final TitleService titleService;

    public TitleGui(GuiSessionRegistry registry, SoundService sounds, TitleService titleService) {
        super(registry, sounds, GuiType.TITLE_SELECT, 6, true);
        this.titleService = titleService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("✦ Kill Titles", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        KillTitle selected = titleService.selected(player);
        List<KillTitle> titles = titleService.all();
        int index = 0;
        for (KillTitle t : titles) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++), icon(player, t, selected));
        }

        // None option at the bottom-left, close at the centre.
        inventory.setItem(com.rumilance.practice.util.GuiSlots.slot(5, 1),
                ItemBuilder.of(org.bukkit.Material.BARRIER)
                        .name(Component.text("No Title", UiTheme.MUTED))
                        .lore(UiTheme.hint("Click to remove your title"))
                        .action("title:none")
                        .glint(selected == KillTitle.NONE)
                        .build());
        MenuScaffold.closeButton(inventory);
    }

    private ItemStack icon(Player player, KillTitle title, KillTitle selected) {
        boolean unlocked = titleService.isUnlocked(player.getUniqueId(), title);
        boolean active = title.id().equals(selected.id());
        ItemBuilder builder = ItemBuilder.of(title.icon())
                .name(Component.text(title.displayName(), unlocked ? title.color() : UiTheme.MUTED))
                .lore(UiTheme.divider());
        if (active) {
            builder.lore(UiTheme.status("ACTIVE", UiTheme.SUCCESS));
        } else if (unlocked) {
            builder.lore(UiTheme.hint("Click to select"));
        } else {
            builder.lore(UiTheme.status("LOCKED", UiTheme.DANGER));
            if (title.requiredWins() > 0) {
                builder.lore(UiTheme.labelValue("Wins needed", String.valueOf(title.requiredWins())));
            }
            if (title.requiredElo() > 0) {
                builder.lore(UiTheme.labelValue("Best Elo needed", String.valueOf(title.requiredElo())));
            }
        }
        return builder
                .glint(active)
                .action("title:" + title.id())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action.startsWith("title:")) {
            String id = action.substring("title:".length());
            KillTitle title = titleService.byId(id);
            if ("none".equals(id)) {
                titleService.select(player, KillTitle.NONE);
                sounds.play(player, "select");
                refresh(player, session, inventory);
                return;
            }
            if (!titleService.select(player, title)) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("That title is not unlocked yet.", UiTheme.DANGER));
                return;
            }
            sounds.play(player, "select");
            player.sendMessage(Component.text("Title set to " + title.displayName() + ".", title.color()));
            refresh(player, session, inventory);
        }
    }
}
