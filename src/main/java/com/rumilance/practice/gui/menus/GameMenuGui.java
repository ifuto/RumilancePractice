package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Fast-access "Game Menu" opened by right-clicking the lobby compass. Lays out the six most
 * common lobby actions (ranked, unranked, FFA, kit editor, spectate, settings) in a single
 * 3x3 cluster so players can jump anywhere in one click. Each button has a clear label,
 * a short description and a click hint; the close button sits on the bottom bar.
 */
public final class GameMenuGui extends AbstractGui {

    private final QueueKitGui rankedGui;
    private final QueueKitGui unrankedGui;
    private final FfaListGui ffaListGui;
    private final EkitSelectGui ekitSelectGui;
    private final SpectateListGui spectateListGui;
    private final SettingsGui settingsGui;
    private final TitleGui titleGui;
    /** Opens the team hub/browser — wired via setter because the team GUIs are built later. */
    private java.util.function.Consumer<Player> openTeams = p -> { };

    public GameMenuGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            QueueKitGui rankedGui,
            QueueKitGui unrankedGui,
            FfaListGui ffaListGui,
            EkitSelectGui ekitSelectGui,
            SpectateListGui spectateListGui,
            SettingsGui settingsGui,
            TitleGui titleGui
    ) {
        super(registry, sounds, GuiType.GAME_MENU, 6, true);
        this.rankedGui = rankedGui;
        this.unrankedGui = unrankedGui;
        this.ffaListGui = ffaListGui;
        this.ekitSelectGui = ekitSelectGui;
        this.spectateListGui = spectateListGui;
        this.settingsGui = settingsGui;
        this.titleGui = titleGui;
    }

    public void setOpenTeams(java.util.function.Consumer<Player> openTeams) {
        this.openTeams = openTeams == null ? p -> { } : openTeams;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("✦ Game Menu", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        // Central 3x3 cluster on rows 2-3.
        inventory.setItem(GuiSlots.slot(2, 2), ItemBuilder.of(Material.IRON_SWORD)
                .name(Component.text("Ranked Duels", UiTheme.SUCCESS))
                .lore(UiTheme.divider(), UiTheme.line("Climb the Elo ladder."),
                        UiTheme.blank(), UiTheme.hint("Click to queue ranked"))
                .action("ranked").build());

        inventory.setItem(GuiSlots.slot(2, 4), ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(Component.text("Unranked Duels", UiTheme.SECONDARY))
                .lore(UiTheme.divider(), UiTheme.line("Casual matches, no Elo change."),
                        UiTheme.blank(), UiTheme.hint("Click to queue unranked"))
                .action("unranked").build());

        inventory.setItem(GuiSlots.slot(2, 6), ItemBuilder.of(Material.GOLDEN_AXE)
                .name(Component.text("FFA Arenas", UiTheme.DANGER))
                .lore(UiTheme.divider(), UiTheme.line("Free-for-all combat zones."),
                        UiTheme.blank(), UiTheme.hint("Click to browse FFA"))
                .action("ffa").build());

        inventory.setItem(GuiSlots.slot(3, 2), ItemBuilder.of(Material.CRAFTING_TABLE)
                .name(Component.text("Kit Editor", UiTheme.PRIMARY))
                .lore(UiTheme.divider(), UiTheme.line("Edit official & original kits."),
                        UiTheme.blank(), UiTheme.hint("Click to open /ekit"))
                .action("ekit").build());

        inventory.setItem(GuiSlots.slot(3, 4), ItemBuilder.of(Material.ENDER_EYE)
                .name(Component.text("Spectate", UiTheme.WARNING))
                .lore(UiTheme.divider(), UiTheme.line("Watch live matches."),
                        UiTheme.blank(), UiTheme.hint("Click to browse"))
                .action("spectate").build());

        inventory.setItem(GuiSlots.slot(3, 6), ItemBuilder.of(Material.COMPARATOR)
                .name(Component.text("Settings", UiTheme.MUTED))
                .lore(UiTheme.divider(), UiTheme.line("Sounds, scoreboard, privacy."),
                        UiTheme.blank(), UiTheme.hint("Click to configure"))
                .action("settings").build());

        // Bottom cluster: teams + titles on row 4.
        inventory.setItem(GuiSlots.slot(4, 3), ItemBuilder.of(Material.NAME_TAG)
                .name(Component.text("Kill Titles", UiTheme.SECONDARY))
                .lore(UiTheme.divider(), UiTheme.line("Equip kill/win title effects."),
                        UiTheme.blank(), UiTheme.hint("Click to browse"))
                .action("titles").build());

        inventory.setItem(GuiSlots.slot(4, 5), ItemBuilder.of(Material.WHITE_BANNER)
                .name(Component.text("Team Battles", UiTheme.PRIMARY))
                .lore(UiTheme.divider(), UiTheme.line("Create or join a team and fight"),
                        UiTheme.line("RED vs BLUE — up to 15 per side."),
                        UiTheme.blank(), UiTheme.hint("Click to open teams"))
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
            case "ranked" -> openChild(player, rankedGui::open);
            case "unranked" -> openChild(player, unrankedGui::open);
            case "ffa" -> openChild(player, ffaListGui::open);
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
