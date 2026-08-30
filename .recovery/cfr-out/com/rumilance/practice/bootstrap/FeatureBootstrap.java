/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.rumilance.practice.RumilancePractice
 *  com.rumilance.practice.admin.AdminToolListener
 *  com.rumilance.practice.arena.ArenaService
 *  com.rumilance.practice.arena.ArenaTemplateStore
 *  com.rumilance.practice.arena.DisposableArenaService
 *  com.rumilance.practice.arena.FaweArenaService
 *  com.rumilance.practice.arena.SimpleArenaService
 *  com.rumilance.practice.arena.fawe.FaweBridge
 *  com.rumilance.practice.arrow.ArrowEffectService
 *  com.rumilance.practice.ban.BanLoginListener
 *  com.rumilance.practice.ban.BanService
 *  com.rumilance.practice.bootstrap.FeatureBootstrap$3
 *  com.rumilance.practice.bootstrap.ServiceRegistry
 *  com.rumilance.practice.bot.SwordBotService
 *  com.rumilance.practice.chat.PendingInput
 *  com.rumilance.practice.chat.SpamFilterListener
 *  com.rumilance.practice.chat.SpamFilterService
 *  com.rumilance.practice.combat.CombatNetTracker
 *  com.rumilance.practice.combat.CombatSyncListener
 *  com.rumilance.practice.combat.CrystalAnchorPerfListener
 *  com.rumilance.practice.combat.InstantExpCollectListener
 *  com.rumilance.practice.combat.KnockbackService
 *  com.rumilance.practice.combat.PaperCombatTuning
 *  com.rumilance.practice.command.AcceptDenyCommand
 *  com.rumilance.practice.command.AdminCommand
 *  com.rumilance.practice.command.ArenaKitAdminCommand
 *  com.rumilance.practice.command.BanCommand
 *  com.rumilance.practice.command.ChatBanCommand
 *  com.rumilance.practice.command.CheckIdCommand
 *  com.rumilance.practice.command.DuelCommand
 *  com.rumilance.practice.command.EkitAdminCommand
 *  com.rumilance.practice.command.FfaCommand
 *  com.rumilance.practice.command.GiveItemCommand
 *  com.rumilance.practice.command.KbCommand
 *  com.rumilance.practice.command.LangCommand
 *  com.rumilance.practice.command.LeaveCommand
 *  com.rumilance.practice.command.LobbyCommand
 *  com.rumilance.practice.command.MatchInvCommand
 *  com.rumilance.practice.command.MatchReportCommand
 *  com.rumilance.practice.command.PlayerCommands
 *  com.rumilance.practice.command.PlayerCommands$Type
 *  com.rumilance.practice.command.PracticeAdminCommand
 *  com.rumilance.practice.command.QueueLeaveCommand
 *  com.rumilance.practice.command.ReplayCommand
 *  com.rumilance.practice.command.ReportCommand
 *  com.rumilance.practice.command.ReportListCommand
 *  com.rumilance.practice.command.SetFuncCommand
 *  com.rumilance.practice.command.SignCheckCommand
 *  com.rumilance.practice.config.PluginSettings
 *  com.rumilance.practice.config.RuntimeFlags
 *  com.rumilance.practice.cosmetic.TitleService
 *  com.rumilance.practice.database.repository.AuditLogRepository
 *  com.rumilance.practice.database.repository.DailyRankedStatsRepository
 *  com.rumilance.practice.database.repository.FfaStatsRepository
 *  com.rumilance.practice.database.repository.KitLayoutRepository
 *  com.rumilance.practice.database.repository.MatchHistoryRepository
 *  com.rumilance.practice.database.repository.ObjectionRepository
 *  com.rumilance.practice.database.repository.OriginalKitRepository
 *  com.rumilance.practice.database.repository.PlayerReportRepository
 *  com.rumilance.practice.database.repository.PlayerRepository
 *  com.rumilance.practice.database.repository.PunishmentRepository
 *  com.rumilance.practice.database.repository.RankedStatsRepository
 *  com.rumilance.practice.database.repository.SettingsRepository
 *  com.rumilance.practice.database.repository.SpamDetectionRepository
 *  com.rumilance.practice.database.repository.WinStreakRepository
 *  com.rumilance.practice.decor.WallTextCommand
 *  com.rumilance.practice.decor.WallTextService
 *  com.rumilance.practice.duel.DuelLogStore
 *  com.rumilance.practice.duel.DuelRequestService
 *  com.rumilance.practice.ekit.EkitItems
 *  com.rumilance.practice.ffa.FfaListener
 *  com.rumilance.practice.ffa.FfaService
 *  com.rumilance.practice.ffa.FfaService$FfaArena
 *  com.rumilance.practice.ffa.FfaSpawnIndex
 *  com.rumilance.practice.gui.AbstractGui
 *  com.rumilance.practice.gui.GuiListener
 *  com.rumilance.practice.gui.GuiSessionRegistry
 *  com.rumilance.practice.gui.menus.AdminMenuGui
 *  com.rumilance.practice.gui.menus.ArrowEffectGui
 *  com.rumilance.practice.gui.menus.BanListGui
 *  com.rumilance.practice.gui.menus.BattleMenuGui
 *  com.rumilance.practice.gui.menus.BotMenuGui
 *  com.rumilance.practice.gui.menus.ConfirmGui
 *  com.rumilance.practice.gui.menus.DuelMapSelectGui
 *  com.rumilance.practice.gui.menus.DuelRequestGui
 *  com.rumilance.practice.gui.menus.EditKitGui
 *  com.rumilance.practice.gui.menus.EkitAdminGui
 *  com.rumilance.practice.gui.menus.EkitChoiceGui
 *  com.rumilance.practice.gui.menus.EkitCopyGui
 *  com.rumilance.practice.gui.menus.EkitSelectGui
 *  com.rumilance.practice.gui.menus.EnchantGui
 *  com.rumilance.practice.gui.menus.FfaListGui
 *  com.rumilance.practice.gui.menus.GameMenuGui
 *  com.rumilance.practice.gui.menus.KitAdminGui
 *  com.rumilance.practice.gui.menus.KitPreviewGui
 *  com.rumilance.practice.gui.menus.KitSelectGui
 *  com.rumilance.practice.gui.menus.MatchInventoryGui
 *  com.rumilance.practice.gui.menus.MatchReportGui
 *  com.rumilance.practice.gui.menus.OriginalKitEditGui
 *  com.rumilance.practice.gui.menus.OriginalKitGui
 *  com.rumilance.practice.gui.menus.PlayersGui
 *  com.rumilance.practice.gui.menus.PotionGui
 *  com.rumilance.practice.gui.menus.PresetAdminGui
 *  com.rumilance.practice.gui.menus.ProfileGui
 *  com.rumilance.practice.gui.menus.QueueKitGui
 *  com.rumilance.practice.gui.menus.ReportGui
 *  com.rumilance.practice.gui.menus.ReportListGui
 *  com.rumilance.practice.gui.menus.SettingsGui
 *  com.rumilance.practice.gui.menus.SpectateListGui
 *  com.rumilance.practice.gui.menus.StatsKitGui
 *  com.rumilance.practice.gui.menus.TeamHubGui
 *  com.rumilance.practice.gui.menus.TeamKitSelectGui
 *  com.rumilance.practice.gui.menus.TeamsBrowserGui
 *  com.rumilance.practice.gui.menus.TitleGui
 *  com.rumilance.practice.item.FunctionalItemListener
 *  com.rumilance.practice.kit.KitLayoutCache
 *  com.rumilance.practice.kit.KitService
 *  com.rumilance.practice.kit.PresetItems
 *  com.rumilance.practice.listener.PracticePearlListener
 *  com.rumilance.practice.listener.PracticeSideListener
 *  com.rumilance.practice.listener.SessionBootstrapListener
 *  com.rumilance.practice.lobby.FloatingTextCleanup
 *  com.rumilance.practice.lobby.LobbyCompassListener
 *  com.rumilance.practice.lobby.LobbyListener
 *  com.rumilance.practice.lobby.LobbyService
 *  com.rumilance.practice.lobby.MotdListener
 *  com.rumilance.practice.locale.MessageService
 *  com.rumilance.practice.match.ArenaBoundsListener
 *  com.rumilance.practice.match.GoldenHeadListener
 *  com.rumilance.practice.match.MatchActionRecorder
 *  com.rumilance.practice.match.MatchListener
 *  com.rumilance.practice.match.MatchRegistry
 *  com.rumilance.practice.match.MatchService
 *  com.rumilance.practice.match.TeamColoredArmorListener
 *  com.rumilance.practice.match.TeamColoredArmorService
 *  com.rumilance.practice.match.inventory.MatchInventoryStore
 *  com.rumilance.practice.match.result.FfaResultProcessor
 *  com.rumilance.practice.match.result.RankedResultProcessor
 *  com.rumilance.practice.match.result.UnrankedResultProcessor
 *  com.rumilance.practice.model.ArenaTemplate
 *  com.rumilance.practice.originalkit.OriginalKitService
 *  com.rumilance.practice.originalkit.OriginalKitService$EditContext
 *  com.rumilance.practice.punishment.ChatBanService
 *  com.rumilance.practice.queue.QueueCoordinator
 *  com.rumilance.practice.queue.QueueService
 *  com.rumilance.practice.replay.ReplayService
 *  com.rumilance.practice.report.ReportEvidenceStore
 *  com.rumilance.practice.report.ReportService
 *  com.rumilance.practice.scoreboard.ScoreboardConfig
 *  com.rumilance.practice.scoreboard.ScoreboardService
 *  com.rumilance.practice.security.sign.SignChangeGuardListener
 *  com.rumilance.practice.security.sign.SignGuardService
 *  com.rumilance.practice.security.sign.SignProbeService
 *  com.rumilance.practice.session.PlayerStateManager
 *  com.rumilance.practice.session.SessionManager
 *  com.rumilance.practice.settings.SettingsService
 *  com.rumilance.practice.sight.SightSettings
 *  com.rumilance.practice.sight.ViewControlService
 *  com.rumilance.practice.sound.SoundService
 *  com.rumilance.practice.spectator.SpectatorBoundsListener
 *  com.rumilance.practice.spectator.SpectatorService
 *  com.rumilance.practice.stats.StatsResetService
 *  com.rumilance.practice.stats.StatsService
 *  com.rumilance.practice.team.TeamCommand
 *  com.rumilance.practice.team.TeamListener
 *  com.rumilance.practice.team.TeamService
 *  com.rumilance.practice.tnt.PracticeTntListener
 *  com.rumilance.practice.tnt.PracticeTntSettings
 *  com.rumilance.practice.util.AsyncExecutor
 *  com.rumilance.practice.util.KitNames
 *  com.rumilance.practice.util.KitNames$CaseStyle
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.PluginManager
 */
