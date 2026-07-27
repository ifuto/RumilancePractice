package com.rumilance.practice.bootstrap;

import com.rumilance.practice.RumilancePractice;
import com.rumilance.practice.admin.AdminToolListener;
import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.arena.FaweArenaService;
import com.rumilance.practice.arena.SimpleArenaService;
import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.command.AcceptDenyCommand;
import com.rumilance.practice.command.ArenaKitAdminCommand;
import com.rumilance.practice.command.ChatBanCommand;
import com.rumilance.practice.command.DuelCommand;
import com.rumilance.practice.command.FfaCommand;
import com.rumilance.practice.command.LobbyCommand;
import com.rumilance.practice.command.PlayerCommands;
import com.rumilance.practice.command.PracticeAdminCommand;
import com.rumilance.practice.command.QueueLeaveCommand;
import com.rumilance.practice.command.SetFuncCommand;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.FfaStatsRepository;
import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.database.repository.MatchHistoryRepository;
import com.rumilance.practice.database.repository.ObjectionRepository;
import com.rumilance.practice.database.repository.OriginalKitRepository;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.database.repository.PunishmentRepository;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.database.repository.SettingsRepository;
import com.rumilance.practice.originalkit.ClientModBridge;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.elo.EloCalculator;
import com.rumilance.practice.ffa.FfaListener;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.GuiListener;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.menus.OriginalKitGui;
import com.rumilance.practice.gui.menus.ArrowEffectGui;
import com.rumilance.practice.gui.menus.DuelRequestGui;
import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.gui.menus.FfaListGui;
import com.rumilance.practice.gui.menus.KitSelectGui;
import com.rumilance.practice.gui.menus.MapSelectGui;
import com.rumilance.practice.gui.menus.PlayersGui;
import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.gui.menus.SettingsGui;
import com.rumilance.practice.gui.menus.SpectateListGui;
import com.rumilance.practice.gui.menus.StatsKitGui;
import com.rumilance.practice.item.FunctionalItemListener;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.listener.PracticeSideListener;
import com.rumilance.practice.listener.SessionBootstrapListener;
import com.rumilance.practice.lobby.LobbyListener;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.match.ArenaBoundsListener;
import com.rumilance.practice.match.MatchListener;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.match.result.FfaResultProcessor;
import com.rumilance.practice.match.result.RankedResultProcessor;
import com.rumilance.practice.match.result.UnrankedResultProcessor;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.scoreboard.ScoreboardService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.time.Duration;

public final class FeatureBootstrap {

    private final RumilancePractice plugin;
    private final ServiceRegistry services;
    private QueueCoordinator queueCoordinator;
    private MatchService matchService;
    private ScoreboardService scoreboardService;
    private ArrowEffectService arrowEffectService;
    private SettingsService settingsService;
    private ChatBanService chatBanService;

