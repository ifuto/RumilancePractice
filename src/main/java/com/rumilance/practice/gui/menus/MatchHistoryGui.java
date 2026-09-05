package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.history.MatchHistoryStore;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Battle-menu history: the viewer's recent matches with outcome, scoreline and a click-through
 * to the end-of-match inventories (colours preserved by {@code MatchInventoryGui}).
 */
public final class MatchHistoryGui extends AbstractGui {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MatchHistoryStore store;
    private final MatchInventoryGui inventoryGui;

    public MatchHistoryGui(GuiSessionRegistry registry, SoundService sounds,
                           MatchHistoryStore store, MatchInventoryGui inventoryGui) {
        super(registry, sounds, GuiType.MATCH_HISTORY, 6, true);
        this.store = store;
        this.inventoryGui = inventoryGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.history-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<MatchHistoryStore.Entry> entries = store.recent(player.getUniqueId());
        int page = session.page();
        int perPage = MenuScaffold.gridPageSize();
        int start = page * perPage;
        for (int index = 0; index < perPage && start + index < entries.size(); index++) {
            inventory.setItem(MenuScaffold.gridSlot(index), entryIcon(player, entries.get(start + index)));
        }
        if (entries.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(0), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "gui.history-empty").color(UiTheme.MUTED))
                    .lore(UiTheme.line(line(player, "gui.history-empty-lore")))
                    .action("decorate")
                    .build());
        }

        paintPaging(player, inventory, page, entries.size());
        paintNav(player, session, inventory);
    }

    private ItemStack entryIcon(Player viewer, MatchHistoryStore.Entry entry) {
        MatchHistoryStore.Participant me = entry.participant(viewer.getUniqueId());
        boolean draw = entry.draw();
        boolean win = me != null && me.winner() && !draw;

        Material material = draw ? Material.YELLOW_STAINED_GLASS_PANE
                : win ? Material.GOLDEN_SWORD : Material.IRON_SWORD;
        if (!draw && me != null && me.teamColor() != null) {
            try {
                material = TeamColor.valueOf(me.teamColor()).wool();
            } catch (IllegalArgumentException ignored) {
                // Unknown colour name from an old record: keep the sword icon.
            }
        }

        String outcomeKey = draw ? "gui.history-draw" : (win ? "gui.history-win" : "gui.history-loss");
        NamedTextColor outcomeColor = draw ? NamedTextColor.YELLOW
                : (win ? NamedTextColor.GREEN : NamedTextColor.RED);

        List<Component> lore = new java.util.ArrayList<>();
        lore.add(UiTheme.divider());
        lore.add(UiTheme.labelValue(line(viewer, "gui.history-mode"), prettyMode(entry.mode())));
        lore.add(UiTheme.labelValue(line(viewer, "gui.history-kit"), entry.kit()));
        lore.add(Component.text(line(viewer, "gui.history-result") + " ", UiTheme.MUTED)
                .append(Component.text(line(viewer, outcomeKey), outcomeColor))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(line(viewer, "gui.history-score") + " ", UiTheme.MUTED)
                .append(scoreLine(viewer, entry, me))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(UiTheme.labelValue(line(viewer, "gui.history-time"), formatDuration(entry.durationMs())));
        lore.add(UiTheme.labelValue(line(viewer, "gui.history-date"), WHEN.format(Instant.ofEpochMilli(entry.endedAtEpochMs()))));
        lore.add(UiTheme.blank());
        lore.add(UiTheme.hint(line(viewer, "gui.history-inv-hint")));

        return ItemBuilder.of(material)
                .name(Component.text(entry.kit(), win ? NamedTextColor.GREEN : (draw ? NamedTextColor.YELLOW : NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false))
                .glint(win)
                .lore(lore.toArray(new Component[0]))
                .action("open:" + entry.matchId())
                .build();
    }

    /** Own kills vs the other side's kills; team matches total each team colour. */
    private Component scoreLine(Player viewer, MatchHistoryStore.Entry entry, MatchHistoryStore.Participant me) {
        int myKills = me == null ? 0 : me.kills();
        if (me != null && me.teamColor() != null) {
            int myTeam = entry.killsOfSide(me.teamColor());
            int others = 0;
            for (MatchHistoryStore.Participant p : entry.participants()) {
                if (p.teamColor() == null || !p.teamColor().equals(me.teamColor())) {
                    others += p.kills();
                }
            }
            return Component.text(myTeam, NamedTextColor.GREEN)
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(others, NamedTextColor.RED))
                    .append(Component.text(" (" + line(viewer, "gui.history-you") + " " + myKills + ")", NamedTextColor.GRAY));
        }
        if ("FFA".equalsIgnoreCase(entry.mode())) {
            // Placement among the free-for-all fighters by kills (ties share a place).
            int place = 1;
            for (MatchHistoryStore.Participant p : entry.participants()) {
                if ((me == null || !p.id().equals(me.id())) && p.kills() > myKills) {
                    place++;
                }
            }
            return Component.text(myKills, NamedTextColor.GREEN)
                    .append(Component.text(" " + line(viewer, "gui.history-kills")
                            + "  ·  #" + place + "/" + entry.participants().size(), NamedTextColor.GRAY));
        }
        int opponentKills = 0;
        String opponent = "-";
        for (MatchHistoryStore.Participant p : entry.participants()) {
            if (me == null || !p.id().equals(me.id())) {
                opponentKills += p.kills();
                opponent = p.name();
            }
        }
        return Component.text(myKills, NamedTextColor.GREEN)
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(opponentKills, NamedTextColor.RED))
                .append(Component.text("  vs " + opponent, NamedTextColor.GRAY));
    }

    private static String prettyMode(String mode) {
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "RANKED" -> "Ranked";
            case "UNRANKED" -> "Unranked";
            case "TEAM" -> "Party";
            case "FFA" -> "FFA";
            default -> mode;
        };
    }

    private static String formatDuration(long durationMs) {
        long seconds = Math.max(0, durationMs / 1000L);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(session.page() - 1);
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
        if (action.startsWith("open:")) {
            try {
                UUID matchId = UUID.fromString(action.substring("open:".length()));
                sounds.play(player, "gui-click");
                inventoryGui.open(player, matchId, player.getUniqueId());
            } catch (IllegalArgumentException ignored) {
                // Malformed action payload — ignore.
            }
        }
    }
}
