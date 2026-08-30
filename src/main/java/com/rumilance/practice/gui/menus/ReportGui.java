package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.PracticeGuiHolder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.MatchActionRecorder;
import com.rumilance.practice.report.ReportService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Player-facing report menu: pick a reason to report the opponent of your most recent 1v1.
 * The report is stored with compressed movement evidence for staff review (see {@link ReportService}).
 */
public final class ReportGui extends AbstractGui {

    private final ReportService reportService;

    public ReportGui(GuiSessionRegistry registry, SoundService sounds, ReportService reportService) {
        super(registry, sounds, GuiType.REPORT, 6, true);
        this.reportService = reportService;
    }

    /** Opens the reason menu for the reporter's last opponent, or messages why it cannot open. */
    public void openReport(Player reporter) {
        MatchActionRecorder.LastMatch last = reportService.lastOpponent(reporter.getUniqueId()).orElse(null);
        if (last == null || last.opponentId() == null) {
            reporter.sendMessage(t(reporter, "gui.report-no-opponent"));
            return;
        }
        GuiSession session = registry.open(reporter.getUniqueId(), type, rows);
        session.setTargetPlayer(last.opponentId());
        PracticeGuiHolder holder = new PracticeGuiHolder(session.sessionId(), type, rows);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title(reporter, session));
        holder.bind(inventory);
        render(reporter, session, inventory);
        reporter.openInventory(inventory);
        sounds.play(reporter, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.report-title").color(UiTheme.DANGER);
    }

    @Override
    protected void render(Player reporter, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        UUID targetId = session.targetPlayer();
        OfflinePlayer target = targetId == null ? null : Bukkit.getOfflinePlayer(targetId);
        String name = target == null || target.getName() == null ? "?" : target.getName();
        inventory.setItem(4, ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(name, UiTheme.VALUE))
                .skullOwner(target)
                .lore(UiTheme.line(line(reporter, "gui.report-recent")),
                        UiTheme.line(line(reporter, "gui.report-pick-reason")))
                .action("decorate")
                .build());
        reason(reporter, inventory, 11, Material.IRON_SWORD, "Combat Cheat", "combat-cheat");
        reason(reporter, inventory, 13, Material.FEATHER, "Movement Cheat", "movement-cheat");
        reason(reporter, inventory, 15, Material.PUFFERFISH, "Bad Manner", "bad-manner");
        reason(reporter, inventory, 29, Material.PAPER, "Chat Spam", "chat-spam");
        reason(reporter, inventory, 33, Material.BARRIER, "Other", "other");
        MenuScaffold.closeButton(inventory);
    }

    private void reason(Player player, Inventory inventory, int slot, Material material, String label, String action) {
        inventory.setItem(slot, ItemBuilder.of(material)
                .name(Component.text(label, UiTheme.DANGER))
                .lore(UiTheme.hint(line(player, "gui.report-click")))
                .action("report:" + action)
                .build());
    }

    @Override
    public void handleClick(Player reporter, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            reporter.closeInventory();
            return;
        }
        if (action == null || !action.startsWith("report:")) {
            return;
        }
        String reason = action.substring("report:".length());
        reporter.closeInventory();
        ReportService.SubmitResult result = reportService.submit(reporter, reason);
        switch (result) {
            case SUBMITTED -> {
                sounds.play(reporter, "select");
                reporter.sendMessage(t(reporter, "gui.report-accepted"));
            }
            case NO_RECENT_MATCH -> reporter.sendMessage(t(reporter, "gui.report-no-opponent"));
            case ALREADY_REPORTED_TARGET -> reporter.sendMessage(t(reporter, "gui.report-already"));
            case SLOTS_FULL -> reporter.sendMessage(t(reporter, "gui.report-limit"));
            case ERROR -> reporter.sendMessage(t(reporter, "gui.report-fail"));
        }
    }
}
