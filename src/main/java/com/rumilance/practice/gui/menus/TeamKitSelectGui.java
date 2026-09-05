package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Kit chooser for a team battle. Opened from the {@link TeamHubGui} "Start Battle" button —
 * clicking a kit immediately launches the RED-vs-BLUE match (no queue). Only the owner of a
 * split-ready team can start; anyone else gets bounced back to the hub.
 */
public final class TeamKitSelectGui extends AbstractGui {

    private final TeamService teamService;
    private final KitService kitService;
    private final com.rumilance.practice.locale.MessageService messageService;
    private PartyMapSelectGui partyMapSelectGui;
    /** Owner's original-kit store (null = original kits unavailable here). */
    private com.rumilance.practice.originalkit.OriginalKitService originalKitService;
    /** Rules kit used when fighting with an original kit (config, falls back to first kit). */
    private volatile String originalKitRulesKitId;

    public void setOriginalKitService(
            com.rumilance.practice.originalkit.OriginalKitService originalKitService) {
        this.originalKitService = originalKitService;
    }

    public void setOriginalKitRulesKitId(String kitId) {
        this.originalKitRulesKitId = kitId;
    }

    public TeamKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                            TeamService teamService, KitService kitService) {
        this(registry, sounds, teamService, kitService, null);
    }

    public TeamKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                            TeamService teamService, KitService kitService,
                            com.rumilance.practice.locale.MessageService messageService) {
        super(registry, sounds, GuiType.TEAM_KIT_SELECT, 6, true);
        this.teamService = teamService;
        this.kitService = kitService;
        this.messageService = messageService;
    }

    /**
     * Party flow: pick a kit here, then pick the party map in {@link PartyMapSelectGui};
     * the match starts when the map is chosen. Wired from bootstrap.
     */
    public void setPartyMapSelectGui(PartyMapSelectGui partyMapSelectGui) {
        this.partyMapSelectGui = partyMapSelectGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.party-kit-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            // Only a split-ready team's owner may launch a battle — everyone else sees a
            // locked screen instead of a kit grid they must not act on.
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.party-owner-only"))
                            .lore(UiTheme.line(line(player, "party.owner-only-lore")))
                            .action("decorate")
                            .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }
        int red = team.side(TeamColor.RED).size();
        int blue = team.side(TeamColor.BLUE).size();
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(Material.RED_WOOL, Math.max(1, red))
                        .name(t(player, "party.red-vs-blue", MessageService.tags(
                                "red", String.valueOf(red),
                                "blue", String.valueOf(blue))).color(UiTheme.VALUE))
                        .lore(UiTheme.line(line(player, "gui.party-uneven-ok")))
                        .action("decorate").build());

        // Live readiness tile: green glow when the battle can start, otherwise the exact
        // reason (unassigned members, someone queued / in FFA / spectating, ...).
        TeamService.Result precheck = teamService.preflightStart(player);
        boolean ready = precheck == TeamService.Result.OK;
        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(ready ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name(t(player, ready ? "party.status-ready" : "party.status-blocked")
                                .color(ready ? UiTheme.SUCCESS : UiTheme.WARNING))
                        .lore(ready
                                ? UiTheme.line(line(player, "party.status-ready-lore"))
                                : UiTheme.line(teamService.errorMessage(player, precheck)))
                        .glintIf(ready)
                        .action("decorate").build());

        List<KitDefinition> kits = kitService.enabled();
        int index = 0;
        for (KitDefinition kit : kits) {
            if (index >= MenuScaffold.gridPageSize()) {
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++),
                    ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                            .nameMini(kit.prettyDisplayName())
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue(line(player, "gui.party-arena"), kit.hasFixedArena()
                                            ? com.rumilance.practice.util.KitNames.pretty(kit.arenaName())
                                            : line(player, "gui.queue-random")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.party-start-click")))
                            .action("kit:" + kit.name())
                            .build());
        }

        // The owner's own original kits are also selectable for the party battle: everyone
        // fights with the owner's saved layout, while the match rules come from the shared
        // rules kit.
        if (originalKitService != null && index < MenuScaffold.gridPageSize()) {
            com.rumilance.practice.originalkit.OriginalKitService.Plan plan =
                    originalKitService.planOf(player);
            for (int slot = 0; slot < 9 && index < MenuScaffold.gridPageSize(); slot++) {
                if (!originalKitService.isSlotUnlocked(plan, slot)
                        || !originalKitService.hasSaved(player.getUniqueId(), slot)) {
                    continue;
                }
                inventory.setItem(MenuScaffold.gridSlot(index++),
                        ItemBuilder.of(Material.NETHER_STAR)
                                .name(Component.text("Original Kit #" + (slot + 1), UiTheme.HEADER)
                                        .decoration(TextDecoration.ITALIC, false))
                                .lore(UiTheme.divider(),
                                        UiTheme.line(line(player, "gui.party-original-kit-lore")),
                                        UiTheme.blank(),
                                        UiTheme.hint(line(player, "gui.party-start-click")))
                                .glint(true)
                                .action("origkit:" + slot)
                                .build());
            }
        }

        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
                player.performCommand("team");
            }
            default -> {
                if (action.startsWith("kit:")) {
                    teamService.teamOf(player.getUniqueId())
                            .ifPresent(team -> team.setOriginalKitSlot(null));
                    proceedWithKit(player, action.substring("kit:".length()));
                } else if (action.startsWith("origkit:")) {
                    // Original kit selected: remember the owner's slot and fight under the
                    // shared rules kit's map rules.
                    int origSlot;
                    try {
                        origSlot = Integer.parseInt(action.substring("origkit:".length()));
                    } catch (NumberFormatException e) {
                        return;
                    }
                    teamService.teamOf(player.getUniqueId())
                            .ifPresent(team -> team.setOriginalKitSlot(origSlot));
                    proceedWithKit(player, resolveOriginalRulesKit());
                }
            }
        }
    }

    /** The kit whose RULES govern an original-kit battle (config override or first enabled). */
    private String resolveOriginalRulesKit() {
        String configured = originalKitRulesKitId;
        if (configured != null && !configured.isBlank()
                && kitService.get(configured).map(KitDefinition::enabled).orElse(false)) {
            return configured;
        }
        List<KitDefinition> enabled = kitService.enabled();
        return enabled.isEmpty() ? "nodebuff" : enabled.get(0).name();
    }

    /** Validates readiness, then enters the map-select flow (or starts directly without it). */
    private void proceedWithKit(Player player, String kitId) {
        // Validate split readiness BEFORE entering map selection so the owner
        // gets the same errors as before.
        TeamService.Result precheck = teamService.preflightStart(player);
        if (precheck != TeamService.Result.OK) {
            sounds.play(player, "error");
            player.sendMessage(Component.text(teamService.errorMessage(player, precheck), UiTheme.DANGER)
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        sounds.play(player, "gui-click");
        if (partyMapSelectGui != null) {
            final String chosenKit = kitId;
            org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                    () -> {
                        if (player.isOnline()) {
                            partyMapSelectGui.openForKit(player, chosenKit);
                        }
                    });
        } else {
            player.closeInventory();
            TeamService.Result r = teamService.start(player, kitId);
            sounds.play(player, r == TeamService.Result.OK ? "match-found" : "error");
            if (r != TeamService.Result.OK) {
                player.sendMessage(Component.text(teamService.errorMessage(player, r), UiTheme.DANGER)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
    }
}
