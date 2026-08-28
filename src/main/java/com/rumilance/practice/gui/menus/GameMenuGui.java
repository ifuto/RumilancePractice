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

    @Override
    protected Component title(Player player, GuiSession session) {
        if (messageService != null) {
            try {
                return messageService.render(messageService.resolveLocale(player), "menu.game-title")
                        .color(UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Component.text("Game Menu", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        Component battleName = Component.text("Battle", UiTheme.SUCCESS);
        if (messageService != null) {
            try {
                battleName = messageService.render(messageService.resolveLocale(player), "menu.battle")
                        .color(UiTheme.SUCCESS);
            } catch (Exception ignored) {
            }
        }
        inventory.setItem(GuiSlots.slot(2, 3), ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(battleName)
                .lore(UiTheme.line("Ranked, unranked, duel, FFA, bot."),
                        UiTheme.hint("Open Battle Menu"))
                .action("battle").build());

        inventory.setItem(GuiSlots.slot(2, 5), ItemBuilder.of(Material.CRAFTING_TABLE)
                .name(Component.text("Kit Editor", UiTheme.PRIMARY))
                .lore(UiTheme.line("Edit official & original kits."),
                        UiTheme.hint("Open /ekit"))
                .action("ekit").build());

        inventory.setItem(GuiSlots.slot(3, 3), ItemBuilder.of(Material.ENDER_EYE)
                .name(Component.text("Spectate", UiTheme.WARNING))
                .lore(UiTheme.line("Watch live matches."),
                        UiTheme.hint("Browse"))
                .action("spectate").build());

        inventory.setItem(GuiSlots.slot(3, 5), ItemBuilder.of(Material.COMPARATOR)
                .name(Component.text("Settings", UiTheme.MUTED))
                .lore(UiTheme.line("Sounds, scoreboard, privacy."),
                        UiTheme.hint("Configure"))
                .action("settings").build());

        inventory.setItem(GuiSlots.slot(4, 3), ItemBuilder.of(Material.NAME_TAG)
                .name(Component.text("Kill Titles", UiTheme.SECONDARY))
                .lore(UiTheme.line("Equip kill/win title effects."),
                        UiTheme.hint("Browse"))
                .action("titles").build());

        inventory.setItem(GuiSlots.slot(4, 5), ItemBuilder.of(Material.WHITE_BANNER)
                .name(Component.text("Party", UiTheme.PRIMARY))
                .lore(UiTheme.line("Create or join a party and fight"),
                        UiTheme.line("RED vs BLUE — up to 15 per side."),
                        UiTheme.hint("Open parties"))
                .action("teams").build());

        MenuScaffold.closeButton(inventory);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        switch (action) {
            case "close" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "battle" -> openChild(player, battleMenuGui::open);
            case "ekit" -> openChild(player, ekitSelectGui::open);
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
