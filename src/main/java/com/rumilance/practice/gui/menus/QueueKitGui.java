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
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Ranked / Unranked kit queue selector. The 28-slot content grid (rows 1-4, cols 1-7) lists
 * every enabled kit with its live queue count and a ranked/unranked accent; the bottom bar
 * holds the close button. Disabled kits render as a barrier with an explanation.
 */
public final class QueueKitGui extends AbstractGui {

    private final KitService kitService;
    private final QueueService queueService;
    private final QueueCoordinator queueCoordinator;
    private final boolean ranked;
    private KitPreviewGui previewGui;
    private com.rumilance.practice.database.repository.WinStreakRepository winStreakRepository;
    // Ranked-only TOP5 hover lore: which repository and how confident a rating must be to list.
    private com.rumilance.practice.database.repository.RankedStatsRepository rankedStatsRepository;
    private double leaderboardMaxDeviation = 115.0d;
    private volatile com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker;

    public QueueKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            QueueService queueService,
            QueueCoordinator queueCoordinator,
            boolean ranked
    ) {
        super(registry, sounds, ranked ? GuiType.RANKED_QUEUE : GuiType.UNRANKED_QUEUE, 6, ranked);
        this.kitService = kitService;
        this.queueService = queueService;
        this.queueCoordinator = queueCoordinator;
        this.ranked = ranked;
        this.winStreakRepository = null;
    }

    public void setPreviewGui(KitPreviewGui previewGui) {
        this.previewGui = previewGui;
    }

    public void setLastKitTracker(com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker) {
        this.lastKitTracker = lastKitTracker;
    }

    /** Wires the TOP5 hover ranking for the ranked queue (unused on the unranked GUI). */
    public void setRankedTopLore(com.rumilance.practice.database.repository.RankedStatsRepository repository,
                                 double leaderboardMaxDeviation) {
        this.rankedStatsRepository = repository;
        this.leaderboardMaxDeviation = leaderboardMaxDeviation;
    }

    public void openPreview(Player player, String kitId) {
        if (previewGui == null) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), previewGui.type(), previewGui.rows());
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(previewGui, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setRanked(ranked);
        // 開き直すたびに2アイコンの画面から始める(前のカテゴリ選択を持ち越さない)。
        session.setKitCategory(null);
        session.setPage(0);
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.BOOK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, ranked ? "gui.ranked-queue" : "gui.unranked-queue")
                .color(ranked ? UiTheme.PRIMARY : UiTheme.SECONDARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        List<KitDefinition> kits = kitService.enabled();
        // Queue も 2 ステップ: まず Main / Sub の2アイコンだけ。押すと木のボタン音がして
        // カーソルがその鍛冶型を持ち、0.2秒後に離れる音と同時にそのカテゴリの一覧が出る。
        if (session.kitCategory() == null) {
            renderChooser(player, inventory);
        } else {
            renderCategory(player, session, inventory);
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
        }

        // Info tile showing total queue depth.
        PlayerPlatform platform = PlayerPlatform.of(player);
        int totalWaiting = kits.stream()
                .filter(k -> kitService.isQueueEnabled(k.name())
                        && kitService.isQueueEnabled(kitService.playableId(k.name())))
                .mapToInt(k -> queueService.waitingCount(mode(), kitService.playableId(k.name()), platform))
                .sum();
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(Material.CLOCK)
                        .name(t(player, "gui.queue-waiting").color(UiTheme.MUTED))
                        .lore(UiTheme.labelValue(line(player, "gui.queue-count"), String.valueOf(totalWaiting)))
                        .action("decorate")
                        .build());

        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(Component.text(player.getName(), UiTheme.VALUE))
                        .skullOwner(player)
                        .lore(
                                UiTheme.labelValue(line(player, "gui.queue-mode"),
                                        line(player, ranked ? "gui.ranked" : "gui.unranked")),
                                UiTheme.hint(line(player, "gui.queue-join-hint"))
                        )
                        .action("decorate")
                        .build());

        paintNav(player, session, inventory);
    }

    /** Main = 大自然風の鍛冶型(Wild)、Sub = ネジ型の装飾(Bolt)。 */
    private void renderChooser(Player player, Inventory inventory) {
        int mainCount = kitService.enabled(com.rumilance.practice.model.KitCategory.MAIN).size();
        int subCount = kitService.enabled(com.rumilance.practice.model.KitCategory.SUB).size();
        inventory.setItem(MenuScaffold.gridSlot(9),
                com.rumilance.practice.gui.KitSections.categoryButton(
                        com.rumilance.practice.model.KitCategory.MAIN,
                        t(player, "gui.kit-main-button").color(UiTheme.SUCCESS),
                        java.util.List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-main-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(mainCount)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-button-hint")))));
        inventory.setItem(MenuScaffold.gridSlot(11),
                com.rumilance.practice.gui.KitSections.categoryButton(
                        com.rumilance.practice.model.KitCategory.SUB,
                        t(player, "gui.kit-sub-button").color(UiTheme.SECONDARY),
                        java.util.List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-sub-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(subCount)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-button-hint")))));
        // 前回選択したキットを MAIN と SUB の間に表示
        renderLastSelectedKit(player, inventory);
    }

    private void renderLastSelectedKit(Player player, Inventory inventory) {
        com.rumilance.practice.kit.LastSelectedKitTracker tracker = lastKitTracker;
        if (tracker == null) return;
        String lastKitId = tracker.get(player.getUniqueId());
        if (lastKitId == null) return;
        KitDefinition kit = kitService.get(lastKitId).orElse(null);
        if (kit == null || !kit.enabled() || !kitService.isQueueEnabled(kit.name())) return;
        KitDefinition shown = kitService.tile(kit);
        if (!shown.enabled() || !kitService.isQueueEnabled(shown.name())) return;
        Material mat = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
        inventory.setItem(MenuScaffold.gridSlot(10),
                ItemBuilder.of(mat)
                        .nameMini(kit.prettyDisplayName())
                        .lore(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-last-selected")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.queue-left-join"))
                        )
                        .glint(true)
                        .action("kit:" + kit.name())
                        .tag(com.rumilance.practice.util.ItemKeys.kitName(), kit.name())
                        .build());
    }

    /** 選ばれたカテゴリのキットをグリッド一杯に並べる(Main と Sub が別画面になった)。 */
    private void renderCategory(Player player, GuiSession session, Inventory inventory) {
        boolean sub = "SUB".equalsIgnoreCase(session.kitCategory());
        List<KitDefinition> kits = kitService.enabled(sub
                ? com.rumilance.practice.model.KitCategory.SUB
                : com.rumilance.practice.model.KitCategory.MAIN);
        int perPage = MenuScaffold.gridPageSize();
        int page = Math.min(session.page(), Math.max(0, (kits.size() - 1) / perPage));
        for (int i = 0; i < perPage && page * perPage + i < kits.size(); i++) {
            inventory.setItem(MenuScaffold.gridSlot(i), kitIcon(player, kits.get(page * perPage + i)));
        }
        paintPaging(player, inventory, page, kits.size());
    }

    private ItemStack kitIcon(Player player, KitDefinition kit) {
        // フォルダ(中メニューを持つキット)は Queue ではデフォルトの子そのものとして並ぶ:
        // 右クリックでも中メニューは開かず、選べるのはデフォルトだけ、という仕様どおり。
        KitDefinition shown = kitService.tile(kit);
        boolean queueOn = shown.enabled() && kitService.isQueueEnabled(kit.name())
                && kitService.isQueueEnabled(shown.name());
        int waiting = queueService.waitingCount(mode(), shown.name(), PlayerPlatform.of(player));

        if (!queueOn) {
            return ItemBuilder.of(Material.BARRIER)
                    .nameMini(kit.prettyDisplayName())
                    .lore(
                            UiTheme.divider(),
                            UiTheme.status(line(player, "gui.queue-closed"), UiTheme.DANGER),
                            UiTheme.line(line(player, "gui.queue-closed-lore"))
                    )
                    .action("decorate")
                    .build();
        }

        // The kit this player is already queued in gets a bright "you are here" marker and
        // its hint flips to leave-queue (the coordinator's join toggles leave when queued).
        QueueService.QueueEntry mine = queueService.get(player.getUniqueId()).orElse(null);
        boolean queuedHere = mine != null && mine.mode() == mode()
                && (shown.name().equalsIgnoreCase(mine.kitId())
                        || kit.name().equalsIgnoreCase(mine.kitId()));

        // The top-level button keeps its original name/icon; the chosen default is shown in
        // lore and supplies the queue's arena/rules/items. A folder is not renamed into its child.
        Material icon = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
        ItemBuilder builder = ItemBuilder.of(icon)
                .nameMini(kit.prettyDisplayName())
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.queue-mode"),
                                line(player, ranked ? "gui.ranked" : "gui.unranked")),
                        UiTheme.labelValue(line(player, "gui.queue-waiting-count"), String.valueOf(waiting))
                );
        if (kitService.isFolder(kit.name())) {
            builder.lore(UiTheme.labelValue(line(player, "gui.innerkit-default-label"),
                    com.rumilance.practice.gui.KitDisplayNames.plain(shown)));
        }
        if (ranked) {
            builder.lore(
                    UiTheme.labelValue(line(player, "gui.queue-arena"), shown.hasFixedArena()
                            ? com.rumilance.practice.util.KitNames.pretty(shown.arenaName())
                            : line(player, "gui.queue-random"))
            );
            addRankedTopLore(player, kit, builder);
        }
        if (queuedHere) {
            builder.lore(
                    UiTheme.blank(),
                    UiTheme.status(line(player, "gui.queue-now"), UiTheme.SUCCESS),
                    UiTheme.hint(line(player, "gui.queue-leave-click"))
            );
        } else {
            builder.lore(
                    UiTheme.blank(),
                    UiTheme.hint(line(player, "gui.queue-left-join")),
                    UiTheme.hint(line(player, "gui.queue-right-preview"))
            );
        }
        return builder
                .glint(queuedHere || waiting > 0)
                .action("kit:" + kit.name())
                .tag(ItemKeys.kitName(), kit.name())
                .build();
    }

    private MatchMode mode() {
        return ranked ? MatchMode.RANKED : MatchMode.UNRANKED;
    }

    // ---------------------------------------------------------------- ranked TOP5 hover lore

    /** Per-kit TOP5 cache so the DB is hit at most once per kit per TTL window. */
    private final java.util.Map<String, java.util.AbstractMap.SimpleEntry<Long, List<Component>>> topCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TOP_CACHE_MS = 10_000L;

    /**
     * Adds the "Ranking" section to a ranked queue kit item: TOP5 eligible players as
     * {@code N. - <face><name> - <bold aqua PT>}. Entries are built once per kit per
     * {@value #TOP_CACHE_MS} ms (cached Components); the viewer's placeholder line is skipped —
     * the click hint below the divider already tells them how to join.
     */
    private void addRankedTopLore(Player viewer, KitDefinition kit, ItemBuilder builder) {
        if (rankedStatsRepository == null) {
            return;
        }
        String fightKit = kitService.playableId(kit.name());
        List<Component> lines = topCache.compute(fightKit, (ignored, cached) -> {
            if (cached != null && System.currentTimeMillis() - cached.getKey() < TOP_CACHE_MS) {
                return cached;
            }
            return new java.util.AbstractMap.SimpleEntry<>(System.currentTimeMillis(), loadTopLines(fightKit));
        }).getValue();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        builder.lore(UiTheme.divider());
        builder.lore(Component.text(line(viewer, "gui.queue-ranking-title"), UiTheme.PRIMARY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        for (Component line : lines) {
            builder.lore(line);
        }
        // 自分が載っていない場合に一目で分かる「あなた: n位 / 計測中」行。
        builder.lore(selfRankLine(viewer, fightKit));
    }

    /** Runs the DB query for one kit's TOP5 (small DBs only — leaderboard usage). */
    private List<Component> loadTopLines(String fightKit) {
        try {
            List<com.rumilance.practice.model.RankedKitStats> top =
                    rankedStatsRepository.topEligibleByKit(fightKit, 5, leaderboardMaxDeviation);
            List<Component> lines = new java.util.ArrayList<>(top.size());
            int rank = 1;
            for (com.rumilance.practice.model.RankedKitStats entry : top) {
                lines.add(rankedTopLine(rank, entry));
                rank++;
            }
            return lines;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** One ranking row: rank number coloured by place, head sprite, white name, aqua bold PT. */
    private static Component rankedTopLine(int rank, com.rumilance.practice.model.RankedKitStats entry) {
        net.kyori.adventure.text.format.TextColor placeColor = switch (rank) {
            case 1 -> net.kyori.adventure.text.format.TextColor.color(0xFFD700); // gold
            case 2 -> net.kyori.adventure.text.format.TextColor.color(0xC0C0C0); // silver
            case 3 -> net.kyori.adventure.text.format.TextColor.color(0xCD7F32); // copper
            default -> net.kyori.adventure.text.format.NamedTextColor.WHITE;     // 4-5 white
        };
        String name = com.rumilance.practice.stats.StatsService.nameOf(entry.uuid());
        return Component.empty()
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .append(Component.text(rank + ". ", placeColor))
                .append(Component.text("- ", net.kyori.adventure.text.format.TextColor.color(0x666666)))
                .append(com.rumilance.practice.headfont.HeadFontService.of(entry.uuid())
                        .color(net.kyori.adventure.text.format.NamedTextColor.WHITE))
                .append(Component.text(name, net.kyori.adventure.text.format.NamedTextColor.WHITE))
                .append(Component.text(" - ", net.kyori.adventure.text.format.TextColor.color(0x666666)))
                .append(Component.text(entry.pt(), net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text("PT", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
    }

    /** "{number}PT" chip in aqua bold, reused by the ranking rows and the self footer. */
    private static Component ptChip(int pt) {
        return Component.empty()
                .append(Component.text(" "))
                .append(Component.text(pt, net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(" PT", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /** Footer row: the viewer's own PT position (or "計測中" while deviation is too high). */
    private Component selfRankLine(Player viewer, String fightKit) {
        try {
            java.util.Optional<com.rumilance.practice.model.RankedKitStats> mine =
                    rankedStatsRepository.find(viewer.getUniqueId(), fightKit);
            if (mine.isEmpty() || mine.get().gamesPlayed() < 1) {
                return UiTheme.hint(line(viewer, "gui.queue-ranking-self-unranked"));
            }
            com.rumilance.practice.model.RankedKitStats stats = mine.get();
            if (!stats.isLeaderboardEligible(leaderboardMaxDeviation)) {
                // 確信不足: 順位には入らないが自分の値は見せる
                return UiTheme.labelValue(line(viewer, "gui.queue-ranking-self-label"),
                        line(viewer, "gui.queue-ranking-self-unranked"))
                        .append(ptChip(stats.pt()));
            }
            int rank = 1;
            boolean included = false;
            try {
                List<com.rumilance.practice.model.RankedKitStats> top =
                        rankedStatsRepository.topEligibleByKit(fightKit, Integer.MAX_VALUE, leaderboardMaxDeviation);
                int pos = 1;
                for (com.rumilance.practice.model.RankedKitStats entry : top) {
                    if (entry.uuid().equals(viewer.getUniqueId())) {
                        rank = pos;
                        included = true;
                        break;
                    }
                    pos++;
                }
            } catch (Exception ignored) {
            }
            return UiTheme.labelValue(line(viewer, "gui.queue-ranking-self-label"),
                    included ? "#" + rank : line(viewer, "gui.queue-ranking-self-unranked"))
                    .append(ptChip(stats.pt()));
        } catch (Exception e) {
            return UiTheme.hint(line(viewer, "gui.queue-ranking-self-unranked"));
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        // 木時差式ボタンは 0.2 秒の押下/復帰を済ませてからここに来る。
        if (action != null && action.startsWith("cat:")) {
            session.setKitCategory(action.substring(4));
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("page:prev".equals(action) || "page:next".equals(action)) {
            session.setPage(Math.max(0, session.page()
                    + ("page:next".equals(action) ? 1 : -1)));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("back".equals(action)) {
            if (session.kitCategory() != null) {
                session.setKitCategory(null);
                session.setPage(0);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
                return;
            }
        }
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action != null && action.startsWith("kit:")) {
            String kitId = action.substring(4);
            if (!kitService.isQueueEnabled(kitId)
                    || !kitService.isQueueEnabled(kitService.playableId(kitId))) {
                sounds.play(player, "error");
                return;
            }
            // Right-click opens a read-only kit preview; left-click (and any other click) joins the queue.
            if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                sounds.play(player, "gui-click");
                player.closeInventory();
                openPreview(player, kitService.playableId(kitId));
                return;
            }
            player.closeInventory();
            if (lastKitTracker != null) {
                lastKitTracker.record(player.getUniqueId(), kitId);
            }
            queueCoordinator.join(player, kitId, mode());
        }
    }
}
