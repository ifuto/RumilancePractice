package com.rumilance.practice.queue;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.glicko.GlickoCalculator;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central queue matching + actionbar wait timer (not per-player repeating tasks for matching).
 */
public final class QueueCoordinator {

    private final Plugin plugin;
    private final QueueService queueService;
    private final MatchService matchService;
    private final KitService kitService;
    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;
    private final SoundService soundService;
    private final MessageService messageService;
    /** Shared anti-spam cadence (wired in FeatureBootstrap together with the sign queue). */
    private QueueClickGuard clickGuard = new QueueClickGuard();
    private final RankedStatsRepository rankedStatsRepository;
    private final AsyncExecutor asyncExecutor;
    private final RuntimeFlags runtimeFlags;
    private final PluginSettings settings;
    private final boolean blockSameIp;
    private final boolean avoidRecent;
    private final RankedQueueState rankedState;
    private BukkitTask matchTask;
    private BukkitTask actionBarTask;
    private com.rumilance.practice.ffa.FfaService ffaService;
    private com.rumilance.practice.team.TeamService teamService;
    private volatile com.rumilance.practice.signqueue.SignQueueService signQueueService;

    public QueueCoordinator(
            Plugin plugin,
            QueueService queueService,
            MatchService matchService,
            KitService kitService,
            LobbyService lobbyService,
            PlayerStateManager stateManager,
            SoundService soundService,
            RankedStatsRepository rankedStatsRepository,
            AsyncExecutor asyncExecutor,
            RuntimeFlags runtimeFlags,
            PluginSettings settings,
            boolean blockSameIp,
            boolean avoidRecent,
            MessageService messageService,
            RankedQueueState rankedState
    ) {
        this.plugin = plugin;
        this.queueService = queueService;
        this.matchService = matchService;
        this.kitService = kitService;
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
        this.soundService = soundService;
        this.rankedStatsRepository = rankedStatsRepository;
        this.asyncExecutor = asyncExecutor;
        this.runtimeFlags = runtimeFlags;
        this.settings = settings;
        this.blockSameIp = blockSameIp;
        this.avoidRecent = avoidRecent;
        this.messageService = messageService;
        this.rankedState = rankedState;
    }

    public void setFfaService(com.rumilance.practice.ffa.FfaService ffaService) {
        this.ffaService = ffaService;
    }

    private volatile com.rumilance.practice.alt.AltDetectionService altDetectionService;

    public void setAltDetectionService(
            com.rumilance.practice.alt.AltDetectionService altDetectionService) {
        this.altDetectionService = altDetectionService;
    }

    private volatile com.rumilance.practice.combat.CombatStyleService combatStyleService;

    public void setCombatStyleService(com.rumilance.practice.combat.CombatStyleService service) {
        this.combatStyleService = service;
    }

