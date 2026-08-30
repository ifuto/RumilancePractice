package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.PlayerReport;
import com.rumilance.practice.replay.ReplayService;
import com.rumilance.practice.report.ReportService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Operator report review list. Left-click a report to replay the recorded match; right-click to
 * dismiss it (deletes the evidence file and frees the reporter's slot).
 */
public final class ReportListGui extends AbstractGui {

    private final ReportService reportService;
    private final ReplayService replayService;

    public ReportListGui(GuiSessionRegistry registry, SoundService sounds,
                         ReportService reportService, ReplayService replayService) {
        super(registry, sounds, GuiType.REPORT_LIST, 6, true);
        this.reportService = reportService;
        this.replayService = replayService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.reports-title").color(UiTheme.HEADER);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        List<PlayerReport> reports = reportService.listPending();
        if (reports.isEmpty()) {
            inventory.setItem(22, ItemBuilder.of(Material.PAPER)
                    .name(t(player, "gui.reports-empty").color(UiTheme.MUTED))
                    .action("decorate")
                    .build());
        }
        int placed = 0;
        for (PlayerReport report : reports) {
            if (placed >= MenuScaffold.gridPageSize()) {
                break;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(report.targetUuid());
            String targetName = report.targetName() == null ? "?" : report.targetName();
            inventory.setItem(MenuScaffold.gridSlot(placed), ItemBuilder.of(Material.PLAYER_HEAD)
                    .name(Component.text(targetName, UiTheme.DANGER))
                    .skullOwner(target)
                    .lore(UiTheme.labelValue(line(player, "gui.report-reason"), report.reason()),
                            UiTheme.labelValue(line(player, "gui.report-reporter"),
                                    report.reporterName() == null ? "?" : report.reporterName()),
                            UiTheme.labelValue(line(player, "gui.match-kit"), report.kit() == null ? "-" : report.kit()),
                            UiTheme.labelValue(line(player, "gui.report-elapsed"), ago(player, report.createdAt())),
                            UiTheme.blank(),
                            UiTheme.hint(line(player, "gui.report-replay")),
                            t(player, "gui.reports-dismiss").color(NamedTextColor.RED))
                    .action("report:" + report.id())
                    .build());
            placed++;
        }
        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private String ago(Player player, Instant created) {
        if (created == null) {
            return "-";
        }
        Duration d = Duration.between(created, Instant.now());
        long minutes = Math.max(0, d.toMinutes());
        if (minutes < 60) {
            return line(player, "gui.report-minutes-ago").replace("<n>", String.valueOf(minutes));
        }
        return line(player, "gui.report-hours-ago").replace("<n>", String.valueOf(minutes / 60));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action == null || !action.startsWith("report:")) {
            return;
        }
        UUID reportId;
        try {
            reportId = UUID.fromString(action.substring("report:".length()));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (clickType.isRightClick()) {
            reportService.dismiss(reportId);
            sounds.play(player, "select");
            player.sendMessage(t(player, "gui.reports-dismissed"));
            refresh(player, session, inventory);
            return;
        }
        // Left-click: start the replay.
        player.closeInventory();
        player.sendMessage(t(player, "gui.reports-loading"));
        replayService.startFromReport(player, reportService, reportId);
    }
}
