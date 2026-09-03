package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
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
    private final KitService kitService;

    public SpectateListGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            MatchRegistry matchRegistry,
            SpectatorService spectatorService
    ) {
        this(registry, sounds, matchRegistry, spectatorService, null, null);
    }

    public SpectateListGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            MatchRegistry matchRegistry,
            SpectatorService spectatorService,
            com.rumilance.practice.ffa.FfaService ffaService
    ) {
        this(registry, sounds, matchRegistry, spectatorService, ffaService, null);
    }

    public SpectateListGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            MatchRegistry matchRegistry,
            SpectatorService spectatorService,
            com.rumilance.practice.ffa.FfaService ffaService,
            KitService kitService
    ) {
        super(registry, sounds, GuiType.SPECTATE_LIST, 6, true);
        this.matchRegistry = matchRegistry;
        this.spectatorService = spectatorService;
        this.ffaService = ffaService;
        this.kitService = kitService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.spectate-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        // Spectatable from "match found" (arena reservation) onwards — not only once the
        // fight is ACTIVE — so the entry shows up the moment the match is made.
        List<MatchSession> active = new ArrayList<>();
        for (MatchSession match : matchRegistry.all()) {
            MatchState state = match.state();
            if (state == MatchState.RESERVING_ARENA
                    || state == MatchState.PASTING_ARENA
                    || state == MatchState.COUNTDOWN
                    || state == MatchState.ACTIVE) {
                active.add(match);
            }
        }

        // FFA arenas with at least one fighter are spectatable too — one entry per live arena.
        List<java.util.Map.Entry<com.rumilance.practice.ffa.FfaService.FfaArena, java.util.UUID>> ffa =
                new java.util.ArrayList<>();
        if (ffaService != null) {
            for (com.rumilance.practice.ffa.FfaService.FfaArena arena : ffaService.list()) {
                if (!arena.enabled()) {
                    continue;
                }
                java.util.UUID target = firstFfaFighter(arena.id());
                if (target != null) {
                    ffa.add(java.util.Map.entry(arena, target));
                }
            }
        }

        int matchCount = active.size();
        int total = matchCount + ffa.size();

        int page = session.page();
        int perPage = MenuScaffold.gridPageSize();
        int start = page * perPage;
        int index = 0;
        for (int i = start; i < total && index < perPage; i++, index++) {
            if (i < matchCount) {
                inventory.setItem(MenuScaffold.gridSlot(index), matchIcon(player, active.get(i)));
            } else {
                var e = ffa.get(i - matchCount);
                inventory.setItem(MenuScaffold.gridSlot(index), ffaIcon(player, e.getKey(), e.getValue()));
            }
        }

        if (total == 0) {
            inventory.setItem(MenuScaffold.gridSlot(0),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.spectate-empty").color(UiTheme.MUTED))
                            .lore(UiTheme.line(line(player, "gui.spectate-empty-lore")))
                            .action("decorate")
                            .build());
        }

        paintPaging(player, inventory, page, total);
        paintNav(player, session, inventory);
    }

    private java.util.UUID firstFfaFighter(String arenaId) {
        if (ffaService == null) {
            return null;
        }
        for (java.util.UUID id : ffaService.occupantIds()) {
            if (arenaId.equals(ffaService.arenaOf(id).orElse(null))) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    return id;
                }
            }
        }
        return null;
    }

    private ItemStack ffaIcon(Player viewer, com.rumilance.practice.ffa.FfaService.FfaArena arena, java.util.UUID target) {
        Material iconMat = Material.IRON_SWORD;
        if (kitService != null) {
            KitDefinition kit = kitService.get(arena.kitId()).orElse(null);
            if (kit != null) {
                Material matched = Material.matchMaterial(kit.icon());
                if (matched != null) {
                    iconMat = matched;
                }
            }
        }
        return ItemBuilder.of(iconMat)
                .name(Component.text(com.rumilance.practice.util.NameDisplay.pretty(arena.id()))
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.spectate-kit"),
                                com.rumilance.practice.util.KitNames.pretty(arena.kitId())),
                        UiTheme.labelValue(line(viewer, "gui.spectate-mode"), "FFA"),
                        UiTheme.blank(),
                        UiTheme.status("LIVE", UiTheme.SUCCESS),
                        UiTheme.hint(line(viewer, "gui.spectate-hint"))
                )
                .glint(true)
                .action("spec:" + target.toString())
                .build();
    }

    private ItemStack matchIcon(Player viewer, MatchSession match) {
        UUID a = match.participants().get(0);
        UUID b = match.participants().size() > 1 ? match.participants().get(1) : a;

        boolean live = match.state() == MatchState.ACTIVE;
        String elapsed = live ? "live" : "starting";
        if (match.startedAt() != null) {
            long seconds = Duration.between(match.startedAt(), Instant.now()).getSeconds();
            elapsed = String.format("%d:%02d", seconds / 60, seconds % 60);
        }

        int winsA = match.seriesWinsOf(a);
        int winsB = match.seriesWinsOf(b);

        Material iconMat = Material.IRON_SWORD;
        if (kitService != null) {
            KitDefinition kit = kitService.get(match.kitName()).orElse(null);
            if (kit != null) {
                Material matched = Material.matchMaterial(kit.icon());
                if (matched != null) {
                    iconMat = matched;
                }
            }
        }

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(UiTheme.divider());
        lore.add(UiTheme.labelValue(line(viewer, "gui.spectate-kit"), match.kitName()));
        lore.add(UiTheme.labelValue(line(viewer, "gui.spectate-mode"), modeWord(viewer, match.mode().name())));
        lore.add(UiTheme.labelValue(line(viewer, "gui.spectate-score"), winsA + " - " + winsB));
        lore.add(UiTheme.labelValue(line(viewer, "gui.spectate-time"), elapsed));
        if (match.isTeamMatch()) {
            lore.add(UiTheme.blank());
            lore.add(UiTheme.labelValue("RED", joinNames(match.team(TeamColor.RED))));
            lore.add(UiTheme.labelValue("BLUE", joinNames(match.team(TeamColor.BLUE))));
        }
        lore.add(UiTheme.blank());
        lore.add(live
                ? UiTheme.status("LIVE", UiTheme.SUCCESS)
                : UiTheme.status("STARTING", UiTheme.WARNING));
        lore.add(UiTheme.hint(line(viewer, "gui.spectate-hint")));

        return ItemBuilder.of(iconMat)
                .name(Component.text(StatsService.nameOf(a) + " §7vs " + StatsService.nameOf(b)))
                .lore(lore.toArray(Component[]::new))
                .glint(true)
                .action("spec:" + a)
                .tag(ItemKeys.targetUuid(), a.toString())
                .build();
    }

    private static String joinNames(java.util.List<UUID> ids) {
        StringBuilder builder = new StringBuilder();
        for (UUID id : ids) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(StatsService.nameOf(id));
        }
        return builder.toString();
    }

    private String modeWord(Player viewer, String mode) {
        return switch (mode) {
            case "RANKED" -> line(viewer, "gui.ranked");
            case "UNRANKED" -> line(viewer, "gui.unranked");
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
