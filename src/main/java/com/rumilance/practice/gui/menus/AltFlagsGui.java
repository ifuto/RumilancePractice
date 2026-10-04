package com.rumilance.practice.gui.menus;

import com.rumilance.practice.alt.AltDetectionService;
import com.rumilance.practice.database.repository.AltRepository;
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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin browser for open alt-detection flags ({@code /practiceadmin altflags}): one tile per
 * flagged account pair, click to dismiss (which also lifts the pair's ranked-play restriction).
 * Detection is private; this screen is admin-only like the command.
 */
public final class AltFlagsGui extends AbstractGui {

    private final AltDetectionService altDetectionService;
    /** Per-admin snapshot of the listed rows so click indexes stay stable across refreshes. */
    private final ConcurrentHashMap<UUID, List<AltRepository.FlagRow>> lastRows =
            new ConcurrentHashMap<>();

    private java.util.function.Consumer<Player> backToAdminMenu = p -> { };

    /** Reopens the admin menu on Back (same flow as the data screen). */
    public void setBackToAdminMenu(java.util.function.Consumer<Player> back) {
        this.backToAdminMenu = back == null ? p -> { } : back;
    }

    public AltFlagsGui(GuiSessionRegistry registry, SoundService sounds,
                       AltDetectionService altDetectionService) {
        super(registry, sounds, GuiType.ADMIN_ALT_FLAGS, 6, false);
        this.altDetectionService = altDetectionService;
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        lastRows.remove(player.getUniqueId());
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.REDSTONE_TORCH;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin: Alt flags").color(NamedTextColor.AQUA);
    }

    private List<AltRepository.FlagRow> rows(UUID admin) {
        if (altDetectionService == null) {
            return List.of();
        }
        List<AltRepository.FlagRow> cached = lastRows.get(admin);
        if (cached != null) {
            return cached;
        }
        try {
            List<AltRepository.FlagRow> loaded = altDetectionService.activeFlags();
            lastRows.put(admin, loaded);
            return loaded;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        try {
            String cached = Bukkit.getOfflinePlayer(uuid).getName();
            if (cached != null && !cached.isBlank()) {
                return cached;
            }
        } catch (Exception ignored) {
            // fall through to the short UUID
        }
        return uuid.toString().substring(0, 8) + "…";
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        UUID admin = player.getUniqueId();
        List<AltRepository.FlagRow> rows = rows(admin);
        int page = Math.max(0, session.page());
        int pageSize = MenuScaffold.gridPageSize();
        int start = page * pageSize;
        int end = Math.min(rows.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            AltRepository.FlagRow row = rows.get(i);
            inventory.setItem(GuiSlots.slot(1 + (i - start) / 7, 1 + (i - start) % 7),
                    ItemBuilder.of(Material.REDSTONE_TORCH)
                            .name(Component.text("Flag #" + (i + 1)
                                            + "  (" + row.level() + ")",
                                    UiTheme.DANGER))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue("Pair", nameOf(row.a())
                                            + " ↔ " + nameOf(row.b())),
                                    UiTheme.labelValue("Score", String.format("%.2f", row.score())),
                                    UiTheme.line(row.evidence() == null || row.evidence().isBlank()
                                            ? "no evidence text" : row.evidence()),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: dismiss + lift pair restriction"))
                            .action("dismiss:" + i)
                            .build());
        }

        if (rows.isEmpty()) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                            .name(Component.text("No active alt flags", UiTheme.SUCCESS))
                            .action("decorate")
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, rows.size(),
                Component.text("◀ Previous", UiTheme.PRIMARY),
                Component.text("Next ▶", UiTheme.PRIMARY),
                Component.text((page + 1) + " / "
                        + Math.max(1, (rows.size() + pageSize - 1) / pageSize), UiTheme.MUTED),
                Component.text("Go to page " + page, UiTheme.MUTED),
                Component.text("Go to page " + (page + 2), UiTheme.MUTED));

        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(UiTheme.BACK)
                        .name(Component.text("Back", UiTheme.WARNING))
                        .action("back_admin")
                        .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("back_admin".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if ("back_admin".equals(action)) {
                backToAdminMenu.accept(player);
            }
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(Math.max(0, session.page() - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("dismiss:")) {
            List<AltRepository.FlagRow> rows = rows(player.getUniqueId());
            int index;
            try {
                index = Integer.parseInt(action.substring(8));
            } catch (NumberFormatException e) {
                return;
            }
            if (index < 0 || index >= rows.size()) {
                sounds.play(player, "error");
                refresh(player, session, inventory);
                return;
            }
            AltRepository.FlagRow row = rows.get(index);
            try {
                altDetectionService.dismiss(row.a(), row.b());
                player.sendMessage(Component.text(
                        "Dismissed flag: " + nameOf(row.a()) + " ↔ " + nameOf(row.b())
                                + " (pair restriction lifted).", NamedTextColor.GREEN));
                sounds.play(player, "select");
            } catch (Exception e) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("Dismiss failed.", NamedTextColor.RED));
            }
            lastRows.remove(player.getUniqueId());
            refresh(player, session, inventory);
        }
    }
}
