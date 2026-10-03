package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The mockup's "Party Start Battle GUI" (2026-10): the party's battle launchpad —
 * <b>Select a Kit</b> (barrel, the classic picker), <b>Select a Battle Mode</b> (iron axe →
 * {@link PartyBattleModeGui}: Party Fight or the Private FFA), <b>Select a Map</b> (map) and
 * the centred <b>Start Battle</b> sword. Light-blue ring, black no-cell bottom row.
 *
 * <p>Party Fight keeps the existing flow exactly (kit picker → red vs blue team battle);
 * Party FFA is today's Private FFA ({@code PartyFfaService}): a dedicated {@code party-ffa}
 * zone where the whole online-and-free party fights free-for-all until the last player
 * leaves.</p>
 */
public final class PartyStartBattleGui extends AbstractGui {

    public static final String MODE_FIGHT = "fight";
    public static final String MODE_FFA = "ffa";
    private static final String MODE_KEY = "party-battle-mode";

    private final TeamService teamService;
    private final TeamKitSelectGui kitSelect;
    private final PartyMapSelectGui mapSelect;
    private final MessageService messageService;
    private PartyBattleModeGui battleModeGui;
    private com.rumilance.practice.session.PlayerStateManager stateManager;
    private volatile com.rumilance.practice.team.PartyFfaService partyFfaService;

    public PartyStartBattleGui(GuiSessionRegistry registry, SoundService sounds,
                               TeamService teamService, TeamKitSelectGui kitSelect,
                               PartyMapSelectGui mapSelect, MessageService messageService) {
        super(registry, sounds, GuiType.PARTY_START_BATTLE, 6, true);
        this.teamService = teamService;
        this.kitSelect = kitSelect;
        this.mapSelect = mapSelect;
        this.messageService = messageService;
    }

    public void setBattleModeGui(PartyBattleModeGui battleModeGui) {
        this.battleModeGui = battleModeGui;
    }

