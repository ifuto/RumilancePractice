package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.MatchCombatTracker;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Post-match combat report card. Shows both participants' heads (green for the winner, red for
 * the loser) side-by-side with their damage dealt/taken, hits, critical hits, arrow hits and
 * longest combo, plus a central "MATCH REPORT" trophy tile. The book item handed out at match
 * end opens this GUI via {@link MatchListener}.
 */
public final class MatchReportGui extends AbstractGui {

    private final MatchService matchService;

    public MatchReportGui(GuiSessionRegistry registry, SoundService sounds, MatchService matchService) {
        super(registry, sounds, GuiType.MATCH_REPORT, 6, true);
        this.matchService = matchService;
    }

    /** Opens the report for the given match; no-op if the match has already been cleaned up. */
    public void openFor(Player viewer, UUID matchId) {
        MatchSession session = matchService.registry().get(matchId).orElse(null);
        if (session == null || session.participants().size() < 2) {
            viewer.sendMessage(t(viewer, "gui.match-report-gone"));
            return;
        }
        GuiSession guiSession = registry.open(viewer.getUniqueId(), type(), rows);
        guiSession.put("match_id", matchId.toString());
        PracticeGuiOpen.open(this, viewer, guiSession);
        sounds.play(viewer, "gui-open");
    }

    /** Opens the report for the player's most recent match, or sends an error if none exists. */
    public void openLastReport(Player viewer) {
        java.util.Optional<UUID> last = matchService.lastMatchId(viewer.getUniqueId());
        if (last.isEmpty()) {
            viewer.sendMessage(t(viewer, "gui.match-report-none"));
            return;
        }
        openFor(viewer, last.get());
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.match-report-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);

