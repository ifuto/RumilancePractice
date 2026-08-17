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
import com.rumilance.practice.command.LeaveCommand;
import com.rumilance.practice.command.LobbyCommand;
import com.rumilance.practice.command.PlayerCommands;
import com.rumilance.practice.command.PracticeAdminCommand;
import com.rumilance.practice.command.QueueLeaveCommand;
import com.rumilance.practice.command.SetFuncCommand;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.cosmetic.KillTitle;
import com.rumilance.practice.cosmetic.TitleService;
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
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.elo.EloCalculator;
import com.rumilance.practice.gui.menus.GameMenuGui;
import com.rumilance.practice.gui.menus.KitPreviewGui;
import com.rumilance.practice.gui.menus.SpectateListGui;
import com.rumilance.practice.gui.menus.TitleGui;
import com.rumilance.practice.lobby.LobbyCompassListener;
import com.rumilance.practice.match.GoldenHeadListener;
import com.rumilance.practice.team.TeamCommand;
import com.rumilance.practice.team.TeamListener;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.ffa.FfaListener;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.GuiListener;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.menus.OriginalKitGui;
import com.rumilance.practice.gui.menus.ConfirmGui;
import com.rumilance.practice.gui.menus.EkitAdminGui;
import com.rumilance.practice.gui.menus.EkitChoiceGui;
import com.rumilance.practice.gui.menus.EkitCopyGui;
import com.rumilance.practice.gui.menus.EkitSelectGui;
import com.rumilance.practice.gui.menus.EnchantGui;
import com.rumilance.practice.gui.menus.OriginalKitEditGui;
import com.rumilance.practice.gui.menus.PotionGui;
import com.rumilance.practice.ekit.EkitItems;
import com.rumilance.practice.gui.menus.ArrowEffectGui;
import com.rumilance.practice.gui.menus.DuelRequestGui;
import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.gui.menus.FfaListGui;
import com.rumilance.practice.gui.menus.KitAdminGui;
import com.rumilance.practice.gui.menus.KitSelectGui;
import com.rumilance.practice.gui.menus.MapSelectGui;
import com.rumilance.practice.gui.menus.PlayersGui;
import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.gui.menus.ProfileGui;
import com.rumilance.practice.gui.menus.SettingsGui;
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
        // Global kit-name display style (underscores -> spaces + configured casing).
        com.rumilance.practice.util.KitNames.configure(
                com.rumilance.practice.util.KitNames.CaseStyle.parse(
                        configService.config().getString("gui.kit-name-case", "KEEP")));
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

        // Arena strategy: with FAWE, matches run on disposable copies pasted at random
        // non-overlapping locations and deleted after the match (arena.disposable-copies=false
        // reverts to in-place regeneration). Without FAWE, plain teleport-only arenas.
        boolean disposableCopies = configService.config().getBoolean("arena.disposable-copies", true);
        ArenaService arenaService = faweBridge.isAvailable()
                ? (disposableCopies
                        ? new com.rumilance.practice.arena.DisposableArenaService(
                                plugin, faweBridge, new File(plugin.getDataFolder(), "schematics"),
                                configService.config().getInt("arena.placement-range", 100000),
                                configService.config().getInt("arena.placement-spacing", 64),
                                configService.config().getInt("arena.placement-center-x", 0),
                                configService.config().getInt("arena.placement-center-z", 0))
                        : new FaweArenaService(faweBridge, new File(plugin.getDataFolder(), "schematics"),
                                settings.regenerateArena()))
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
        matchService.setMessageService(messageService);

        chatBanService = new ChatBanService(punishmentRepository, auditLogRepository, objectionRepository,
                asyncExecutor, plugin.getLogger(), Duration.ofDays(7));
        services.register(ChatBanService.class, chatBanService);
        matchService.setChatBanService(chatBanService);

        SpectatorService spectatorService = new SpectatorService(plugin, matchRegistry, stateManager,
                settingsService, lobbyService, settings);
        services.register(SpectatorService.class, spectatorService);
        matchService.setSpectatorService(spectatorService);

        FfaService ffaService = new FfaService(plugin, configService, kitService, layoutCache, lobbyService,
                stateManager, ffaStatsRepository, asyncExecutor, runtimeFlags, messageService, soundService);
        services.register(FfaService.class, ffaService);

        // Per-player borders + view-distance caps fitted to the current play area.
        com.rumilance.practice.sight.ViewControlService viewControl =
                new com.rumilance.practice.sight.ViewControlService(
                        arenaService,
                        configService.config().getBoolean("sight.enabled", true));
        services.register(com.rumilance.practice.sight.ViewControlService.class, viewControl);
        matchService.setViewControl(viewControl);
        spectatorService.setViewControl(viewControl);
        ffaService.setViewControl(viewControl);
        // Lobby sight is fitted to the /slobby region itself (no fixed chunk radius).
        lobbyService.setSightHook(player -> viewControl.applyLobby(player, lobbyService.region()));

        // Wall-mounted text labels (e.g. aqua "N Arena" on an arena border wall).
        com.rumilance.practice.decor.WallTextService wallTextService =
                new com.rumilance.practice.decor.WallTextService(plugin);
        services.register(com.rumilance.practice.decor.WallTextService.class, wallTextService);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            wallTextService.load();
            wallTextService.clearAutoLabels();
            // Static labels: FFA arenas always; template builds only in in-place mode
            // (in disposable mode matches never happen at the template's own location).
            if (!(arenaService instanceof com.rumilance.practice.arena.DisposableArenaService)) {
                for (var template : arenaStore.templates()) {
                    wallTextService.placeAutoLabel(
                            "auto_arena_" + template.name(), template.world(),
                            template.minX(), template.minY(), template.minZ(),
                            template.maxX(), template.maxY(), template.maxZ(),
                            com.rumilance.practice.util.KitNames.pretty(template.name()) + " Arena");
                }
            }
            for (var ffaArena : ffaService.list()) {
                var region = ffaArena.region();
                wallTextService.placeAutoLabel(
                        "auto_ffa_" + ffaArena.id(), region.worldName(),
                        region.minX(), region.minY(), region.minZ(),
                        region.maxX(), region.maxY(), region.maxZ(),
                        com.rumilance.practice.util.KitNames.pretty(ffaArena.id()) + " FFA");
            }
        });
        // Disposable copies get their label when pasted and lose it when cleared.
        if (arenaService instanceof com.rumilance.practice.arena.DisposableArenaService disposableService) {
            disposableService.setCopyHooks(
                    inst -> wallTextService.placeAutoLabel(
                            "auto_copy_" + inst.id(), inst.template().world(),
                            inst.minX(), inst.minY(), inst.minZ(),
                            inst.maxX(), inst.maxY(), inst.maxZ(),
                            com.rumilance.practice.util.KitNames.pretty(inst.template().name()) + " Arena"),
                    inst -> wallTextService.removeAutoLabel("auto_copy_" + inst.id()));
        }

        StatsService statsService = new StatsService(rankedStatsRepository, matchHistoryRepository,
                dailyRankedStatsRepository, configService);
        services.register(StatsService.class, statsService);

        arrowEffectService = new ArrowEffectService(plugin, configService, settingsService);
        arrowEffectService.start();
        services.register(ArrowEffectService.class, arrowEffectService);

        queueCoordinator = new QueueCoordinator(
                plugin, queueService, matchService, kitService, lobbyService, stateManager,
                soundService, rankedStatsRepository, asyncExecutor, runtimeFlags, settings,
                false, true, messageService
        );
        services.register(QueueCoordinator.class, queueCoordinator);
        queueCoordinator.start();
        matchService.setQueueCoordinator(queueCoordinator);

        QueueKitGui rankedGui = new QueueKitGui(guiSessions, soundService, kitService, queueService, queueCoordinator, true);
        QueueKitGui unrankedGui = new QueueKitGui(guiSessions, soundService, kitService, queueService, queueCoordinator, false);
        KitSelectGui kitSelectGui = new KitSelectGui(guiSessions, soundService, kitService);
        MapSelectGui mapSelectGui = new MapSelectGui(guiSessions, soundService);
        DuelRequestGui duelRequestGui = new DuelRequestGui(guiSessions, soundService, kitService, duelRequestService,
                settingsService, statsService, mapSelectGui, kitSelectGui, messageService);
        kitSelectGui.setDuelRequestGui(duelRequestGui);
        mapSelectGui.setDuelRequestGui(duelRequestGui);

        SettingsGui settingsGui = new SettingsGui(guiSessions, soundService, settingsService);
        settingsGui.setToggleCooldownSeconds(configService.config().getInt("gui.toggle-cooldown-seconds", 2));
        StatsKitGui statsKitGui = new StatsKitGui(guiSessions, soundService, kitService, statsService);
        ProfileGui profileGui = new ProfileGui(guiSessions, soundService, kitService, statsService);
        PlayersGui playersGui = new PlayersGui(guiSessions, soundService, stateManager, statsService, duelRequestGui);
        SpectateListGui spectateListGui = new SpectateListGui(guiSessions, soundService, matchRegistry, spectatorService);
        FfaListGui ffaListGui = new FfaListGui(guiSessions, soundService, ffaService);
        KitPreviewGui kitPreviewGui = new KitPreviewGui(guiSessions, soundService, kitService);
        rankedGui.setPreviewGui(kitPreviewGui);
        unrankedGui.setPreviewGui(kitPreviewGui);
        TitleService titleService = new TitleService(settingsService, statsService);
        services.register(TitleService.class, titleService);
        matchService.setTitleService(titleService);
        TitleGui titleGui = new TitleGui(guiSessions, soundService, titleService);
        com.rumilance.practice.gui.menus.MatchReportGui matchReportGui =
                new com.rumilance.practice.gui.menus.MatchReportGui(guiSessions, soundService, matchService);
        matchService.setMatchReportOpener(matchReportGui::openLastReport);
        TeamService teamService = new TeamService(plugin, matchService);
        services.register(TeamService.class, teamService);
        com.rumilance.practice.gui.menus.TeamsBrowserGui teamsBrowserGui =
                new com.rumilance.practice.gui.menus.TeamsBrowserGui(guiSessions, soundService, teamService, null);
        com.rumilance.practice.gui.menus.TeamKitSelectGui teamKitSelectGui =
                new com.rumilance.practice.gui.menus.TeamKitSelectGui(guiSessions, soundService, teamService, kitService);
        com.rumilance.practice.gui.menus.TeamHubGui teamHubGui =
                new com.rumilance.practice.gui.menus.TeamHubGui(guiSessions, soundService, teamService, teamsBrowserGui, teamKitSelectGui);
        teamsBrowserGui.setHub(teamHubGui);
        EditKitGui editKitGui = new EditKitGui(guiSessions, soundService, kitService, kitLayoutRepository,
                layoutCache, asyncExecutor, stateManager);
        ArrowEffectGui arrowEffectGui = new ArrowEffectGui(guiSessions, soundService, arrowEffectService, settingsService);
        OriginalKitService originalKitService = new OriginalKitService(originalKitRepository, asyncExecutor,
                plugin.getLogger(), configService);
        services.register(OriginalKitService.class, originalKitService);
        EkitItems ekitItems = new EkitItems(configService);
        services.register(EkitItems.class, ekitItems);

        ConfirmGui confirmGui = new ConfirmGui(guiSessions, soundService);
        confirmGui.setOriginalKitService(originalKitService);
        OriginalKitEditGui originalKitEditGui = new OriginalKitEditGui(guiSessions, soundService,
                originalKitService, ekitItems);
        EnchantGui enchantGui = new EnchantGui(guiSessions, soundService, originalKitService, originalKitEditGui);
        PotionGui potionGui = new PotionGui(guiSessions, soundService, originalKitService, originalKitEditGui);
        EkitCopyGui ekitCopyGui = new EkitCopyGui(guiSessions, soundService, kitService,
                originalKitEditGui, originalKitService);
        EkitChoiceGui ekitChoiceGui = new EkitChoiceGui(guiSessions, soundService, originalKitService,
                originalKitEditGui, ekitCopyGui);
        OriginalKitGui originalKitGui = new OriginalKitGui(guiSessions, soundService, originalKitService);
        originalKitGui.setConfirmGui(confirmGui);
        originalKitGui.setEditGui(originalKitEditGui);
        originalKitGui.setChoiceGui(ekitChoiceGui);
        originalKitEditGui.setEnchantGui(enchantGui);
        originalKitEditGui.setPotionGui(potionGui);
        originalKitEditGui.setConfirmGui(confirmGui);
        originalKitEditGui.setOriginalKitGui(originalKitGui);
        ekitCopyGui.setChoiceGui(ekitChoiceGui);
        EkitSelectGui ekitSelectGui = new EkitSelectGui(guiSessions, soundService, kitService);
        ekitSelectGui.setEditKitGui(editKitGui);
        ekitSelectGui.setOriginalKitGui(originalKitGui);
        EkitAdminGui ekitAdminGui = new EkitAdminGui(guiSessions, soundService, ekitItems);
        KitAdminGui kitAdminGui = new KitAdminGui(guiSessions, soundService, kitService, messageService);

        GameMenuGui gameMenuGui = new GameMenuGui(guiSessions, soundService,
                rankedGui, unrankedGui, ffaListGui, ekitSelectGui, spectateListGui, settingsGui, titleGui);

        GuiListener guiListener = new GuiListener(guiSessions, stateManager, originalKitService);
        guiListener.register(rankedGui);
        guiListener.register(unrankedGui);
        guiListener.register(kitSelectGui);
        guiListener.register(mapSelectGui);
        guiListener.register(duelRequestGui);
        guiListener.register(settingsGui);
        guiListener.register(statsKitGui);
        guiListener.register(profileGui);
        guiListener.register(playersGui);
        guiListener.register(ekitSelectGui);
        guiListener.register(originalKitGui);
        guiListener.register(confirmGui);
        guiListener.register(ekitChoiceGui);
        guiListener.register(ekitCopyGui);
        guiListener.register(originalKitEditGui);
        guiListener.register(enchantGui);
        guiListener.register(potionGui);
        guiListener.register(ekitAdminGui);
        guiListener.register(spectateListGui);
        guiListener.register(ffaListGui);
        guiListener.register(editKitGui);
        guiListener.register(arrowEffectGui);
        guiListener.register(originalKitGui);
        guiListener.register(kitAdminGui);
        guiListener.register(kitPreviewGui);
        guiListener.register(titleGui);
        guiListener.register(matchReportGui);
        guiListener.register(teamsBrowserGui);
        guiListener.register(teamHubGui);
        guiListener.register(teamKitSelectGui);
        guiListener.register(gameMenuGui);
        // Esc / Close inside Game Menu sub-menus returns players to the Game Menu.
        guiListener.setMenuReturn(gameMenuGui::open);

        FunctionalItemListener functionalItemListener = new FunctionalItemListener(
                soundService, queueCoordinator, rankedGui, unrankedGui);
        functionalItemListener.setOpenSettings(settingsGui::open);
        functionalItemListener.setOpenFfa(ffaListGui::open);
        functionalItemListener.setOpenEkit(ekitSelectGui::open);
        functionalItemListener.setOpenSpectate(spectateListGui::open);
        functionalItemListener.setOpenMenu(gameMenuGui::open);
        functionalItemListener.setOpenTitles(titleGui::open);
        functionalItemListener.setOpenParty(player -> {
            if (teamService.teamOf(player.getUniqueId()).isPresent()) {
                teamHubGui.open(player);
            } else {
                teamsBrowserGui.open(player);
            }
        });
        gameMenuGui.setOpenTeams(player -> {
            if (teamService.teamOf(player.getUniqueId()).isPresent()) {
                teamHubGui.open(player);
            } else {
                teamsBrowserGui.open(player);
            }
        });

        scoreboardService = new ScoreboardService(plugin, settings, stateManager,
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
        pm.registerEvents(new com.rumilance.practice.spectator.SpectatorBoundsListener(
                spectatorService, matchRegistry, arenaService), plugin);
        pm.registerEvents(new FfaListener(ffaService, kitService, stateManager), plugin);
        pm.registerEvents(new GoldenHeadListener(plugin, matchRegistry), plugin);
        pm.registerEvents(guiListener, plugin);
        pm.registerEvents(functionalItemListener, plugin);
        pm.registerEvents(new LobbyCompassListener(stateManager, soundService, gameMenuGui::open), plugin);
        pm.registerEvents(new AdminToolListener(lobbyService, soundService), plugin);
        pm.registerEvents(new com.rumilance.practice.team.TeamListener(teamService), plugin);
        com.rumilance.practice.chat.PendingInput.init(plugin);
        // One-shot sweep: delete leftover floating-text entities (holograms) from all worlds,
        // including ones spawned by other plugins that crashed without cleaning up.
        // Floating-text (hologram) cleanup: entities load async AFTER chunks since 1.17, so a
        // one-shot scan at enable sees nothing - sweep via EntitiesLoadEvent for a window instead.
        com.rumilance.practice.lobby.FloatingTextCleanup.start(plugin,
                configService.config().getLong("cleanup.floating-text-window-seconds", 300L));
        pm.registerEvents(new PracticeSideListener(chatBanService, settingsService, guiSessions,
                arrowEffectService, spectatorService, ffaService, originalKitService), plugin);

        DuelCommand rankedDuel = new DuelCommand(rankedGui, unrankedGui, duelRequestService, matchService,
                kitService, stateManager, soundService, lobbyService, queueCoordinator, runtimeFlags, true, messageService);
        DuelCommand unrankedDuel = new DuelCommand(rankedGui, unrankedGui, duelRequestService, matchService,
                kitService, stateManager, soundService, lobbyService, queueCoordinator, runtimeFlags, false, messageService);
        AcceptDenyCommand acceptDeny = new AcceptDenyCommand(rankedDuel, duelRequestService);
        ArenaKitAdminCommand arenaKitAdmin = new ArenaKitAdminCommand(configService, arenaStore, arenaService,
                kitService, queueService, faweBridge, new File(plugin.getDataFolder(), "schematics"), soundService, kitAdminGui);
        ChatBanCommand chatBanCommand = new ChatBanCommand(chatBanService);
        FfaCommand ffaCommand = new FfaCommand(ffaListGui, ffaService, kitService);
        PracticeAdminCommand practiceAdmin = new PracticeAdminCommand(plugin, configService, soundService,
                matchService, lobbyService, runtimeFlags, kitService, arenaStore, arenaService, ffaService);

        bind("duel", rankedDuel);
        bind("ranked", rankedDuel);
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
        bind("ekitadmin", new com.rumilance.practice.command.EkitAdminCommand(ekitAdminGui));
        bind("giveitem", new com.rumilance.practice.command.GiveItemCommand());
        bind("matchreport", new com.rumilance.practice.command.MatchReportCommand(matchService, settingsService));
        bind("walltext", new com.rumilance.practice.decor.WallTextCommand(wallTextService));
        bind("ffa", ffaCommand);
        bind("leave", new LeaveCommand(matchService, messageService));
        // "/party" and "/p" are plugin.yml aliases of "/team" — only bind the real command.
        bind("team", new TeamCommand(teamService, kitService, teamHubGui, teamsBrowserGui));
        bind("title", (org.bukkit.command.CommandExecutor) (sender, command, label, args) -> {
            if (sender instanceof org.bukkit.entity.Player player) {
                titleGui.open(player);
            }
            return true;
        });

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
                    profileGui, settingsGui, ekitSelectGui, playersGui, spectateListGui, spectatorService,
                    ffaListGui, editKitGui, arrowEffectGui, chatBanService));
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
        // Delete any still-pasted disposable arena copies so nothing leaks into the world.
        com.rumilance.practice.arena.ArenaService disposable =
                services.find(com.rumilance.practice.arena.ArenaService.class).orElse(null);
        if (disposable instanceof com.rumilance.practice.arena.DisposableArenaService copies) {
            try {
                copies.clearAllCopies().get(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not clear all disposable arena copies on shutdown: " + e.getMessage());
            }
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
