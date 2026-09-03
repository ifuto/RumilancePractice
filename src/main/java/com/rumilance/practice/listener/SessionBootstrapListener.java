package com.rumilance.practice.listener;

import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.join.JoinQuitMessages;
import com.rumilance.practice.join.WelcomeTitle;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.PlayerData;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.session.PlayerSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Creates sessions on join, resets to lobby, and cleans up on quit.
 */
public final class SessionBootstrapListener implements Listener {

    private final SessionManager sessionManager;
    private final PlayerStateManager playerStateManager;
    private final LobbyService lobbyService;
    private final String defaultLocale;
    private final PlayerRepository playerRepository;
    private final KitLayoutCache layoutCache;
    private final SettingsService settingsService;
    private final AsyncExecutor asyncExecutor;
    private final Plugin plugin;
    private final MessageService messageService;
    private final RankService rankService;
    private final ChatBanService chatBanService;

    public SessionBootstrapListener(
            SessionManager sessionManager,
            PlayerStateManager playerStateManager,
            LobbyService lobbyService,
            String defaultLocale,
            PlayerRepository playerRepository,
            KitLayoutCache layoutCache,
            SettingsService settingsService,
            AsyncExecutor asyncExecutor
    ) {
        this(sessionManager, playerStateManager, lobbyService, defaultLocale, playerRepository,
                layoutCache, settingsService, asyncExecutor, null, null, null);
    }

    public SessionBootstrapListener(
            SessionManager sessionManager,
            PlayerStateManager playerStateManager,
            LobbyService lobbyService,
            String defaultLocale,
            PlayerRepository playerRepository,
            KitLayoutCache layoutCache,
            SettingsService settingsService,
            AsyncExecutor asyncExecutor,
            Plugin plugin,
            MessageService messageService
    ) {
        this(sessionManager, playerStateManager, lobbyService, defaultLocale, playerRepository,
                layoutCache, settingsService, asyncExecutor, plugin, messageService, null);
    }

    public SessionBootstrapListener(
            SessionManager sessionManager,
            PlayerStateManager playerStateManager,
            LobbyService lobbyService,
            String defaultLocale,
            PlayerRepository playerRepository,
            KitLayoutCache layoutCache,
            SettingsService settingsService,
            AsyncExecutor asyncExecutor,
            Plugin plugin,
            MessageService messageService,
            RankService rankService
    ) {
        this(sessionManager, playerStateManager, lobbyService, defaultLocale, playerRepository,
                layoutCache, settingsService, asyncExecutor, plugin, messageService, rankService, null);
    }

    public SessionBootstrapListener(
            SessionManager sessionManager,
            PlayerStateManager playerStateManager,
            LobbyService lobbyService,
            String defaultLocale,
            PlayerRepository playerRepository,
            KitLayoutCache layoutCache,
            SettingsService settingsService,
            AsyncExecutor asyncExecutor,
            Plugin plugin,
            MessageService messageService,
            RankService rankService,
            ChatBanService chatBanService
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.playerStateManager = Objects.requireNonNull(playerStateManager, "playerStateManager");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
        this.playerRepository = playerRepository;
        this.layoutCache = layoutCache;
        this.settingsService = settingsService;
        this.asyncExecutor = asyncExecutor;
        this.plugin = plugin;
        this.messageService = messageService;
        this.rankService = rankService;
        this.chatBanService = chatBanService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        JoinQuitMessages.apply(event);
        Player player = event.getPlayer();
        String locale = player.locale() != null ? player.locale().toString().toLowerCase(Locale.ROOT) : defaultLocale;
        PlayerSession session = sessionManager.createSession(player.getUniqueId(), locale);
        var settings = settingsService.get(player.getUniqueId());
        session.setSoundsEnabled(settings.soundsEnabled());
        session.setScoreboardEnabled(settings.scoreboardEnabled());
        playerStateManager.initialize(player.getUniqueId());
        if (rankService != null) {
            rankService.load(player.getUniqueId());
        }
        layoutCache.preload(player.getUniqueId());
        if (chatBanService != null) {
            chatBanService.warmCache(player.getUniqueId());
            // Tell the player if they were chat-banned while offline (or have an active ban they
            // have not yet been informed about). Delayed a tick so cache warm / repository can
            // resolve the record.
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> chatBanService.notifyOnJoin(player), 20L);
        }
        // Teleporting during PlayerJoinEvent freezes look + movement on the client.
        Runnable lobbyAndWelcome = () -> {
            if (!player.isOnline()) {
                return;
            }
            lobbyService.sendToLobby(player);
            if (plugin != null && messageService != null) {
                try {
                    WelcomeTitle.play(plugin, player,
                            messageService.render(messageService.resolveLocale(player), "welcome.subtitle"));
                    messageService.sendRaw(player, "welcome.record-tip");
                } catch (Exception e) {
                    WelcomeTitle.play(plugin, player);
                }
            }
        };
        if (plugin != null) {
            plugin.getServer().getScheduler().runTask(plugin, lobbyAndWelcome);
        } else {
            lobbyService.sendToLobby(player);
        }
        asyncExecutor.execute(() -> {
            try {
                Instant now = Instant.now();
                PlayerData data = playerRepository.findByUuid(player.getUniqueId())
                        .orElse(new PlayerData(player.getUniqueId(), player.getName(), now, now, locale));
                playerRepository.upsert(new PlayerData(data.uuid(), player.getName(), data.firstJoin(), now, locale));
            } catch (Exception e) {
                // logged via async executor callers if needed
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(org.bukkit.event.player.PlayerKickEvent event) {
        // Kicked / banned players leave silently: no "[-] name" line follows the kick screen.
        JoinQuitMessages.apply(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        JoinQuitMessages.apply(event);
        Player player = event.getPlayer();
        settingsService.unload(player.getUniqueId());
        if (rankService != null) {
            rankService.unload(player.getUniqueId());
        }
        layoutCache.unload(player.getUniqueId());
        asyncExecutor.execute(() -> {
            try {
                playerRepository.updateLastSeen(player.getUniqueId(), Instant.now());
            } catch (Exception ignored) {
            }
        });
        sessionManager.removeSession(player.getUniqueId());
        playerStateManager.remove(player.getUniqueId());
    }
}
