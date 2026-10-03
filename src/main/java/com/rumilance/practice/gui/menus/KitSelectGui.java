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
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * THE kit selection screen — one implementation for both consumers:
 *
 * <ul>
 *   <li><b>Duel flow</b> ({@link #openFor}): picking a kit returns to the duel-request GUI.</li>
 *   <li><b>Queue flow</b> ({@link #openForQueue}): the same chooser/category screens, but a
 *   kit tile joins the ranked/unranked queue and right-click previews the kit. Live queue
 *   depth per kit, the ranked TOP5 hover ranking and queue-closed barriers are queue-only
 *   lore; the layout is identical to the duel picker.</li>
 * </ul>
 *
 * <p>Queue used to render its own look-alike screen ({@code QueueKitGui}); it was a full
 * duplicate that drifted from this one — the two-step 木時差式 chooser now shared here is
 * THE kit screen everywhere.</p>
 */
public final class KitSelectGui extends AbstractGui {

    /** Session attribute marking a queue-mode open: "ranked" / "unranked" (null = duel). */
    public static final String QUEUE_MODE_KEY = "queue-mode";

    private final KitService kitService;
    private DuelRequestGui duelRequestGui;
    private InnerKitSelectGui innerKitSelectGui;
    private volatile com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker;

    // ---- queue mode wiring (null-safe: every queue feature degrades without it) ----
    private QueueService queueService;
    private QueueCoordinator queueCoordinator;
    private KitPreviewGui previewGui;
    private com.rumilance.practice.database.repository.RankedStatsRepository rankedStatsRepository;
    private double leaderboardMaxDeviation = 115.0d;

    public KitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_SELECT, 6, true);
        this.kitService = kitService;
    }

    public void setLastKitTracker(com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker) {
        this.lastKitTracker = lastKitTracker;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void setInnerKitSelectGui(InnerKitSelectGui innerKitSelectGui) {
        this.innerKitSelectGui = innerKitSelectGui;
    }

    /** Queue-mode dependencies; without them queue tiles fall back to the plain picker. */
    public void setQueueServices(QueueService queueService, QueueCoordinator queueCoordinator) {
        this.queueService = queueService;
        this.queueCoordinator = queueCoordinator;
    }

    public void setPreviewGui(KitPreviewGui previewGui) {
        this.previewGui = previewGui;
    }

    /** Wires the TOP5 hover ranking for the ranked queue (unused in the duel flow). */
    public void setRankedTopLore(com.rumilance.practice.database.repository.RankedStatsRepository repository,
                                 double leaderboardMaxDeviation) {
        this.rankedStatsRepository = repository;
        this.leaderboardMaxDeviation = leaderboardMaxDeviation;
    }

    // ---------------------------------------------------------------- opening

    public void openFor(Player player, GuiSession parent) {
        openFor(player, parent, null, 0);
    }

    /** Return from a child menu to the SAME parent category/page before first render. */
    public void openFor(Player player, GuiSession parent, String category, int page) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setKitCategory(category);
        session.setPage(Math.max(0, page));
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFirstTo(parent.firstTo());
        session.setFromBattleMenu(parent.fromBattleMenu());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    /** Opens THE kit screen in queue mode: left-click joins, right-click previews. */
    public void openForQueue(Player player, boolean ranked) {
        openWithSession(player, session -> {
            session.put(QUEUE_MODE_KEY, ranked ? "ranked" : "unranked");
            session.setRanked(ranked);
        });
    }

    private boolean queueMode(GuiSession session) {
        return session.get(QUEUE_MODE_KEY, String.class) != null;
    }

    private boolean rankedQueue(GuiSession session) {
        return "ranked".equals(session.get(QUEUE_MODE_KEY, String.class));
    }

    private MatchMode mode(GuiSession session) {
        return rankedQueue(session) ? MatchMode.RANKED : MatchMode.UNRANKED;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        if (queueMode(session)) {
            return t(player, rankedQueue(session) ? "gui.ranked-queue" : "gui.unranked-queue")
                    .color(rankedQueue(session) ? UiTheme.PRIMARY : UiTheme.SECONDARY);
        }
        return t(player, "gui.kit-select-title").color(UiTheme.PRIMARY);
    }

    /**
     * Two-step picker, rebuilt for the 2026-09-28 GUI refresh with generous whitespace and
     * left-right symmetry: step 1 is the 木時差式 (delayed wooden-button) MAIN KITS/SUB KITS
     * branch on the centre row; step 2 is one centred row of that category's kits (folders:
     * LEFT = default child, RIGHT = child list) with mirrored page arrows at the row ends
     * and the kit currently selected glowing.
     */
    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        if (session.kitCategory() == null) {
            renderChooser(player, session, inventory);
        } else {
            renderCategory(player, session, inventory);
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
        }
        if (queueMode(session)) {
            paintNav(player, session, inventory);
        }
    }

    private static int CHOOSER_ROW = 2;

    /** The branch screen: two big centred category buttons (delayed-press 木時差式). */
    private void renderChooser(Player player, GuiSession session, Inventory inventory) {
        inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 3),
                categoryTile(player, com.rumilance.practice.model.KitCategory.MAIN,
                        "gui.kit-main-button", UiTheme.SUCCESS));
        inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 5),
                categoryTile(player, com.rumilance.practice.model.KitCategory.SUB,
                        "gui.kit-sub-button", UiTheme.SECONDARY));
        // 前回選択したキットを MAIN と SUB の間に表示 (クリックで即選択/即キュー参加)
        renderLastSelectedKit(player, session, inventory);
        if (queueMode(session)) {
            inventory.setItem(GuiSlots.slot(5, 1), ItemBuilder.of(Material.CLOCK)
                    .name(t(player, "gui.queue-waiting").color(UiTheme.MUTED))
                    .lore(UiTheme.labelValue(line(player, "gui.queue-count"),
                            String.valueOf(totalWaiting(player, session))))
                    .action("decorate")
                    .build());
        }
    }

    /** Sum of every queue-enabled kit's live waiting count for the viewer's platform. */
    private int totalWaiting(Player player, GuiSession session) {
        QueueService queues = queueService;
        if (queues == null) {
            return 0;
        }
        PlayerPlatform platform = PlayerPlatform.of(player);
        MatchMode matchMode = mode(session);
        int total = 0;
        for (KitDefinition kit : kitService.enabled()) {
            if (!kitService.isQueueEnabled(kit.name())
                    || !kitService.isQueueEnabled(kitService.playableId(kit.name()))) {
                continue;
            }
            total += queues.waitingCount(matchMode, kitService.playableId(kit.name()), platform);
        }
        return total;
    }

    private void renderLastSelectedKit(Player player, GuiSession session, Inventory inventory) {
        com.rumilance.practice.kit.LastSelectedKitTracker tracker = lastKitTracker;
        if (tracker == null) return;
        String lastKitId = tracker.get(player.getUniqueId());
        if (lastKitId == null) return;
        var kit = kitService.get(lastKitId).orElse(null);
        if (kit == null || !kit.enabled()) return;
        if (queueMode(session)) {
            // Queue variant: only queue-enabled kits, click = join the queue.
            if (!kitService.isQueueEnabled(kit.name())
                    || !kitService.isQueueEnabled(kitService.playableId(kit.name()))) return;
            KitDefinition shown = kitService.tile(kit);
            if (!shown.enabled() || !kitService.isQueueEnabled(shown.name())) return;
            Material mat = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
            inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 4),
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
                            .tag(ItemKeys.kitName(), kit.name())
                            .build());
            return;
        }
        Material mat = Material.matchMaterial(kit.icon());
        if (mat == null) mat = Material.DIAMOND_SWORD;
        inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 4),
                ItemBuilder.of(mat)
                        .name(Component.text(KitNames.pretty(kit.name()), UiTheme.VALUE)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-last-selected")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-click-select"))
                        )
                        .glint(true)
                        .action("pick:" + kit.name())
                        .build());
    }

    private ItemStack categoryTile(Player player, com.rumilance.practice.model.KitCategory category,
                                   String nameKey, net.kyori.adventure.text.format.TextColor color) {
        int count = kitService.enabled(category).size();
        return com.rumilance.practice.gui.KitSections.categoryButton(category,
                t(player, nameKey).color(color),
                java.util.List.of(
                        UiTheme.labelValue(line(player, "gui.kit-count-label"), String.valueOf(count)),
                        UiTheme.hint(line(player, "gui.kit-click-select"))));
    }

    private static final int CATEGORY_LABEL_SLOT = 4 + 1 * 9;   // (1,4) header
    private static final int CATEGORY_FIRST_SLOT = 1 + 2 * 9;   // (2,1) → 7 slots row
    private static final int CATEGORY_CAPACITY = 7;

    /** One category: header centred at (1,4); kits centered on row 2; arrows at the row ends. */
    private void renderCategory(Player player, GuiSession session, Inventory inventory) {
        boolean main = !("SUB".equalsIgnoreCase(session.kitCategory()));
        com.rumilance.practice.model.KitCategory category = main
                ? com.rumilance.practice.model.KitCategory.MAIN
                : com.rumilance.practice.model.KitCategory.SUB;
        List<KitDefinition> kits = queueMode(session)
                ? queuePool(session, category)
                : kitService.enabled(category);
        inventory.setItem(CATEGORY_LABEL_SLOT, ItemBuilder.of(
                        com.rumilance.practice.gui.KitSections.icon(category))
                .name(t(player, main ? "gui.kit-main-button" : "gui.kit-sub-button")
                        .color(main ? UiTheme.SUCCESS : UiTheme.SECONDARY))
                .lore(UiTheme.labelValue(line(player, "gui.kit-count-label"),
                        String.valueOf(kits.size())))
                .action("decorate")
                .build());
        int page = Math.min(Math.max(0, session.page()), Math.max(0, (kits.size() - 1) / CATEGORY_CAPACITY));
        if (page != session.page()) {
            session.setPage(page);
        }
        String current = session.selectedKit();
        int from = page * CATEGORY_CAPACITY;
        for (int i = 0; i < CATEGORY_CAPACITY && from + i < kits.size(); i++) {
            KitDefinition kit = kits.get(from + i);
            inventory.setItem(CATEGORY_FIRST_SLOT + i,
                    queueMode(session)
                            ? queueKitIcon(player, session, kit)
                            : kitIcon(player, session, kit, current));
        }
        if (page > 0) {
            inventory.setItem(2 * 9,
                    ItemBuilder.action(UiTheme.PREV_PAGE, t(player, "menu.page-prev"), "page:prev"));
        }
        if (from + CATEGORY_CAPACITY < kits.size()) {
            inventory.setItem((2 + 1) * 9 - 1,
                    ItemBuilder.action(UiTheme.NEXT_PAGE, t(player, "menu.page-next"), "page:next"));
        }
    }

    /** The category's queue pool: enabled kits of the category, ranked-filtered in ranked queue. */
    private List<KitDefinition> queuePool(GuiSession session,
                                          com.rumilance.practice.model.KitCategory category) {
        List<KitDefinition> kits = kitService.enabled(category);
        if (rankedQueue(session)) {
            kits = kits.stream().filter(KitDefinition::ranked).toList();
        }
        return kits;
    }

    private ItemStack kitIcon(Player player, GuiSession session, KitDefinition kit, String current) {
        // フォルダになっても親の名前とアイコンはそのまま。左=既定の子、右=子一覧。
        List<KitDefinition> children = kitService.children(kit.name());
        Material mat = Material.matchMaterial(kit.icon());
        boolean selected = kit.name().equalsIgnoreCase(current)
                || kitService.get(current).map(k -> kit.name().equalsIgnoreCase(k.parent())).orElse(false);
        List<Component> lore = new ArrayList<>(List.of(
                UiTheme.line(kit.prettyDisplayName())));
        if (!children.isEmpty()) {
            lore.add(UiTheme.hint(line(player, "gui.innerkit-right-hint")));
        }
        lore.add(selected
                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                : UiTheme.hint(line(player, "gui.kit-click-select")));
        return ItemBuilder.of(mat == null ? Material.DIAMOND_SWORD : mat)
                .name(Component.text(KitNames.pretty(kit.name()),
                        selected ? UiTheme.SUCCESS : UiTheme.VALUE))
                .lore(lore.toArray(new Component[0]))
                .glint(selected)
                .action("pick:" + kit.name())
                .build();
    }

    /**
     * Queue tile: the kit's own name/icon, live queue depth, folder default, ranked arena and
     * TOP5 hover ranking; already-queued kits glow with a leave hint, queue-closed kits are
     * barriers. LEFT = join, RIGHT = preview.
     */
    private ItemStack queueKitIcon(Player player, GuiSession session, KitDefinition kit) {
        // フォルダ(中メニューを持つキット)は Queue ではデフォルトの子そのものとして並ぶ:
        // 右クリックでも中メニューは開かず、選べるのはデフォルトだけ、という仕様どおり。
        KitDefinition shown = kitService.tile(kit);
        boolean queueOn = shown.enabled() && kitService.isQueueEnabled(kit.name())
                && kitService.isQueueEnabled(shown.name());
        int waiting = queueService == null ? 0
                : queueService.waitingCount(mode(session), shown.name(), PlayerPlatform.of(player));

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

        // Multi-queue: キュー参加中のキットはグロー + 「退出」表示
        boolean queuedHere = queueService != null
                && (queueService.isQueuedFor(player.getUniqueId(), shown.name(), mode(session))
                || queueService.isQueuedFor(player.getUniqueId(), kit.name(), mode(session)));

        // The top-level button keeps its original name/icon; the chosen default is shown in
        // lore and supplies the queue's arena/rules/items. A folder is not renamed into its child.
        Material icon = ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD);
        ItemBuilder builder = ItemBuilder.of(icon)
                .nameMini(kit.prettyDisplayName())
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.queue-mode"),
                                line(player, rankedQueue(session) ? "gui.ranked" : "gui.unranked")),
                        UiTheme.labelValue(line(player, "gui.queue-waiting-count"), String.valueOf(waiting))
                );
        if (kitService.isFolder(kit.name())) {
            builder.lore(UiTheme.labelValue(line(player, "gui.innerkit-default-label"),
                    com.rumilance.practice.gui.KitDisplayNames.plain(shown)));
        }
        if (rankedQueue(session)) {
            builder.lore(
                    UiTheme.labelValue(line(player, "gui.queue-arena"), shown.hasFixedArena()
                            ? KitNames.pretty(shown.arenaName())
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

    /** Opens the read-only kit preview (queue right-click). */
    public void openPreview(Player player, String kitId) {
        if (previewGui == null) {
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), previewGui.type(), previewGui.rows());
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(previewGui, player, session);
        sounds.play(player, "gui-open");
    }

    // ---------------------------------------------------------------- clicks

    /** Folder RIGHT-click opens its child-kit list (duel); in queue mode RIGHT previews. */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("kit:") && queueMode(session)) {
            // Queue: RIGHT opens a read-only kit preview (no inner-kit menus in queue).
            handleQueueKitClick(player, session, action.substring(4), clickType);
            return;
        }
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("pick:")
                && innerKitSelectGui != null && !queueMode(session)) {
            String kitId = action.substring("pick:".length());
            if (kitService.isFolder(kitId)) {
                sounds.play(player, "gui-click");
                session.setNavigatingAway(true);
                innerKitSelectGui.openForDuel(player, session, kitId);
                return;
            }
        }
        handleClick(player, session, inventory, slot, action);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action != null && action.startsWith("cat:")) {
            session.setKitCategory(action.substring(4));
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("page:")) {
            // Per-category paging on the kit row: page:prev / page:next.
            boolean next = "next".equals(action.substring(5));
            boolean main = !("SUB".equalsIgnoreCase(session.kitCategory()));
            com.rumilance.practice.model.KitCategory category = main
                    ? com.rumilance.practice.model.KitCategory.MAIN
                    : com.rumilance.practice.model.KitCategory.SUB;
            int poolSize = queueMode(session)
                    ? queuePool(session, category).size()
                    : kitService.enabled(category).size();
            int pages = Math.max(1, (poolSize + CATEGORY_CAPACITY - 1) / CATEGORY_CAPACITY);
            session.setPage(Math.min(Math.max(0, session.page() + (next ? 1 : -1)), pages - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("back".equals(action) || "close".equals(action)) {
            if (session.kitCategory() != null && "back".equals(action)) {
                // Back from a category returns to the MAIN/SUB branch, not all the way out.
                session.setKitCategory(null);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
                return;
            }
            if (queueMode(session)) {
                // Queue screen: no duel GUI to return to — back/close just leave the screen.
                sounds.play(player, "gui-back");
                player.closeInventory();
                return;
            }
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if (action != null && action.startsWith("kit:")) {
            handleQueueKitClick(player, session, action.substring(4),
                    org.bukkit.event.inventory.ClickType.LEFT);
            return;
        }
        if (action != null && action.startsWith("pick:")) {
            // フォルダを選んだらデフォルトの子で進む(中メニューから選んだ子はそのまま)。
            String chosen = kitService.playableId(action.substring(5));
            session.setSelectedKit(chosen);
            if (lastKitTracker != null) {
                lastKitTracker.record(player.getUniqueId(), chosen);
            }
            sounds.play(player, "select");
            returnToDuel(player, session);
        }
    }

    /** Queue-mode kit tile: RIGHT = preview, LEFT (and any other click) = join the queue. */
    private void handleQueueKitClick(Player player, GuiSession session, String kitId,
                                     org.bukkit.event.inventory.ClickType clickType) {
        if (queueCoordinator == null) {
            sounds.play(player, "error");
            return;
        }
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
        if (lastKitTracker != null) {
            lastKitTracker.record(player.getUniqueId(), kitId);
        }
        // closeInventory FIRST: GuiListener.onClose で OPENING_GUI → LOBBY に戻す。
        // そうしないと stateManager.transition(QUEUED) が例外→即 leave される。
        player.closeInventory();
        queueCoordinator.join(player, kitId, mode(session));
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        player.closeInventory();
        // Single carry-everything return: openFor builds a FRESH session, so returning through
        // it used to wipe the KB choice, combat mode and FT picked before the kit pick.
        duelRequestGui.reopenCarrying(player, session);
    }
}
