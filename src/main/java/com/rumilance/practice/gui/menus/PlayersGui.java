package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.ItemKeys;
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
 * Online-player picker used to send a duel request. Heads are paged 28 per page; each head
 * shows the target's state, ping, and ranked win-rate, with a click hint. Players currently in
 * a fight/countdown are rendered dimmed and their request is refused by the duel service
 * regardless, but the visual state sets expectations.
 */
public final class PlayersGui extends AbstractGui {

    private final PlayerStateManager stateManager;
    private final StatsService statsService;
    private final DuelRequestGui duelRequestGui;

    public PlayersGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            PlayerStateManager stateManager,
            StatsService statsService,
            DuelRequestGui duelRequestGui
    ) {
        super(registry, sounds, GuiType.PLAYERS, 6, true);
        this.stateManager = stateManager;
        this.statsService = statsService;
        this.duelRequestGui = duelRequestGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("☺ Online Players", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));

        int page = session.page();
        int perPage = MenuScaffold.gridPageSize();
        int start = page * perPage;
        int end = Math.min(start + perPage, online.size());

        int index = 0;
        for (int i = start; i < end; i++) {
            inventory.setItem(MenuScaffold.gridSlot(index++), playerHead(online.get(i)));
        }

        if (online.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No other players online", UiTheme.MUTED))
                            .lore(UiTheme.line("Wait for someone to join."))
                            .action("decorate")
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, online.size());
        MenuScaffold.closeButton(inventory);
    }

    private ItemStack playerHead(Player target) {
        PlayerState state = stateManager.getState(target.getUniqueId());
        boolean busy = state == PlayerState.FIGHTING || state == PlayerState.COUNTDOWN
                || state == PlayerState.PREPARING_MATCH;

        String winRate;
        try {
            var kits = statsService.allKits(target.getUniqueId());
            int wins = kits.stream().mapToInt(s -> s.wins()).sum();
            int matches = kits.stream().mapToInt(s -> s.gamesPlayed()).sum();
            winRate = matches < 21
                    ? "計測中 " + matches + "/21"
                    : String.format("%.1f%%", matches == 0 ? 0 : 100.0 * wins / matches);
        } catch (Exception e) {
            winRate = "-";
        }

        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(target.getName(), busy ? UiTheme.MUTED : UiTheme.SECONDARY))
                .skullOwner(target)
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("State", prettyState(state)),
                        UiTheme.labelValue("Ping", target.getPing() + "ms"),
                        UiTheme.labelValue("Win rate", winRate),
                        UiTheme.blank(),
                        busy
                                ? UiTheme.status("BUSY", UiTheme.WARNING)
                                : UiTheme.hint("Click to duel")
                )
                .glint(!busy)
                .action("player:" + target.getUniqueId())
                .tag(ItemKeys.targetUuid(), target.getUniqueId().toString())
                .build();
    }

    private static String prettyState(PlayerState state) {
        return switch (state) {
            case LOBBY -> "In Lobby";
            case QUEUED_RANKED -> "In Ranked Queue";
            case QUEUED_UNRANKED -> "In Unranked Queue";
            case FIGHTING -> "Fighting";
            case COUNTDOWN -> "Countdown";
            case PREPARING_MATCH -> "Preparing";
            case SPECTATING -> "Spectating";
            case FFA -> "In FFA";
            case EDITING_KIT -> "Editing Kit";
            case OPENING_GUI -> "In Menu";
            case REQUESTING_DUEL -> "Requesting Duel";
            case ENDING -> "Match Ending";
            case IDLE -> "Idle";
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
        if (action.startsWith("player:")) {
            UUID target = UUID.fromString(action.substring(7));
            Player targetPlayer = Bukkit.getPlayer(target);
            if (targetPlayer == null) {
                sounds.play(player, "error");
                return;
            }
            sounds.play(player, "select");
            player.closeInventory();
            duelRequestGui.openFor(player, targetPlayer, true);
        }
    }
}
