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
import com.rumilance.practice.locale.MessageService;
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

import java.util.List;
import java.util.UUID;

/**
 * Per-kit ranked stats browser. The header shows whose stats are being viewed (with a head),
 * the content grid lists every enabled kit with W/L, win-rate, K/D, streak and Elo, and the
 * bottom bar holds a close button. Stats are loaded via {@link StatsService} with failures
 * degraded to a "Stats unavailable" lore rather than aborting the whole menu.
 */
public final class StatsKitGui extends AbstractGui {

    private final KitService kitService;
    private final StatsService statsService;

    public StatsKitGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService, StatsService statsService) {
        super(registry, sounds, GuiType.STATS_KIT, 6, true);
        this.kitService = kitService;
        this.statsService = statsService;
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        if (session.targetPlayer() == null) {
            session.setTargetPlayer(player.getUniqueId());
        }
    }

    public void openFor(Player viewer, UUID target) {
        GuiSession session = registry.open(viewer.getUniqueId(), type(), rows);
        session.setTargetPlayer(target);
        session.setRanked(true);
        PracticeGuiOpen.open(this, viewer, session);
        sounds.play(viewer, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();
        return t(player, "gui.stats-title", MessageService.tags("name", StatsService.nameOf(target)))
                .color(UiTheme.HEADER);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        UUID target = session.targetPlayer() == null ? player.getUniqueId() : session.targetPlayer();
        MenuScaffold.chrome(inventory);

        Player online = Bukkit.getPlayer(target);
        inventory.setItem(GuiSlots.slot(0, 4),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(Component.text(StatsService.nameOf(target), UiTheme.VALUE))
                        .skullOwner(online != null ? online : Bukkit.getOfflinePlayer(target))
                        .lore(
                                UiTheme.status(online != null ? line(player, "gui.online") : line(player, "gui.offline"),
                                        online != null ? UiTheme.SUCCESS : UiTheme.MUTED),
                                online != null ? UiTheme.labelValue(line(player, "gui.ping"), online.getPing() + "ms")
                                        : UiTheme.line(line(player, "gui.last-seen"))
                        )
                        .action("decorate")
                        .build());

        List<KitDefinition> kits = kitService.enabled();
        int perPage = MenuScaffold.gridPageSize();
        int page = session.page();
        int offset = page * perPage;

        int placed = 0;
        for (int i = offset; i < kits.size() && placed < perPage; i++, placed++) {
            inventory.setItem(MenuScaffold.gridSlot(placed), kitIcon(player, kits.get(i), target));
        }

        paintPaging(player, inventory, page, kits.size());
        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    private ItemStack kitIcon(Player viewer, KitDefinition kit, UUID target) {
        Material material = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
        ItemBuilder builder = ItemBuilder.of(material).nameMini(kit.prettyDisplayName());
        try {
            RankedKitStats stats = statsService.kitStats(target, kit.name())
                    .orElse(RankedKitStats.starting(target, kit.name()));
            builder.lore(
                    UiTheme.divider(),
                    UiTheme.labelValue(line(viewer, "gui.profile-wins"), String.valueOf(stats.wins())),
                    UiTheme.labelValue(line(viewer, "gui.profile-losses"), String.valueOf(stats.losses())),
                    UiTheme.labelValue(line(viewer, "gui.profile-matches"), String.valueOf(stats.gamesPlayed())),
                    UiTheme.labelValue(line(viewer, "gui.profile-wr"), statsService.winRateLabel(stats)),
                    UiTheme.labelValue("K/D", String.format("%.2f", statsService.kd(stats))),
                    UiTheme.labelValue(line(viewer, "gui.profile-streak"), String.valueOf(stats.winStreak())),
                    UiTheme.labelValue("Elo", String.valueOf(stats.elo())),
                    UiTheme.blank(),
                    UiTheme.labelValue(line(viewer, "gui.profile-best-elo"), String.valueOf(stats.bestElo()))
            );
        } catch (Exception e) {
            builder.lore(UiTheme.status(line(viewer, "gui.stats-unavailable"), UiTheme.DANGER));
        }
        return builder.action("decorate").glint(statsPlayed(kit, target) > 0).build();
    }

    private int statsPlayed(KitDefinition kit, UUID target) {
        try {
            return statsService.kitStats(target, kit.name())
                    .map(RankedKitStats::gamesPlayed)
                    .orElse(0);
        } catch (Exception e) {
            return 0;
        }
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
        }
    }
}
