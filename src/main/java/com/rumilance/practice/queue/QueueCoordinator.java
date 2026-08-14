package com.rumilance.practice.queue;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.RankedKitStats;
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
    private final RankedStatsRepository rankedStatsRepository;
    private final AsyncExecutor asyncExecutor;
    private final RuntimeFlags runtimeFlags;
    private final PluginSettings settings;
    private final boolean blockSameIp;
    private final boolean avoidRecent;
    private BukkitTask matchTask;
    private BukkitTask actionBarTask;

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
            MessageService messageService
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

    public void join(Player player, String kitId, MatchMode mode) {
        if (mode == MatchMode.FFA) {
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
        if (!kitService.isQueueEnabled(kitId) || kitService.get(kitId).filter(k -> k.enabled()).isEmpty()) {
            messageService.send(player, "queue.kit-disabled");
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            messageService.send(player, "queue.cannot-join");
            return;
        }
        if (queueService.isQueued(player.getUniqueId())) {
            leave(player);
            return;
        }

        AtomicReference<Integer> elo = new AtomicReference<>(1000);
        if (mode == MatchMode.RANKED) {
            try {
                elo.set(rankedStatsRepository.find(player.getUniqueId(), kitId)
                        .map(RankedKitStats::elo)
                        .orElse(1000));
            } catch (Exception ignored) {
                elo.set(1000);
            }
        }

        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        if (!queueService.join(player.getUniqueId(), kitId, mode, elo.get(), ip)) {
            messageService.send(player, "queue.already-queued");
            return;
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
                MessageService.tags("mode", messageService.modeWord(player, mode == MatchMode.RANKED), "kit", kitId));
    }

    public void leave(Player player) {
        queueService.leave(player.getUniqueId()).ifPresent(entry -> {
            stateManager.resetToLobby(player.getUniqueId());
            lobbyService.applyLobbyInventory(player);
            soundService.play(player, "queue-leave");
            messageService.send(player, "queue.left");
        });
    }

    private void tickMatchmaking() {
        if (runtimeFlags.maintenance()) {
            return;
        }
        List<QueueService.MatchPair> pairs = queueService.pollMatches(blockSameIp, avoidRecent, Instant.now());
        for (QueueService.MatchPair pair : pairs) {
            matchService.startDuel(
                    pair.a().playerId(),
                    pair.b().playerId(),
                    pair.a().kitId(),
                    pair.a().mode(),
                    kitService.get(pair.a().kitId()).map(k -> k.arenaTerrain()).orElse(null),
                    1
            );
        }
    }

    private void tickActionBars() {
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            queueService.get(player.getUniqueId()).ifPresent(entry -> {
                long waited = now.getEpochSecond() - entry.joinedAt().getEpochSecond();
                int waiting = queueService.waitingCount(entry.mode(), entry.kitId());
                player.sendActionBar(Component.text("Queue " + entry.kitId() + " | "
                        + waited + "s | " + waiting + " waiting", NamedTextColor.AQUA));
            });
        }
    }

    private void giveLeaveItem(Player player) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Leave Queue", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.leaveQueue(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        player.getInventory().setItem(4, item);
    }
}
