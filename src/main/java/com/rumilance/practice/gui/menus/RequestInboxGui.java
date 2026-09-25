package com.rumilance.practice.gui.menus;

import com.rumilance.practice.command.DuelCommand;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiFrame;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.team.GroupKind;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import net.kyori.adventure.text.Component;
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
 * 申請一覧 — the centralised request inbox behind the Battle Menu button (Bedrock-only). It
 * collects every incoming item a player can act on in one place: 1v1 duel requests (from
 * {@link DuelRequestService}), party/team invites (from {@link TeamService#incomingInvite})
 * and party-vs-party "team duel" requests (from {@link TeamService#pendingDuelFor}).
 *
 * <p>One tile per request: <b>left-click accepts, right-click denies</b>. Accept mirrors the
 * command flow exactly — duels run {@link DuelCommand#handleAccept} with the sender's name;
 * invites run {@link TeamService#join}; party duel requests run
 * {@link TeamService#acceptTeamDuel}. Deny mirrors {@link DuelCommand#handleDeny},
 * {@link TeamService#decline} or {@link TeamService#denyTeamDuel}.</p>
 */
public final class RequestInboxGui extends AbstractGui {

    private final DuelRequestService duelRequestService;
    private final TeamService teamService;
    private final DuelCommand duelCommand;

    /** One screen holds up to 14 tiles (grid rows 1-2); extra requests simply don't render. */
    private static final int MAX_ITEMS = 14;

    /** One inbox tile: the action code plus its visual identity. */
    private record Entry(String action, Material icon, TextColorName name, List<String> lore,
                         UUID sender) {
        boolean duel() {
            return action.startsWith("duel:");
        }
    }

    /** Tiny holder so the name colour choice stays a single field. */
    private record TextColorName(String text, net.kyori.adventure.text.format.TextColor color) {
    }

    public RequestInboxGui(GuiSessionRegistry registry, SoundService sounds,
                           DuelRequestService duelRequestService, TeamService teamService,
                           DuelCommand duelCommand) {
        super(registry, sounds, GuiType.REQUEST_INBOX, 4, true);
        this.duelRequestService = duelRequestService;
        this.teamService = teamService;
        this.duelCommand = duelCommand;
    }

    @Override
    protected GuiFrame.Theme theme() {
        return GuiFrame.Theme.GREEN;
    }

    @Override
    protected Material titleIcon() {
        return Material.PAPER;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.request-inbox-title").color(UiTheme.PRIMARY);
    }

    private String senderName(UUID id) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        return p != null && p.getName() != null ? p.getName() : "?";
    }

    /** Builds the ordered list of pending requests; render and clicks share this ordering. */
    private List<Entry> entries(Player player) {
        List<Entry> out = new ArrayList<>();

        // 1. Incoming party / team invite.
        teamService.incomingInvite(player.getUniqueId()).ifPresent(invite -> {
            boolean party = invite.kind() == GroupKind.PARTY;
            String kind = line(player, party ? "gui.request-inbox-party" : "gui.request-inbox-team");
            out.add(new Entry("invite", party ? Material.LIME_BANNER : Material.CYAN_BANNER,
                    new TextColorName(kind, UiTheme.SUCCESS),
                    List.of(line(player, "gui.request-inbox-sender")
                            .replace("<sender>", senderName(invite.fromOwner()))),
                    invite.fromOwner()));
        });

        Team myParty = teamService.teamOf(player.getUniqueId()).orElse(null);
        boolean inParty = myParty != null && myParty.kind() == GroupKind.PARTY;

        // 2. Party-vs-party: explicit team duel request.
        if (inParty) {
            teamService.pendingDuelFor(myParty.id()).ifPresent(req -> {
                out.add(new Entry("party-fight", Material.IRON_SWORD,
                        new TextColorName(line(player, "gui.request-inbox-team-duel"), UiTheme.SUCCESS),
                        List.of("\"" + req.fromPartyName() + "\""), req.fromOwner()));
            });
        }

        // 3. Incoming 1v1 duel requests (newest first).
        for (DuelRequestService.RichDuelRequest req
                : duelRequestService.incoming(player.getUniqueId())) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            out.add(new Entry("duel:" + req.id(), Material.PLAYER_HEAD,
                    new TextColorName(line(player, "gui.request-inbox-duel") + "  "
                            + senderName(req.sender()), UiTheme.SECONDARY),
                    List.of(line(player, "gui.request-inbox-duel-raison") + ": "
                            + (messages() != null
                                ? messages().modeWord(player, req.ranked())
                                : (req.ranked() ? "Ranked" : "Unranked"))),
                    req.sender()));
        }
        return out;
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        List<Entry> entries = entries(player);
        if (entries.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "gui.request-inbox-empty").color(UiTheme.MUTED))
                    .lore(UiTheme.line(line(player, "gui.request-inbox-empty-lore")))
                    .action("decorate")
                    .build());
        } else {
            for (int i = 0; i < entries.size() && i < MAX_ITEMS; i++) {
                Entry entry = entries.get(i);
                inventory.setItem(MenuScaffold.gridSlot(i), tile(player, entry));
            }
        }

        paintNav(player, session, inventory);
    }

    private ItemStack tile(Player player, Entry entry) {
        List<Component> lore = new ArrayList<>();
        lore.add(UiTheme.divider());
        for (String text : entry.lore()) {
            lore.add(UiTheme.line(text));
        }
        lore.add(UiTheme.blank());
        lore.add(UiTheme.hint(line(player, entry.duel()
                ? "gui.request-inbox-duel-hint" : "gui.request-inbox-generic-hint")));

        return ItemBuilder.of(entry.icon())
                .name(Component.text(entry.name().text(), entry.name().color()))
                .lore(lore.toArray(new Component[0]))
                .action(entry.action())
                .skullOwner(entry.icon() == Material.PLAYER_HEAD
                        ? Bukkit.getOfflinePlayer(entry.sender())
                        : null)
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType clickType) {
        if (action == null) {
            return;
        }
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            default -> {
                if ("decorate".equals(action)) {
                    return;
                }
                Entry entry = entries(player).stream()
                        .filter(e -> e.action().equals(action))
                        .findFirst().orElse(null);
                if (entry == null) {
                    return;
                }
                boolean accept = clickType == null || clickType.isLeftClick();
                if (accept) {
                    requestJoin(player, entry);
                } else {
                    sounds.play(player, "gui-click");
                    runDeny(player, entry);
                    refresh(player, session, inventory);
                }
            }
        }
    }

    /** Accept an entry: mirrors the command flow, closes to lobby/arena on success. */
    private void requestJoin(Player player, Entry entry) {
        sounds.play(player, "gui-click");
        switch (entry.action()) {
            case "invite" -> {
                TeamService.Result r = teamService.join(player,
                        teamService.incomingInvite(player.getUniqueId())
                                .map(TeamService.IncomingInvite::teamName).orElse(null));
                respond(player, r);
            }
            case "party-fight" -> respond(player, teamService.acceptTeamDuel(player));
            default -> {
                if (entry.duel()) {
                    UUID requestId = UUID.fromString(entry.action().substring(5));
                    String fromName = duelRequestService.incoming(player.getUniqueId()).stream()
                            .filter(r -> r.id().equals(requestId))
                            .findFirst()
                            .map(r -> senderName(r.sender()))
                            .orElse(null);
                    if (fromName == null) {
                        sounds.play(player, "error");
                        return;
                    }
                    player.closeInventory();
                    duelCommand.handleAccept(player, fromName);
                }
            }
        }
    }

    private void respond(Player player, TeamService.Result r) {
        if (r == TeamService.Result.OK) {
            player.closeInventory();
        } else {
            sounds.play(player, "error");
            player.sendMessage(Component.text(nameOf(player, r), UiTheme.DANGER));
        }
    }

    private String nameOf(Player player, TeamService.Result r) {
        String localized;
        try {
            localized = teamService.errorMessage(player, r);
        } catch (Exception ignored) {
            localized = String.valueOf(r);
        }
        return localized == null || localized.isBlank() ? String.valueOf(r) : localized;
    }

    /** Deny wrappers: command-flow calls the same service methods. */
    private void runDeny(Player player, Entry entry) {
        switch (entry.action()) {
            case "invite" -> respondFeedback(player, teamService.decline(player));
            case "party-fight" -> respondFeedback(player, teamService.denyTeamDuel(player));
            default -> {
                if (entry.duel()) {
                    UUID requestId = UUID.fromString(entry.action().substring(5));
                    String fromName = duelRequestService.incoming(player.getUniqueId()).stream()
                            .filter(r -> r.id().equals(requestId))
                            .findFirst()
                            .map(r -> senderName(r.sender()))
                            .orElse(null);
                    if (fromName != null) {
                        duelCommand.handleDeny(player, fromName);
                    }
                }
            }
        }
    }

    private void respondFeedback(Player player, TeamService.Result r) {
        if (r != TeamService.Result.OK) {
            sounds.play(player, "error");
            player.sendMessage(Component.text(nameOf(player, r), UiTheme.DANGER));
        }
    }
}