package com.rumilance.practice.bootstrap;

import com.rumilance.practice.RumilancePractice;
import com.rumilance.practice.admin.AdminToolListener;
import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.arena.DisposableArenaService;
import com.rumilance.practice.arena.FaweArenaService;
import com.rumilance.practice.arena.SimpleArenaService;
import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.arrow.ArrowEffectService;
import com.rumilance.practice.ban.BanLoginListener;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.bootstrap.FeatureBootstrap;
import com.rumilance.practice.bootstrap.ServiceRegistry;
import com.rumilance.practice.bot.SwordBotService;
import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.chat.SpamFilterListener;
import com.rumilance.practice.chat.SpamFilterService;
import com.rumilance.practice.combat.CombatNetTracker;
import com.rumilance.practice.combat.CombatSyncListener;
import com.rumilance.practice.combat.CrystalAnchorPerfListener;
import com.rumilance.practice.combat.InstantExpCollectListener;
import com.rumilance.practice.combat.KnockbackService;
import com.rumilance.practice.combat.PaperCombatTuning;
import com.rumilance.practice.command.AcceptDenyCommand;
import com.rumilance.practice.command.AdminCommand;
import com.rumilance.practice.command.ArenaKitAdminCommand;
import com.rumilance.practice.command.BanCommand;
import com.rumilance.practice.command.ChatBanCommand;
import com.rumilance.practice.command.CheckIdCommand;
import com.rumilance.practice.command.DuelCommand;
import com.rumilance.practice.command.EkitAdminCommand;
import com.rumilance.practice.command.FfaCommand;
import com.rumilance.practice.command.GiveItemCommand;
import com.rumilance.practice.command.KbCommand;
import com.rumilance.practice.command.LangCommand;
import com.rumilance.practice.command.LeaveCommand;
import com.rumilance.practice.command.LobbyCommand;
import com.rumilance.practice.command.MatchInvCommand;
import com.rumilance.practice.command.MatchReportCommand;
import com.rumilance.practice.command.PlayerCommands;
import com.rumilance.practice.command.PracticeAdminCommand;
import com.rumilance.practice.command.QueueLeaveCommand;
import com.rumilance.practice.command.ReplayCommand;
import com.rumilance.practice.command.ReportCommand;
import com.rumilance.practice.command.ReportListCommand;
import com.rumilance.practice.command.SetFuncCommand;
import com.rumilance.practice.command.SignCheckCommand;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.config.RuntimeFlags;
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
import com.rumilance.practice.ffa.FfaListener;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.ffa.FfaSpawnIndex;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiListener;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.menus.AdminMenuGui;
import com.rumilance.practice.gui.menus.ArrowEffectGui;
import com.rumilance.practice.gui.menus.BanListGui;
import com.rumilance.practice.gui.menus.BattleMenuGui;
import com.rumilance.practice.gui.menus.BotMenuGui;
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
import com.rumilance.practice.gui.menus.KitPreviewGui;
import com.rumilance.practice.gui.menus.KitSelectGui;
import com.rumilance.practice.gui.menus.MatchInventoryGui;
import com.rumilance.practice.gui.menus.MatchReportGui;
import com.rumilance.practice.gui.menus.OriginalKitEditGui;
import com.rumilance.practice.gui.menus.OriginalKitGui;
import com.rumilance.practice.gui.menus.PlayersGui;
import com.rumilance.practice.gui.menus.PotionGui;
import com.rumilance.practice.gui.menus.PresetAdminGui;
import com.rumilance.practice.gui.menus.ProfileGui;
import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.gui.menus.ReportGui;
import com.rumilance.practice.gui.menus.ReportListGui;
import com.rumilance.practice.gui.menus.SettingsGui;
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
import com.rumilance.practice.listener.PracticePearlListener;
import com.rumilance.practice.listener.PracticeSideListener;
import com.rumilance.practice.listener.SessionBootstrapListener;
import com.rumilance.practice.lobby.FloatingTextCleanup;
import com.rumilance.practice.lobby.LobbyCompassListener;
import com.rumilance.practice.lobby.LobbyListener;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.lobby.MotdListener;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.match.ArenaBoundsListener;
import com.rumilance.practice.match.GoldenHeadListener;
import com.rumilance.practice.match.MatchActionRecorder;
import com.rumilance.practice.match.MatchListener;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.match.TeamColoredArmorListener;
import com.rumilance.practice.match.TeamColoredArmorService;
import com.rumilance.practice.match.inventory.MatchInventoryStore;
import com.rumilance.practice.match.result.FfaResultProcessor;
import com.rumilance.practice.match.result.RankedResultProcessor;
import com.rumilance.practice.match.result.UnrankedResultProcessor;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.originalkit.OriginalKitService;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.queue.QueueService;
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
import com.rumilance.practice.team.TeamListener;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.tnt.PracticeTntListener;
import com.rumilance.practice.tnt.PracticeTntSettings;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.KitNames;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

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

    public FeatureBootstrap(RumilancePractice plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    public void enable() {
        BanService banService;
        ConfigService configService = (ConfigService)this.services.get(ConfigService.class);
        PluginSettings settings = (PluginSettings)this.services.get(PluginSettings.class);
        KitNames.configure((KitNames.CaseStyle)KitNames.CaseStyle.parse((String)configService.config().getString("gui.kit-name-case", "KEEP")));
        SessionManager sessionManager = (SessionManager)this.services.get(SessionManager.class);
        PlayerStateManager stateManager = (PlayerStateManager)this.services.get(PlayerStateManager.class);
        AsyncExecutor asyncExecutor = (AsyncExecutor)this.services.get(AsyncExecutor.class);
        FaweBridge faweBridge = (FaweBridge)this.services.get(FaweBridge.class);
        RankedStatsRepository rankedStatsRepository = (RankedStatsRepository)this.services.get(RankedStatsRepository.class);
        MatchHistoryRepository matchHistoryRepository = (MatchHistoryRepository)this.services.get(MatchHistoryRepository.class);
        AuditLogRepository auditLogRepository = (AuditLogRepository)this.services.get(AuditLogRepository.class);
        SettingsRepository settingsRepository = (SettingsRepository)this.services.get(SettingsRepository.class);
        PunishmentRepository punishmentRepository = (PunishmentRepository)this.services.get(PunishmentRepository.class);
        KitLayoutRepository kitLayoutRepository = (KitLayoutRepository)this.services.get(KitLayoutRepository.class);
        OriginalKitRepository originalKitRepository = (OriginalKitRepository)this.services.get(OriginalKitRepository.class);
        DailyRankedStatsRepository dailyRankedStatsRepository = (DailyRankedStatsRepository)this.services.get(DailyRankedStatsRepository.class);
        FfaStatsRepository ffaStatsRepository = (FfaStatsRepository)this.services.get(FfaStatsRepository.class);
        ObjectionRepository objectionRepository = (ObjectionRepository)this.services.get(ObjectionRepository.class);
        PlayerRepository playerRepository = (PlayerRepository)this.services.get(PlayerRepository.class);
        WinStreakRepository winStreakRepository = (WinStreakRepository)this.services.get(WinStreakRepository.class);
        PlayerReportRepository playerReportRepository = (PlayerReportRepository)this.services.get(PlayerReportRepository.class);
        SpamDetectionRepository spamDetectionRepository = (SpamDetectionRepository)this.services.get(SpamDetectionRepository.class);
        MessageService messageService = (MessageService)this.services.get(MessageService.class);
        RuntimeFlags runtimeFlags = new RuntimeFlags(settings.maintenanceMode());
        this.services.register(RuntimeFlags.class, (Object)runtimeFlags);
        this.settingsService = new SettingsService(settingsRepository, asyncExecutor, this.plugin.getLogger(), settings.defaultLocale());
        this.services.register(SettingsService.class, (Object)this.settingsService);
        SoundService soundService = new SoundService(configService, this.settingsService, stateManager);
        this.services.register(SoundService.class, (Object)soundService);
        LobbyService lobbyService = new LobbyService(configService);
        this.services.register(LobbyService.class, (Object)lobbyService);
        KitService kitService = new KitService(configService);
        this.services.register(KitService.class, (Object)kitService);
        KitLayoutCache layoutCache = new KitLayoutCache(kitLayoutRepository, asyncExecutor, this.plugin.getLogger());
        this.services.register(KitLayoutCache.class, (Object)layoutCache);
        ArenaTemplateStore arenaStore = new ArenaTemplateStore(configService);
        arenaStore.reload();
        this.services.register(ArenaTemplateStore.class, (Object)arenaStore);
        boolean disposableCopies = configService.config().getBoolean("arena.disposable-copies", true);
        SimpleArenaService arenaService = faweBridge.isAvailable() ? (disposableCopies ? new DisposableArenaService((Plugin)this.plugin, faweBridge, new File(this.plugin.getDataFolder(), "schematics"), configService.config().getInt("arena.placement-range", 100000), configService.config().getInt("arena.placement-spacing", 64), configService.config().getInt("arena.placement-center-x", 0), configService.config().getInt("arena.placement-center-z", 0)) : new FaweArenaService(faweBridge, new File(this.plugin.getDataFolder(), "schematics"), settings.regenerateArena())) : new SimpleArenaService();
        arenaService.setTemplates(arenaStore.templates());
        this.services.register(ArenaService.class, (Object)arenaService);
        GuiSessionRegistry guiSessions = new GuiSessionRegistry();
        this.services.register(GuiSessionRegistry.class, (Object)guiSessions);
        QueueService queueService = new QueueService(settings);
        this.services.register(QueueService.class, (Object)queueService);
        DuelRequestService duelRequestService = new DuelRequestService(60L, 3000L);
        this.services.register(DuelRequestService.class, (Object)duelRequestService);
        MatchRegistry matchRegistry = new MatchRegistry();
        this.matchActionRecorder = new MatchActionRecorder((Plugin)this.plugin, matchRegistry);
        this.services.register(MatchActionRecorder.class, (Object)this.matchActionRecorder);
        RankedResultProcessor rankedResultProcessor = new RankedResultProcessor(rankedStatsRepository, matchHistoryRepository, dailyRankedStatsRepository, winStreakRepository, true);
        UnrankedResultProcessor unrankedResultProcessor = new UnrankedResultProcessor(auditLogRepository, winStreakRepository);
        FfaResultProcessor ffaResultProcessor = new FfaResultProcessor(auditLogRepository);
        this.matchService = new MatchService((Plugin)this.plugin, (ArenaService)arenaService, kitService, layoutCache, lobbyService, matchRegistry, stateManager, duelRequestService, soundService, asyncExecutor, rankedResultProcessor, unrankedResultProcessor, ffaResultProcessor, settings.matchCountdownSeconds(), Math.max(1, settings.endTeleportDelaySeconds()), settings.matchMaxDurationSeconds());
        this.services.register(MatchService.class, (Object)this.matchService);
        this.matchService.setSettingsService(this.settingsService);
        this.matchService.setMessageService(messageService);
        DuelLogStore duelLogStore = new DuelLogStore(new File(this.plugin.getDataFolder(), "duels.rpd").toPath());
        this.matchService.setDuelLogStore(duelLogStore);
        this.matchService.setActionRecorder(this.matchActionRecorder);
        MatchInventoryStore matchInventoryStore = new MatchInventoryStore((Plugin)this.plugin);
        this.matchService.setInventoryStore(matchInventoryStore);
        this.services.register(MatchInventoryStore.class, (Object)matchInventoryStore);
        ReportEvidenceStore reportEvidenceStore = new ReportEvidenceStore(new File(this.plugin.getDataFolder(), "reports").toPath());
        ReportService reportService = new ReportService(playerReportRepository, reportEvidenceStore, this.matchActionRecorder, asyncExecutor, this.plugin.getLogger());
        this.services.register(ReportService.class, (Object)reportService);
        ReplayService replayService = new ReplayService((Plugin)this.plugin, lobbyService);
        this.services.register(ReplayService.class, (Object)replayService);
        this.replayService = replayService;
        this.banService = banService = new BanService((Plugin)this.plugin);
        this.services.register(BanService.class, (Object)banService);
        this.chatBanService = new ChatBanService(punishmentRepository, auditLogRepository, objectionRepository, asyncExecutor, this.plugin.getLogger(), Duration.ofDays(7L));
        this.services.register(ChatBanService.class, (Object)this.chatBanService);
        this.matchService.setChatBanService(this.chatBanService);
        SpamFilterService spamFilterService = new SpamFilterService(configService, spamDetectionRepository, this.chatBanService, asyncExecutor, this.plugin.getLogger());
        this.services.register(SpamFilterService.class, (Object)spamFilterService);
        SignGuardService signGuardService = new SignGuardService(configService, banService, auditLogRepository, asyncExecutor, this.plugin.getLogger());
        this.services.register(SignGuardService.class, (Object)signGuardService);
        SignProbeService signProbeService = new SignProbeService((Plugin)this.plugin, configService, banService, auditLogRepository, asyncExecutor, this.plugin.getLogger());
        signProbeService.init();
        this.services.register(SignProbeService.class, (Object)signProbeService);
        SpectatorService spectatorService = new SpectatorService((Plugin)this.plugin, matchRegistry, stateManager, this.settingsService, lobbyService, settings);
        this.services.register(SpectatorService.class, (Object)spectatorService);
        this.matchService.setSpectatorService(spectatorService);
        TeamColoredArmorService teamColoredArmor = new TeamColoredArmorService((Plugin)this.plugin, matchRegistry, this.settingsService);
        teamColoredArmor.setSpectatorService(spectatorService);
        this.services.register(TeamColoredArmorService.class, (Object)teamColoredArmor);
        this.matchService.setTeamColoredArmorService(teamColoredArmor);
        spectatorService.setTeamColoredArmorService(teamColoredArmor);
        FfaService ffaService = new FfaService((Plugin)this.plugin, configService, kitService, layoutCache, lobbyService, stateManager, ffaStatsRepository, asyncExecutor, runtimeFlags, messageService, soundService);
        this.services.register(FfaService.class, (Object)ffaService);
        spectatorService.setFfaService(ffaService);
        SightSettings sightSettings = SightSettings.from((FileConfiguration)configService.config());
        this.services.register(SightSettings.class, (Object)sightSettings);
        ViewControlService viewControl = new ViewControlService((ArenaService)arenaService, sightSettings);
        this.services.register(ViewControlService.class, (Object)viewControl);
        this.matchService.setViewControl(viewControl);
        spectatorService.setViewControl(viewControl);
        ffaService.setViewControl(viewControl);
        FfaSpawnIndex ffaSpawnIndex = new FfaSpawnIndex((Plugin)this.plugin, asyncExecutor, ffaService);
        ffaService.setSpawnIndex(ffaSpawnIndex);
        ffaSpawnIndex.reindexAll();
        lobbyService.setSightHook(player -> {
            Cuboid region = lobbyService.region();
            if (region != null && lobbyService.spawn() != null) {
                region = region.including(lobbyService.spawn());
            }
            viewControl.applyLobby(player, region);
        });
        CombatNetTracker combatNet = new CombatNetTracker(configService.config().getInt("combat.ping-offset-ms", 25), configService.config().getInt("combat.spike-threshold-ms", 20));
        this.services.register(CombatNetTracker.class, (Object)combatNet);
        this.matchService.setCombatNet(combatNet);
        PracticeTntSettings practiceTnt = new PracticeTntSettings(configService);
        this.services.register(PracticeTntSettings.class, (Object)practiceTnt);
        CombatSyncListener combatSync = new CombatSyncListener((Plugin)this.plugin, combatNet, this.matchService, ffaService, lobbyService, (ArenaService)arenaService, viewControl, kitService);
        KnockbackService knockbackService = new KnockbackService((Plugin)this.plugin);
        knockbackService.load();
        combatSync.setKnockbackService(knockbackService);
        this.services.register(KnockbackService.class, (Object)knockbackService);
        if (configService.config().getBoolean("combat.vanilla-item-swap", true)) {
            PaperCombatTuning.applyVanillaItemSwap((Logger)this.plugin.getLogger());
        }
        WallTextService wallTextService = new WallTextService((Plugin)this.plugin);
        this.services.register(WallTextService.class, (Object)wallTextService);
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> FeatureBootstrap.lambda$enable$1(wallTextService, (ArenaService)arenaService, arenaStore, ffaService));
        if (arenaService instanceof DisposableArenaService) {
            DisposableArenaService disposableService = (DisposableArenaService)arenaService;
            disposableService.setCopyHooks(inst -> wallTextService.placeAutoLabel("auto_copy_" + String.valueOf(inst.id()), inst.template().world(), inst.minX(), inst.minY(), inst.minZ(), inst.maxX(), inst.maxY(), inst.maxZ(), KitNames.pretty((String)inst.template().name()) + " Arena"), inst -> wallTextService.removeAutoLabel("auto_copy_" + String.valueOf(inst.id())));
        }
        StatsService statsService = new StatsService(rankedStatsRepository, matchHistoryRepository, dailyRankedStatsRepository, winStreakRepository, configService);
        this.services.register(StatsService.class, (Object)statsService);
        StatsResetService statsResetService = new StatsResetService(rankedStatsRepository, ffaStatsRepository, dailyRankedStatsRepository, matchHistoryRepository, winStreakRepository);
        this.services.register(StatsResetService.class, (Object)statsResetService);
        this.arrowEffectService = new ArrowEffectService((Plugin)this.plugin, configService, this.settingsService);
        this.arrowEffectService.start();
        this.services.register(ArrowEffectService.class, (Object)this.arrowEffectService);
        this.queueCoordinator = new QueueCoordinator((Plugin)this.plugin, queueService, this.matchService, kitService, lobbyService, stateManager, soundService, rankedStatsRepository, asyncExecutor, runtimeFlags, settings, false, true, messageService);
        this.services.register(QueueCoordinator.class, (Object)this.queueCoordinator);
        this.queueCoordinator.start();
        this.matchService.setQueueCoordinator(this.queueCoordinator);
        this.matchService.setFfaService(ffaService);
        this.queueCoordinator.setFfaService(ffaService);
        ffaService.setQueueService(queueService);
        QueueKitGui rankedGui = new QueueKitGui(guiSessions, soundService, kitService, queueService, this.queueCoordinator, true, winStreakRepository);
        QueueKitGui unrankedGui = new QueueKitGui(guiSessions, soundService, kitService, queueService, this.queueCoordinator, false, winStreakRepository);
        KitSelectGui kitSelectGui = new KitSelectGui(guiSessions, soundService, kitService);
        DuelRequestGui duelRequestGui = new DuelRequestGui(guiSessions, soundService, kitService, duelRequestService, this.settingsService, statsService, kitSelectGui, messageService);
        kitSelectGui.setDuelRequestGui(duelRequestGui);
        DuelMapSelectGui duelMapSelectGui = new DuelMapSelectGui(guiSessions, soundService, kitService, duelRequestGui, messageService);
        duelRequestGui.setMapSelectGui(duelMapSelectGui);
        SettingsGui settingsGui = new SettingsGui(guiSessions, soundService, this.settingsService);
        settingsGui.setToggleCooldownSeconds(configService.config().getInt("gui.toggle-cooldown-seconds", 2));
        settingsGui.setTeamColoredArmorService(teamColoredArmor);
        StatsKitGui statsKitGui = new StatsKitGui(guiSessions, soundService, kitService, statsService);
        ProfileGui profileGui = new ProfileGui(guiSessions, soundService, kitService, statsService, banService);
        PlayersGui playersGui = new PlayersGui(guiSessions, soundService, stateManager, statsService, duelRequestGui, messageService);
        SpectateListGui spectateListGui = new SpectateListGui(guiSessions, soundService, matchRegistry, spectatorService, ffaService);
        FfaListGui ffaListGui = new FfaListGui(guiSessions, soundService, ffaService);
        KitPreviewGui kitPreviewGui = new KitPreviewGui(guiSessions, soundService, kitService);
        rankedGui.setPreviewGui(kitPreviewGui);
        unrankedGui.setPreviewGui(kitPreviewGui);
        TitleService titleService = new TitleService(this.settingsService, statsService);
        this.services.register(TitleService.class, (Object)titleService);
        this.matchService.setTitleService(titleService);
        TitleGui titleGui = new TitleGui(guiSessions, soundService, titleService);
        MatchReportGui matchReportGui = new MatchReportGui(guiSessions, soundService, this.matchService);
        this.matchService.setMatchReportOpener(arg_0 -> ((MatchReportGui)matchReportGui).openLastReport(arg_0));
        TeamService teamService = new TeamService((Plugin)this.plugin, this.matchService, messageService);
        this.services.register(TeamService.class, (Object)teamService);
        TeamsBrowserGui teamsBrowserGui = new TeamsBrowserGui(guiSessions, soundService, teamService, null, messageService);
        TeamKitSelectGui teamKitSelectGui = new TeamKitSelectGui(guiSessions, soundService, teamService, kitService, messageService);
        TeamHubGui teamHubGui = new TeamHubGui(guiSessions, soundService, teamService, teamsBrowserGui, teamKitSelectGui, messageService);
        teamsBrowserGui.setHub(teamHubGui);
        PresetItems presetItems = new PresetItems(configService);
        this.services.register(PresetItems.class, (Object)presetItems);
        EditKitGui editKitGui = new EditKitGui(guiSessions, soundService, kitService, kitLayoutRepository, layoutCache, asyncExecutor, stateManager, presetItems);
        ArrowEffectGui arrowEffectGui = new ArrowEffectGui(guiSessions, soundService, this.arrowEffectService, this.settingsService);
        OriginalKitService originalKitService = new OriginalKitService(originalKitRepository, asyncExecutor, this.plugin.getLogger(), configService);
        this.services.register(OriginalKitService.class, (Object)originalKitService);
        EkitItems ekitItems = new EkitItems(configService);
        this.services.register(EkitItems.class, (Object)ekitItems);
        ConfirmGui confirmGui = new ConfirmGui(guiSessions, soundService);
        confirmGui.setOriginalKitService(originalKitService);
        OriginalKitEditGui originalKitEditGui = new OriginalKitEditGui(guiSessions, soundService, originalKitService, ekitItems);
        EnchantGui enchantGui = new EnchantGui(guiSessions, soundService, originalKitService, originalKitEditGui);
        PotionGui potionGui = new PotionGui(guiSessions, soundService, originalKitService, originalKitEditGui);
        EkitCopyGui ekitCopyGui = new EkitCopyGui(guiSessions, soundService, kitService, originalKitEditGui, originalKitService);
        EkitChoiceGui ekitChoiceGui = new EkitChoiceGui(guiSessions, soundService, originalKitService, originalKitEditGui, ekitCopyGui);
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
        PresetAdminGui presetAdminGui = new PresetAdminGui(guiSessions, soundService, presetItems);
        KitAdminGui kitAdminGui = new KitAdminGui(guiSessions, soundService, kitService, messageService);
        kitAdminGui.setArenaNames(() -> arenaStore.templates().stream().map(ArenaTemplate::name).toList());
        AdminMenuGui adminMenuGui = new AdminMenuGui(guiSessions, soundService);
        adminMenuGui.setOpenKitAdmin(arg_0 -> ((KitAdminGui)kitAdminGui).open(arg_0));
        adminMenuGui.setOpenPresetAdmin(player -> {
            presetAdminGui.setReturnTo(arg_0 -> ((AdminMenuGui)adminMenuGui).open(arg_0));
            presetAdminGui.openAdmin(player);
        });
        adminMenuGui.setOpenEkitAdmin(arg_0 -> ((EkitAdminGui)ekitAdminGui).openAdmin(arg_0));
        kitAdminGui.setOpenPresetAdmin(player -> {
            presetAdminGui.setReturnTo(arg_0 -> ((KitAdminGui)kitAdminGui).open(arg_0));
            presetAdminGui.openAdmin(player);
        });
        presetAdminGui.setReturnTo(arg_0 -> ((AdminMenuGui)adminMenuGui).open(arg_0));
        SwordBotService swordBotService = new SwordBotService((Plugin)this.plugin, (ArenaService)arenaService, kitService, lobbyService, stateManager, soundService);
        this.services.register(SwordBotService.class, (Object)swordBotService);
        BotMenuGui botMenuGui = new BotMenuGui(guiSessions, soundService, swordBotService, kitService);
        BattleMenuGui battleMenuGui = new BattleMenuGui(guiSessions, soundService, rankedGui, unrankedGui, playersGui, ffaListGui, botMenuGui, messageService);
        GameMenuGui gameMenuGui = new GameMenuGui(guiSessions, soundService, battleMenuGui, ekitSelectGui, spectateListGui, settingsGui, titleGui, messageService);
        MatchInventoryGui matchInventoryGui = new MatchInventoryGui(guiSessions, soundService, matchInventoryStore);
        BanListGui banListGui = new BanListGui(guiSessions, soundService, banService);
        ReportGui reportGui = new ReportGui(guiSessions, soundService, reportService);
        ReportListGui reportListGui = new ReportListGui(guiSessions, soundService, reportService, replayService);
        GuiListener guiListener = new GuiListener(guiSessions, stateManager, originalKitService);
        guiListener.register((AbstractGui)rankedGui);
        guiListener.register((AbstractGui)unrankedGui);
        guiListener.register((AbstractGui)kitSelectGui);
        guiListener.register((AbstractGui)duelRequestGui);
        guiListener.register((AbstractGui)duelMapSelectGui);
        guiListener.register((AbstractGui)settingsGui);
        guiListener.register((AbstractGui)statsKitGui);
        guiListener.register((AbstractGui)profileGui);
        guiListener.register((AbstractGui)banListGui);
        guiListener.register((AbstractGui)reportGui);
        guiListener.register((AbstractGui)reportListGui);
        guiListener.register((AbstractGui)playersGui);
        guiListener.register((AbstractGui)ekitSelectGui);
        guiListener.register((AbstractGui)originalKitGui);
        guiListener.register((AbstractGui)confirmGui);
        guiListener.register((AbstractGui)ekitChoiceGui);
        guiListener.register((AbstractGui)ekitCopyGui);
        guiListener.register((AbstractGui)originalKitEditGui);
        guiListener.register((AbstractGui)enchantGui);
        guiListener.register((AbstractGui)potionGui);
        guiListener.register((AbstractGui)ekitAdminGui);
        guiListener.register((AbstractGui)presetAdminGui);
        guiListener.register((AbstractGui)adminMenuGui);
        guiListener.register((AbstractGui)spectateListGui);
        guiListener.register((AbstractGui)ffaListGui);
        guiListener.register((AbstractGui)editKitGui);
        guiListener.register((AbstractGui)arrowEffectGui);
        guiListener.register((AbstractGui)originalKitGui);
        guiListener.register((AbstractGui)kitAdminGui);
        guiListener.register((AbstractGui)kitPreviewGui);
        guiListener.register((AbstractGui)titleGui);
        guiListener.register((AbstractGui)matchReportGui);
        guiListener.register((AbstractGui)teamsBrowserGui);
        guiListener.register((AbstractGui)teamHubGui);
        guiListener.register((AbstractGui)teamKitSelectGui);
        guiListener.register((AbstractGui)gameMenuGui);
        guiListener.register((AbstractGui)battleMenuGui);
        guiListener.register((AbstractGui)botMenuGui);
        guiListener.register((AbstractGui)matchInventoryGui);
        guiListener.setMenuReturn(arg_0 -> ((GameMenuGui)gameMenuGui).open(arg_0));
        guiListener.setBattleMenuReturn(arg_0 -> ((BattleMenuGui)battleMenuGui).open(arg_0));
        guiListener.setReopenOriginalEditor(player -> {
            OriginalKitService.EditContext ctx = originalKitService.context(player.getUniqueId());
            if (ctx != null) {
                originalKitEditGui.open(player, ctx.slot, ctx.layout);
            }
        });
        FunctionalItemListener functionalItemListener = new FunctionalItemListener(soundService, this.queueCoordinator, rankedGui, unrankedGui);
        functionalItemListener.setOpenSettings(arg_0 -> ((SettingsGui)settingsGui).open(arg_0));
        functionalItemListener.setOpenFfa(arg_0 -> ((FfaListGui)ffaListGui).open(arg_0));
        functionalItemListener.setOpenEkit(arg_0 -> ((EkitSelectGui)ekitSelectGui).open(arg_0));
        functionalItemListener.setOpenSpectate(arg_0 -> ((SpectateListGui)spectateListGui).open(arg_0));
        functionalItemListener.setOpenMenu(arg_0 -> ((GameMenuGui)gameMenuGui).open(arg_0));
        functionalItemListener.setOpenBattle(arg_0 -> ((BattleMenuGui)battleMenuGui).open(arg_0));
        functionalItemListener.setOpenTitles(arg_0 -> ((TitleGui)titleGui).open(arg_0));
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
        ScoreboardConfig scoreboardConfig = new ScoreboardConfig(configService.scoreboard());
        this.scoreboardService = new ScoreboardService((Plugin)this.plugin, scoreboardConfig, stateManager, queueService, matchRegistry, rankedStatsRepository, winStreakRepository, this.settingsService, asyncExecutor);
        this.scoreboardService.setSpectatorService(spectatorService);
        this.scoreboardService.setFfaService(ffaService);
        if (scoreboardConfig.enabled()) {
            this.scoreboardService.start();
        }
        this.services.register(ScoreboardService.class, (Object)this.scoreboardService);
        PluginManager pm = this.plugin.getServer().getPluginManager();
        pm.registerEvents((Listener)new BanLoginListener(banService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new SessionBootstrapListener(sessionManager, stateManager, lobbyService, settings.defaultLocale(), playerRepository, layoutCache, this.settingsService, asyncExecutor, (Plugin)this.plugin, messageService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new /* Unavailable Anonymous Inner Class!! */, (Plugin)this.plugin);
        pm.registerEvents((Listener)new LobbyListener(lobbyService, stateManager), (Plugin)this.plugin);
        pm.registerEvents((Listener)new MotdListener(), (Plugin)this.plugin);
        pm.registerEvents((Listener)new MatchListener(this.matchService, kitService, combatNet, practiceTnt), (Plugin)this.plugin);
        pm.registerEvents((Listener)new TeamColoredArmorListener(teamColoredArmor, this.settingsService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new ArenaBoundsListener(this.matchService, (ArenaService)arenaService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new SpectatorBoundsListener(spectatorService, matchRegistry, (ArenaService)arenaService, ffaService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new FfaListener(ffaService, kitService, stateManager, combatNet, practiceTnt), (Plugin)this.plugin);
        pm.registerEvents((Listener)ffaSpawnIndex, (Plugin)this.plugin);
        pm.registerEvents((Listener)new InstantExpCollectListener(), (Plugin)this.plugin);
        pm.registerEvents((Listener)new PracticeTntListener(practiceTnt, this.matchService, ffaService, (Plugin)this.plugin), (Plugin)this.plugin);
        pm.registerEvents((Listener)new CrystalAnchorPerfListener(this.matchService, ffaService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new PracticePearlListener(this.matchService, ffaService, (ArenaService)arenaService, sightSettings), (Plugin)this.plugin);
        pm.registerEvents((Listener)combatSync, (Plugin)this.plugin);
        combatSync.start();
        pm.registerEvents((Listener)new GoldenHeadListener((Plugin)this.plugin, matchRegistry), (Plugin)this.plugin);
        pm.registerEvents((Listener)guiListener, (Plugin)this.plugin);
        pm.registerEvents((Listener)functionalItemListener, (Plugin)this.plugin);
        pm.registerEvents((Listener)new LobbyCompassListener(stateManager, soundService, arg_0 -> ((GameMenuGui)gameMenuGui).open(arg_0)), (Plugin)this.plugin);
        AdminToolListener adminToolListener = new AdminToolListener(soundService);
        adminToolListener.setOpenAdminMenu(arg_0 -> ((AdminMenuGui)adminMenuGui).open(arg_0));
        pm.registerEvents((Listener)adminToolListener, (Plugin)this.plugin);
        pm.registerEvents((Listener)new TeamListener(teamService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new SpamFilterListener(spamFilterService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new SignChangeGuardListener(signGuardService), (Plugin)this.plugin);
        pm.registerEvents((Listener)new /* Unavailable Anonymous Inner Class!! */, (Plugin)this.plugin);
        PendingInput.init((Plugin)this.plugin);
        FloatingTextCleanup.start((Plugin)this.plugin, (long)configService.config().getLong("cleanup.floating-text-window-seconds", 300L));
        pm.registerEvents((Listener)new PracticeSideListener(this.chatBanService, this.settingsService, guiSessions, this.arrowEffectService, spectatorService, ffaService, originalKitService), (Plugin)this.plugin);
        DuelCommand rankedDuel = new DuelCommand(rankedGui, unrankedGui, duelRequestGui, duelRequestService, this.matchService, kitService, stateManager, soundService, lobbyService, this.queueCoordinator, runtimeFlags, true, messageService);
        DuelCommand unrankedDuel = new DuelCommand(rankedGui, unrankedGui, duelRequestGui, duelRequestService, this.matchService, kitService, stateManager, soundService, lobbyService, this.queueCoordinator, runtimeFlags, false, messageService);
        AcceptDenyCommand acceptDeny = new AcceptDenyCommand(rankedDuel, duelRequestService);
        ArenaKitAdminCommand arenaKitAdmin = new ArenaKitAdminCommand(configService, arenaStore, (ArenaService)arenaService, kitService, queueService, faweBridge, new File(this.plugin.getDataFolder(), "schematics"), soundService, kitAdminGui);
        arenaKitAdmin.setPresetItems(presetItems);
        arenaKitAdmin.setPresetAdminGui(presetAdminGui);
        ChatBanCommand chatBanCommand = new ChatBanCommand(this.chatBanService);
        FfaCommand ffaCommand = new FfaCommand(ffaListGui, ffaService, kitService);
        PracticeAdminCommand practiceAdmin = new PracticeAdminCommand(this.plugin, configService, soundService, this.matchService, lobbyService, runtimeFlags, kitService, arenaStore, (ArenaService)arenaService, ffaService);
        practiceAdmin.setOpenAdminMenu(arg_0 -> ((AdminMenuGui)adminMenuGui).open(arg_0));
        practiceAdmin.setScoreboardService(this.scoreboardService);
        AdminCommand adminCommand = new AdminCommand((Plugin)this.plugin, statsResetService, playerRepository, asyncExecutor, originalKitService);
        adminCommand.setScoreboardService(this.scoreboardService);
        this.bind("duel", unrankedDuel);
        this.bind("ranked", rankedDuel);
        this.bind("unranked", unrankedDuel);
        this.bind("accept", acceptDeny);
        this.bind("deny", acceptDeny);
        this.bind("queue", new QueueLeaveCommand(this.queueCoordinator));
        LobbyCommand lobbyCommand = new LobbyCommand(lobbyService, stateManager, spectatorService, ffaService, messageService);
        lobbyCommand.setQueueCoordinator(this.queueCoordinator);
        lobbyCommand.setSwordBotService(swordBotService);
        this.bind("lobby", lobbyCommand);
        this.bind("spawn", lobbyCommand);
        this.bind("hub", lobbyCommand);
        this.bind("lang", new LangCommand(sessionManager, this.settingsService, messageService));
        this.bind("matchinv", new MatchInvCommand(matchInventoryGui));
        this.bind("setfunc", new SetFuncCommand());
        this.bind("admin", adminCommand);
        this.bind("practiceadmin", practiceAdmin);
        this.bind("slobby", practiceAdmin);
        this.bind("setlobbyitem", practiceAdmin);
        this.bind("arena", arenaKitAdmin);
        this.bind("kit", arenaKitAdmin);
        this.bind("toggle", arenaKitAdmin);
        this.bind("chatban", chatBanCommand);
        this.bind("chatunban", chatBanCommand);
        this.bind("ekitadmin", new EkitAdminCommand(ekitAdminGui));
        this.bind("giveitem", new GiveItemCommand());
        this.bind("matchreport", new MatchReportCommand(this.matchService, this.settingsService));
        this.bind("walltext", new WallTextCommand(wallTextService));
        this.bind("ffa", ffaCommand);
        this.bind("kb", new KbCommand(knockbackService));
        this.bind("leave", new LeaveCommand(this.matchService, messageService));
        this.bind("team", new TeamCommand(teamService, kitService, teamHubGui, teamsBrowserGui, messageService));
        this.bind("title", (sender, command, label, args) -> {
            if (sender instanceof Player) {
                Player player = (Player)sender;
                titleGui.open(player);
            }
            return true;
        });
        for (PlayerCommands.Type type : PlayerCommands.Type.values()) {
            if (type == PlayerCommands.Type.FFA) continue;
            String cmd = switch (3.$SwitchMap$com$rumilance$practice$command$PlayerCommands$Type[type.ordinal()]) {
                default -> throw new MatchException(null, null);
                case 1 -> "ping";
                case 2 -> "stats";
                case 3 -> "profile";
                case 4 -> "ranking";
                case 5 -> "players";
                case 6 -> "spec";
                case 7 -> "specgui";
                case 8 -> "setting";
                case 9 -> "ffa";
                case 10 -> "ekit";
                case 11 -> "arroweffect";
                case 12 -> "matchhistory";
                case 13 -> "kdr";
                case 14 -> "objection";
            };
            this.bind(cmd, new PlayerCommands(type, (Plugin)this.plugin, asyncExecutor, statsService, kitService, statsKitGui, profileGui, settingsGui, ekitSelectGui, playersGui, spectateListGui, spectatorService, ffaListGui, editKitGui, arrowEffectGui, this.chatBanService));
        }
        BanCommand banCommand = new BanCommand(banService, banListGui, playerRepository);
        this.bind("ban", banCommand);
        this.bind("kick", banCommand);
        this.bind("banlist", banCommand);
        this.bind("unban", banCommand);
        this.bind("testban", banCommand);
        this.bind("testkick", banCommand);
        this.bind("report", new ReportCommand(reportGui));
        this.bind("reportlist", new ReportListCommand(reportListGui));
        this.bind("replay", new ReplayCommand(replayService));
        this.bind("signcheck", new SignCheckCommand(signProbeService));
        this.bind("checkid", new CheckIdCommand(duelLogStore));
        this.bind("originalkit", (sender, command, label, args) -> {
            if (sender instanceof Player) {
                Player player = (Player)sender;
                if (!originalKitService.isOpen() && !player.hasPermission("rumilance.admin")) {
                    player.sendMessage((Component)Component.text((String)"\u30aa\u30ea\u30b8\u30ca\u30eb\u30ad\u30c3\u30c8\u306f\u73fe\u5728\u9589\u9396\u3055\u308c\u3066\u3044\u307e\u3059\u3002", (TextColor)NamedTextColor.RED));
                    return true;
                }
                originalKitGui.open(player);
            }
            return true;
        });
        this.plugin.getLogger().info("Feature services enabled (all player GUIs and admin commands wired).");
    }

    public void disable() {
        ArenaService disposable;
        if (this.queueCoordinator != null) {
            this.queueCoordinator.stop();
        }
        if (this.matchService != null) {
            this.matchService.shutdown();
        }
        if (this.matchActionRecorder != null) {
            this.matchActionRecorder.stop();
        }
        if (this.replayService != null) {
            this.replayService.shutdown();
        }
        if ((disposable = (ArenaService)this.services.find(ArenaService.class).orElse(null)) instanceof DisposableArenaService) {
            DisposableArenaService copies = (DisposableArenaService)disposable;
            try {
                copies.clearAllCopies().get(20L, TimeUnit.SECONDS);
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("Could not clear all disposable arena copies on shutdown: " + e.getMessage());
            }
        }
        if (this.scoreboardService != null) {
            this.scoreboardService.stop();
        }
        if (this.arrowEffectService != null) {
            this.arrowEffectService.stop();
        }
        if (this.settingsService != null) {
            this.settingsService.flushAll();
        }
        if (this.chatBanService != null) {
            this.chatBanService.setShuttingDown(true);
        }
        if (this.banService != null) {
            this.banService.persist();
        }
    }

    private void bind(String name, Object executor) {
        PluginCommand command = this.plugin.getCommand(name);
        if (command == null) {
            this.plugin.getLogger().warning("Missing command in plugin.yml: " + name);
            return;
        }
        if (executor instanceof CommandExecutor) {
            CommandExecutor commandExecutor = (CommandExecutor)executor;
            command.setExecutor(commandExecutor);
        }
        if (executor instanceof TabCompleter) {
            TabCompleter tabCompleter = (TabCompleter)executor;
            command.setTabCompleter(tabCompleter);
        }
    }

    private static /* synthetic */ void lambda$enable$1(WallTextService wallTextService, ArenaService arenaService, ArenaTemplateStore arenaStore, FfaService ffaService) {
        wallTextService.load();
        wallTextService.clearAutoLabels();
        if (!(arenaService instanceof DisposableArenaService)) {
            for (ArenaTemplate template : arenaStore.templates()) {
                wallTextService.placeAutoLabel("auto_arena_" + template.name(), template.world(), template.minX(), template.minY(), template.minZ(), template.maxX(), template.maxY(), template.maxZ(), KitNames.pretty((String)template.name()) + " Arena");
            }
        }
        for (FfaService.FfaArena ffaArena : ffaService.list()) {
            Cuboid region = ffaArena.region();
            wallTextService.placeAutoLabel("auto_ffa_" + ffaArena.id(), region.worldName(), region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ(), KitNames.pretty((String)ffaArena.id()) + " FFA");
        }
    }
}
