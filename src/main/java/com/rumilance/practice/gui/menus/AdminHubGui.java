package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiFrame;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * The {@code /admin} home screen — the superset of {@code /practiceadmin menu}.
 *
 * <p>Seven sections, spaced a column apart (row 2 for the four busiest, row 4 for the rest) so
 * the tiles read as separate panels rather than a strip of icons. Every destination is injected
 * as a {@link Consumer}, so the hub itself knows nothing about the screens behind it and stays
 * usable while sections are still being built.</p>
 */
public final class AdminHubGui extends AbstractGui {

    private Consumer<Player> openArenaFfa = NOOP;
    private Consumer<Player> openPlayerData = NOOP;
    private Consumer<Player> openPunishment = NOOP;
    private Consumer<Player> openCheatReports = NOOP;
    private Consumer<Player> openMatchManagement = NOOP;
    private Consumer<Player> openStats = NOOP;
    private Consumer<Player> openServerSettings = NOOP;

    private static final Consumer<Player> NOOP = player -> { };

    public AdminHubGui(GuiSessionRegistry registry, SoundService sounds) {
        super(registry, sounds, GuiType.ADMIN_HUB, 6, false);
    }

    public void setOpenArenaFfa(Consumer<Player> opener) {
        this.openArenaFfa = orNoop(opener);
    }

    public void setOpenPlayerData(Consumer<Player> opener) {
        this.openPlayerData = orNoop(opener);
    }

    public void setOpenPunishment(Consumer<Player> opener) {
        this.openPunishment = orNoop(opener);
    }

    public void setOpenCheatReports(Consumer<Player> opener) {
        this.openCheatReports = orNoop(opener);
    }

    public void setOpenMatchManagement(Consumer<Player> opener) {
        this.openMatchManagement = orNoop(opener);
    }

    public void setOpenStats(Consumer<Player> opener) {
        this.openStats = orNoop(opener);
    }

    public void setOpenServerSettings(Consumer<Player> opener) {
        this.openServerSettings = orNoop(opener);
    }

    @Override
    protected GuiFrame.Theme theme() {
        return GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.COMMAND_BLOCK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin").color(NamedTextColor.AQUA);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        // Row 2 — the four sections an admin reaches for constantly.
        inventory.setItem(GuiSlots.slot(2, 1), tile(Material.GRASS_BLOCK, "Arena / FFA",
                "Arenas, arena copies, FFA arenas and their settings.", "arena_ffa"));
        inventory.setItem(GuiSlots.slot(2, 3), tile(Material.PLAYER_HEAD, "Player Data",
                "Look up a player and manage everything stored on them.", "player_data"));
        inventory.setItem(GuiSlots.slot(2, 5), tile(Material.IRON_BARS, "Punishment",
                "Bans, mutes, chat bans and the ban list.", "punishment"));
        inventory.setItem(GuiSlots.slot(2, 7), tile(Material.SPYGLASS, "Cheat / Alt / Reports",
                "Alt detection, cheat flags and player reports.", "cheat_reports"));
        // Row 4 — the rest, inset by one column so the two rows do not line up rigidly.
        inventory.setItem(GuiSlots.slot(4, 2), tile(Material.WRITABLE_BOOK, "Match Management",
                "Live matches, force-end and spectating.", "matches"));
        inventory.setItem(GuiSlots.slot(4, 4), tile(Material.BOOKSHELF, "Statistics",
                "Leaderboards, per-kit stats and match history.", "stats"));
        inventory.setItem(GuiSlots.slot(4, 6), tile(Material.LEVER, "Server Settings",
                "Server-wide toggles and defaults.", "settings"));
        paintNav(player, session, inventory);
    }

    private ItemStack tile(Material material, String name, String lore, String action) {
        return ItemBuilder.of(material)
                .name(Component.text(name, UiTheme.HEADER))
                .lore(UiTheme.divider(),
                        UiTheme.line(Component.text(lore, UiTheme.MUTED)),
                        UiTheme.blank(),
                        UiTheme.hint(Component.text("Click to open", UiTheme.MUTED)))
                .action(action)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (action == null || "decorate".equals(action) || "close".equals(action)) {
            return;
        }
        Consumer<Player> target = switch (action) {
            case "arena_ffa" -> openArenaFfa;
            case "player_data" -> openPlayerData;
            case "punishment" -> openPunishment;
            case "cheat_reports" -> openCheatReports;
            case "matches" -> openMatchManagement;
            case "stats" -> openStats;
            case "settings" -> openServerSettings;
            default -> null;
        };
        if (target == null) {
            return;
        }
        sounds.play(player, "gui-click");
        player.closeInventory();
        target.accept(player);
    }

    private static Consumer<Player> orNoop(Consumer<Player> opener) {
        return opener == null ? NOOP : opener;
    }
}
