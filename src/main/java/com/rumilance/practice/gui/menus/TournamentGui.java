package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.KitSections;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitCategory;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.tournament.TournamentService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Tournament setup, staged like the kit picker: first the two category buttons, then the
 * chosen category's kits, always with a progression toggle (parallel / sequential) and a
 * start button in the bottom bar. Reuses {@link TournamentService#start} for the actual
 * launch so the command and the GUI stay in lockstep. Opened from the party hub button and
 * from {@code /tournament} alike.
 */
public final class TournamentGui extends AbstractGui {

    private final TournamentService tournamentService;
    private final TeamService teamService;
    private final KitService kitService;
    private java.util.function.Consumer<Player> returnToHub;

    public TournamentGui(GuiSessionRegistry registry, SoundService sounds,
                         TournamentService tournamentService, TeamService teamService,
                         KitService kitService) {
        super(registry, sounds, GuiType.TOURNAMENT, 6, true);
        this.tournamentService = tournamentService;
        this.teamService = teamService;
        this.kitService = kitService;
    }

    /** Nested screens return to the party hub on Back/Close when set; otherwise just close. */
    public void setReturnToHub(java.util.function.Consumer<Player> returnToHub) {
        this.returnToHub = returnToHub;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.GOLDEN_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "tournament.gui-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setKitCategory(null);
        session.setPage(0);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        var teamOpt = teamService.teamOf(player.getUniqueId());
        if (teamOpt.isEmpty() || !teamOpt.get().isOwner(player.getUniqueId())) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.party-owner-only"))
                            .lore(UiTheme.line(line(player, "tournament.owner-only-lore")))
                            .action("decorate")
                            .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }

        String category = session.kitCategory();
        if (category == null) {
            renderCategoryChooser(player, inventory);
        } else {
            renderKitList(player, session, inventory, category);
        }

        paintBottomBar(player, session, inventory);
    }

    private void renderCategoryChooser(Player player, Inventory inventory) {
        List<KitDefinition> main = kitService.enabled(KitCategory.MAIN);
        List<KitDefinition> sub = kitService.enabled(KitCategory.SUB);
        inventory.setItem(MenuScaffold.gridSlot(9),
                KitSections.categoryButton(KitCategory.MAIN,
                        t(player, "gui.kit-main-button").color(UiTheme.SUCCESS),
                        List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-main-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(main.size())),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "tournament.pick-hint")))));
        inventory.setItem(MenuScaffold.gridSlot(11),
                KitSections.categoryButton(KitCategory.SUB,
                        t(player, "gui.kit-sub-button").color(UiTheme.SECONDARY),
                        List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-sub-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(sub.size())),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "tournament.pick-hint")))));
    }

    private void renderKitList(Player player, GuiSession session, Inventory inventory, String category) {
        boolean sub = "SUB".equalsIgnoreCase(category);
        KitCategory cat = sub ? KitCategory.SUB : KitCategory.MAIN;
        List<KitDefinition> kits = kitService.enabled(cat);
        int index = 0;
        inventory.setItem(MenuScaffold.gridSlot(index++),
                KitSections.header(cat, kits.size(), line(player, "tournament.pick-hint")));
        String selected = session.get("tournamentKit", String.class);
        for (KitDefinition kit : kits) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++), kitTile(player, kit, selected));
        }
    }

    private ItemStack kitTile(Player player, KitDefinition kit, String selected) {
        boolean isSelected = selected != null && selected.equals(kit.name());
        return ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                .nameMini(kit.prettyDisplayName())
                .lore(UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.party-arena"), kit.hasFixedArena()
                                ? com.rumilance.practice.util.KitNames.pretty(kit.arenaName())
                                : line(player, "gui.queue-random")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "tournament.pick-hint")))
                .glintIf(isSelected)
                .action("kit:" + kit.name())
                .build();
    }

    private void paintBottomBar(Player player, GuiSession session, Inventory inventory) {
        int entrants = tournamentService.entrantCount(player);
        boolean sequential = sequentialOf(session);
        String readiness = tournamentService.readinessError(player);

        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(Material.NETHER_STAR, Math.max(1, entrants))
                        .name(t(player, "tournament.entrants").color(UiTheme.VALUE))
                        .lore(UiTheme.line(line(player, "tournament.entrants-lore")
                                .replace("<n>", String.valueOf(entrants))))
                        .action("decorate")
                        .build());

        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(sequential ? Material.CLOCK : Material.COMPASS)
                        .name(t(player, sequential
                                        ? "tournament.mode-sequential" : "tournament.mode-parallel")
                                .color(sequential ? UiTheme.WARNING : UiTheme.SUCCESS))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, sequential
                                        ? "tournament.mode-sequential-lore"
                                        : "tournament.mode-parallel-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "tournament.mode-hint")))
                        .action("toggle_mode")
                        .build());

        boolean ready = readiness == null;
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(ready ? Material.GOLDEN_SWORD : Material.GRAY_DYE)
                        .name(t(player, "tournament.start")
                                .color(ready ? UiTheme.SUCCESS : UiTheme.MUTED))
                        .lore(UiTheme.divider(),
                                ready ? UiTheme.hint(line(player, "tournament.start-hint"))
                                        : UiTheme.line(readiness))
                        .glintIf(ready)
                        .action(ready ? "start" : "decorate")
                        .build());

        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private static boolean sequentialOf(GuiSession session) {
        Boolean value = session.get("tournamentSequential", Boolean.class);
        return value != null && value;
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action) {
        if (action != null && action.startsWith("cat:")) {
            session.setKitCategory(action.substring(4));
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        switch (action) {
            case "back", "close" -> {
                if (session.kitCategory() != null) {
                    session.setKitCategory(null);
                    sounds.play(player, "gui-back");
                    refresh(player, session, inventory);
                    return;
                }
                sounds.play(player, "gui-back");
                player.closeInventory();
                if (returnToHub != null) {
                    returnToHub.accept(player);
                }
            }
            case "toggle_mode" -> {
                boolean current = sequentialOf(session);
                session.put("tournamentSequential", !current);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "start" -> {
                var teamOpt = teamService.teamOf(player.getUniqueId());
                if (teamOpt.isEmpty() || !teamOpt.get().isOwner(player.getUniqueId())) {
                    sounds.play(player, "error");
                    refresh(player, session, inventory);
                    return;
                }
                String kitId = session.get("tournamentKit", String.class);
                if (kitId == null) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "tournament.pick-first"));
                    return;
                }
                String readiness = tournamentService.readinessError(player);
                if (readiness != null) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(readiness, UiTheme.DANGER)
                            .decoration(TextDecoration.ITALIC, false));
                    refresh(player, session, inventory);
                    return;
                }
                sounds.play(player, "match-found");
                player.closeInventory();
                tournamentService.start(player, kitId,
                        sequentialOf(session) ? "sequential" : "parallel");
            }
            default -> {
                if (action.startsWith("kit:")) {
                    String kitId = action.substring("kit:".length());
                    if (kitService.get(kitId).filter(k -> k.enabled()).isEmpty()) {
                        sounds.play(player, "error");
                        return;
                    }
                    session.put("tournamentKit", kitId);
                    sounds.play(player, "select");
                    refresh(player, session, inventory);
                }
            }
        }
    }
}
