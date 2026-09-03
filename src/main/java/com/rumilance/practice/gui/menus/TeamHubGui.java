package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The main team control panel (fully GUI-driven). The owner gets invite / visibility /
 * side-assignment / auto-split / start controls; members get a "leave team" panel. Every
 * member is listed in the standard 28-slot content grid with a side-coloured icon:
 * <ul>
 *   <li>Left-click a member — assign RED</li>
 *   <li>Right-click a member — assign BLUE</li>
 *   <li>Shift-click a member — kick (owner only)</li>
 * </ul>
 * Sides may be arbitrarily uneven (max 15 per side); paging kicks in past 28 members.
 */
public final class TeamHubGui extends AbstractGui {

    private static final TextColor BLUE = TextColor.color(0x55AAFF);

    private final TeamService teamService;
    private final TeamsBrowserGui browser;
    private final TeamKitSelectGui kitSelect;
    private final com.rumilance.practice.locale.MessageService messageService;
    private PartyInviteGui partyInviteGui;
    private PartyMapSelectGui partyMapSelectGui;
    private ArenaTemplateStoreSupplier arenaStoreSupplier;
    private ConfirmGui confirmGui;
    private com.rumilance.practice.session.PlayerStateManager stateManager;

    public void setConfirmGui(ConfirmGui confirmGui) {
        this.confirmGui = confirmGui;
    }

