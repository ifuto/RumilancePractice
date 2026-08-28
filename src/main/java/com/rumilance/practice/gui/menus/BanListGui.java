package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ban.BanDuration;
import com.rumilance.practice.ban.BanRecord;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Active bans, newest at the top-left of the content grid.
 */
public final class BanListGui extends AbstractGui {

    private static final int PAGE_SIZE = 28;

    private final BanService banService;

    public BanListGui(GuiSessionRegistry registry, SoundService sounds, BanService banService) {
        super(registry, sounds, GuiType.BAN_LIST, 6, true);
        this.banService = banService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Ban List", UiTheme.HEADER).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        List<BanRecord> bans = banService.activeNewestFirst();
        int pages = Math.max(1, (bans.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (session.page() >= pages) {
            session.setPage(pages - 1);
        }
        int page = session.page();
        int offset = page * PAGE_SIZE;
        long now = System.currentTimeMillis();
        int placed = 0;
        for (int i = offset; i < bans.size() && placed < PAGE_SIZE; i++, placed++) {
            BanRecord ban = bans.get(i);
            inventory.setItem(GuiSlots.slot(1 + placed / 7, 1 + placed % 7), head(ban, now));
        }
        if (page > 0) {
            inventory.setItem(GuiSlots.slot(5, 2),
                    ItemBuilder.of(UiTheme.PREV_PAGE)
                            .name(Component.text("◀ Previous", UiTheme.PRIMARY))
                            .action("page:prev")
                            .build());
        }
        if (offset + PAGE_SIZE < bans.size()) {
            inventory.setItem(GuiSlots.slot(5, 6),
                    ItemBuilder.of(UiTheme.NEXT_PAGE)
                            .name(Component.text("Next ▶", UiTheme.PRIMARY))
                            .action("page:next")
                            .build());
        }
        MenuScaffold.closeButton(inventory, Component.text("Close", UiTheme.MUTED));
    }

    private org.bukkit.inventory.ItemStack head(BanRecord ban, long now) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(ban.playerName(), NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false))
                .skullOwner(Bukkit.getOfflinePlayer(ban.playerId()))
                .lore(
                        Component.text("Reason: " + ban.reason(), NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Until: " + BanDuration.remaining(ban.expiresAtEpochMilli(), now),
                                        NamedTextColor.WHITE)
                                .decoration(TextDecoration.ITALIC, false)
                )
                .action("decorate")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(Math.max(0, session.page() - 1));
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            refresh(player, session, inventory);
        }
    }
}
