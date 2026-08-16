package com.rumilance.practice.gui.menus;

import com.rumilance.practice.chat.PendingInput;
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

    public TeamHubGui(GuiSessionRegistry registry, SoundService sounds,
                      TeamService teamService, TeamsBrowserGui browser, TeamKitSelectGui kitSelect) {
        super(registry, sounds, GuiType.TEAM_HUB, 6, true);
        this.teamService = teamService;
        this.browser = browser;
        this.kitSelect = kitSelect;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("✦ Team", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null) {
            // Player left / was kicked while the menu was open: show a friendly redirect tile.
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.COMPASS)
                            .name(Component.text("You are not in a team", UiTheme.WARNING))
                            .lore(UiTheme.hint("Click to browse public teams"))
                            .action("open_browser").build());
            MenuScaffold.closeButton(inventory);
            return;
        }
        boolean owner = team.isOwner(player.getUniqueId());

        // --- top bar: team info + owner controls ---
        inventory.setItem(GuiSlots.slot(0, 4), headerItem(team));
        if (owner) {
            inventory.setItem(GuiSlots.slot(0, 1),
                    ItemBuilder.of(Material.WRITABLE_BOOK)
                            .name(Component.text("Invite Player", UiTheme.SUCCESS))
                            .lore(UiTheme.divider(),
                                    UiTheme.line("Invites expire after 60s."),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click, then type a name in chat"))
                            .action("invite").build());
            inventory.setItem(GuiSlots.slot(0, 2),
                    ItemBuilder.of(team.isPublic() ? UiTheme.TOGGLE_ON : UiTheme.TOGGLE_OFF)
                            .name(Component.text(team.isPublic() ? "Public Team" : "Private Team",
                                    team.isPublic() ? UiTheme.SUCCESS : UiTheme.MUTED))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(team.isPublic()
                                            ? "Anyone can join without an invite."
                                            : "Only invited players can join."),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click to toggle visibility"))
                            .action("toggle_public").build());
            inventory.setItem(GuiSlots.slot(0, 7),
                    ItemBuilder.of(Material.TNT)
                            .name(Component.text("Disband Team", UiTheme.DANGER))
                            .lore(UiTheme.hint("Shift-click to confirm"))
                            .action("disband").build());
        } else {
            inventory.setItem(GuiSlots.slot(0, 7),
                    ItemBuilder.of(Material.OAK_DOOR)
                            .name(Component.text("Leave Team", UiTheme.WARNING))
                            .lore(UiTheme.hint("Click to leave this team"))
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
            inventory.setItem(MenuScaffold.gridSlot(index++), memberItem(team, members.get(i), owner));
        }
        MenuScaffold.pagingButtons(inventory, page, members.size());

        // --- bottom bar: side counters + owner controls ---
        int redCount = team.side(TeamColor.RED).size();
        int blueCount = team.side(TeamColor.BLUE).size();
        int unassigned = team.size() - redCount - blueCount;
        inventory.setItem(GuiSlots.slot(5, 0),
                ItemBuilder.of(Material.RED_WOOL, Math.max(1, redCount))
                        .name(Component.text("RED — " + redCount + "/15", UiTheme.DANGER))
                        .lore(UiTheme.line("Left-click a member to assign RED."))
                        .action("decorate").build());
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.of(Material.BLUE_WOOL, Math.max(1, blueCount))
                        .name(Component.text("BLUE — " + blueCount + "/15", BLUE))
                        .lore(UiTheme.line("Right-click a member to assign BLUE."))
                        .action("decorate").build());

        if (owner) {
            inventory.setItem(GuiSlots.slot(5, 1),
                    ItemBuilder.of(Material.ENDER_PEARL)
                            .name(Component.text("Auto Split", UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line("Randomly assigns everyone to RED/BLUE."),
                                    UiTheme.line("Uneven ratios are fine — both sides"),
                                    UiTheme.line("just need at least one player."),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click to auto split"))
                            .action("autosplit").build());
            inventory.setItem(GuiSlots.slot(5, 3),
                    ItemBuilder.of(Material.WET_SPONGE)
                            .name(Component.text("Clear Sides", UiTheme.WARNING))
                            .lore(UiTheme.hint("Reset every side assignment"))
                            .action("clearsides").build());
            boolean ready = team.isSplitReady();
            inventory.setItem(GuiSlots.slot(5, 5),
                    ItemBuilder.of(Material.DIAMOND_SWORD)
                            .name(Component.text("Start Battle", ready ? UiTheme.SUCCESS : UiTheme.MUTED))
                            .lore(UiTheme.divider(),
                                    ready
                                            ? UiTheme.line("RED " + redCount + " vs BLUE " + blueCount)
                                            : UiTheme.line(unassigned > 0
                                                    ? unassigned + " member(s) still unassigned."
                                                    : "Both sides need at least one player."),
                                    UiTheme.blank(),
                                    ready ? UiTheme.hint("Click to pick a kit & fight")
                                            : UiTheme.line("Assign sides first (or Auto Split)."))
                            .glintIf(ready)
                            .action("choose_kit").build());
        }
        MenuScaffold.closeButton(inventory);
    }

    private ItemStack headerItem(Team team) {
        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(team.owner());
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(team.name(), UiTheme.HEADER))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("Owner", ownerPlayer.getName() == null ? "?" : ownerPlayer.getName()),
                        UiTheme.labelValue("Members", team.size() + "/30"),
                        UiTheme.status(team.isPublic() ? "PUBLIC" : "PRIVATE",
                                team.isPublic() ? UiTheme.SUCCESS : UiTheme.MUTED)
                )
                .skullOwner(ownerPlayer)
                .action("decorate")
                .build();
    }

    private ItemStack memberItem(Team team, UUID member, boolean viewerIsOwner) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(member);
        TeamColor side = team.sideOf(member);
        String name = p.getName() == null ? "?" : p.getName();
        TextColor nameColor = side == TeamColor.RED ? UiTheme.DANGER
                : side == TeamColor.BLUE ? BLUE : UiTheme.MUTED;
        ItemBuilder b = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text((team.isOwner(member) ? "★ " : "") + name, nameColor))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue("Side", side == null ? "unassigned" : side.name()));
        if (team.isOwner(member)) {
            b.lore(UiTheme.status("OWNER", UiTheme.SECONDARY));
        }
        if (viewerIsOwner) {
            b.lore(UiTheme.blank(),
                    UiTheme.hint("Left-click: RED"),
                    UiTheme.hint("Right-click: BLUE"));
            if (!team.isOwner(member)) {
                b.lore(UiTheme.hint("Shift-click: kick"));
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
            case "close" -> {
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
                // Require shift-click so a stray click can't nuke a 30-player team.
                if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
                    sounds.play(player, "error");
                    return;
                }
                sounds.play(player, "select");
                teamService.disband(player);
                player.closeInventory();
                browser.open(player);
            }
            case "toggle_public" -> {
                if (owner) {
                    teamService.togglePublic(player);
                    sounds.play(player, "gui-click");
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
                    player.sendMessage(Component.text("チームオーナーのみ開始できます。", UiTheme.DANGER)
                            .decoration(TextDecoration.ITALIC, false));
                    return;
                }
                if (!team.isSplitReady()) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            "全員を赤/青に割り当ててください(Auto Split か 各メンバーをクリック)。",
                            UiTheme.WARNING).decoration(TextDecoration.ITALIC, false));
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
                player.closeInventory();
                player.sendMessage(Component.text("Type the player name to invite (or 'cancel'):",
                        UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false));
                TeamHubGui self = this;
                PendingInput.await(player, text -> {
                    if (text.equalsIgnoreCase("cancel") || text.isBlank()) {
                        player.sendMessage(Component.text("Invite cancelled.", UiTheme.MUTED)
                                .decoration(TextDecoration.ITALIC, false));
                    } else {
                        TeamService.Result r = teamService.invite(player, text);
                        if (r != TeamService.Result.OK) {
                            player.sendMessage(Component.text(inviteError(r), UiTheme.DANGER)
                                    .decoration(TextDecoration.ITALIC, false));
                        }
                    }
                    self.open(player);
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
                    } else if (click == ClickType.RIGHT) {
                        r = teamService.assignSide(player, name, "blue");
                    } else {
                        r = teamService.assignSide(player, name, "red");
                    }
                    sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
                    refresh(player, session, inventory);
                }
            }
        }
    }

    private static String inviteError(TeamService.Result r) {
        return switch (r) {
            case TARGET_OFFLINE -> "Player not found or offline.";
            case TARGET_IN_TEAM -> "That player is already in a team.";
            case TEAM_FULL -> "Your team is full (30 max).";
            case NOT_OWNER -> "Only the owner can invite.";
            default -> "Could not invite: " + r.name();
        };
    }
}