        String rawId = session.get("match_id", String.class);
        if (rawId == null) {
            MenuScaffold.closeButton(inventory);
            return;
        }
        UUID matchId;
        try {
            matchId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            MenuScaffold.closeButton(inventory);
            return;
        }
        MatchSession match = matchService.registry().get(matchId).orElse(null);
        if (match == null || match.participants().size() < 2) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.match-report-unavailable").color(UiTheme.DANGER))
                            .lore(UiTheme.line(line(player, "gui.match-report-cleaned")))
                            .action("decorate").build());
            MenuScaffold.closeButton(inventory);
            return;
        }

        UUID a = match.participants().get(0);
        UUID b = match.participants().get(1);
        MatchCombatTracker.CombatStats statsA = matchService.combatStats(matchId, a).orElse(null);
        MatchCombatTracker.CombatStats statsB = matchService.combatStats(matchId, b).orElse(null);

        MenuScaffold.header(inventory, 0, title(player, session));

        // Trophy / draw marker at the centre of the grid.
        Material trophy = match.isDraw() ? Material.WHITE_BANNER : Material.GOLDEN_HELMET;
        inventory.setItem(MenuScaffold.gridSlot(13),
                ItemBuilder.of(trophy)
                        .name(t(player, match.isDraw() ? "gui.match-draw" : "gui.match-report-banner")
                                .color(match.isDraw() ? UiTheme.WARNING : UiTheme.SECONDARY))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.labelValue(line(player, "gui.match-kit"), match.kitName()),
                                UiTheme.labelValue(line(player, "gui.match-mode"), match.mode().name()),
                                winnerLine(player, match)
                        )
                        .glint(!match.isDraw())
                        .action("decorate")
                        .build());

        inventory.setItem(GuiSlots.slot(2, 2), playerHead(player, a, match, statsA));
        inventory.setItem(GuiSlots.slot(2, 6), playerHead(player, b, match, statsB));

        // Stat comparison rows 3-4 (columns 2/4/6), leaving row 5 for the close button row.
        placeStatRow(inventory, player, a, b, statsA, statsB, 3, "gui.match-dmg-dealt",
                s -> s == null ? 0 : s.damageDealt());
        placeStatRow(inventory, player, a, b, statsA, statsB, 3, "gui.match-dmg-taken",
                s -> s == null ? 0 : s.damageTaken());
        placeStatRow(inventory, player, a, b, statsA, statsB, 4, "gui.match-hits",
                s -> s == null ? 0 : s.hits());
        placeStatRow(inventory, player, a, b, statsA, statsB, 4, "gui.match-crits-arrows",
                s -> s == null ? 0 : s.crits(),
                s -> s == null ? 0 : s.projectileHits());

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private void placeStatRow(Inventory inventory, Player viewer, UUID a, UUID b,
                              MatchCombatTracker.CombatStats statsA,
                              MatchCombatTracker.CombatStats statsB,
                              int row, String labelKey,
                              java.util.function.ToIntFunction<MatchCombatTracker.CombatStats> extractor) {
        placeStatRow(inventory, viewer, a, b, statsA, statsB, row, labelKey, extractor, extractor);
    }

    private void placeStatRow(Inventory inventory, Player viewer, UUID a, UUID b,
                              MatchCombatTracker.CombatStats statsA,
                              MatchCombatTracker.CombatStats statsB,
                              int row, String labelKey,
                              java.util.function.ToIntFunction<MatchCombatTracker.CombatStats> extractA,
                              java.util.function.ToIntFunction<MatchCombatTracker.CombatStats> extractB) {
        int valueA = extractA.applyAsInt(statsA);
        int valueB = extractB.applyAsInt(statsB);
        boolean aWins = valueA > valueB;
        boolean bWins = valueB > valueA;
        inventory.setItem(GuiSlots.slot(row, 2), valueTile(viewer, StatsService.nameOf(a), valueA, aWins));
        inventory.setItem(GuiSlots.slot(row, 4), ItemBuilder.of(Material.PAPER)
                .name(t(viewer, labelKey).color(UiTheme.MUTED))
                .action("decorate").build());
        inventory.setItem(GuiSlots.slot(row, 6), valueTile(viewer, StatsService.nameOf(b), valueB, bWins));
    }

    private ItemStack valueTile(Player viewer, String player, int value, boolean leading) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(player, leading ? UiTheme.SUCCESS : UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.match-value"), String.valueOf(value)),
                        leading ? UiTheme.status(line(viewer, "gui.match-leading"), UiTheme.SUCCESS)
                                : UiTheme.line("")
                )
                .glint(leading)
                .action("decorate")
                .build();
    }

    private Component winnerLine(Player viewer, MatchSession match) {
        if (match.isDraw()) {
            return UiTheme.status(line(viewer, "gui.match-draw-status"), UiTheme.WARNING);
        }
        UUID winner = match.winner();
        return winner == null ? UiTheme.line("")
                : UiTheme.labelValue(line(viewer, "gui.match-winner"), StatsService.nameOf(winner));
    }

    private ItemStack playerHead(Player viewer, UUID playerId, MatchSession match,
                                  MatchCombatTracker.CombatStats stats) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(playerId);
        boolean isWinner = !match.isDraw() && playerId.equals(match.winner());
        ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(StatsService.nameOf(playerId),
                        isWinner ? UiTheme.SUCCESS : (match.isDraw() ? UiTheme.WARNING : UiTheme.DANGER)))
                .skullOwner(owner)
                .lore(UiTheme.divider());
        if (isWinner) {
            builder.lore(UiTheme.status(line(viewer, "gui.match-winner-badge"), UiTheme.SUCCESS));
        } else if (match.isDraw()) {
            builder.lore(UiTheme.status(line(viewer, "gui.match-draw"), UiTheme.WARNING));
        } else {
            builder.lore(UiTheme.status(line(viewer, "gui.match-loser-badge"), UiTheme.DANGER));
        }
        if (stats != null) {
            builder.lore(
                    UiTheme.labelValue(line(viewer, "gui.match-dmg"), String.valueOf(stats.damageDealt())),
                    UiTheme.labelValue(line(viewer, "gui.match-taken"), String.valueOf(stats.damageTaken())),
                    UiTheme.labelValue(line(viewer, "gui.match-hits-short"), String.valueOf(stats.hits())),
                    UiTheme.labelValue(line(viewer, "gui.match-crits"), String.valueOf(stats.crits())),
                    UiTheme.labelValue(line(viewer, "gui.match-arrows"), String.valueOf(stats.projectileHits())),
                    UiTheme.labelValue(line(viewer, "gui.match-best-combo"), String.valueOf(stats.bestCombo()))
            );
        }
        return builder.action("decorate").glint(isWinner).build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
        }
    }
}
