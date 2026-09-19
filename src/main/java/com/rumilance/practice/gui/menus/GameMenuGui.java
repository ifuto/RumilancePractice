package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.MenuTile;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.RealPlayers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Lobby compass hub — the refresh layout. Three rows of exactly three core actions, a status
 * chip (top-left) and the real online count (top-right), nothing else on the screen:
 *
 * <pre>
 *   [you]  N Arena                    [N online]
 *   ─────────────────────────────────────────────
 *        BATTLE          KITS          PARTY
 *
 *       SPECTATE      PROFILE       SETTINGS
 *
 *                    TITLES
 *   ─────────────────────────────────────────────
 *                      [close]
 * </pre>
 *
 * <p>Combat entries live under {@link BattleMenuGui}; this screen keeps kit editor, spectate,
 * settings, titles and teams — each tile in the standard {@link MenuTile} anatomy.</p>
 */
public final class GameMenuGui extends AbstractGui {

    private final BattleMenuGui battleMenuGui;
    private final EkitSelectGui ekitSelectGui;
    private final SpectateListGui spectateListGui;
    private final SettingsGui settingsGui;
    private final TitleGui titleGui;
    private final MessageService messageService;
    /** Opens the team hub/browser — wired via setter because the team GUIs are built later. */
    private java.util.function.Consumer<Player> openTeams = p -> { };
    /** Gates the kit editor entry while the player is committed to a match/queue/activity. */
    private java.util.function.Predicate<Player> kitEditBusy = p -> false;
    private com.rumilance.practice.team.TeamService teamService;
    private com.rumilance.practice.session.PlayerStateManager stateManager;
    private ProfileGui profileGui;

    public void setTeamService(com.rumilance.practice.team.TeamService teamService) {
        this.teamService = teamService;
    }

