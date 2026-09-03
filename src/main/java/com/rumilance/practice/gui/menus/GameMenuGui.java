package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Lobby compass hub. Combat entries live under {@link BattleMenuGui}; this screen keeps
 * kit editor, spectate, settings, titles, and teams.
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

    public void setTeamService(com.rumilance.practice.team.TeamService teamService) {
        this.teamService = teamService;
    }

    public void setStateManager(com.rumilance.practice.session.PlayerStateManager stateManager) {
        this.stateManager = stateManager;
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
        super(registry, sounds, GuiType.GAME_MENU, 6, true);
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
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));
        paintStatusChip(player, inventory);

        inventory.setItem(GuiSlots.slot(2, 4), tile(player, Material.NETHERITE_SWORD,
                "menu.battle", UiTheme.SUCCESS, "menu.battle-lore", "battle", true));

        inventory.setItem(GuiSlots.slot(3, 2), tile(player, Material.CRAFTING_TABLE,
                "menu.kits", UiTheme.PRIMARY, "menu.kits-lore", "ekit", false));
        // Teams tile adapts to membership: inside a party it becomes the party hub entry.
        boolean inParty = teamService != null && teamService.teamOf(player.getUniqueId()).isPresent();
        inventory.setItem(GuiSlots.slot(3, 4), tile(player, Material.WHITE_BANNER,
                inParty ? "menu.teams-in-party" : "menu.teams", UiTheme.HEADER,
                inParty ? "menu.teams-in-party-lore" : "menu.teams-lore", "teams", inParty));
        inventory.setItem(GuiSlots.slot(3, 6), tile(player, Material.ENDER_EYE,
                "menu.spectate", UiTheme.WARNING, "menu.spectate-lore", "spectate", false));

        inventory.setItem(GuiSlots.slot(4, 3), tile(player, Material.COMPARATOR,
                "menu.settings", UiTheme.MUTED, "menu.settings-lore", "settings", false));
        inventory.setItem(GuiSlots.slot(4, 5), tile(player, Material.NAME_TAG,
                "menu.titles", UiTheme.SECONDARY, "menu.titles-lore", "titles", false));

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

    private org.bukkit.inventory.ItemStack tile(Player player, Material material,
                                                String nameKey, TextColor color,
                                                String loreKey, String action, boolean glint) {
        return ItemBuilder.of(material)
                .name(t(player, nameKey).color(color))
                .lore(UiTheme.divider(),
                        UiTheme.line(line(player, loreKey)),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "menu.click")))
                .glint(glint)
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
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
            default -> {
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
