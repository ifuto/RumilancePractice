package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin W/L editor ("WL" = wins & losses): one tile per ranked-kit row of the target
 * player. Left-click asks for an exact "wins losses" pair in chat, right-click adds a win,
 * shift-click adds a loss — the Glicko rating fields stay untouched. The TNT re-runs the
 * /practiceadmin statsreset wipe for this player.
 */
public final class AdminStatsGui extends AbstractGui {

    /** Pending targets handed into {@link #configureSession} (session is fresh there). */
    private final Map<UUID, UUID> pendingTargets = new ConcurrentHashMap<>();
    private final StatsService statsService;
    private AdminPlayerDataGui dataGui;

    public AdminStatsGui(GuiSessionRegistry registry, SoundService sounds,
                         StatsService statsService) {
        super(registry, sounds, GuiType.ADMIN_STATS, 6, false);
        this.statsService = statsService;
    }

    public void setDataGui(AdminPlayerDataGui dataGui) {
        this.dataGui = dataGui;
    }

    /** Opens the W/L editor for {@code target} (offline works). */
    public void openFor(Player admin, UUID target) {
        pendingTargets.put(admin.getUniqueId(), target);
        open(admin);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        UUID target = pendingTargets.remove(player.getUniqueId());
        if (target != null) {
            session.put(AdminPlayerDataGui.KEY_TARGET, target.toString());
        }
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.GOLDEN_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin: W/L editor").color(NamedTextColor.AQUA);
    }

    private UUID targetOf(GuiSession session) {
        String raw = session.get(AdminPlayerDataGui.KEY_TARGET, String.class);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String nameOf(UUID target) {
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            return online.getName();
        }
        return target.toString().substring(0, 8) + "…";
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        UUID target = targetOf(session);
        if (target == null) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No target selected", UiTheme.DANGER))
                            .action("decorate").build());
            backTile(inventory);
            return;
        }

        List<RankedKitStats> rows = List.of();
        try {
            rows = statsService == null ? List.of() : statsService.allKits(target);
        } catch (Exception ignored) {
            // render the empty state below
        }
        long wins = rows.stream().mapToLong(RankedKitStats::wins).sum();
        long losses = rows.stream().mapToLong(RankedKitStats::losses).sum();

        inventory.setItem(GuiSlots.slot(0, 4),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .skullOwner(Bukkit.getOfflinePlayer(target))
                        .name(Component.text(nameOf(target), UiTheme.HEADER)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue("Total W / L", wins + " / " + losses),
                                UiTheme.labelValue("Kits played", String.valueOf(rows.size())),
                                UiTheme.blank(),
                                UiTheme.line("Glicko rating (PT) is left untouched"),
                                UiTheme.line("when you edit W/L."))
                        .action("decorate").build());

        rows.sort((a, b) -> Integer.compare(b.pt(), a.pt()));
        int page = Math.max(0, session.page());
        int pageSize = MenuScaffold.gridPageSize();
        int start = page * pageSize;
        int end = Math.min(rows.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            RankedKitStats row = rows.get(i);
            double games = Math.max(1, row.wins() + row.losses());
            inventory.setItem(GuiSlots.slot(1 + (i - start) / 7, 1 + (i - start) % 7),
                    ItemBuilder.of(row.wins() >= row.losses()
                            ? Material.LIME_WOOL : Material.RED_WOOL)
                            .name(Component.text(row.kit(), UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue("Wins", String.valueOf(row.wins())),
                                    UiTheme.labelValue("Losses", String.valueOf(row.losses())),
                                    UiTheme.labelValue("Win rate",
                                            Math.round(100.0 * row.wins() / games) + "%"),
                                    UiTheme.labelValue("PT", row.pt() + " (best " + row.bestPt() + ")"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: type exact W/L (e.g. '12 8')"),
                                    UiTheme.hint("Right-click: +1 win"),
                                    UiTheme.hint("Shift-click: +1 loss"))
                            .action("kit:" + row.kit())
                            .build());
        }

        if (rows.isEmpty()) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                            .name(Component.text("No ranked stats yet", UiTheme.SUCCESS))
                            .lore(UiTheme.line("Rows appear after the player's first"),
                                    UiTheme.line("ranked match of a kit."))
                            .action("decorate").build());
        }

        MenuScaffold.pagingButtons(inventory, page, rows.size(),
                Component.text("◀ Previous", UiTheme.PRIMARY),
                Component.text("Next ▶", UiTheme.PRIMARY),
                Component.text((page + 1) + " / "
                        + Math.max(1, (rows.size() + pageSize - 1) / pageSize), UiTheme.MUTED),
                Component.text("Go to page " + page, UiTheme.MUTED),
                Component.text("Go to page " + (page + 2), UiTheme.MUTED));

        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(Material.TNT)
                        .name(Component.text("Reset this player's stats & rating", UiTheme.DANGER))
                        .lore(UiTheme.divider(),
                                UiTheme.line("Same as /practiceadmin statsreset <player>:"),
                                UiTheme.line("wipes every kit row incl. PT."),
                                UiTheme.blank(),
                                UiTheme.hint("Shift-click: execute"))
                        .action("act:reset_player").build());

        backTile(inventory);
    }

    private void backTile(Inventory inventory) {
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(UiTheme.BACK)
                        .name(Component.text("Back", UiTheme.WARNING))
                        .action("back_data").build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        UUID target = targetOf(session);
        if ("back_data".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if (target != null && dataGui != null) {
                dataGui.openFor(player, target);
            }
            return;
        }
        if (target == null || action == null) {
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
        if ("act:reset_player".equals(action)) {
            if (clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                    || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                if (dataGui != null) {
                    dataGui.adminResetStats(player, target);
                }
            } else {
                sounds.play(player, "error");
                player.sendMessage(Component.text(
                        "Shift-click the TNT to reset this player's stats & rating.",
                        NamedTextColor.YELLOW));
            }
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("kit:")) {
            String kit = action.substring(4);
            RankedKitStats row = null;
            try {
                row = statsService == null ? null : statsService.kitStats(target, kit).orElse(null);
            } catch (Exception ignored) {
                // treated as missing row
            }
            int wins = row == null ? 0 : row.wins();
            int losses = row == null ? 0 : row.losses();
            boolean shift = clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                    || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
            try {
                if (shift) {
                    statsService.setWinsLosses(target, kit, wins, losses + 1);
                    sounds.play(player, "gui-click");
                } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                    statsService.setWinsLosses(target, kit, wins + 1, losses);
                    sounds.play(player, "gui-click");
                } else {
                    session.put(com.rumilance.practice.admin.AdminPlayerLookupListener.AWAIT_STATS_TARGET,
                            target + "|" + kit);
                    sounds.play(player, "gui-click");
                    player.closeInventory();
                    player.sendMessage(Component.text(
                            "Type exact W/L for '" + kit + "' as two numbers (e.g. '12 8').",
                            NamedTextColor.AQUA));
                    return;
                }
            } catch (Exception e) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("W/L update failed.", NamedTextColor.RED));
            }
            refresh(player, session, inventory);
        }
    }
}
