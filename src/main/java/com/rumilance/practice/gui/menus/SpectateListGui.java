package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lists every active duel/FFA match and lets the player spectate one. Each entry shows both
 * participants, the kit, mode, current series score and elapsed time.
 */
public final class SpectateListGui extends AbstractGui {

    private final MatchRegistry matchRegistry;
    private final SpectatorService spectatorService;
    private final com.rumilance.practice.ffa.FfaService ffaService;

    public SpectateListGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            MatchRegistry matchRegistry,
            SpectatorService spectatorService
    ) {
        this(registry, sounds, matchRegistry, spectatorService, null);
    }

    public SpectateListGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            MatchRegistry matchRegistry,
            SpectatorService spectatorService,
            com.rumilance.practice.ffa.FfaService ffaService
    ) {
        super(registry, sounds, GuiType.SPECTATE_LIST, 6, true);
        this.matchRegistry = matchRegistry;
        this.spectatorService = spectatorService;
        this.ffaService = ffaService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("👁 Spectate", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<MatchSession> active = new ArrayList<>();
        for (MatchSession match : matchRegistry.all()) {
            if (match.state() == MatchState.ACTIVE) {
                active.add(match);
            }
        }

        int page = session.page();
        int perPage = MenuScaffold.gridPageSize();
        int start = page * perPage;
        int index = 0;
        for (int i = start; i < active.size() && index < perPage; i++, index++) {
            inventory.setItem(MenuScaffold.gridSlot(index), matchIcon(active.get(i)));
        }

        if (active.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No active matches", UiTheme.MUTED))
                            .lore(UiTheme.line("Check back once a match starts."))
                            .action("decorate")
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, active.size());
        MenuScaffold.closeButton(inventory);
    }

    private ItemStack matchIcon(MatchSession match) {
        UUID a = match.participants().get(0);
        UUID b = match.participants().size() > 1 ? match.participants().get(1) : a;

        String elapsed = "live";
        if (match.startedAt() != null) {
            long seconds = Duration.between(match.startedAt(), Instant.now()).getSeconds();
            elapsed = String.format("%d:%02d", seconds / 60, seconds % 60);
        }

        int winsA = match.seriesWinsOf(a);
        int winsB = match.seriesWinsOf(b);

        return ItemBuilder.of(Material.IRON_SWORD)
                .name(Component.text(StatsService.nameOf(a) + " §7vs " + StatsService.nameOf(b)))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("Kit", match.kitName()),
                        UiTheme.labelValue("Mode", modeWord(match.mode().name())),
                        UiTheme.labelValue("Score", winsA + " - " + winsB),
                        UiTheme.labelValue("Time", elapsed),
                        UiTheme.blank(),
                        UiTheme.status("LIVE", UiTheme.SUCCESS),
                        UiTheme.hint("Click to spectate")
                )
                .glint(true)
                .action("spec:" + a)
                .tag(ItemKeys.targetUuid(), a.toString())
                .build();
    }

    private static String modeWord(String mode) {
        return switch (mode) {
            case "RANKED" -> "Ranked";
            case "UNRANKED" -> "Unranked";
            case "FFA" -> "FFA";
            default -> mode;
        };
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
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
        if (action.startsWith("spec:")) {
            Player target = Bukkit.getPlayer(UUID.fromString(action.substring(5)));
            sounds.play(player, "select");
            player.closeInventory();
            if (target != null) {
                spectatorService.trySpectate(player, target);
            }
        }
    }
}
