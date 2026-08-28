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
        return Component.text("通報一覧", UiTheme.HEADER);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        List<PlayerReport> reports = reportService.listPending();
        if (reports.isEmpty()) {
            inventory.setItem(22, ItemBuilder.of(Material.PAPER)
                    .name(Component.text("通報はありません", UiTheme.MUTED))
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
                    .lore(UiTheme.labelValue("理由", report.reason()),
                            UiTheme.labelValue("通報者", report.reporterName() == null ? "?" : report.reporterName()),
                            UiTheme.labelValue("キット", report.kit() == null ? "-" : report.kit()),
                            UiTheme.labelValue("経過", ago(report.createdAt())),
                            UiTheme.blank(),
                            UiTheme.hint("左クリック: リプレイを見る"),
                            Component.text("▶ 右クリック: 却下（削除）", NamedTextColor.RED))
                    .action("report:" + report.id())
                    .build());
            placed++;
        }
        MenuScaffold.closeButton(inventory);
    }

    private static String ago(Instant created) {
        if (created == null) {
            return "-";
        }
        Duration d = Duration.between(created, Instant.now());
        long minutes = Math.max(0, d.toMinutes());
        if (minutes < 60) {
            return minutes + "分前";
        }
        return (minutes / 60) + "時間前";
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
            player.sendMessage(Component.text("通報を却下しました。", NamedTextColor.YELLOW));
            refresh(player, session, inventory);
            return;
        }
        // Left-click: start the replay.
        player.closeInventory();
        player.sendMessage(Component.text("リプレイを読み込み中...", NamedTextColor.GRAY));
        replayService.startFromReport(player, reportService, reportId);
    }
}
