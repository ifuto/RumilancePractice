package com.rumilance.practice.bootstrap;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.RumilancePractice;
import com.rumilance.practice.admin.AdminToolListener;
import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.arena.DisposableArenaService;
import com.rumilance.practice.arena.FaweArenaService;
import com.rumilance.practice.arena.PartyIconListener;
import com.rumilance.practice.arena.SimpleArenaService;
import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.ban.BanLoginListener;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.chat.SpamFilterListener;
import com.rumilance.practice.chat.SpamFilterService;
import com.rumilance.practice.combat.CombatNetTracker;
import com.rumilance.practice.combat.CombatSyncListener;
import com.rumilance.practice.combat.TotemPickupListener;
import com.rumilance.practice.combat.CrystalAnchorPerfListener;
import com.rumilance.practice.combat.InstantExpCollectListener;
import com.rumilance.practice.combat.PaperCombatTuning;
import com.rumilance.practice.combat.KillFeed;
import com.rumilance.practice.command.AcceptDenyCommand;
import com.rumilance.practice.command.AdminCommand;
import com.rumilance.practice.command.ArenaKitAdminCommand;
import com.rumilance.practice.command.BanCommand;
import com.rumilance.practice.command.ChatBanCommand;
import com.rumilance.practice.command.CheckIdCommand;
import com.rumilance.practice.command.DuelChatInterceptListener;
import com.rumilance.practice.command.DuelCommand;
import com.rumilance.practice.command.EkitAdminCommand;
import com.rumilance.practice.command.FfaCommand;
import com.rumilance.practice.command.GiveItemCommand;
import com.rumilance.practice.command.LangCommand;
import com.rumilance.practice.command.LeaveCommand;
import com.rumilance.practice.command.LobbyCommand;
import com.rumilance.practice.command.MatchInvCommand;
import com.rumilance.practice.command.MatchReportCommand;
import com.rumilance.practice.command.PlayerCommands;
import com.rumilance.practice.command.PracCommand;
import com.rumilance.practice.command.PracticeAdminCommand;
import com.rumilance.practice.command.RumilanceReloadCommand;
import com.rumilance.practice.command.PracticeCommand;
import com.rumilance.practice.command.QueueLeaveCommand;
import com.rumilance.practice.command.ReplayCommand;
import com.rumilance.practice.command.ReportCommand;
import com.rumilance.practice.command.ReportListCommand;
import com.rumilance.practice.command.SetFuncCommand;
import com.rumilance.practice.command.SetRankCommand;
import com.rumilance.practice.command.SignCheckCommand;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.cosmetic.SmithingTrimListener;
import com.rumilance.practice.cosmetic.TitleService;
import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.FfaStatsRepository;
import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.database.repository.MatchHistoryRepository;
import com.rumilance.practice.database.repository.ObjectionRepository;
import com.rumilance.practice.database.repository.OriginalKitRepository;
import com.rumilance.practice.database.repository.PlayerReportRepository;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.database.repository.PracticeLayoutRepository;
import com.rumilance.practice.database.repository.PunishmentRepository;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.database.repository.SettingsRepository;
import com.rumilance.practice.database.repository.SpamDetectionRepository;
import com.rumilance.practice.database.repository.WinStreakRepository;
import com.rumilance.practice.decor.WallTextCommand;
import com.rumilance.practice.decor.WallTextService;
import com.rumilance.practice.duel.DuelLogStore;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.ekit.EkitItems;
import com.rumilance.practice.elo.EloCalculator;
import com.rumilance.practice.ffa.FfaBlockTracker;
import com.rumilance.practice.ffa.FfaListener;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.guard.ItemFlowGuardListener;
import com.rumilance.practice.ffa.FfaSpawnIndex;
import com.rumilance.practice.gui.KitAnvilRenameService;
import com.rumilance.practice.gui.KitEditStash;
import com.rumilance.practice.gui.GuiListener;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.menus.AdminMenuGui;
import com.rumilance.practice.gui.menus.ArenaAdminGui;
import com.rumilance.practice.gui.menus.ArrowEffectGui;
import com.rumilance.practice.gui.menus.BanListGui;
import com.rumilance.practice.gui.menus.BattleMenuGui;
import com.rumilance.practice.gui.menus.ConfirmGui;
import com.rumilance.practice.gui.menus.DuelMapSelectGui;
import com.rumilance.practice.gui.menus.DuelRequestGui;
import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.gui.menus.EkitAdminGui;
import com.rumilance.practice.gui.menus.EkitChoiceGui;
import com.rumilance.practice.gui.menus.EkitCopyGui;
import com.rumilance.practice.gui.menus.EkitSelectGui;
import com.rumilance.practice.gui.menus.EnchantGui;
import com.rumilance.practice.gui.menus.FfaListGui;
import com.rumilance.practice.gui.menus.GameMenuGui;
import com.rumilance.practice.gui.menus.KitAdminGui;
import com.rumilance.practice.gui.menus.KitArenaSelectGui;
import com.rumilance.practice.gui.menus.KitStartEffectsGui;
import com.rumilance.practice.gui.menus.KitPreviewGui;
import com.rumilance.practice.gui.menus.KitSelectGui;
import com.rumilance.practice.gui.menus.MatchInventoryGui;
import com.rumilance.practice.gui.menus.MatchReportGui;
import com.rumilance.practice.gui.menus.OriginalKitEditGui;
import com.rumilance.practice.gui.menus.OriginalKitGui;
import com.rumilance.practice.gui.menus.PartyInviteGui;
import com.rumilance.practice.gui.menus.PartyMapSelectGui;
import com.rumilance.practice.gui.menus.PlayersGui;
import com.rumilance.practice.gui.menus.PotionGui;
import com.rumilance.practice.gui.menus.PracticeBotGui;
import com.rumilance.practice.gui.menus.PracticeLayoutGui;
import com.rumilance.practice.gui.menus.PracticeMaceGui;
import com.rumilance.practice.gui.menus.PresetAdminGui;
import com.rumilance.practice.gui.menus.ProfileGui;
import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.gui.menus.ReportGui;
import com.rumilance.practice.gui.menus.ReportListGui;
import com.rumilance.practice.gui.menus.SettingsGui;
import com.rumilance.practice.gui.menus.SmithingTrimGui;
import com.rumilance.practice.gui.menus.SpectateListGui;
import com.rumilance.practice.gui.menus.StatsKitGui;
import com.rumilance.practice.gui.menus.TeamHubGui;
import com.rumilance.practice.gui.menus.TeamKitSelectGui;
import com.rumilance.practice.gui.menus.TeamsBrowserGui;
import com.rumilance.practice.gui.menus.TitleGui;
import com.rumilance.practice.item.FunctionalItemListener;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.kit.PresetItems;
import com.rumilance.practice.listener.AdvancementBlockListener;
import com.rumilance.practice.listener.PracticePearlListener;
import com.rumilance.practice.listener.PracticeSideListener;
import com.rumilance.practice.listener.SessionBootstrapListener;
import com.rumilance.practice.lobby.FloatingTextCleanup;
import com.rumilance.practice.lobby.LobbyCompassListener;
import com.rumilance.practice.lobby.LobbyListener;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.lobby.MotdListener;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.lunar.LunarRichPresenceService;
import com.rumilance.practice.match.ArenaBoundsListener;
import com.rumilance.practice.match.GoldenHeadListener;
import com.rumilance.practice.match.MatchActionRecorder;
import com.rumilance.practice.match.MatchCountdownLockListener;
import com.rumilance.practice.match.MatchCommandGuardListener;
import com.rumilance.practice.match.MatchListener;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.match.TeamColoredArmorListener;
import com.rumilance.practice.match.TeamColoredArmorService;
import com.rumilance.practice.match.OpponentHealthNametagService;
import com.rumilance.practice.match.TeamGlowLosService;
import com.rumilance.practice.scoreboard.TabFightListService;
import com.rumilance.practice.scoreboard.TabVisibilityService;
import com.rumilance.practice.match.inventory.MatchInventoryStore;
import com.rumilance.practice.match.result.FfaResultProcessor;
import com.rumilance.practice.match.result.RankedResultProcessor;
import com.rumilance.practice.match.result.UnrankedResultProcessor;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.platform.BedrockJoinListener;
import com.rumilance.practice.practice.PracticeListener;
import com.rumilance.practice.practice.PracticeCloneService;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.rank.RankRepository;
import com.rumilance.practice.rank.RankService;
import com.rumilance.practice.replay.ReplayService;
import com.rumilance.practice.report.ReportEvidenceStore;
import com.rumilance.practice.report.ReportService;
import com.rumilance.practice.scoreboard.ScoreboardConfig;
import com.rumilance.practice.scoreboard.ScoreboardService;
import com.rumilance.practice.security.sign.SignChangeGuardListener;
import com.rumilance.practice.security.sign.SignGuardService;
import com.rumilance.practice.security.sign.SignProbeService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sight.SightSettings;
import com.rumilance.practice.sight.ViewControlService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.spectator.SpectatorBoundsListener;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.stats.StatsResetService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.team.TeamCommand;
import com.rumilance.practice.team.PartyHotbar;
import com.rumilance.practice.team.TeamListener;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.tnt.PracticeTntListener;
import com.rumilance.practice.tnt.PracticeTntSettings;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Wires feature services, GUIs, listeners, and commands on enable.
 */
