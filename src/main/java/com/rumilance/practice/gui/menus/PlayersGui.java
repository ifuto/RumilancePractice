package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
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
    private final MessageService messageService;

    public PlayersGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            PlayerStateManager stateManager,
            StatsService statsService,
            DuelRequestGui duelRequestGui
    ) {
        this(registry, sounds, stateManager, statsService, duelRequestGui, null);
    }

    public PlayersGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            PlayerStateManager stateManager,
            StatsService statsService,
            DuelRequestGui duelRequestGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.PLAYERS, 6, true);
        this.stateManager = stateManager;
        this.statsService = statsService;
        this.duelRequestGui = duelRequestGui;
        this.messageService = messageService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.players-title").color(UiTheme.PRIMARY);
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
            inventory.setItem(MenuScaffold.gridSlot(index++), playerHead(player, online.get(i)));
        }

        if (online.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.players-empty").color(UiTheme.MUTED))
                            .lore(UiTheme.line(line(player, "gui.players-empty-lore")))
                            .action("decorate")
                            .build());
        }

        paintPaging(player, inventory, page, online.size());
        paintNav(player, session, inventory);
    }

    private ItemStack playerHead(Player viewer, Player target) {
        PlayerState state = stateManager.getState(target.getUniqueId());
        boolean busy = state == PlayerState.FIGHTING || state == PlayerState.COUNTDOWN
                || state == PlayerState.PREPARING_MATCH;

        String winRate;
        try {
            var kits = statsService.allKits(target.getUniqueId());
            int wins = kits.stream().mapToInt(s -> s.wins()).sum();
            int matches = kits.stream().mapToInt(s -> s.gamesPlayed()).sum();
            winRate = matches < 21
                    ? line(viewer, "gui.players-calibrating").replace("<matches>", String.valueOf(matches))
                    : String.format("%.1f%%", matches == 0 ? 0 : 100.0 * wins / matches);
        } catch (Exception e) {
            winRate = "-";
        }

        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(target.getName(), busy ? UiTheme.MUTED : UiTheme.SECONDARY))
                .skullOwner(target)
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.players-state"), prettyState(viewer, state)),
                        UiTheme.labelValue("Ping", target.getPing() + "ms"),
                        UiTheme.labelValue(line(viewer, "gui.players-wr"), winRate),
                        UiTheme.blank(),
                        busy
                                ? UiTheme.status(line(viewer, "gui.players-busy"), UiTheme.WARNING)
                                : UiTheme.hint(line(viewer, "gui.players-duel-hint"))
                )
                .glint(!busy)
                .action("player:" + target.getUniqueId())
                .tag(ItemKeys.targetUuid(), target.getUniqueId().toString())
                .build();
    }

    private String prettyState(Player viewer, PlayerState state) {
        String key = switch (state) {
            case LOBBY -> "menu.state-lobby";
            case QUEUED_RANKED -> "menu.state-ranked-queue";
            case QUEUED_UNRANKED -> "menu.state-unranked-queue";
            case FIGHTING -> "menu.state-fighting";
            case COUNTDOWN -> "menu.state-countdown";
            case PREPARING_MATCH -> "menu.state-preparing";
            case SPECTATING -> "menu.state-spectating";
            case FFA -> "menu.state-ffa";
            case EDITING_KIT -> "menu.state-editing";
            case OPENING_GUI -> "menu.state-menu";
            case REQUESTING_DUEL -> "menu.state-dueling";
            case ENDING -> "menu.state-ending";
            case IDLE -> "menu.state-idle";
            case PRACTICE_WAIT -> "gui.practice-wait";
            case PRACTICE_ACTIVE -> "gui.practice-active";
        };
        return line(viewer, key);
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
