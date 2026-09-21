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
import com.rumilance.practice.settings.ChatPolicy;
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
    /** Opens the language picker (wired from bootstrap; null = picker disabled). */
    private volatile java.util.function.Consumer<org.bukkit.entity.Player> languagePicker;
    /** 参加時に言語ピッカーを自動で開くか。既定は false(設定言語に合わせる)。 */
    private volatile boolean languagePickerOnJoin;

    public void setLanguagePicker(java.util.function.Consumer<org.bukkit.entity.Player> languagePicker) {
        this.languagePicker = languagePicker;
    }

    public void setLanguagePickerOnJoin(boolean enabled) {
        this.languagePickerOnJoin = enabled;
    }

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
        Player player = event.getPlayer();
        // Fake players (HeroBot/PacketBot) join through the same event but are not players of this
        // server's network: the lobby bootstrap would teleport them to the hub, switch them to
        // adventure and give them the lobby's infinite Resistance 255, which turns every bot fight
        // in the Quantum map into a no-damage tick loop.
        //
        // Presence policy: bots leave NO join line — they are sparring partners of individual
        // users, not arrivals on the server.
        if (com.rumilance.practice.packetbot.PacketBot.isBot(player)) {
            event.joinMessage(null);
            return;
        }
        // メッセージの受信: the line is not broadcast by the server any more — each viewer
        // decides whether other players' join/quit lines reach them at all.
        event.joinMessage(null);
        broadcastJoinQuit(player, JoinQuitMessages.join(player.getName()));
        // The join handshake just sent this player the full tab list, bot entries included —
        // strip every live bot's row so bots never show up in the TAB.
        com.rumilance.practice.packetbot.PacketBot.hideAllFromTab(player);
        String clientLocale = player.locale() != null ? player.locale().toString().toLowerCase(Locale.ROOT) : defaultLocale;
        var settings = settingsService.get(player.getUniqueId());
        // A previously chosen language wins over the client locale; first-timers (LOCALE_AUTO)
        // keep the client locale until they pick one in the language GUI below.
        boolean localeUnset = com.rumilance.practice.model.PlayerSettings.isLocaleUnset(settings.locale());
        String locale = localeUnset
                ? clientLocale
                : com.rumilance.practice.locale.LocaleService.normalize(settings.locale());
        PlayerSession session = sessionManager.createSession(player.getUniqueId(), locale);
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
                    messageService.sendRaw(player, "welcome.discord-tip");
                } catch (Exception e) {
                    WelcomeTitle.play(plugin, player);
                }
            }
            // 言語は「設定に合わせる」: 参加時にピッカーは出さない。未設定ならサーバの
            // 既定言語(設定言語)で表示され、変えたい人は /lang で自分で開く。
            // locale.picker-on-join: true にすると旧来の自動オープンを復活できる。
            if (languagePickerOnJoin && localeUnset && languagePicker != null && plugin != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        languagePicker.accept(player);
                    }
                }, 12L);
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

    /**
     * Sends one {@code [+] name} / {@code [-] name} line to every viewer whose reception
     * settings accept it. Bots never see them, and the subject never gets their own line.
     * Friends are not a thing yet, so everyone counts as {@link ChatPolicy.Relation#OTHER}.
     */
    private void broadcastJoinQuit(Player subject, net.kyori.adventure.text.Component line) {
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(subject.getUniqueId())
                    || com.rumilance.practice.packetbot.PacketBot.isBot(viewer)) {
                continue;
            }
            if (ChatPolicy.receivesJoinQuit(settingsService.get(viewer.getUniqueId()),
                    ChatPolicy.Relation.OTHER)) {
                viewer.sendMessage(line);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(org.bukkit.event.player.PlayerKickEvent event) {
        // Kicked / banned players leave silently: no "[-] name" line follows the kick screen.
        JoinQuitMessages.apply(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Presence policy: bots leave no quit line either, and they need no player-side
        // bookkeeping (no settings, no session, no repository rows). Live-registry cleanup
        // already happened at the despawn/die call site.
        if (com.rumilance.practice.packetbot.PacketBot.isBot(player)) {
            event.quitMessage(null);
            return;
        }
        event.quitMessage(null);
        if (!JoinQuitMessages.consumeQuitSuppression(player.getUniqueId())) {
            broadcastJoinQuit(player, JoinQuitMessages.quit(player.getName()));
        }
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
