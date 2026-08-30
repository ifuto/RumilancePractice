package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Player profile / ranked summary. A player head anchors the top bar; two summary rows show
 * aggregate ranked stats (matches, wins, losses, win-rate, K/D, best streak, best kit, best
 * Elo); the bottom content row lists per-kit breakdowns; the close button is on the bottom bar.
 */
public final class ProfileGui extends AbstractGui {

    private final KitService kitService;
    private final StatsService statsService;
    private final com.rumilance.practice.ban.BanService banService;

    public ProfileGui(GuiSessionRegistry registry, SoundService sounds,
                      KitService kitService, StatsService statsService) {
        this(registry, sounds, kitService, statsService, null);
    }

    public ProfileGui(GuiSessionRegistry registry, SoundService sounds,
                      KitService kitService, StatsService statsService,
                      com.rumilance.practice.ban.BanService banService) {
        super(registry, sounds, GuiType.PROFILE, 6, true);
        this.kitService = kitService;
        this.statsService = statsService;
        this.banService = banService;
    }

    public void openFor(Player viewer, UUID target) {
        GuiSession session = registry.open(viewer.getUniqueId(), type(), rows);
        session.setTargetPlayer(target);
        PracticeGuiOpen.open(this, viewer, session);
        sounds.play(viewer, "gui-open");
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (session.targetPlayer() == null) {
            session.setTargetPlayer(player.getUniqueId());
        }
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();
        return Component.text(StatsService.nameOf(target), UiTheme.HEADER)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();

        List<RankedKitStats> kits;
        try {
            kits = statsService.allKits(target);
        } catch (Exception e) {
            kits = List.of();
        }
        int matches = kits.stream().mapToInt(RankedKitStats::gamesPlayed).sum();
        int wins = kits.stream().mapToInt(RankedKitStats::wins).sum();
        int losses = kits.stream().mapToInt(RankedKitStats::losses).sum();
        int bestStreak = kits.stream().mapToInt(RankedKitStats::winStreak).max().orElse(0);
        int bestElo = kits.stream().mapToInt(RankedKitStats::bestElo).max().orElse(1000);
        String bestKit = kits.stream().max(Comparator.comparingInt(RankedKitStats::wins))
                .map(RankedKitStats::kit).orElse("-");
        String winRate = matches < 21
                ? line(player, "gui.players-calibrating").replace("<matches>", String.valueOf(matches))
                : String.format("%.1f%%", 100.0 * wins / Math.max(1, matches));
        String kd = String.format("%.2f", (double) wins / Math.max(1, losses));

        // Elo is private: only the player viewing their own profile sees Elo numbers.
        boolean self = target.equals(player.getUniqueId());

        MenuScaffold.chrome(inventory);

        // Header: player head on the accent bar, with ping/online status.
        Player online = Bukkit.getPlayer(target);
        var head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(StatsService.nameOf(target), UiTheme.VALUE))
                .skullOwner(online != null ? online : Bukkit.getOfflinePlayer(target))
                .lore(
                        UiTheme.status(online != null ? line(player, "gui.online") : line(player, "gui.offline"),
                                online != null ? UiTheme.SUCCESS : UiTheme.MUTED),
                        online != null
                                ? UiTheme.labelValue(line(player, "gui.ping"), online.getPing() + "ms")
                                : UiTheme.line(line(player, "gui.last-seen"))
                )
                .glint(online != null)
                .action("decorate");
        inventory.setItem(GuiSlots.slot(0, 4), head.build());

        // Summary tiles (rows 1-2). Elo is only shown on the owner's own profile.
        inventory.setItem(GuiSlots.slot(1, 1), summary(Material.BOOK, line(player, "gui.profile-matches"), String.valueOf(matches)));
        inventory.setItem(GuiSlots.slot(1, 3), summary(Material.DIAMOND_SWORD, line(player, "gui.profile-wins"), String.valueOf(wins)));
        inventory.setItem(GuiSlots.slot(1, 5), summary(Material.SHIELD, line(player, "gui.profile-losses"), String.valueOf(losses)));
        inventory.setItem(GuiSlots.slot(1, 7), summary(Material.TARGET, line(player, "gui.profile-wr"), winRate));
        inventory.setItem(GuiSlots.slot(2, 1), summary(Material.NETHERITE_SWORD, "K/D", kd));
        inventory.setItem(GuiSlots.slot(2, 3), summary(Material.EMERALD, line(player, "gui.profile-best-streak"), String.valueOf(bestStreak)));
        inventory.setItem(GuiSlots.slot(2, 5), summary(Material.NETHER_STAR, line(player, "gui.profile-best-kit"), bestKit));
        if (self) {
            inventory.setItem(GuiSlots.slot(2, 7), summary(Material.DIAMOND, line(player, "gui.profile-best-elo"), String.valueOf(bestElo)));
        } else {
            inventory.setItem(GuiSlots.slot(2, 7), summary(Material.DIAMOND, line(player, "gui.profile-best-elo"), line(player, "gui.profile-hidden")));
        }

        // Per-kit breakdown (rows 3-4 = 14 slots).
        int index = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (index >= 14) {
                break;
            }
            inventory.setItem(GuiSlots.slot(3 + index / 7, 1 + index % 7), kitIcon(player, kit, target, kits, self));
            index++;
        }

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private ItemStack summary(Material material, String label, String value) {
        return ItemBuilder.of(material)
                .name(Component.text(label, UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        Component.text(value, UiTheme.VALUE)
                                .decoration(TextDecoration.ITALIC, false)
                )
                .action("decorate")
                .build();
    }

    private ItemStack kitIcon(Player player, KitDefinition kit, UUID target, List<RankedKitStats> kits, boolean self) {
        Material material = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
        RankedKitStats stats = kits.stream()
                .filter(s -> s.kit().equalsIgnoreCase(kit.name()))
                .findFirst()
                .orElse(RankedKitStats.starting(target, kit.name()));
        String winRate = stats.gamesPlayed() < 21
                ? line(player, "gui.players-calibrating").replace("<matches>", String.valueOf(stats.gamesPlayed()))
                : String.format("%.1f%%", stats.winRate() * 100);
        var builder = ItemBuilder.of(material)
                .nameMini(kit.prettyDisplayName())
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("W/L", stats.wins() + "/" + stats.losses()),
                        UiTheme.labelValue(line(player, "gui.profile-wr"), winRate),
                        UiTheme.labelValue("K/D", String.format("%.2f", statsService.kd(stats))),
                        UiTheme.labelValue(line(player, "gui.profile-streak"), String.valueOf(stats.winStreak()))
                );
        if (self) {
            builder.lore(
                    UiTheme.labelValue("Elo", String.valueOf(stats.elo())),
                    UiTheme.labelValue("Best", String.valueOf(stats.bestElo()))
            );
        } else {
            builder.lore(UiTheme.labelValue("Elo", line(player, "gui.profile-hidden")));
        }
        return builder
                .glint(stats.gamesPlayed() > 0)
                .action("decorate")
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
        }
    }
}