    public FeatureBootstrap(RumilancePractice plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void enable() {
        ConfigService configService = services.get(ConfigService.class);
        PluginSettings settings = services.get(PluginSettings.class);
        SessionManager sessionManager = services.get(SessionManager.class);
        PlayerStateManager stateManager = services.get(PlayerStateManager.class);
        AsyncExecutor asyncExecutor = services.get(AsyncExecutor.class);
        FaweBridge faweBridge = services.get(FaweBridge.class);
        EloCalculator eloCalculator = services.get(EloCalculator.class);
        RankedStatsRepository rankedStatsRepository = services.get(RankedStatsRepository.class);
        MatchHistoryRepository matchHistoryRepository = services.get(MatchHistoryRepository.class);
        AuditLogRepository auditLogRepository = services.get(AuditLogRepository.class);
        SettingsRepository settingsRepository = services.get(SettingsRepository.class);
        PunishmentRepository punishmentRepository = services.get(PunishmentRepository.class);
        KitLayoutRepository kitLayoutRepository = services.get(KitLayoutRepository.class);
        OriginalKitRepository originalKitRepository = services.get(OriginalKitRepository.class);
        DailyRankedStatsRepository dailyRankedStatsRepository = services.get(DailyRankedStatsRepository.class);
        FfaStatsRepository ffaStatsRepository = services.get(FfaStatsRepository.class);
        ObjectionRepository objectionRepository = services.get(ObjectionRepository.class);
        PlayerRepository playerRepository = services.get(PlayerRepository.class);

        MessageService messageService = services.get(MessageService.class);

        RuntimeFlags runtimeFlags = new RuntimeFlags(settings.maintenanceMode());
        services.register(RuntimeFlags.class, runtimeFlags);

        settingsService = new SettingsService(settingsRepository, asyncExecutor, plugin.getLogger(), settings.defaultLocale());
        services.register(SettingsService.class, settingsService);

        SoundService soundService = new SoundService(configService, settingsService);
        services.register(SoundService.class, soundService);

        LobbyService lobbyService = new LobbyService(configService);
        services.register(LobbyService.class, lobbyService);

        KitService kitService = new KitService(configService);
        services.register(KitService.class, kitService);

        KitLayoutCache layoutCache = new KitLayoutCache(kitLayoutRepository, asyncExecutor, plugin.getLogger());
        services.register(KitLayoutCache.class, layoutCache);

        ArenaTemplateStore arenaStore = new ArenaTemplateStore(configService);
        arenaStore.reload();
        services.register(ArenaTemplateStore.class, arenaStore);

        ArenaService arenaService = faweBridge.isAvailable()
                ? new FaweArenaService(faweBridge, new File(plugin.getDataFolder(), "schematics"), settings.regenerateArena())
                : new SimpleArenaService();
        arenaService.setTemplates(arenaStore.templates());
        services.register(ArenaService.class, arenaService);

        GuiSessionRegistry guiSessions = new GuiSessionRegistry();
        services.register(GuiSessionRegistry.class, guiSessions);

        QueueService queueService = new QueueService(settings);
        services.register(QueueService.class, queueService);

        DuelRequestService duelRequestService = new DuelRequestService(60L, 3000L);
        services.register(DuelRequestService.class, duelRequestService);

        MatchRegistry matchRegistry = new MatchRegistry();
        RankedResultProcessor rankedResultProcessor = new RankedResultProcessor(
                rankedStatsRepository, matchHistoryRepository, dailyRankedStatsRepository, eloCalculator, settings, true);
        UnrankedResultProcessor unrankedResultProcessor = new UnrankedResultProcessor(auditLogRepository);
        FfaResultProcessor ffaResultProcessor = new FfaResultProcessor(auditLogRepository);

        matchService = new MatchService(
                plugin, arenaService, kitService, layoutCache, lobbyService, matchRegistry, stateManager,
                duelRequestService, soundService, asyncExecutor,
                rankedResultProcessor, unrankedResultProcessor, ffaResultProcessor,
                settings.matchCountdownSeconds(), Math.max(1, settings.endTeleportDelaySeconds()),
                settings.matchMaxDurationSeconds()
        );
        services.register(MatchService.class, matchService);
        matchService.setSettingsService(settingsService);

        chatBanService = new ChatBanService(punishmentRepository, auditLogRepository, objectionRepository,
                asyncExecutor, plugin.getLogger(), Duration.ofDays(7));
        services.register(ChatBanService.class, chatBanService);
        matchService.setChatBanService(chatBanService);

        SpectatorService spectatorService = new SpectatorService(plugin, matchRegistry, stateManager,
                settingsService, lobbyService, settings);
        services.register(SpectatorService.class, spectatorService);
        matchService.setSpectatorService(spectatorService);

        FfaService ffaService = new FfaService(plugin, configService, kitService, layoutCache, lobbyService,
                stateManager, ffaStatsRepository, asyncExecutor, runtimeFlags);
        services.register(FfaService.class, ffaService);

        StatsService statsService = new StatsService(rankedStatsRepository, matchHistoryRepository,
                dailyRankedStatsRepository, configService);
        services.register(StatsService.class, statsService);

        arrowEffectService = new ArrowEffectService(plugin, configService, settingsService);
        arrowEffectService.start();
        services.register(ArrowEffectService.class, arrowEffectService);

        queueCoordinator = new QueueCoordinator(
                plugin, queueService, matchService, kitService, lobbyService, stateManager,
                soundService, rankedStatsRepository, asyncExecutor, runtimeFlags, false, true
        );
        services.register(QueueCoordinator.class, queueCoordinator);
        queueCoordinator.start();
        matchService.setQueueCoordinator(queueCoordinator);

        QueueKitGui rankedGui = new QueueKitGui(guiSessions, soundService, kitService, queueService, queueCoordinator, true);
        QueueKitGui unrankedGui = new QueueKitGui(guiSessions, soundService, kitService, queueService, queueCoordinator, false);
        KitSelectGui kitSelectGui = new KitSelectGui(guiSessions, soundService, kitService);
        MapSelectGui mapSelectGui = new MapSelectGui(guiSessions, soundService);
        DuelRequestGui duelRequestGui = new DuelRequestGui(guiSessions, soundService, kitService, duelRequestService,
                settingsService, statsService, mapSelectGui, kitSelectGui);
        kitSelectGui.setDuelRequestGui(duelRequestGui);
        mapSelectGui.setDuelRequestGui(duelRequestGui);

        SettingsGui settingsGui = new SettingsGui(guiSessions, soundService, settingsService);
        StatsKitGui statsKitGui = new StatsKitGui(guiSessions, soundService, kitService, statsService);
        PlayersGui playersGui = new PlayersGui(guiSessions, soundService, stateManager, statsService, duelRequestGui);
        SpectateListGui spectateListGui = new SpectateListGui(guiSessions, soundService, matchRegistry, spectatorService);
        FfaListGui ffaListGui = new FfaListGui(guiSessions, soundService, ffaService);
        EditKitGui editKitGui = new EditKitGui(guiSessions, soundService, kitService, kitLayoutRepository,
                layoutCache, asyncExecutor, stateManager);
        ArrowEffectGui arrowEffectGui = new ArrowEffectGui(guiSessions, soundService, arrowEffectService, settingsService);
        OriginalKitService originalKitService = new OriginalKitService(originalKitRepository, asyncExecutor,
                plugin.getLogger(), configService);
        services.register(OriginalKitService.class, originalKitService);
        services.register(ClientModBridge.class, ClientModBridge.NoOp.INSTANCE);
        OriginalKitGui originalKitGui = new OriginalKitGui(guiSessions, soundService, originalKitService,
                services.get(ClientModBridge.class));

        GuiListener guiListener = new GuiListener(guiSessions, stateManager);
        guiListener.register(rankedGui);
        guiListener.register(unrankedGui);
        guiListener.register(kitSelectGui);
        guiListener.register(mapSelectGui);
        guiListener.register(duelRequestGui);
        guiListener.register(settingsGui);
        guiListener.register(statsKitGui);
        guiListener.register(playersGui);
        guiListener.register(spectateListGui);
        guiListener.register(ffaListGui);
        guiListener.register(editKitGui);
        guiListener.register(arrowEffectGui);
        guiListener.register(originalKitGui);

        FunctionalItemListener functionalItemListener = new FunctionalItemListener(
                soundService, queueCoordinator, rankedGui, unrankedGui);
        functionalItemListener.setOpenSettings(settingsGui::open);
        functionalItemListener.setOpenFfa(ffaListGui::open);
        functionalItemListener.setOpenEkit(editKitGui::openKitPicker);
        functionalItemListener.setOpenSpectate(spectateListGui::open);

        scoreboardService = new ScoreboardService(plugin, settings, sessionManager, stateManager,
                queueService, matchRegistry, rankedStatsRepository, settingsService);
        if (settings.scoreboardEnabled()) {
            scoreboardService.start();
        }
        services.register(ScoreboardService.class, scoreboardService);

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new SessionBootstrapListener(sessionManager, stateManager, lobbyService,
                settings.defaultLocale(), playerRepository, layoutCache, settingsService, asyncExecutor), plugin);
        pm.registerEvents(new LobbyListener(lobbyService, stateManager), plugin);
        pm.registerEvents(new MatchListener(matchService, kitService), plugin);
        pm.registerEvents(new ArenaBoundsListener(matchService, arenaService), plugin);
        pm.registerEvents(new FfaListener(ffaService, kitService, stateManager), plugin);
        pm.registerEvents(guiListener, plugin);
        pm.registerEvents(functionalItemListener, plugin);
        pm.registerEvents(new AdminToolListener(lobbyService, soundService), plugin);
        pm.registerEvents(new PracticeSideListener(chatBanService, settingsService, guiSessions,
                arrowEffectService, spectatorService, ffaService), plugin);

        DuelCommand rankedDuel = new DuelCommand(rankedGui, unrankedGui, duelRequestService, matchService,
                kitService, stateManager, soundService, lobbyService, queueCoordinator, runtimeFlags, true);
        DuelCommand unrankedDuel = new DuelCommand(rankedGui, unrankedGui, duelRequestService, matchService,
                kitService, stateManager, soundService, lobbyService, queueCoordinator, runtimeFlags, false);
        rankedDuel.setDuelRequestGui(duelRequestGui);
        unrankedDuel.setDuelRequestGui(duelRequestGui);

        AcceptDenyCommand acceptDeny = new AcceptDenyCommand(rankedDuel);
        ArenaKitAdminCommand arenaKitAdmin = new ArenaKitAdminCommand(configService, arenaStore, arenaService,
                kitService, queueService, faweBridge, new File(plugin.getDataFolder(), "schematics"));
        ChatBanCommand chatBanCommand = new ChatBanCommand(chatBanService);
        FfaCommand ffaCommand = new FfaCommand(ffaListGui, ffaService, kitService);
        PracticeAdminCommand practiceAdmin = new PracticeAdminCommand(plugin, configService, soundService,
                matchService, lobbyService, runtimeFlags, kitService, arenaStore, arenaService, ffaService);

        bind("duel", rankedDuel);
        bind("unranked", unrankedDuel);
        bind("accept", acceptDeny);
        bind("deny", acceptDeny);
        bind("queue", new QueueLeaveCommand(queueCoordinator));
        bind("lobby", new LobbyCommand(lobbyService, stateManager, spectatorService, ffaService, messageService));
        bind("spawn", new LobbyCommand(lobbyService, stateManager, spectatorService, ffaService, messageService));
        bind("setfunc", new SetFuncCommand());
        bind("practiceadmin", practiceAdmin);
        bind("slobby", practiceAdmin);
        bind("setlobbyitem", practiceAdmin);
        bind("arena", arenaKitAdmin);
        bind("kit", arenaKitAdmin);
        bind("toggle", arenaKitAdmin);
        bind("chatban", chatBanCommand);
        bind("chatunban", chatBanCommand);
        bind("ffa", ffaCommand);
        bind("adffa", ffaCommand);

        for (PlayerCommands.Type type : PlayerCommands.Type.values()) {
            if (type == PlayerCommands.Type.FFA) {
                continue;
            }
            String cmd = switch (type) {
                case PING -> "ping";
                case STATS -> "stats";
                case PROFILE -> "profile";
                case RANKING -> "ranking";
                case PLAYERS -> "players";
                case SPEC -> "spec";
                case SPECGUI -> "specgui";
                case SETTING -> "setting";
                case FFA -> "ffa";
                case EKIT -> "ekit";
                case ARROW -> "arroweffect";
                case HISTORY -> "matchhistory";
                case KDR -> "kdr";
                case OBJECTION -> "objection";
            };
            bind(cmd, new PlayerCommands(type, plugin, asyncExecutor, statsService, kitService, statsKitGui,
                    settingsGui, playersGui, spectateListGui, spectatorService, ffaListGui, editKitGui,
                    arrowEffectGui, chatBanService));
        }
        bind("originalkit", (org.bukkit.command.CommandExecutor) (sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player player) {
                originalKitGui.open(player);
            }
            return true;
        });

        plugin.getLogger().info("Feature services enabled (all player GUIs and admin commands wired).");
    }

    public void disable() {
        if (queueCoordinator != null) {
            queueCoordinator.stop();
        }
        if (matchService != null) {
            matchService.shutdown();
        }
        if (scoreboardService != null) {
            scoreboardService.stop();
        }
        if (arrowEffectService != null) {
            arrowEffectService.stop();
        }
        if (settingsService != null) {
            settingsService.flushAll();
        }
        if (chatBanService != null) {
            chatBanService.setShuttingDown(true);
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Missing command in plugin.yml: " + name);
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor commandExecutor) {
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