    public void start() {
        matchTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMatchmaking, 40L, 40L);
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActionBars, 20L, 20L);
    }

    public void stop() {
        if (matchTask != null) {
            matchTask.cancel();
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        queueService.clearAll();
    }

    public void setTeamService(com.rumilance.practice.team.TeamService teamService) {
        this.teamService = teamService;
    }

    public void setSignQueueService(com.rumilance.practice.signqueue.SignQueueService signQueueService) {
        this.signQueueService = signQueueService;
    }

    public RankedQueueState rankedState() {
        return rankedState;
    }

    /** FeatureBootstrap injects the guard shared with the sign-queue service. */
    public void setClickGuard(QueueClickGuard clickGuard) {
        if (clickGuard != null) {
            this.clickGuard = clickGuard;
        }
    }

    public void join(Player player, String kitId, MatchMode mode) {
        if (player == null) {
            return;
        }
        QueueClickGuard.Decision gate = clickGuard.evaluate(player.getUniqueId(),
                System.currentTimeMillis());
        if (!gate.allowed()) {
            if (gate.warning()) {
                player.sendActionBar(messageService.render(player, "queue.slow-down"));
            }
            return;
        }
        if (teamService != null && teamService.teamOf(player.getUniqueId()).isPresent()) {
            messageService.send(player, "party.solo-only");
            return;
        }
        if (mode == MatchMode.FFA) {
            return;
        }
        if (mode == MatchMode.RANKED && !rankedState.isEnabled()) {
            messageService.send(player, "queue.ranked-locked",
                    MessageService.tags("count",
                            String.valueOf(rankedState.uniqueJoinCount()),
                            "threshold",
                            String.valueOf(RankedQueueState.AUTO_UNLOCK_THRESHOLD)));
            return;
        }
        if (runtimeFlags.maintenance() && !player.hasPermission("rumilance.admin")) {
            messageService.send(player, "queue.maintenance");
            return;
        }
        if (mode == MatchMode.RANKED) {
            int maxPing = settings.queueMaxRankedPingMs();
            if (maxPing > 0 && player.getPing() > maxPing) {
                messageService.send(player, "queue.high-ping",
                        MessageService.tags("ping", String.valueOf(player.getPing()),
                                "max", String.valueOf(maxPing)));
                return;
            }
        }
        // Queue has exactly ONE option per top-level tile. Even a direct /queue <child> command
        // resolves through its folder: a non-default child cannot bypass the no-submenu rule.
        var requested = kitService.get(kitId).orElse(null);
        var topLevel = requested != null && requested.isChild()
                ? kitService.get(requested.parent()).orElse(null) : requested;
        if (topLevel == null || !topLevel.enabled() || !kitService.isQueueEnabled(topLevel.name())) {
            messageService.send(player, "queue.kit-disabled");
            return;
        }
        final String fightKitId = kitService.playableId(topLevel.name());
        if (!kitService.isQueueEnabled(fightKitId) || kitService.get(fightKitId).filter(k -> k.enabled()).isEmpty()) {
            messageService.send(player, "queue.kit-disabled");
            return;
        }
        if (ffaService != null && ffaService.isInFfa(player.getUniqueId())) {
            messageService.send(player, "queue.cannot-join");
            return;
        }
        // Joining the regular queue pulls the player out of the sign queue if they were
        // waiting there (one queue at a time).
        if (signQueueService != null) {
            signQueueService.leaveIfQueued(player);
        }
        // Multi-queue: 同一キットクリック → そのキットだけ退出。それ以外 → 新規追加。
        if (queueService.isQueuedFor(player.getUniqueId(), fightKitId, mode)) {
            queueService.leaveKit(player.getUniqueId(), fightKitId, mode, PlayerPlatform.of(player));
            soundService.play(player, "queue-leave");
            messageService.send(player, "queue.left",
                    MessageService.tags("kit", kitService.displayName(fightKitId)));
            if (!queueService.isQueued(player.getUniqueId())) {
                stateManager.resetToLobby(player.getUniqueId());
            }
            return;
        }
        // Already in some queue(s) but joining a new kit: state is QUEUED_* which is fine.
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI
                && state != PlayerState.QUEUED_RANKED && state != PlayerState.QUEUED_UNRANKED) {
            messageService.send(player, "queue.cannot-join");
            return;
        }

        AtomicReference<Integer> pt = new AtomicReference<>(GlickoCalculator.DEFAULT_RATING_INT);
        if (mode == MatchMode.RANKED) {
            try {
                pt.set(rankedStatsRepository.find(player.getUniqueId(), fightKitId)
                        .map(RankedKitStats::pt)
                        .orElse(GlickoCalculator.DEFAULT_RATING_INT));
            } catch (Exception ignored) {
                pt.set(GlickoCalculator.DEFAULT_RATING_INT);
            }
        }

        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        PlayerPlatform platform = PlayerPlatform.of(player);
        if (!queueService.join(player.getUniqueId(), fightKitId, mode, pt.get(), ip, platform)) {
            messageService.send(player, "queue.already-queued");
            return;
        }
        // 同一IPランク制限は「絶対にペアにならない」が動作 — 待機中に同一IP相手がいる
        // ことだけは黙ってはめられないよう参加直後に一度だけ案内する。
        if (blockSameIp && mode == MatchMode.RANKED && ip != null
                && queueService.hasSameIpWaiter(fightKitId, mode, platform,
                        ip, player.getUniqueId())) {
            messageService.send(player, "queue.same-ip-notice");
        }

        try {
            stateManager.transition(player.getUniqueId(),
                    mode == MatchMode.RANKED ? PlayerState.QUEUED_RANKED : PlayerState.QUEUED_UNRANKED);
        } catch (Exception e) {
            queueService.leave(player.getUniqueId());
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        giveLeaveItem(player);
        soundService.play(player, "queue-joined");
        messageService.send(player, "queue.joined",
                MessageService.tags("mode", messageService.modeWord(player, mode == MatchMode.RANKED), "kit", fightKitId));
    }

    public void leave(Player player) {
        queueService.leave(player.getUniqueId()).ifPresent(entry -> {
            stateManager.resetToLobby(player.getUniqueId());
            lobbyService.applyLobbyInventory(player);
            soundService.play(player, "queue-leave");
            messageService.send(player, "queue.left");
        });
    }

    /** 1つのキットだけキューから抜く (MultiQueueGui 用)。 */
    public void leaveKit(Player player, String kitId, MatchMode mode) {
        if (queueService.leaveKit(player.getUniqueId(), kitId, mode, PlayerPlatform.of(player))) {
            soundService.play(player, "queue-leave");
            messageService.send(player, "queue.left",
                    MessageService.tags("kit", kitService.displayName(kitId)));
            if (!queueService.isQueued(player.getUniqueId())) {
                stateManager.resetToLobby(player.getUniqueId());
                lobbyService.applyLobbyInventory(player);
            }
        }
    }

    /** 全キューから退出 (MultiQueueGui 用)。 */
    public void leaveAll(Player player) {
        if (queueService.isQueued(player.getUniqueId())) {
            leave(player);
        }
    }

    /** 指定モードの全有効キットにキュー参加 (MultiQueueGui の「全部参加」用)。 */
    public void joinAll(Player player, MatchMode mode) {
        for (com.rumilance.practice.model.KitDefinition kit : kitService.enabled()) {
            String id = kitService.playableId(kit.name());
            if (kitService.isQueueEnabled(id)) {
                join(player, id, mode);
            }
        }
    }

    private void tickMatchmaking() {
        if (runtimeFlags.maintenance()) {
            return;
        }
        // Belt-and-suspenders: evict any queued player who is no longer online (e.g. missed by the
        // quit hook) BEFORE polling, so an offline entry can never be paired with a live waiter.
        queueService.pruneOffline();
        com.rumilance.practice.alt.AltDetectionService alt = altDetectionService;
        List<QueueService.MatchPair> pairs = queueService.pollMatches(blockSameIp, avoidRecent,
                Instant.now(), alt == null ? null : alt::restrictedPair);
        for (QueueService.MatchPair pair : pairs) {
            matchService.startDuel(
                    pair.a().playerId(),
                    pair.b().playerId(),
                    pair.a().kitId(),
                    pair.a().mode(),
                    1
            );
            // Queue: Bedrock vs Bedrock → 自動で Bedrock 戦闘仕様を適用
            if (combatStyleService != null
                    && pair.a().platform() == PlayerPlatform.BEDROCK
                    && pair.b().platform() == PlayerPlatform.BEDROCK) {
                String matchId = matchService.findActiveMatchId(pair.a().playerId())
                        .orElse(null);
                if (matchId != null) {
                    combatStyleService.setMatchMode(matchId,
                            com.rumilance.practice.combat.CombatMode.BEDROCK);
                    combatStyleService.applyToMatch(matchId,
                            java.util.List.of(pair.a().playerId(), pair.b().playerId()));
                }
            }
        }
    }

    private void tickActionBars() {
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            queueService.get(player.getUniqueId()).ifPresent(entry -> {
                long waited = now.getEpochSecond() - entry.joinedAt().getEpochSecond();
                int waiting = queueService.waitingCount(entry.mode(), entry.kitId(), entry.platform());
                String platformLabel = entry.platform() == PlayerPlatform.BEDROCK ? "BE" : "Java";
                player.sendActionBar(messageService.render(player, "queue.waiting-bar",
                        MessageService.tags("kit", entry.kitId(), "platform", platformLabel,
                                "seconds", String.valueOf(waited),
                                "waiting", String.valueOf(waiting))));
            });
        }
    }

    private void giveLeaveItem(Player player) {
        // Slot 4: Queue Leave (赤)
        ItemStack leaveItem = new ItemStack(Material.RED_DYE);
        ItemMeta leaveMeta = leaveItem.getItemMeta();
        leaveMeta.displayName(messageService.render(player, "menu.leave-queue")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        leaveMeta.getPersistentDataContainer().set(ItemKeys.leaveQueue(), PersistentDataType.BYTE, (byte) 1);
        leaveItem.setItemMeta(leaveMeta);
        player.getInventory().setItem(4, leaveItem);

        // Slot 3: Queue Select (MultiQueueGui を開く — 黄色)
        ItemStack selectItem = new ItemStack(Material.CLOCK);
        ItemMeta selectMeta = selectItem.getItemMeta();
        selectMeta.displayName(Component.text("Queue Select", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        selectMeta.getPersistentDataContainer().set(ItemKeys.queueSelect(), PersistentDataType.BYTE, (byte) 1);
        selectItem.setItemMeta(selectMeta);
        player.getInventory().setItem(3, selectItem);
    }
}