public final class FeatureBootstrap {

    private final RumilancePractice plugin;
    private final ServiceRegistry services;
    private QueueCoordinator queueCoordinator;
    private MatchService matchService;
    private ScoreboardService scoreboardService;
    private ArrowEffectService arrowEffectService;
    private SettingsService settingsService;
    private ChatBanService chatBanService;
    private BanService banService;
    private MatchActionRecorder matchActionRecorder;
    private ReplayService replayService;
    private PracticeService practiceService;
    private TeamGlowLosService teamGlowLosService;

    public FeatureBootstrap(RumilancePractice plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void enable() {
        ConfigService configService = services.get(ConfigService.class);
        PluginSettings settings = services.get(PluginSettings.class);
        KitNames.configure(KitNames.CaseStyle.parse(
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
        WinStreakRepository winStreakRepository = services.get(WinStreakRepository.class);
        PlayerReportRepository playerReportRepository = services.get(PlayerReportRepository.class);
        SpamDetectionRepository spamDetectionRepository = services.get(SpamDetectionRepository.class);
        PracticeLayoutRepository practiceLayoutRepository = services.get(PracticeLayoutRepository.class);
        RankRepository rankRepository = services.get(RankRepository.class);
        MessageService messageService = services.get(MessageService.class);

        RuntimeFlags runtimeFlags = new RuntimeFlags(settings.maintenanceMode());
        services.register(RuntimeFlags.class, runtimeFlags);

        settingsService = new SettingsService(settingsRepository, asyncExecutor, plugin.getLogger(),
                settings.defaultLocale());
        services.register(SettingsService.class, settingsService);

        SoundService soundService = new SoundService(configService, settingsService, stateManager);
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

        boolean disposableCopies = configService.config().getBoolean("arena.disposable-copies", true);
        ArenaService arenaService = faweBridge.isAvailable()
                ? (disposableCopies
                ? new DisposableArenaService(
                plugin, faweBridge, new File(PluginIdentity.dataFolder(plugin), "schematics"),
                configService.config().getInt("arena.placement-range", 100000),
                configService.config().getInt("arena.placement-spacing", 64),
                configService.config().getInt("arena.placement-center-x", 0),
                configService.config().getInt("arena.placement-center-z", 0))
                : new FaweArenaService(faweBridge, new File(PluginIdentity.dataFolder(plugin), "schematics"),
                settings.regenerateArena()))
                : new SimpleArenaService();
        arenaService.setTemplates(arenaStore.templates());
        services.register(ArenaService.class, arenaService);

        GuiSessionRegistry guiSessions = new GuiSessionRegistry();
        services.register(GuiSessionRegistry.class, guiSessions);

        QueueService queueService = new QueueService(settings);
        services.register(QueueService.class, queueService);

        DuelRequestService duelRequestService = new DuelRequestService(60L, DuelRequestService.DEFAULT_RATE_LIMIT_MS);
        services.register(DuelRequestService.class, duelRequestService);

        MatchRegistry matchRegistry = new MatchRegistry();
        matchActionRecorder = new MatchActionRecorder(plugin, matchRegistry);
        services.register(MatchActionRecorder.class, matchActionRecorder);

        RankedResultProcessor rankedResultProcessor = new RankedResultProcessor(
                rankedStatsRepository, matchHistoryRepository, dailyRankedStatsRepository,
                eloCalculator, settings, true);
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

        DuelLogStore duelLogStore = new DuelLogStore(new File(PluginIdentity.dataFolder(plugin), "duels.rpd").toPath());
        matchService.setDuelLogStore(duelLogStore);
        matchService.setActionRecorder(matchActionRecorder);
        MatchInventoryStore matchInventoryStore = new MatchInventoryStore(plugin);
        matchService.setInventoryStore(matchInventoryStore);
        services.register(MatchInventoryStore.class, matchInventoryStore);

        ReportEvidenceStore reportEvidenceStore =
                new ReportEvidenceStore(new File(PluginIdentity.dataFolder(plugin), "reports").toPath());
        ReportService reportService = new ReportService(
                playerReportRepository, reportEvidenceStore, matchActionRecorder, asyncExecutor,
                plugin.getLogger());
        services.register(ReportService.class, reportService);

        com.rumilance.practice.replay.ReplayNpcService replayNpcService =
                new com.rumilance.practice.replay.ReplayNpcService(plugin);
        replayNpcService.init();
        replayService = new ReplayService(plugin, lobbyService, replayNpcService);
        services.register(ReplayService.class, replayService);
        com.rumilance.practice.replay.ReplayArchive replayArchive =
                new com.rumilance.practice.replay.ReplayArchive();
        services.register(com.rumilance.practice.replay.ReplayArchive.class, replayArchive);
        matchService.setReplayArchive(replayArchive);

        banService = new BanService(plugin);
        services.register(BanService.class, banService);

        chatBanService = new ChatBanService(punishmentRepository, auditLogRepository, objectionRepository,
                asyncExecutor, plugin.getLogger(), Duration.ofDays(7), plugin);
        services.register(ChatBanService.class, chatBanService);
        matchService.setChatBanService(chatBanService);

        com.rumilance.practice.util.PlayerPlacedBlockTracker playerPlacedBlockTracker =
                new com.rumilance.practice.util.PlayerPlacedBlockTracker();
        services.register(com.rumilance.practice.util.PlayerPlacedBlockTracker.class, playerPlacedBlockTracker);
        matchService.setPlayerPlacedBlockTracker(playerPlacedBlockTracker);

        SpamFilterService spamFilterService = new SpamFilterService(
                configService, spamDetectionRepository, chatBanService, asyncExecutor, plugin.getLogger());
        services.register(SpamFilterService.class, spamFilterService);

        SignGuardService signGuardService = new SignGuardService(
                configService, banService, auditLogRepository, asyncExecutor, plugin.getLogger());
        services.register(SignGuardService.class, signGuardService);
        SignProbeService signProbeService = new SignProbeService(
                plugin, configService, banService, auditLogRepository, asyncExecutor, plugin.getLogger());
        signProbeService.init();
        services.register(SignProbeService.class, signProbeService);

        SpectatorService spectatorService = new SpectatorService(
                plugin, matchRegistry, stateManager, settingsService, lobbyService, settings);
        services.register(SpectatorService.class, spectatorService);
        matchService.setSpectatorService(spectatorService);

        TeamColoredArmorService teamColoredArmor =
                new TeamColoredArmorService(plugin, matchRegistry, settingsService);
        teamColoredArmor.setSpectatorService(spectatorService);
        services.register(TeamColoredArmorService.class, teamColoredArmor);
        matchService.setTeamColoredArmorService(teamColoredArmor);
        spectatorService.setTeamColoredArmorService(teamColoredArmor);

        teamGlowLosService = new TeamGlowLosService(plugin, matchRegistry, settingsService);
        teamGlowLosService.start();
        services.register(TeamGlowLosService.class, teamGlowLosService);
        teamColoredArmor.setTeamGlowLosService(teamGlowLosService);

        FfaService ffaService = new FfaService(
                plugin, configService, kitService, layoutCache, lobbyService, stateManager,
                ffaStatsRepository, asyncExecutor, runtimeFlags, messageService, soundService);
        services.register(FfaService.class, ffaService);
        spectatorService.setFfaService(ffaService);

        PracticeCloneService practiceCloneService = new PracticeCloneService(
                plugin, faweBridge, new File(PluginIdentity.dataFolder(plugin), "schematics"),
                configService.config().getInt("arena.placement-range", 100000),
                configService.config().getInt("arena.placement-spacing", 64),
                configService.config().getInt("arena.placement-center-x", 0),
                configService.config().getInt("arena.placement-center-z", 0));
        if (arenaService instanceof DisposableArenaService disposableArenas) {
            practiceCloneService.setExternalOverlaps(() -> disposableArenas.liveCopies().stream()
                    .map(inst -> new PracticeCloneService.OverlapBox(
                            inst.template().world(),
                            inst.minX(), inst.minZ(), inst.maxX(), inst.maxZ()))
                    .toList());
        }
        services.register(PracticeCloneService.class, practiceCloneService);

        practiceService = new PracticeService(
                plugin, configService, stateManager, lobbyService, practiceLayoutRepository,
                asyncExecutor, practiceCloneService);
        practiceService.start();
        services.register(PracticeService.class, practiceService);

        SightSettings sightSettings = SightSettings.from(configService.config());
        services.register(SightSettings.class, sightSettings);
        ViewControlService viewControl = new ViewControlService(arenaService, sightSettings);
        services.register(ViewControlService.class, viewControl);
        matchService.setViewControl(viewControl);
        spectatorService.setViewControl(viewControl);
        ffaService.setViewControl(viewControl);

        FfaSpawnIndex ffaSpawnIndex = new FfaSpawnIndex(plugin, asyncExecutor, ffaService);
        ffaService.setSpawnIndex(ffaSpawnIndex);
        ffaSpawnIndex.reindexAll();

        lobbyService.setSightHook(player -> {
            Cuboid region = lobbyService.region();
            if (region != null && lobbyService.spawn() != null) {
                region = region.including(lobbyService.spawn());
            }
            viewControl.applyLobby(player, region);
        });

        CombatNetTracker combatNet = new CombatNetTracker(
                configService.config().getInt("combat.ping-offset-ms", 25),
                configService.config().getInt("combat.spike-threshold-ms", 20));
        services.register(CombatNetTracker.class, combatNet);
        matchService.setCombatNet(combatNet);

        PracticeTntSettings practiceTnt = new PracticeTntSettings(configService);
        services.register(PracticeTntSettings.class, practiceTnt);

        CombatSyncListener combatSync = new CombatSyncListener(
                plugin, combatNet, matchService, ffaService, lobbyService, arenaService, viewControl, kitService);
        // Knockback shaping (coefficients, Y/ping sync) is delegated to an external plugin
        // (KnockBackSync); this plugin no longer creates its own knockback profile service.
        if (configService.config().getBoolean("combat.vanilla-item-swap", true)) {
            PaperCombatTuning.applyVanillaItemSwap(plugin.getLogger());
        }

        WallTextService wallTextService = new WallTextService(plugin);
        services.register(WallTextService.class, wallTextService);
        Bukkit.getScheduler().runTask(plugin, () -> {
            wallTextService.load();
            wallTextService.clearAutoLabels();
            if (!(arenaService instanceof DisposableArenaService)) {
                for (ArenaTemplate template : arenaStore.templates()) {
                    wallTextService.placeAutoLabel(
                            "auto_arena_" + template.name(), template.world(),
                            template.minX(), template.minY(), template.minZ(),
                            template.maxX(), template.maxY(), template.maxZ(),
                            KitNames.pretty(template.name()) + " Arena");
                }
            }
            for (FfaService.FfaArena ffaArena : ffaService.list()) {
                Cuboid region = ffaArena.region();
                wallTextService.placeAutoLabel(
                        "auto_ffa_" + ffaArena.id(), region.worldName(),
                        region.minX(), region.minY(), region.minZ(),
                        region.maxX(), region.maxY(), region.maxZ(),
                        KitNames.pretty(ffaArena.id()) + " FFA");
            }
        });
        if (arenaService instanceof DisposableArenaService disposableService) {
            disposableService.setCopyHooks(
                    inst -> wallTextService.placeAutoLabel(
                            "auto_copy_" + inst.id(), inst.template().world(),
                            inst.minX(), inst.minY(), inst.minZ(),
                            inst.maxX(), inst.maxY(), inst.maxZ(),
                            KitNames.pretty(inst.template().name()) + " Arena"),
                    inst -> wallTextService.removeAutoLabel("auto_copy_" + inst.id()));
        }

        StatsService statsService = new StatsService(
                rankedStatsRepository, matchHistoryRepository, dailyRankedStatsRepository, configService);
        services.register(StatsService.class, statsService);
        StatsResetService statsResetService = new StatsResetService(
                rankedStatsRepository, ffaStatsRepository, dailyRankedStatsRepository,
                matchHistoryRepository, winStreakRepository);
        services.register(StatsResetService.class, statsResetService);

        arrowEffectService = new ArrowEffectService(plugin, configService, settingsService);
        arrowEffectService.start();
        services.register(ArrowEffectService.class, arrowEffectService);

        queueCoordinator = new QueueCoordinator(
                plugin, queueService, matchService, kitService, lobbyService, stateManager,
                soundService, rankedStatsRepository, asyncExecutor, runtimeFlags, settings,
                false, true, messageService);
        services.register(QueueCoordinator.class, queueCoordinator);
        queueCoordinator.start();
        matchService.setQueueCoordinator(queueCoordinator);
        matchService.setFfaService(ffaService);
        if (practiceService != null) {
            matchService.setPracticeService(practiceService);
        }
        queueCoordinator.setFfaService(ffaService);
        ffaService.setQueueService(queueService);

        RankService rankService = new RankService(plugin, rankRepository, asyncExecutor);
        services.register(RankService.class, rankService);

        // Resource-pack icon glyphs (rank badges + RED/BLUE team markers) rendered in front of
        // player names in TAB / nametags via a custom font. The resolver combines the rank badge
        // (admin / VIP+ / VIP) with the team marker during team fights.
        final com.rumilance.practice.font.IconFontService iconFontService =
                new com.rumilance.practice.font.IconFontService(configService);
        services.register(com.rumilance.practice.font.IconFontService.class, iconFontService);
        final RankService rankServiceRef = rankService;
        com.rumilance.practice.match.MatchTeamVisuals.setPrefixResolver((player, session) -> {
            net.kyori.adventure.text.Component prefix = net.kyori.adventure.text.Component.empty();
            // Effective rank: stored rank or granted permissions (admin > VIP+ > VIP).
            com.rumilance.practice.rank.PlayerRank effective;
            if (rankServiceRef.isAdmin(player)) {
                effective = com.rumilance.practice.rank.PlayerRank.ADMIN;
            } else if (rankServiceRef.isVipPlusOrAbove(player)) {
                effective = com.rumilance.practice.rank.PlayerRank.VIP_PLUS;
            } else if (rankServiceRef.isVipOrAbove(player)) {
                effective = com.rumilance.practice.rank.PlayerRank.VIP;
            } else {
                effective = com.rumilance.practice.rank.PlayerRank.NORM;
            }
            net.kyori.adventure.text.Component rankIcon = iconFontService.rankIcon(effective);
            if (!rankIcon.equals(net.kyori.adventure.text.Component.empty())) {
                prefix = prefix.append(rankIcon).append(net.kyori.adventure.text.Component.space());
            }
            if (session != null && session.isTeamMatch()) {
                // Team marker: a plain coloured ● in the team colour (no resource pack needed).
                com.rumilance.practice.state.TeamColor teamColor =
                        session.teamColor(player.getUniqueId());
                net.kyori.adventure.text.format.NamedTextColor dotColor =
                        teamColor == com.rumilance.practice.state.TeamColor.RED
                                ? net.kyori.adventure.text.format.NamedTextColor.RED
                                : net.kyori.adventure.text.format.NamedTextColor.AQUA;
                prefix = prefix
                        .append(net.kyori.adventure.text.Component.text("\u25CF", dotColor))
                        .append(net.kyori.adventure.text.Component.space());
            }
            return prefix;
        });

        // Paid kill-effect cosmetics: played at a victim's death position for VIP+ killers.
        com.rumilance.practice.cosmetic.kill.KillEffectRegistry killEffectRegistry =
                new com.rumilance.practice.cosmetic.kill.KillEffectRegistry(configService, plugin.getLogger());
        com.rumilance.practice.cosmetic.kill.KillEffectService killEffectService =
                new com.rumilance.practice.cosmetic.kill.KillEffectService(
                        plugin, settingsService, rankService, killEffectRegistry);
        killEffectService.start();
        services.register(com.rumilance.practice.cosmetic.kill.KillEffectService.class, killEffectService);
        services.register(com.rumilance.practice.cosmetic.kill.KillEffectRegistry.class, killEffectRegistry);
        com.rumilance.practice.combat.KillFeed.setKillEffectPlayer(killEffectService::playOnKill);

        // When a player drops below VIP+, reset smithing trims to default: strip premium
        // materials/patterns from everything worn/held, and scrub saved kit layouts.
        final KitLayoutRepository kitLayoutRepositoryRef = kitLayoutRepository;
        final AsyncExecutor asyncExecutorRef = asyncExecutor;
        final org.slf4j.Logger trimLogger = plugin.getSLF4JLogger();
        rankService.setRankChangeListener(player -> {
            try {
                int worn = com.rumilance.practice.cosmetic.ArmorTrimReset.stripPremiumTrims(
                        java.util.Arrays.asList(player.getInventory().getContents()));
                if (worn > 0) {
                    player.updateInventory();
                }
            } catch (RuntimeException e) {
                trimLogger.warn("Failed to reset worn trims on rank downgrade", e);
            }
            final java.util.UUID pid = player.getUniqueId();
            asyncExecutorRef.runAsync(() -> {
                try {
                    for (var snap : kitLayoutRepositoryRef.findAllForPlayer(pid)) {
                        org.bukkit.inventory.ItemStack[] items =
                                com.rumilance.practice.util.ItemSerializer.fromBase64(snap.itemDataBase64());
                        if (items == null) {
                            continue;
                        }
                        if (com.rumilance.practice.cosmetic.ArmorTrimReset.stripPremiumTrims(java.util.Arrays.asList(items)) > 0) {
                            kitLayoutRepositoryRef.upsert(com.rumilance.practice.model.KitLayoutSnapshot.create(
                                    snap.uuid(), snap.kit(),
                                    com.rumilance.practice.util.ItemSerializer.toBase64(items)));
                        }
                    }
                } catch (RuntimeException | java.sql.SQLException e) {
                    trimLogger.warn("Failed to reset saved kit trims on rank downgrade", e);
                }
            });
        });

        QueueKitGui rankedGui = new QueueKitGui(
                guiSessions, soundService, kitService, queueService, queueCoordinator, true);
        QueueKitGui unrankedGui = new QueueKitGui(
                guiSessions, soundService, kitService, queueService, queueCoordinator, false);
        KitSelectGui kitSelectGui = new KitSelectGui(guiSessions, soundService, kitService);
        DuelRequestGui duelRequestGui = new DuelRequestGui(
                guiSessions, soundService, kitService, duelRequestService, settingsService,
                statsService, kitSelectGui, messageService);
        kitSelectGui.setDuelRequestGui(duelRequestGui);
        DuelMapSelectGui duelMapSelectGui = new DuelMapSelectGui(
                guiSessions, soundService, kitService, duelRequestGui, messageService);
        duelRequestGui.setMapSelectGui(duelMapSelectGui);

        SettingsGui settingsGui = new SettingsGui(guiSessions, soundService, settingsService);
        settingsGui.setToggleCooldownSeconds(configService.config().getInt("gui.toggle-cooldown-seconds", 2));
        settingsGui.setTeamColoredArmorService(teamColoredArmor);
        StatsKitGui statsKitGui = new StatsKitGui(guiSessions, soundService, kitService, statsService);
        ProfileGui profileGui = new ProfileGui(guiSessions, soundService, kitService, statsService);
        PlayersGui playersGui = new PlayersGui(
                guiSessions, soundService, stateManager, statsService, duelRequestGui, messageService);
        SpectateListGui spectateListGui = new SpectateListGui(
                guiSessions, soundService, matchRegistry, spectatorService, ffaService, kitService);
        FfaListGui ffaListGui = new FfaListGui(guiSessions, soundService, ffaService);
        KitPreviewGui kitPreviewGui = new KitPreviewGui(guiSessions, soundService, kitService);
        rankedGui.setPreviewGui(kitPreviewGui);
        unrankedGui.setPreviewGui(kitPreviewGui);

        TitleService titleService = new TitleService(settingsService, statsService);
        services.register(TitleService.class, titleService);
        matchService.setTitleService(titleService);
        TitleGui titleGui = new TitleGui(guiSessions, soundService, titleService);
        MatchReportGui matchReportGui = new MatchReportGui(guiSessions, soundService, matchService);
        matchService.setMatchReportOpener(matchReportGui::openLastReport);

        TeamService teamService = new TeamService(plugin, matchService, messageService);
        services.register(TeamService.class, teamService);
        matchService.setTeamService(teamService);
        PartyHotbar partyHotbar = new PartyHotbar(lobbyService);
        teamService.setPartyHotbar(partyHotbar);
        teamService.setHasPartyMaps(() -> !arenaStore.partyArenas().isEmpty());
        ffaService.setTeamService(teamService);
        lobbyService.setHubInventoryCustomizer(player -> {
            var team = teamService.teamOf(player.getUniqueId());
            if (team.isEmpty()) {
                return false;
            }
            var t = team.get();
            partyHotbar.give(player, t.isOwner(player.getUniqueId()),
                    !arenaStore.partyArenas().isEmpty(), t.friendlyFire());
            return true;
        });
        TeamsBrowserGui teamsBrowserGui =
                new TeamsBrowserGui(guiSessions, soundService, teamService, null, messageService);
        TeamKitSelectGui teamKitSelectGui =
                new TeamKitSelectGui(guiSessions, soundService, teamService, kitService, messageService);
        TeamHubGui teamHubGui = new TeamHubGui(
                guiSessions, soundService, teamService, teamsBrowserGui, teamKitSelectGui, messageService);
        teamsBrowserGui.setHub(teamHubGui);
        PartyInviteGui partyInviteGui = new PartyInviteGui(
                guiSessions, soundService, teamService, messageService);
        partyInviteGui.setTeamHubGui(teamHubGui);
        teamHubGui.setPartyInviteGui(partyInviteGui);
        PartyMapSelectGui partyMapSelectGui = new PartyMapSelectGui(
                guiSessions, soundService, teamService, arenaStore, kitService);
        partyMapSelectGui.setTeamHubGui(teamHubGui);
        partyMapSelectGui.setTeamKitSelectGui(teamKitSelectGui);
        teamKitSelectGui.setPartyMapSelectGui(partyMapSelectGui);
        teamHubGui.setPartyMapSelectGui(partyMapSelectGui);
        teamHubGui.setArenaStoreSupplier(arenaStore::partyArenas);
        ArenaAdminGui arenaAdminGui = new ArenaAdminGui(
                guiSessions, soundService, arenaStore, arenaService);
        PartyIconListener partyIconListener = new PartyIconListener(plugin, arenaStore, arenaService);
        arenaAdminGui.setPartyIconPrompt(partyIconListener::await);

        PresetItems presetItems = new PresetItems(configService);
        services.register(PresetItems.class, presetItems);
        // Wire the kit-id catalogue so each kit gets its own independently-editable
        // preset; wiring also performs the one-time migration of the old shared pool.
        presetItems.setKitIdProvider(() -> kitService.all().stream()
                .map(com.rumilance.practice.model.KitDefinition::name)
                .toList());
        EditKitGui editKitGui = new EditKitGui(
                guiSessions, soundService, kitService, kitLayoutRepository, layoutCache, asyncExecutor,
                stateManager, presetItems);
        KitEditStash kitEditStash = new KitEditStash();
        editKitGui.setKitEditStash(kitEditStash);
        KitAnvilRenameService kitAnvilRenameService = new KitAnvilRenameService(plugin, rankService, messageService);
        kitAnvilRenameService.setEditKitGui(editKitGui);
        editKitGui.setKitAnvilRenameService(kitAnvilRenameService);
        editKitGui.setLobbyService(lobbyService);
        services.register(KitAnvilRenameService.class, kitAnvilRenameService);
        ArrowEffectGui arrowEffectGui =
                new ArrowEffectGui(guiSessions, soundService, arrowEffectService, settingsService);
        com.rumilance.practice.gui.menus.KillEffectGui killEffectGui =
                new com.rumilance.practice.gui.menus.KillEffectGui(guiSessions, soundService, settingsService, rankService, killEffectService);

        OriginalKitService originalKitService = new OriginalKitService(
                originalKitRepository, asyncExecutor, plugin.getLogger(), configService);
        services.register(OriginalKitService.class, originalKitService);
        com.rumilance.practice.originalkit.OriginalKitRoomService originalKitRoomService =
                new com.rumilance.practice.originalkit.OriginalKitRoomService(configService, plugin);
        services.register(com.rumilance.practice.originalkit.OriginalKitRoomService.class, originalKitRoomService);
        originalKitService.setRoomService(originalKitRoomService);
        plugin.getServer().getPluginManager().registerEvents(
                new com.rumilance.practice.originalkit.OriginalKitRoomListener(
                        originalKitRoomService, originalKitService, plugin), plugin);
        EkitItems ekitItems = new EkitItems(configService);
        services.register(EkitItems.class, ekitItems);

        ConfirmGui confirmGui = new ConfirmGui(guiSessions, soundService);
        confirmGui.setOriginalKitService(originalKitService);
        OriginalKitEditGui originalKitEditGui =
                new OriginalKitEditGui(guiSessions, soundService, originalKitService, ekitItems);
        EnchantGui enchantGui =
                new EnchantGui(guiSessions, soundService, originalKitService, originalKitEditGui);
        PotionGui potionGui =
                new PotionGui(guiSessions, soundService, originalKitService, originalKitEditGui);
        EkitCopyGui ekitCopyGui =
                new EkitCopyGui(guiSessions, soundService, kitService, originalKitEditGui, originalKitService);
        EkitChoiceGui ekitChoiceGui = new EkitChoiceGui(
                guiSessions, soundService, originalKitService, originalKitEditGui, ekitCopyGui);
        OriginalKitGui originalKitGui = new OriginalKitGui(guiSessions, soundService, originalKitService);
        originalKitGui.setConfirmGui(confirmGui);
        originalKitGui.setEditGui(originalKitEditGui);
        originalKitGui.setChoiceGui(ekitChoiceGui);
        originalKitEditGui.setEnchantGui(enchantGui);
        originalKitEditGui.setPotionGui(potionGui);
        originalKitEditGui.setConfirmGui(confirmGui);
        originalKitEditGui.setOriginalKitGui(originalKitGui);
        ekitCopyGui.setChoiceGui(ekitChoiceGui);
        ekitChoiceGui.setOriginalKitGui(originalKitGui);

        EkitSelectGui ekitSelectGui = new EkitSelectGui(guiSessions, soundService, kitService);
        ekitSelectGui.setEditKitGui(editKitGui);
        ekitSelectGui.setOriginalKitGui(originalKitGui);
        editKitGui.setEkitSelectGui(ekitSelectGui);
        EkitAdminGui ekitAdminGui = new EkitAdminGui(guiSessions, soundService, ekitItems);
        PresetAdminGui presetAdminGui = new PresetAdminGui(guiSessions, soundService, presetItems);
        KitAdminGui kitAdminGui = new KitAdminGui(guiSessions, soundService, kitService, messageService);
        kitAdminGui.setArenaNames(() -> arenaStore.templates().stream().map(ArenaTemplate::name).toList());
        KitStartEffectsGui kitStartEffectsGui = new KitStartEffectsGui(guiSessions, soundService, kitService);
        kitAdminGui.setOpenStartEffects(kitStartEffectsGui::open);
        kitStartEffectsGui.setReturnTo(kitAdminGui::openConfig);

        AdminMenuGui adminMenuGui = new AdminMenuGui(guiSessions, soundService);
        adminMenuGui.setOpenKitAdmin(kitAdminGui::open);
        adminMenuGui.setOpenPresetAdmin(player -> {
            presetAdminGui.setReturnTo(adminMenuGui::open);
            presetAdminGui.openAdmin(player);
        });
        adminMenuGui.setOpenEkitAdmin(ekitAdminGui::openAdmin);
        kitAdminGui.setOpenPresetAdmin(player -> {
            presetAdminGui.setReturnTo(kitAdminGui::open);
            presetAdminGui.openAdmin(player);
        });
        KitArenaSelectGui kitArenaSelectGui = new KitArenaSelectGui(
                guiSessions, soundService, kitService, arenaStore);
        kitArenaSelectGui.setKitAdminGui(kitAdminGui);
        kitAdminGui.setOpenArenaSelect(kitArenaSelectGui::open);
        presetAdminGui.setReturnTo(adminMenuGui::open);

        BattleMenuGui battleMenuGui = new BattleMenuGui(
                guiSessions, soundService, rankedGui, unrankedGui, playersGui, ffaListGui,
                messageService);
        GameMenuGui gameMenuGui = new GameMenuGui(
                guiSessions, soundService, battleMenuGui, ekitSelectGui, spectateListGui, settingsGui,
                titleGui, messageService);

        MatchInventoryGui matchInventoryGui =
                new MatchInventoryGui(guiSessions, soundService, matchInventoryStore);
        KillFeed.setInventoryOpener(matchInventoryGui::open);
        BanListGui banListGui = new BanListGui(guiSessions, soundService, banService);
        ReportGui reportGui = new ReportGui(guiSessions, soundService, reportService);
        ReportListGui reportListGui =
                new ReportListGui(guiSessions, soundService, reportService, replayService);
        SmithingTrimGui smithingTrimGui = new SmithingTrimGui(guiSessions, soundService, rankService);
        smithingTrimGui.setEditKitGui(editKitGui);
        editKitGui.setSmithingTrimGui(smithingTrimGui);

        PracticeLayoutGui practiceLayoutGui =
                new PracticeLayoutGui(guiSessions, soundService, practiceService);
        PracticeMaceGui practiceMaceGui =
                new PracticeMaceGui(guiSessions, soundService, practiceService);
        PracticeBotGui practiceBotGui =
                new PracticeBotGui(guiSessions, soundService, practiceService);
        practiceService.setOpenLayoutGui(practiceLayoutGui::openFor);
        practiceService.setOpenMaceGui(practiceMaceGui::openFor);
        practiceService.setOpenBotGui(practiceBotGui::openFor);

        GuiListener guiListener = new GuiListener(guiSessions, stateManager, originalKitService, messageService);
        guiListener.register(rankedGui);
        guiListener.register(unrankedGui);
        guiListener.register(kitSelectGui);
        guiListener.register(duelRequestGui);
        guiListener.register(duelMapSelectGui);
        guiListener.register(settingsGui);
        guiListener.register(statsKitGui);
        guiListener.register(profileGui);
        guiListener.register(banListGui);
        guiListener.register(reportGui);
        guiListener.register(reportListGui);
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
        guiListener.register(presetAdminGui);
        guiListener.register(adminMenuGui);
        guiListener.register(spectateListGui);
        guiListener.register(ffaListGui);
        guiListener.register(editKitGui);
        guiListener.register(arrowEffectGui);
        guiListener.register(killEffectGui);
        guiListener.register(kitAdminGui);
        guiListener.register(kitArenaSelectGui);
        guiListener.register(kitStartEffectsGui);
        guiListener.register(kitPreviewGui);
        guiListener.register(titleGui);
        guiListener.register(matchReportGui);
        guiListener.register(teamsBrowserGui);
        guiListener.register(teamHubGui);
        guiListener.register(teamKitSelectGui);
        guiListener.register(partyInviteGui);
        guiListener.register(partyMapSelectGui);
        guiListener.register(arenaAdminGui);
        guiListener.register(gameMenuGui);
        guiListener.register(battleMenuGui);
        guiListener.register(matchInventoryGui);
        guiListener.register(practiceLayoutGui);
        guiListener.register(practiceMaceGui);
        guiListener.register(practiceBotGui);
        guiListener.register(smithingTrimGui);
        guiListener.setMenuReturn(gameMenuGui::open);
        guiListener.setBattleMenuReturn(battleMenuGui::open);
        guiListener.setReopenOriginalEditor(player -> {
            OriginalKitService.EditContext ctx = originalKitService.context(player.getUniqueId());
            if (ctx != null) {
                originalKitEditGui.open(player, ctx.slot, ctx.layout);
            }
        });

        FunctionalItemListener functionalItemListener =
                new FunctionalItemListener(soundService, queueCoordinator, rankedGui, unrankedGui);
        functionalItemListener.setOpenSettings(settingsGui::open);
        functionalItemListener.setOpenFfa(ffaListGui::open);
        functionalItemListener.setOpenEkit(ekitSelectGui::open);
        functionalItemListener.setOpenSpectate(spectateListGui::open);
        functionalItemListener.setOpenMenu(gameMenuGui::open);
        functionalItemListener.setOpenBattle(battleMenuGui::open);
        functionalItemListener.setOpenTitles(titleGui::open);
        functionalItemListener.setOpenParty(player -> {
            if (teamService.teamOf(player.getUniqueId()).isPresent()) {
                teamHubGui.open(player);
            } else {
                teamsBrowserGui.open(player);
            }
        });
        functionalItemListener.setOpenPartyInvite(partyInviteGui::openFor);
        functionalItemListener.setOpenPartyStart(teamKitSelectGui::open);
        // The party-map hotkey routes through kit selection: a map can only be chosen for
        // a specific kit (kit -> that kit's party maps -> pick to start).
        functionalItemListener.setOpenPartyMap(teamKitSelectGui::open);
        functionalItemListener.setPartyLeave(player -> {
            TeamService.Result r = teamService.leave(player);
            if (r != TeamService.Result.OK) {
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        teamService.errorMessage(player, r),
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        });
        functionalItemListener.setPartyTogglePublic(player -> teamService.togglePublic(player));
        functionalItemListener.setPartyToggleFf(player -> teamService.toggleFriendlyFire(player));
        gameMenuGui.setOpenTeams(player -> {
            if (teamService.teamOf(player.getUniqueId()).isPresent()) {
                teamHubGui.open(player);
            } else {
                teamsBrowserGui.open(player);
            }
        });

        ScoreboardConfig scoreboardConfig = new ScoreboardConfig(configService.scoreboard());
        scoreboardService = new ScoreboardService(
                plugin,
                scoreboardConfig,
                stateManager,
                queueService,
                matchRegistry,
                rankedStatsRepository,
                winStreakRepository,
                settingsService,
                asyncExecutor);
        scoreboardService.setSpectatorService(spectatorService);
        scoreboardService.setFfaService(ffaService);
        scoreboardService.setIconFontService(iconFontService);
        scoreboardService.setRankService(rankService);
        TabVisibilityService tabVisibilityService =
                new TabVisibilityService(plugin, stateManager, matchRegistry);
        tabVisibilityService.setSpectatorService(spectatorService);
        services.register(TabVisibilityService.class, tabVisibilityService);
        scoreboardService.setTabVisibilityService(tabVisibilityService);
        matchService.setTabVisibilityService(tabVisibilityService);
        OpponentHealthNametagService opponentHealthNametagService =
                new OpponentHealthNametagService(plugin, matchRegistry);
        services.register(OpponentHealthNametagService.class, opponentHealthNametagService);
        scoreboardService.setOpponentHealthNametagService(opponentHealthNametagService);
        opponentHealthNametagService.start();
        com.rumilance.practice.headfont.HeadFontService headFontService =
                new com.rumilance.practice.headfont.HeadFontService();
        services.register(com.rumilance.practice.headfont.HeadFontService.class, headFontService);
        com.rumilance.practice.match.MatchActionBarService matchActionBarService =
                new com.rumilance.practice.match.MatchActionBarService(plugin, matchRegistry);
        matchActionBarService.setSpectatorService(spectatorService);
        matchActionBarService.setHeadFontService(headFontService);
        matchActionBarService.setConfigService(configService);
        matchActionBarService.start();
        TabFightListService tabFightListService = new TabFightListService(plugin);
        scoreboardService.setTabFightListService(tabFightListService);
        if (scoreboardConfig.enabled()) {
            scoreboardService.start();
        }
        services.register(ScoreboardService.class, scoreboardService);

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new com.rumilance.practice.replay.ReplayControlListener(replayService), plugin);
        pm.registerEvents(new BanLoginListener(banService), plugin);
        pm.registerEvents(new com.rumilance.practice.listener.ChatBanGuardListener(chatBanService), plugin);
        pm.registerEvents(new SessionBootstrapListener(
                sessionManager, stateManager, lobbyService, settings.defaultLocale(), playerRepository,
                layoutCache, settingsService, asyncExecutor, plugin, messageService, rankService, chatBanService), plugin);
        pm.registerEvents(new LobbyListener(lobbyService, stateManager, guiSessions, ffaService), plugin);
        pm.registerEvents(new MotdListener(), plugin);
        com.rumilance.practice.world.WorldOptimizer worldOptimizer =
                new com.rumilance.practice.world.WorldOptimizer(plugin);
        pm.registerEvents(worldOptimizer, plugin);
        worldOptimizer.optimizeLoadedWorlds();
        com.rumilance.practice.combat.ExplosionSourceTracker explosionSources =
                new com.rumilance.practice.combat.ExplosionSourceTracker(plugin);
        pm.registerEvents(explosionSources, plugin);
        pm.registerEvents(new MatchListener(matchService, kitService, combatNet, practiceTnt, playerPlacedBlockTracker, explosionSources), plugin);
        pm.registerEvents(new MatchCommandGuardListener(stateManager, messageService), plugin);
        pm.registerEvents(new MatchCountdownLockListener(stateManager), plugin);
        pm.registerEvents(new com.rumilance.practice.match.MatchChatListener(matchRegistry, spectatorService), plugin);
        pm.registerEvents(new TeamColoredArmorListener(teamColoredArmor, settingsService), plugin);
        pm.registerEvents(new ArenaBoundsListener(matchService, arenaService), plugin);
        pm.registerEvents(new SpectatorBoundsListener(
                spectatorService, matchRegistry, arenaService, ffaService), plugin);
        pm.registerEvents(new FfaListener(ffaService, kitService, stateManager, combatNet, practiceTnt, playerPlacedBlockTracker, explosionSources), plugin);
        pm.registerEvents(new FfaBlockTracker(ffaService), plugin);
        pm.registerEvents(new ItemFlowGuardListener(stateManager, ffaService), plugin);
        pm.registerEvents(ffaSpawnIndex, plugin);
        pm.registerEvents(new InstantExpCollectListener(), plugin);
        PracticeTntListener practiceTntListener =
                new PracticeTntListener(practiceTnt, matchService, ffaService, plugin);
        // Vanilla skips explosion damage for the blast's source entity (Paper #11167): on
        // modern versions that is the crystal detonator / creeper igniter / TNT lighter, so
        // own-crystal & own-creeper self-damage silently disappears. This listener restores
        // the skipped share (damage + knockback) without touching anything vanilla applied.
        com.rumilance.practice.combat.ExplosionSelfDamageListener explosionSelfDamage =
                new com.rumilance.practice.combat.ExplosionSelfDamageListener(plugin);
        practiceTntListener.setSelfDamage(explosionSelfDamage);
        pm.registerEvents(explosionSelfDamage, plugin);
        pm.registerEvents(practiceTntListener, plugin);
        pm.registerEvents(new CrystalAnchorPerfListener(matchService, ffaService), plugin);
        pm.registerEvents(new com.rumilance.practice.combat.PortalBlockListener(matchRegistry, ffaService), plugin);
        pm.registerEvents(new PracticePearlListener(matchService, ffaService, arenaService, sightSettings), plugin);
        pm.registerEvents(combatSync, plugin);
        combatSync.start();
        pm.registerEvents(new com.rumilance.practice.combat.ProjectileSpreadListener(configService), plugin);
        // Paper PvP regression workarounds (shield stun i-frames, blocked-hit knockback,
        // trident jab reconnect, bow draw-force arrow damage). Combatant-only predicate.
        java.util.function.Predicate<java.util.UUID> combatant = id -> {
            com.rumilance.practice.session.MatchSession s = matchService.registry().byPlayer(id).orElse(null);
            if (s != null) {
                com.rumilance.practice.state.MatchState st = s.state();
                return st == com.rumilance.practice.state.MatchState.ACTIVE
                        || st == com.rumilance.practice.state.MatchState.COUNTDOWN
                        || st == com.rumilance.practice.state.MatchState.ENDING;
            }
            return ffaService.isInFfa(id);
        };
        pm.registerEvents(new com.rumilance.practice.combat.PaperCombatCompatListener(plugin, combatant), plugin);
        // Paper #11012/#9504: resync the hotbar when our kit/arena rules cancel a place/break.
        pm.registerEvents(new com.rumilance.practice.guard.BlockInteractionResyncListener(plugin, combatant), plugin);
        pm.registerEvents(new GoldenHeadListener(plugin, matchRegistry), plugin);
        pm.registerEvents(new TotemPickupListener(combatant), plugin);
        pm.registerEvents(guiListener, plugin);
        pm.registerEvents(kitAnvilRenameService, plugin);
        pm.registerEvents(opponentHealthNametagService, plugin);
        pm.registerEvents(functionalItemListener, plugin);
        pm.registerEvents(new LobbyCompassListener(stateManager, soundService, gameMenuGui::open), plugin);
        pm.registerEvents(new com.rumilance.practice.lobby.DuelRightClickListener(
                stateManager, duelRequestGui, soundService, messageService), plugin);
        AdminToolListener adminToolListener = new AdminToolListener(soundService);
        adminToolListener.setOpenAdminMenu(adminMenuGui::open);
        pm.registerEvents(adminToolListener, plugin);
        pm.registerEvents(new TeamListener(teamService), plugin);
        pm.registerEvents(partyIconListener, plugin);
        pm.registerEvents(new SpamFilterListener(spamFilterService), plugin);
        pm.registerEvents(new SignChangeGuardListener(signGuardService), plugin);
        pm.registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (!configService.config().getBoolean("sign-guard.active-probe.check-on-join", false)) {
                    return;
                }
                if (!signProbeService.isAvailable()) {
                    return;
                }
                Player joined = event.getPlayer();
                if (joined.hasPermission("rumilance.admin") || joined.hasPermission("rumilance.sign.bypass")) {
                    return;
                }
                long delay = Math.max(20L,
                        configService.config().getLong("sign-guard.active-probe.join-delay-ticks", 60));
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> signProbeService.probe(joined, null), delay);
            }

            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                // Release any in-flight probe state so a disconnect mid-scan can't leak it.
                signProbeService.abandon(event.getPlayer().getUniqueId());
            }
        }, plugin);
        PendingInput.init(plugin);
        FloatingTextCleanup.start(plugin,
                configService.config().getLong("cleanup.floating-text-window-seconds", 300L));
        pm.registerEvents(new AdvancementBlockListener(), plugin);
        pm.registerEvents(new PracticeSideListener(
                chatBanService, settingsService, guiSessions, arrowEffectService, spectatorService,
                ffaService, originalKitService), plugin);
        pm.registerEvents(new PracticeListener(practiceService), plugin);
        pm.registerEvents(new BedrockJoinListener(plugin), plugin);
        pm.registerEvents(new SmithingTrimListener(rankService, smithingTrimGui, stateManager, messageService), plugin);

        LunarRichPresenceService lunarRichPresence = new LunarRichPresenceService(plugin, stateManager);
        services.register(LunarRichPresenceService.class, lunarRichPresence);
        pm.registerEvents(lunarRichPresence, plugin);
        lunarRichPresence.start();

        DuelCommand rankedDuel = new DuelCommand(
                rankedGui, unrankedGui, duelRequestGui, duelRequestService, matchService, kitService,
                stateManager, soundService, lobbyService, queueCoordinator, runtimeFlags, true, messageService);
        DuelCommand unrankedDuel = new DuelCommand(
                rankedGui, unrankedGui, duelRequestGui, duelRequestService, matchService, kitService,
                stateManager, soundService, lobbyService, queueCoordinator, runtimeFlags, false, messageService);
        rankedDuel.setFfaService(ffaService);
        rankedDuel.setSpectatorService(spectatorService);
        rankedDuel.setGuiSessions(guiSessions);
        rankedDuel.setTeamService(teamService);
        unrankedDuel.setFfaService(ffaService);
        unrankedDuel.setSpectatorService(spectatorService);
        unrankedDuel.setGuiSessions(guiSessions);
        unrankedDuel.setTeamService(teamService);
        duelRequestGui.setTeamService(teamService);
        duelRequestGui.setMatchService(matchService);
        queueCoordinator.setTeamService(teamService);
        AcceptDenyCommand acceptDeny = new AcceptDenyCommand(rankedDuel, duelRequestService);
        pm.registerEvents(new DuelChatInterceptListener(rankedDuel, duelRequestService), plugin);
        ArenaKitAdminCommand arenaKitAdmin = new ArenaKitAdminCommand(
                configService, arenaStore, arenaService, kitService, queueService, faweBridge,
                new File(PluginIdentity.dataFolder(plugin), "schematics"), soundService, kitAdminGui);
        arenaKitAdmin.setPresetItems(presetItems);
        arenaKitAdmin.setPresetAdminGui(presetAdminGui);
        arenaKitAdmin.setArenaAdminGui(arenaAdminGui);
        arenaKitAdmin.setPartyIconPrompt(partyIconListener::await);
        ChatBanCommand chatBanCommand = new ChatBanCommand(chatBanService, playerRepository);
        FfaCommand ffaCommand = new FfaCommand(ffaListGui, ffaService, kitService);
        PracticeAdminCommand practiceAdmin = new PracticeAdminCommand(
                plugin, configService, soundService, matchService, lobbyService, runtimeFlags, kitService,
                arenaStore, arenaService, ffaService);
        practiceAdmin.setOpenAdminMenu(adminMenuGui::open);
        practiceAdmin.setScoreboardService(scoreboardService);
        practiceAdmin.setPracticeService(practiceService);
        AdminCommand adminCommand = new AdminCommand(
                plugin, statsResetService, playerRepository, asyncExecutor, originalKitService);
        adminCommand.setScoreboardService(scoreboardService);

        LobbyCommand lobbyCommand = new LobbyCommand(
                lobbyService, stateManager, spectatorService, ffaService, messageService, practiceService);
        lobbyCommand.setQueueCoordinator(queueCoordinator);
        lobbyCommand.setMatchService(matchService);
        matchService.setHubReturn(lobbyCommand::applyHub);
        ffaService.setHubReturn(lobbyCommand::applyHub);

        bind("duel", unrankedDuel);
        bind("ranked", rankedDuel);
        bind("unranked", unrankedDuel);
        bind("accept", acceptDeny);
        bind("deny", acceptDeny);
        bind("cancel", acceptDeny);
        bind("rpaccept", acceptDeny);
        bind("rpdeny", acceptDeny);
        bind("rpcancel", acceptDeny);
        bind("queue", new QueueLeaveCommand(queueCoordinator));
        bind("lobby", lobbyCommand);
        bind("spawn", lobbyCommand);
        bind("hub", lobbyCommand);
        bind("lang", new LangCommand(sessionManager, settingsService, messageService));
        bind("matchinv", new MatchInvCommand(matchInventoryGui));
        bind("setfunc", new SetFuncCommand());
        bind("admin", adminCommand);
        bind("practiceadmin", practiceAdmin);
        bind("slobby", practiceAdmin);
        bind("setlobbyitem", practiceAdmin);
        bind("rumireload", new RumilanceReloadCommand(services));
        bind("arena", arenaKitAdmin);
        bind("kit", arenaKitAdmin);
        bind("toggle", arenaKitAdmin);
        bind("chatban", chatBanCommand);
        bind("chatunban", chatBanCommand);
        bind("ekitadmin", new EkitAdminCommand(ekitAdminGui,
                services.get(com.rumilance.practice.originalkit.OriginalKitRoomService.class)));
        bind("giveitem", new GiveItemCommand());
        bind("matchreport", new MatchReportCommand(matchService, settingsService));
        bind("walltext", new WallTextCommand(wallTextService));
        bind("ffa", ffaCommand);
        bind("killeffect", new com.rumilance.practice.command.KillEffectCommand(killEffectGui));
        bind("leave", new LeaveCommand(matchService, messageService));
        bind("team", new TeamCommand(teamService, kitService, teamHubGui, teamsBrowserGui, messageService));
        bind("prac", new PracCommand(practiceService));
        bind("practice", new PracticeCommand(practiceService));
        bind("setrank", new SetRankCommand(rankService, playerRepository));
        bind("title", (CommandExecutor) (sender, command, label, args) -> {
            if (sender instanceof Player player) {
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
            bind(cmd, new PlayerCommands(
                    type, plugin, asyncExecutor, statsService, kitService, statsKitGui, profileGui,
                    settingsGui, ekitSelectGui, playersGui, spectateListGui, spectatorService, ffaListGui,
                    editKitGui, arrowEffectGui, chatBanService));
        }

        BanCommand banCommand = new BanCommand(banService, banListGui, playerRepository);
        bind("ban", banCommand);
        bind("kick", banCommand);
        bind("banlist", banCommand);
        bind("unban", banCommand);
        bind("testban", banCommand);
        bind("testkick", banCommand);
        bind("report", new ReportCommand(reportGui));
        bind("reportlist", new ReportListCommand(reportListGui));
        bind("replay", new ReplayCommand(replayService,
                services.find(com.rumilance.practice.replay.ReplayArchive.class).orElse(null),
                rankService));
        bind("signcheck", new SignCheckCommand(signProbeService));
        bind("checkid", new CheckIdCommand(duelLogStore));
        bind("originalkit", (CommandExecutor) (sender, command, label, args) -> {
            if (sender instanceof Player player) {
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
        if (matchActionRecorder != null) {
            matchActionRecorder.stop();
        }
        if (replayService != null) {
            replayService.shutdown();
        }
        ArenaService disposable = services.find(ArenaService.class).orElse(null);
        if (disposable instanceof DisposableArenaService copies) {
            try {
                copies.clearAllCopies().get(20L, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "Could not clear all disposable arena copies on shutdown: " + e.getMessage());
            }
        }
        if (scoreboardService != null) {
            scoreboardService.stop();
        }
        services.find(FfaService.class).ifPresent(FfaService::shutdown);
        services.find(LunarRichPresenceService.class).ifPresent(LunarRichPresenceService::shutdown);
        if (arrowEffectService != null) {
            arrowEffectService.stop();
        }
        services.find(com.rumilance.practice.cosmetic.kill.KillEffectService.class)
                .ifPresent(com.rumilance.practice.cosmetic.kill.KillEffectService::stop);
        if (settingsService != null) {
            settingsService.flushAll();
        }
        if (chatBanService != null) {
            chatBanService.setShuttingDown(true);
        }
        if (banService != null) {
            banService.persist();
        }
        if (practiceService != null) {
            practiceService.stop();
        }
        if (teamGlowLosService != null) {
            teamGlowLosService.stop();
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Missing command in plugin.yml: " + name);
            return;
        }
        if (executor instanceof CommandExecutor commandExecutor) {
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