    public void setStateManager(com.rumilance.practice.session.PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setProfileGui(ProfileGui profileGui) {
        this.profileGui = profileGui;
    }

    public GameMenuGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            BattleMenuGui battleMenuGui,
            EkitSelectGui ekitSelectGui,
            SpectateListGui spectateListGui,
            SettingsGui settingsGui,
            TitleGui titleGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.GAME_MENU, 5, true);
        this.battleMenuGui = battleMenuGui;
        this.ekitSelectGui = ekitSelectGui;
        this.spectateListGui = spectateListGui;
        this.settingsGui = settingsGui;
        this.titleGui = titleGui;
        this.messageService = messageService;
    }

    public void setOpenTeams(java.util.function.Consumer<Player> openTeams) {
        this.openTeams = openTeams == null ? p -> { } : openTeams;
    }

    public void setKitEditBusyCheck(java.util.function.Predicate<Player> busyCheck) {
        this.kitEditBusy = busyCheck == null ? p -> false : busyCheck;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "menu.game-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected Material titleIcon() {
        return Material.COMPASS;
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        paintStatusChip(player, inventory);
        paintOnlineChip(player, inventory);

        // Row 1 — the three things players reach for first.
        boolean inParty = teamService != null && teamService.teamOf(player.getUniqueId()).isPresent();
        boolean kitLocked = kitEditBusy.test(player);
        inventory.setItem(GuiSlots.slot(1, 2),
                MenuTile.of(player, this, Material.NETHERITE_SWORD,
                        "menu.battle", UiTheme.SUCCESS, "menu.battle-lore", "battle")
                        .glint(true)
                        .live(UiTheme.labelValue(line(player, "menu.server-online"),
                                String.valueOf(Math.max(0, RealPlayers.count() - 1))))
                        .build(false, null));
        inventory.setItem(GuiSlots.slot(1, 4),
                MenuTile.of(player, this, Material.CRAFTING_TABLE,
                        "menu.kits", UiTheme.PRIMARY, "menu.kits-lore", "ekit")
                        .build(kitLocked, "menu.kits-busy-reason"));
        inventory.setItem(GuiSlots.slot(1, 6),
                MenuTile.of(player, this,
                        inParty ? Material.BEACON : Material.WHITE_BANNER,
                        inParty ? "menu.teams-in-party" : "menu.teams",
                        inParty ? UiTheme.HEADER : UiTheme.SECONDARY,
                        inParty ? "menu.teams-in-party-lore" : "menu.teams-lore",
                        "teams")
                        .live(UiTheme.status(line(player, inParty
                                ? "menu.teams-in-party" : "menu.teams-none"), inParty ? UiTheme.HEADER : UiTheme.MUTED))
                        .build(false, null));

        // Row 2 — secondary actions.
        inventory.setItem(GuiSlots.slot(2, 2),
                MenuTile.of(player, this, Material.SPYGLASS,
                        "menu.spectate", UiTheme.WARNING, "menu.spectate-lore", "spectate")
                        .build(false, null));
        inventory.setItem(GuiSlots.slot(2, 4),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(t(player, "menu.profile").color(UiTheme.VALUE))
                        .skullOwner(player)
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "menu.profile-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("profile")
                        .build());
        inventory.setItem(GuiSlots.slot(2, 6),
                MenuTile.of(player, this, Material.COMPARATOR,
                        "menu.settings", UiTheme.MUTED, "menu.settings-lore", "settings")
                        .build(false, null));

        // Row 3 — cosmetics, centred.
        inventory.setItem(GuiSlots.slot(3, 4),
                MenuTile.of(player, this, Material.NAME_TAG,
                        "menu.titles", UiTheme.SECONDARY, "menu.titles-lore", "titles")
                        .build(false, null));

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    /** Top-left chip: the viewer's own head and their current activity state. */
    private void paintStatusChip(Player player, Inventory inventory) {
        if (stateManager == null) {
            return;
        }
        com.rumilance.practice.state.PlayerState state = stateManager.getState(player.getUniqueId());
        String stateKey = switch (state) {
            case QUEUED_RANKED -> "menu.state-ranked-queue";
            case QUEUED_UNRANKED -> "menu.state-unranked-queue";
            case FIGHTING, PREPARING_MATCH, COUNTDOWN, ENDING -> "menu.state-fighting";
            case SPECTATING -> "menu.state-spectating";
            case FFA -> "menu.state-ffa";
            case EDITING_KIT -> "menu.state-editing";
            case REQUESTING_DUEL -> "menu.state-dueling";
            case PRACTICE_WAIT, PRACTICE_ACTIVE -> "menu.state-fighting";
            default -> "menu.state-lobby";
        };
        inventory.setItem(GuiSlots.slot(0, 1),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(Component.text(player.getName(), UiTheme.VALUE))
                        .skullOwner(player)
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue(line(player, "menu.status"),
                                        line(player, stateKey)))
                        .action("decorate")
                        .build());
    }

    /** Top-right chip: real online count — bots never count as players. */
    private void paintOnlineChip(Player player, Inventory inventory) {
        inventory.setItem(GuiSlots.slot(0, 7),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(t(player, "menu.server-online-name").color(UiTheme.SECONDARY))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue(line(player, "menu.server-online"),
                                        String.valueOf(Math.max(0, RealPlayers.count() - 1))))
                        .action("decorate")
                        .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        switch (action) {
            case "close" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "battle" -> openChild(player, battleMenuGui::open);
            case "ekit" -> {
                if (kitEditBusy.test(player)) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "menu.kits-busy").color(UiTheme.DANGER));
                    return;
                }
                openChild(player, ekitSelectGui::open);
            }
            case "spectate" -> openChild(player, spectateListGui::open);
            case "settings" -> openChild(player, settingsGui::open);
            case "titles" -> openChild(player, titleGui::open);
            case "teams" -> openChild(player, openTeams);
            case "profile" -> {
                if (profileGui != null) {
                    openChild(player, profileGui::open);
                }
            }
            default -> {
                if (action.startsWith("locked:")) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "menu.kits-busy").color(UiTheme.DANGER));
                }
            }
        }
    }

    /**
     * Opens a child screen and marks its fresh session as "from the Game Menu" so Esc/Close
     * inside it returns here. Screens opened any other way (e.g. /setfunc hotbar items or
     * commands) don't get the flag and simply close.
     */
    private void openChild(Player player, java.util.function.Consumer<Player> opener) {
        sounds.play(player, "gui-click");
        player.closeInventory();
        opener.accept(player);
        registry.get(player.getUniqueId()).ifPresent(child -> child.setFromGameMenu(true));
    }
}