    public void setStateManager(com.rumilance.practice.session.PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    @FunctionalInterface
    public interface ArenaTemplateStoreSupplier {
        java.util.List<com.rumilance.practice.model.ArenaTemplate> partyArenas();
    }

    public TeamHubGui(GuiSessionRegistry registry, SoundService sounds,
                      TeamService teamService, TeamsBrowserGui browser, TeamKitSelectGui kitSelect) {
        this(registry, sounds, teamService, browser, kitSelect, null);
    }

    public TeamHubGui(GuiSessionRegistry registry, SoundService sounds,
                      TeamService teamService, TeamsBrowserGui browser, TeamKitSelectGui kitSelect,
                      com.rumilance.practice.locale.MessageService messageService) {
        super(registry, sounds, GuiType.TEAM_HUB, 6, true);
        this.teamService = teamService;
        this.browser = browser;
        this.kitSelect = kitSelect;
        this.messageService = messageService;
    }

    public void setPartyInviteGui(PartyInviteGui partyInviteGui) {
        this.partyInviteGui = partyInviteGui;
    }

    public void setPartyMapSelectGui(PartyMapSelectGui partyMapSelectGui) {
        this.partyMapSelectGui = partyMapSelectGui;
    }

    public void setArenaStoreSupplier(ArenaTemplateStoreSupplier arenaStoreSupplier) {
        this.arenaStoreSupplier = arenaStoreSupplier;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "party.hub-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null) {
            // Player left / was kicked while the menu was open: show a friendly redirect tile.
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.COMPASS)
                            .name(t(player, "party.not-in-team").color(UiTheme.WARNING))
                            .lore(UiTheme.hint(line(player, "party.not-in-team-lore")))
                            .action("open_browser").build());
            MenuScaffold.closeButton(inventory, t(player, "menu.close"));
            return;
        }
        boolean owner = team.isOwner(player.getUniqueId());

        // --- top bar: team info + owner controls ---
        inventory.setItem(GuiSlots.slot(0, 4), headerItem(player, team));
        if (owner) {
            inventory.setItem(GuiSlots.slot(0, 1),
                    ItemBuilder.of(Material.PLAYER_HEAD)
                            .name(t(player, "party.invite").color(UiTheme.SUCCESS))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "party.invite-lore-heads")),
                                    UiTheme.line(line(player, "party.invite-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "party.invite-hint")))
                            .action("invite").build());
            inventory.setItem(GuiSlots.slot(0, 2),
                    ItemBuilder.of(team.isPublic() ? UiTheme.TOGGLE_ON : UiTheme.TOGGLE_OFF)
                            .name(t(player, team.isPublic() ? "party.public-team" : "party.private-team")
                                    .color(team.isPublic() ? UiTheme.SUCCESS : UiTheme.MUTED))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(team.isPublic()
                                            ? line(player, "party.public-lore")
                                            : line(player, "party.private-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "party.toggle-hint")))
                            .action("toggle_public").build());
            if (arenaStoreSupplier != null && !arenaStoreSupplier.partyArenas().isEmpty()) {
                inventory.setItem(GuiSlots.slot(0, 3),
                        ItemBuilder.of(Material.MAP)
                                .name(t(player, "party.select-map").color(UiTheme.PRIMARY))
                                .lore(UiTheme.divider(),
                                        UiTheme.labelValue(line(player, "party.map-label"), team.selectedArena() == null
                                                ? line(player, "party.random")
                                                : com.rumilance.practice.util.NameDisplay.pretty(team.selectedArena())),
                                        UiTheme.blank(),
                                        UiTheme.hint(line(player, "party.select-map-hint")))
                                .action("select_map").build());
            }
            inventory.setItem(GuiSlots.slot(0, 6),
                    ItemBuilder.of(team.friendlyFire() ? Material.TNT : Material.SHIELD)
                            .name(t(player, team.friendlyFire() ? "party.ff-on-label" : "party.ff-off-label")
                                    .color(team.friendlyFire() ? UiTheme.DANGER : UiTheme.SUCCESS))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(team.friendlyFire()
                                            ? line(player, "gui.party-ff-on")
                                            : line(player, "gui.party-ff-off")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.toggle-hint")))
                            .action("toggle_ff").build());
            inventory.setItem(GuiSlots.slot(0, 7),
                    ItemBuilder.of(Material.TNT)
                            .name(t(player, "party.disband").color(UiTheme.DANGER))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "party.disband-confirm-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "party.disband-hint")))
                            .action("disband").build());
        } else {
            inventory.setItem(GuiSlots.slot(0, 7),
                    ItemBuilder.of(Material.OAK_DOOR)
                            .name(t(player, "party.leave").color(UiTheme.WARNING))
                            .lore(UiTheme.hint(line(player, "party.leave-hint")))
                            .action("leave").build());
        }

        // --- member grid (rows 1-4, cols 1-7 = 28 slots, paged past 28 members) ---
        List<UUID> members = new ArrayList<>(team.members());
        int page = session.page();
        int pageSize = MenuScaffold.gridPageSize();
        int from = Math.min(page * pageSize, members.size());
        int to = Math.min(from + pageSize, members.size());
        int index = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(MenuScaffold.gridSlot(index++), memberItem(player, team, members.get(i), owner));
        }
        paintPaging(player, inventory, page, members.size());

        // --- bottom bar: side counters + owner controls ---
        int redCount = team.side(TeamColor.RED).size();
        int blueCount = team.side(TeamColor.BLUE).size();
        int unassigned = team.size() - redCount - blueCount;
        inventory.setItem(GuiSlots.slot(5, 0),
                ItemBuilder.of(Material.RED_WOOL, Math.max(1, redCount))
                        .name(Component.text(line(player, "party.red-count")
                                .replace("<n>", String.valueOf(redCount)), UiTheme.DANGER))
                        .lore(UiTheme.line(line(player, "party.red-hint")))
                        .action("decorate").build());
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.of(Material.BLUE_WOOL, Math.max(1, blueCount))
                        .name(Component.text(line(player, "party.blue-count")
                                .replace("<n>", String.valueOf(blueCount)), BLUE))
                        .lore(UiTheme.line(line(player, "party.blue-hint")))
                        .action("decorate").build());

        if (owner) {
            inventory.setItem(GuiSlots.slot(5, 1),
                    ItemBuilder.of(Material.ENDER_PEARL)
                            .name(t(player, "party.autosplit").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "party.autosplit-lore-1")),
                                    UiTheme.line(line(player, "party.autosplit-lore-2")),
                                    UiTheme.line(line(player, "party.autosplit-lore-3")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "party.autosplit-hint")))
                            .action("autosplit").build());
            inventory.setItem(GuiSlots.slot(5, 3),
                    ItemBuilder.of(Material.WET_SPONGE)
                            .name(t(player, "party.clear-sides").color(UiTheme.WARNING))
                            .lore(UiTheme.hint(line(player, "party.clear-sides-hint")))
                            .action("clearsides").build());
            // Start button shows the FULL readiness picture: sides assigned AND every member
            // free in the lobby (not queued / in FFA / spectating / fighting). Whatever is
            // missing is named in the lore so the owner knows exactly what to fix.
            TeamService.Result precheck = teamService.preflightStart(player);
            boolean ready = precheck == TeamService.Result.OK;
            Component blockedReason;
            if (ready) {
                blockedReason = null;
            } else if (!team.isSplitReady()) {
                blockedReason = UiTheme.line(unassigned > 0
                        ? line(player, "gui.party-unassigned-n")
                                .replace("<n>", String.valueOf(unassigned))
                        : line(player, "gui.party-need-both"));
            } else {
                blockedReason = UiTheme.line(teamService.errorMessage(player, precheck));
            }
            Component blockedHint = !team.isSplitReady()
                    ? UiTheme.line(line(player, "gui.party-assign-first"))
                    : UiTheme.line(line(player, "party.start-wait-lobby"));
            inventory.setItem(GuiSlots.slot(5, 5),
                    ItemBuilder.of(Material.DIAMOND_SWORD)
                            .name(t(player, "gui.party-start").color(ready ? UiTheme.SUCCESS : UiTheme.MUTED))
                            .lore(UiTheme.divider(),
                                    ready
                                            ? UiTheme.line(line(player, "party.start-ready")
                                                    .replace("<red>", String.valueOf(redCount))
                                                    .replace("<blue>", String.valueOf(blueCount)))
                                            : blockedReason,
                                    UiTheme.blank(),
                                    ready ? UiTheme.hint(line(player, "gui.party-start-hint"))
                                            : blockedHint)
                            .glintIf(ready)
                            .action("choose_kit").build());
        }
        MenuScaffold.returnButton(inventory, t(player, "menu.close"));
    }

    /**
     * @return the lang key of the member's blocking activity state, or {@code null} when the
     *         member is free in the lobby (or the state manager is not wired).
     */
    private String busyStateKey(UUID member) {
        if (stateManager == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(member);
        if (online == null) {
            return null;
        }
        com.rumilance.practice.state.PlayerState state = stateManager.getState(member);
        return switch (state) {
            case QUEUED_RANKED -> "menu.state-ranked-queue";
            case QUEUED_UNRANKED -> "menu.state-unranked-queue";
            case FIGHTING, PREPARING_MATCH, COUNTDOWN, ENDING -> "menu.state-fighting";
            case SPECTATING -> "menu.state-spectating";
            case FFA -> "menu.state-ffa";
            case EDITING_KIT -> "menu.state-editing";
            case REQUESTING_DUEL -> "menu.state-dueling";
            case PRACTICE_WAIT, PRACTICE_ACTIVE -> "menu.state-fighting";
            default -> null;
        };
    }

    private void teamHubReopen(Player player) {
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> {
                    if (player.isOnline()) {
                        open(player);
                    }
                });
    }

    private ItemStack headerItem(Player viewer, Team team) {
        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(team.owner());
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(team.name(), UiTheme.HEADER))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.party-owner"),
                                ownerPlayer.getName() == null ? "?" : ownerPlayer.getName()),
                        UiTheme.labelValue(line(viewer, "gui.party-members"), team.size() + "/30"),
                        UiTheme.status(team.isPublic()
                                        ? line(viewer, "gui.party-public")
                                        : line(viewer, "gui.party-private"),
                                team.isPublic() ? UiTheme.SUCCESS : UiTheme.MUTED)
                )
                .skullOwner(ownerPlayer)
                .action("decorate")
                .build();
    }

    private ItemStack memberItem(Player viewer, Team team, UUID member, boolean viewerIsOwner) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(member);
        TeamColor side = team.sideOf(member);
        String name = p.getName() == null ? "?" : p.getName();
        TextColor nameColor = side == TeamColor.RED ? UiTheme.DANGER
                : side == TeamColor.BLUE ? BLUE : UiTheme.MUTED;
        ItemBuilder b = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text((team.isOwner(member) ? "★ " : "") + name, nameColor))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.party-side"),
                                side == null ? line(viewer, "gui.party-unassigned") : side.name()));
        // Busy members (queue / FFA / match / spectate) are flagged so the owner sees at a
        // glance why a battle cannot start.
        String busyState = busyStateKey(member);
        if (busyState != null) {
            b.lore(UiTheme.status(line(viewer, busyState), UiTheme.WARNING));
        }
        if (team.isOwner(member)) {
            b.lore(UiTheme.status(line(viewer, "gui.party-owner"), UiTheme.SECONDARY));
        }
        if (viewerIsOwner) {
            b.lore(UiTheme.blank(),
                    UiTheme.hint(line(viewer, "gui.party-click-cycle")));
            if (!team.isOwner(member)) {
                b.lore(UiTheme.hint(line(viewer, "party.shift-kick")));
            }
        }
        return b.skullOwner(p).action("member:" + member).build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || "open_browser".equals(action)) {
            player.closeInventory();
            browser.open(player);
            return;
        }
        boolean owner = team.isOwner(player.getUniqueId());
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "leave" -> {
                sounds.play(player, "select");
                teamService.leave(player);
                player.closeInventory();
                browser.open(player);
            }
            case "disband" -> {
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                if (confirmGui != null) {
                    confirmGui.open(player,
                            t(player, "party.disband-confirm").color(UiTheme.DANGER),
                            java.util.List.of(UiTheme.line(line(player, "party.disband-confirm-lore"))),
                            who -> {
                                sounds.play(who, "select");
                                teamService.disband(who);
                                who.closeInventory();
                                browser.open(who);
                            },
                            who -> {
                                sounds.play(who, "gui-back");
                                teamHubReopen(who);
                            });
                } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    sounds.play(player, "select");
                    teamService.disband(player);
                    player.closeInventory();
                    browser.open(player);
                } else {
                    sounds.play(player, "error");
                }
            }
            case "toggle_public" -> {
                if (owner) {
                    teamService.togglePublic(player);
                    sounds.play(player, "gui-click");
                    refresh(player, session, inventory);
                }
            }
            case "toggle_ff" -> {
                if (owner) {
                    TeamService.Result r = teamService.toggleFriendlyFire(player);
                    sounds.play(player, r == TeamService.Result.OK ? "select" : "error");
                    refresh(player, session, inventory);
                }
            }
            case "autosplit" -> {
                if (owner) {
                    TeamService.Result r = teamService.autoAssign(player);
                    sounds.play(player, r == TeamService.Result.OK ? "select" : "error");
                    refresh(player, session, inventory);
                }
            }
            case "clearsides" -> {
                if (owner) {
                    teamService.clearSides(player);
                    sounds.play(player, "gui-click");
                    refresh(player, session, inventory);
                }
            }
            case "choose_kit" -> {
                if (!owner) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "gui.party-owner-only"));
                    return;
                }
                TeamService.Result precheck = teamService.preflightStart(player);
                if (precheck != TeamService.Result.OK) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            teamService.errorMessage(player, precheck), UiTheme.DANGER)
                            .decoration(TextDecoration.ITALIC, false));
                    refresh(player, session, inventory);
                    return;
                }
                sounds.play(player, "gui-click");
                // Open on the next tick: switching inventories from inside an
                // InventoryClickEvent handler is unreliable on some clients.
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> {
                            if (player.isOnline()) {
                                kitSelect.open(player);
                            }
                        });
            }
            case "page:prev" -> {
                session.setPage(session.page() - 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "page:next" -> {
                session.setPage(session.page() + 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "invite" -> {
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                if (partyInviteGui != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                            () -> {
                                if (player.isOnline()) {
                                    partyInviteGui.openFor(player);
                                }
                            });
                } else {
                    player.closeInventory();
                    player.sendMessage(t(player, "party.invite-unavailable"));
                }
            }
            case "select_map" -> {
                // Map selection now always goes through kit selection first: picking a kit
                // opens that kit's party-map list, and choosing a map starts the battle.
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> {
                            if (player.isOnline()) {
                                kitSelect.open(player);
                            }
                        });
            }
            default -> {
                if (action.startsWith("member:") && owner) {
                    UUID target;
                    try {
                        target = UUID.fromString(action.substring("member:".length()));
                    } catch (IllegalArgumentException e) {
                        return;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(target);
                    String name = op.getName();
                    if (name == null) {
                        sounds.play(player, "error");
                        return;
                    }
                    TeamService.Result r;
                    if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                        r = teamService.kick(player, name);
                    } else {
                        // One-button cycling: first click RED, next toggles to BLUE, then
                        // back to unassigned — no left/right distinction needed.
                        r = teamService.cycleSide(player, name);
                    }
                    sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
                    refresh(player, session, inventory);
                }
            }
        }
    }
}
