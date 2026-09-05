package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Combat entry: Ranked / Unranked / Player Duel / FFA. Every tile is state-aware: while the
 * player is queued the matching queue tile turns into a "leave queue" button, while they sit
 * in a party the solo modes are visibly locked, and while fighting/spectating/in FFA all
 * entries dim with the exact reason. Live waiting counts are shown on each tile.
 */
public final class BattleMenuGui extends AbstractGui {

    private final QueueKitGui rankedGui;
    private final QueueKitGui unrankedGui;
    private final PlayersGui playersGui;
    private final FfaListGui ffaListGui;
    private final MessageService messageService;
    private QueueService queueService;
    private QueueCoordinator queueCoordinator;
    private PlayerStateManager stateManager;
    private TeamService teamService;
    private java.util.function.IntSupplier ffaOccupants = () -> 0;
    private MatchHistoryGui matchHistoryGui;

    public BattleMenuGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            QueueKitGui rankedGui,
            QueueKitGui unrankedGui,
            PlayersGui playersGui,
            FfaListGui ffaListGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.BATTLE_MENU, 6, true);
        this.rankedGui = rankedGui;
        this.unrankedGui = unrankedGui;
        this.playersGui = playersGui;
        this.ffaListGui = ffaListGui;
        this.messageService = messageService;
    }

    public void setQueueServices(QueueService queueService, QueueCoordinator queueCoordinator) {
        this.queueService = queueService;
        this.queueCoordinator = queueCoordinator;
    }

    public void setStateManager(PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setTeamService(TeamService teamService) {
        this.teamService = teamService;
    }

    /** Live FFA occupant count, wired from bootstrap so this menu stays service-agnostic. */
    public void setFfaOccupants(java.util.function.IntSupplier ffaOccupants) {
        this.ffaOccupants = ffaOccupants == null ? () -> 0 : ffaOccupants;
    }

    /** Battle-menu match history (recent results, scores, end inventories). */
    public void setMatchHistoryGui(MatchHistoryGui matchHistoryGui) {
        this.matchHistoryGui = matchHistoryGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return text(player, "menu.battle-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));
        paintStatusChip(player, inventory);

        PlayerState state = stateOf(player);
        boolean inParty = teamService != null && teamService.teamOf(player.getUniqueId()).isPresent();
        QueueService.QueueEntry queueEntry = queueService == null
                ? null : queueService.get(player.getUniqueId()).orElse(null);

        // Row 1 — the three competitive entries, one clean row.
        inventory.setItem(GuiSlots.slot(1, 1), queueTile(player, Material.DIAMOND_SWORD,
                "menu.ranked", UiTheme.PRIMARY, "menu.ranked-lore", "ranked", true,
                MatchMode.RANKED, state, inParty, queueEntry));
        inventory.setItem(GuiSlots.slot(1, 4), queueTile(player, Material.GOLDEN_SWORD,
                "menu.unranked", UiTheme.VALUE, "menu.unranked-lore", "unranked", false,
                MatchMode.UNRANKED, state, inParty, queueEntry));
        inventory.setItem(GuiSlots.slot(1, 7), duelTile(player, state, inParty));

        // Row 2 — FFA centred, match history to its right.
        inventory.setItem(GuiSlots.slot(2, 4), ffaTile(player, state));
        if (matchHistoryGui != null) {
            inventory.setItem(GuiSlots.slot(2, 6), historyTile(player, state));
        }

        paintNav(player, session, inventory);
    }

    /** Top-left chip: the viewer's own head and their current activity state. */
    private void paintStatusChip(Player player, Inventory inventory) {
        String stateKey = switch (stateOf(player)) {
            case LOBBY, OPENING_GUI, IDLE -> "menu.state-lobby";
            case QUEUED_RANKED -> "menu.state-ranked-queue";
            case QUEUED_UNRANKED -> "menu.state-unranked-queue";
            case FIGHTING, PREPARING_MATCH, COUNTDOWN, ENDING -> "menu.state-fighting";
            case SPECTATING -> "menu.state-spectating";
            case FFA -> "menu.state-ffa";
            case EDITING_KIT -> "menu.state-editing";
            case REQUESTING_DUEL -> "menu.state-dueling";
            case PRACTICE_WAIT, PRACTICE_ACTIVE -> "menu.state-fighting";
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

    private PlayerState stateOf(Player player) {
        return stateManager == null
                ? PlayerState.LOBBY
                : stateManager.getState(player.getUniqueId());
    }

    /** Locked (busy with something else) / party-locked / ready variants share this shell. */
    private ItemStack mode(Player player, Material material, String nameKey, TextColor color,
                           String loreKey, String action, boolean glint, PlayerState state,
                           boolean inParty, boolean partyLocked, String liveLine) {
        boolean busyLocked = isBusy(state) && action != null && !action.startsWith("leave-queue");
        boolean locked = busyLocked || (partyLocked && inParty);
        ItemBuilder builder = ItemBuilder.of(material)
                .name(text(player, nameKey).color(locked ? UiTheme.MUTED : color))
                .glint(glint && !locked)
                .action(locked ? "locked:" + action : action);
        builder.lore(UiTheme.line(raw(player, loreKey)));
        if (liveLine != null && !locked) {
            builder.lore(UiTheme.blank(), UiTheme.status(liveLine, UiTheme.SECONDARY));
        }
        if (busyLocked) {
            builder.lore(UiTheme.blank(),
                    UiTheme.status(raw(player, "menu.battle-locked")
                            .replace("<state>", raw(player, stateKey(state))), UiTheme.WARNING),
                    UiTheme.line(raw(player, "menu.battle-locked-hint")));
        } else if (partyLocked && inParty) {
            builder.lore(UiTheme.blank(), UiTheme.status(raw(player, "menu.party-only"), UiTheme.WARNING));
        } else {
            builder.lore(UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")));
        }
        return builder.build();
    }

    private ItemStack queueTile(Player player, Material material, String nameKey, TextColor color,
                                String loreKey, String action, boolean glint, MatchMode mode,
                                PlayerState state, boolean inParty, QueueService.QueueEntry entry) {
        boolean queuedHere = entry != null && entry.mode() == mode;
        if (queuedHere) {
            // The tile flips into a leave control — one click gets the player out.
            return ItemBuilder.of(Material.RED_DYE)
                    .name(text(player, "menu.leave-queue").color(UiTheme.DANGER))
                    .lore(UiTheme.divider(),
                            UiTheme.labelValue(raw(player, "menu.in-queue-kit"), entry.kitId()),
                            UiTheme.blank(),
                            UiTheme.hint(raw(player, "menu.leave-queue-hint")))
                    .action("leave-queue")
                    .build();
        }
        String live = queueService == null ? null
                : raw(player, "menu.battle-waiting").replace("<n>",
                        String.valueOf(totalWaiting(player, mode)));
        return mode(player, material, nameKey, color, loreKey, action, glint, state, inParty, true, live);
    }

    private ItemStack duelTile(Player player, PlayerState state, boolean inParty) {
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
        String live = raw(player, "menu.battle-online").replace("<n>", String.valueOf(online));
        return mode(player, Material.PLAYER_HEAD, "menu.player-duel", UiTheme.SECONDARY,
                "menu.player-duel-lore", "player-duel", false, state, inParty, true, live);
    }

    private ItemStack ffaTile(Player player, PlayerState state) {
        String live = raw(player, "menu.battle-ffa-now").replace("<n>", String.valueOf(ffaOccupants.getAsInt()));
        // FFA is never party-locked: it's the one combat mode a whole party can enter freely.
        return mode(player, Material.END_CRYSTAL, "menu.ffa", UiTheme.WARNING,
                "menu.ffa-lore", "ffa", false, state, false, false, live);
    }

    /** Review-only tile: never busy/party locked, so results stay reachable right after a fight. */
    private ItemStack historyTile(Player player, PlayerState state) {
        return ItemBuilder.of(Material.BOOK)
                .name(text(player, "menu.history").color(UiTheme.SECONDARY))
                .lore(UiTheme.line(raw(player, "menu.history-lore")),
                        UiTheme.blank(), UiTheme.hint(raw(player, "menu.click")))
                .action("history")
                .build();
    }

    private int totalWaiting(Player player, MatchMode mode) {
        if (queueService == null) {
            return 0;
        }
        PlayerPlatform platform = PlayerPlatform.of(player);
        return queueService.totalWaiting(mode, platform);
    }

    private static boolean isBusy(PlayerState state) {
        return switch (state) {
            case QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL, PREPARING_MATCH, COUNTDOWN,
                 FIGHTING, ENDING, SPECTATING, FFA, PRACTICE_WAIT, PRACTICE_ACTIVE -> true;
            default -> false;
        };
    }

    private static String stateKey(PlayerState state) {
        return switch (state) {
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
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "leave-queue" -> {
                if (queueCoordinator != null) {
                    sounds.play(player, "gui-click");
                    queueCoordinator.leave(player);
                    refresh(player, session, inventory);
                }
            }
            case "ranked" -> openChild(player, rankedGui::open);
            case "unranked" -> openChild(player, unrankedGui::open);
            case "player-duel" -> openChild(player, playersGui::open);
            case "ffa" -> openChild(player, ffaListGui::open);
            case "history" -> {
                if (matchHistoryGui != null) {
                    openChild(player, matchHistoryGui::open);
                }
            }
            default -> {
                if (action.startsWith("locked:")) {
                    sounds.play(player, "error");
                    boolean inParty = teamService != null
                            && teamService.teamOf(player.getUniqueId()).isPresent();
                    PlayerState state = stateOf(player);
                    if (isBusy(state)) {
                        player.sendMessage(Component.text(
                                raw(player, "menu.battle-locked")
                                        .replace("<state>", raw(player, stateKey(state))),
                                UiTheme.WARNING));
                    } else if (inParty) {
                        player.sendMessage(Component.text(raw(player, "menu.party-only"), UiTheme.WARNING));
                    }
                }
            }
        }
    }

    private void openChild(Player player, java.util.function.Consumer<Player> opener) {
        sounds.play(player, "gui-click");
        opener.accept(player);
        registry.get(player.getUniqueId()).ifPresent(child -> child.setFromBattleMenu(true));
    }

    private Component text(Player player, String key) {
        return messageService.render(messageService.resolveLocale(player), key);
    }

    private String raw(Player player, String key) {
        return messageService.localeService().rawMessage(messageService.resolveLocale(player), key);
    }
}
