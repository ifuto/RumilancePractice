package com.rumilance.practice.gui.menus;

import com.rumilance.practice.chat.ChatLogService;
import com.rumilance.practice.chat.ChatReportService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiFrame;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chat reports filed through the "click to report" hover.
 *
 * <p>Each tile carries the reported line's <em>timestamp</em>, who it belongs to, the line
 * itself and the player messages around it. The context is not stored with the report — it is
 * read from {@link ChatLogService} every time this screen renders, so it is always whatever the
 * chat buffer still holds. Once a line scrolls out of the buffer the tile says so instead of
 * showing a stale copy.</p>
 *
 * <p>Left-click marks a report handled, right-click dismisses it. Both go to the database, so a
 * closed report stays closed across restarts.</p>
 */
public final class ChatReportsGui extends AbstractGui {

    /** How many surrounding lines fit in a tile before it stops being readable. */
    private static final int MAX_CONTEXT_LINES = 9;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ChatReportService reports;

    private Consumer<Player> onBack = player -> { };

    public ChatReportsGui(GuiSessionRegistry registry, SoundService sounds,
                          ChatReportService reports) {
        super(registry, sounds, GuiType.CHAT_REPORTS, 6, true);
        this.reports = reports;
    }

    /** Where the Back button goes; defaults to just closing. */
    public void setOnBack(Consumer<Player> onBack) {
        this.onBack = onBack == null ? player -> { } : onBack;
    }

    @Override
    protected GuiFrame.Theme theme() {
        return GuiFrame.Theme.RED;
    }

    @Override
    protected Material titleIcon() {
        return Material.WRITTEN_BOOK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Chat reports", UiTheme.HEADER);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        List<ChatReportService.Report> all = reports == null ? List.of() : reports.recent(45);
        if (all.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(0), ItemBuilder.of(Material.PAPER)
                    .name(Component.text("No chat reports", UiTheme.MUTED))
                    .lore(UiTheme.line("Nothing has been reported from chat yet."))
                    .action("decorate")
                    .build());
        }
        int placed = 0;
        for (ChatReportService.Report report : all) {
            if (placed >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(placed), tileFor(report));
            placed++;
        }
        inventory.setItem(49, ItemBuilder.of(UiTheme.BACK)
                .name(Component.text("Back", UiTheme.WARNING))
                .action("back")
                .build());
    }

    private ItemStack tileFor(ChatReportService.Report report) {
        List<Component> lore = new ArrayList<>();
        lore.add(UiTheme.divider());
        lore.add(UiTheme.labelValue("Reported", report.reportedName()));
        lore.add(UiTheme.labelValue("Status", report.status()));
        lore.add(UiTheme.labelValue("Sent at", STAMP.format(report.timestamp())));
        lore.add(UiTheme.blank());

        String text = report.text();
        if (text == null) {
            // The buffer scrolled past this line — say so rather than pretending.
            lore.add(UiTheme.line("(original message expired)"));
            lore.add(UiTheme.labelValue("Filed at",
                    STAMP.format(Instant.ofEpochMilli(report.reportedTs()))));
        } else {
            lore.add(UiTheme.line("Context:"));
            lore.addAll(contextLore(report));
        }
        lore.add(UiTheme.blank());
        lore.add(UiTheme.hint("Left-click: handled    Right-click: dismiss"));

        return ItemBuilder.of(Material.WRITTEN_BOOK)
                .name(Component.text(report.reportedName(), UiTheme.DANGER))
                .lore(lore.toArray(new Component[0]))
                .glint(ChatReportService.OPEN.equals(report.status()))
                .action("cr:" + report.chatLineId() + ":" + report.reporterId())
                .build();
    }

    /**
     * The reported line plus its neighbours, oldest first, with the reported one marked.
     * Trims from the far end when the window is wider than a tile can show.
     */
    private List<Component> contextLore(ChatReportService.Report report) {
        List<ChatLogService.ChatLine> raw = report.context();
        List<ChatLogService.ChatLine> window = raw.size() > MAX_CONTEXT_LINES
                ? raw.subList(raw.size() - MAX_CONTEXT_LINES, raw.size())
                : raw;
        List<Component> out = new ArrayList<>();
        for (ChatLogService.ChatLine line : window) {
            boolean reported = line.id() == report.chatLineId();
            String prefix = reported ? "» " : "  ";
            out.add(Component.text(prefix + line.senderName() + ": " + line.message(),
                    reported ? UiTheme.DANGER : UiTheme.MUTED));
        }
        return out;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        if ("back".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if ("back".equals(action)) {
                onBack.accept(player);
            }
            return;
        }
        if (action == null || !action.startsWith("cr:")) {
            return;
        }
        String[] parts = action.substring(3).split(":");
        if (parts.length != 2) {
            return;
        }
        long chatLineId;
        UUID reporterId;
        try {
            chatLineId = Long.parseLong(parts[0]);
            reporterId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return;
        }
        boolean dismiss = clickType != null && clickType.isRightClick();
        reports.setStatus(chatLineId, reporterId,
                dismiss ? ChatReportService.DISMISSED : ChatReportService.HANDLED);
        sounds.play(player, "select");
        player.sendMessage(Component.text(
                dismiss ? "Report dismissed." : "Report marked handled.", UiTheme.SUCCESS));
        refresh(player, session, inventory);
    }
}