    public void setStateManager(com.rumilance.practice.session.PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setPartyFfaService(com.rumilance.practice.team.PartyFfaService partyFfaService) {
        this.partyFfaService = partyFfaService;
    }

    /** Normalizes a mode string; anything but "ffa" is the classic Party Fight. */
    public static String normalizeMode(String mode) {
        return MODE_FFA.equals(mode) ? MODE_FFA : MODE_FIGHT;
    }

    /** Opens the launchpad carrying {@code mode} ("fight"/"ffa"). */
    public void openFor(Player player, String mode) {
        openWithSession(player, session ->
                session.put(MODE_KEY, normalizeMode(mode)));
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.IRON_AXE;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.party-launch-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        String mode = normalizeMode(session.get(MODE_KEY, String.class));
        boolean ffa = MODE_FFA.equals(mode);

        // --- mockup frame ---
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(1, col), pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(4, col), pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(5, col),
                    com.rumilance.practice.gui.GuiMockups.noCell(player, messageService));
            inventory.setItem(GuiSlots.slot(3, col), col <= 1 || col >= 7
                    ? pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE) : null);
            inventory.setItem(GuiSlots.slot(2, col), col <= 1 || col >= 7
                    ? pane(player, Material.LIGHT_BLUE_STAINED_GLASS_PANE) : null);
        }

        if (team == null || !team.isOwner(player.getUniqueId())) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.party-owner-only"))
                            .lore(UiTheme.line(line(player, "party.owner-only-lore")))
                            .action("decorate")
                            .build());
            return;
        }

        // --- (1,2) Select a Kit ---
        inventory.setItem(GuiSlots.slot(1, 2),
                ItemBuilder.of(Material.BARREL)
                        .name(t(player, "gui.party-launch-kit").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.party-launch-kit-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("select_kit").build());
        // --- (1,4) Select a Battle Mode ---
        inventory.setItem(GuiSlots.slot(1, 4),
                ItemBuilder.of(Material.IRON_AXE)
                        .name(t(player, "gui.party-launch-mode").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.party-launch-mode-lore")),
                                UiTheme.labelValue(line(player, "gui.party-launch-mode-current"),
                                        line(player, ffa ? "gui.battle-mode-ffa" : "gui.battle-mode-fight")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .glint(ffa)
                        .action("select_mode").build());
        // --- (1,6) Select a Map ---
        String arena = team.selectedArena();
        inventory.setItem(GuiSlots.slot(1, 6),
                ItemBuilder.of(Material.MAP)
                        .name(t(player, "gui.party-launch-map").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue(line(player, "party.map-label"),
                                        arena == null || arena.isBlank()
                                                ? line(player, "party.random")
                                                : com.rumilance.practice.util.NameDisplay.pretty(arena)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("select_map").build());
        // --- (3,3) Start Battle ---
        inventory.setItem(GuiSlots.slot(3, 3),
                ItemBuilder.of(Material.DIAMOND_SWORD)
                        .name(t(player, "gui.party-launch-go").color(UiTheme.SUCCESS))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, ffa
                                        ? "gui.party-launch-go-ffa-lore"
                                        : "gui.party-launch-go-fight-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .glint(true)
                        .action("start_battle").build());
    }

    private ItemStack pane(Player player, Material material) {
        return com.rumilance.practice.gui.GuiMockups.deco(player, material, messageService);
    }

    // ---------------------------------------------------------------- clicks

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            return;
        }
        String mode = normalizeMode(session.get(MODE_KEY, String.class));
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "select_kit" -> {
                // The classic flow: the kit picker starts the red-vs-blue battle on pick.
                sounds.play(player, "gui-click");
                player.closeInventory();
                kitSelect.open(player);
            }
            case "select_map" -> {
                if (mapSelect != null) {
                    sounds.play(player, "gui-click");
                    player.closeInventory();
                    mapSelect.open(player);
                }
            }
            case "select_mode" -> {
                if (battleModeGui != null) {
                    sounds.play(player, "gui-click");
                    player.closeInventory();
                    battleModeGui.openFor(player, mode);
                }
            }
            case "start_battle" -> {
                if (MODE_FFA.equals(mode)) {
                    startPartyFfa(player, team);
                    return;
                }
                // Party Fight: identical to the classic START (preflight, then the kit picker
                // that launches the battle on pick).
                TeamService.Result precheck = teamService.preflightStart(player);
                if (precheck != TeamService.Result.OK) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            teamService.errorMessage(player, precheck), UiTheme.DANGER)
                            .decoration(TextDecoration.ITALIC, false));
                    return;
                }
                sounds.play(player, "gui-click");
                player.closeInventory();
                kitSelect.open(player);
            }
            default -> {
            }
        }
    }

    /** Party FFA (Private FFA): sends every online, free party member into a fresh zone. */
    private void startPartyFfa(Player player, Team team) {
        com.rumilance.practice.team.PartyFfaService ffa = partyFfaService;
        if (ffa == null) {
            sounds.play(player, "error");
            return;
        }
        List<UUID> members = new ArrayList<>();
        for (UUID member : team.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online == null || !online.isOnline()) {
                continue;
            }
            PlayerState state = stateManager == null ? PlayerState.LOBBY
                    : stateManager.getState(member);
            if (state == PlayerState.LOBBY || state == PlayerState.OPENING_GUI) {
                members.add(member);
            }
        }
        if (members.size() < 2) {
            sounds.play(player, "error");
            player.sendMessage(t(player, "gui.party-ffa-too-few").color(UiTheme.DANGER));
            return;
        }
        String matchId = ffa.startForPlayers(members);
        if (matchId == null) {
            sounds.play(player, "error");
            player.sendMessage(t(player, "gui.party-ffa-failed").color(UiTheme.DANGER));
            return;
        }
        sounds.play(player, "select");
        player.closeInventory();
    }
}
