package com.rumilance.practice;

import com.rumilance.practice.bootstrap.FeatureBootstrap;
import com.rumilance.practice.bootstrap.ServiceRegistry;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.database.SchemaMigrator;
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
import com.rumilance.practice.elo.EloCalculator;
import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.arena.fawe.FaweBridgeImpl;
import com.rumilance.practice.arena.fawe.NoOpFaweBridge;
import com.rumilance.practice.locale.LocaleService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.session.PlayerSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemKeys;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * NARENA main plugin entry. Data files remain under {@code plugins/RumilancePractice}.
 */
public final class RumilancePractice extends JavaPlugin {

    private ServiceRegistry serviceRegistry;
    private AsyncExecutor asyncExecutor;
    private DatabaseService databaseService;
    private FeatureBootstrap featureBootstrap;

    @Override
    public void onEnable() {
        try {
            enableInternal();
        } catch (Throwable t) {
            // Any failure while enabling must be visible in the log with a full stack
            // trace - otherwise the plugin silently ends up disabled and every command
            // reports "plugin is disabled".
            getLogger().log(Level.SEVERE,
                    "Fatal error while enabling NARENA - disabling plugin.", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void enableInternal() {
        this.serviceRegistry = new ServiceRegistry();
        ItemKeys.init(this);

        ConfigService configService = new ConfigService(this);
        configService.loadAll();
        writeBukkitFolderPointer();
        PluginSettings settings = PluginSettings.from(configService.config());
        serviceRegistry.register(ConfigService.class, configService);
        serviceRegistry.register(PluginSettings.class, settings);

        this.asyncExecutor = new AsyncExecutor(
                settings.executorCoreThreads(),
                settings.executorMaxThreads(),
                settings.executorKeepAliveSeconds());
        serviceRegistry.register(AsyncExecutor.class, asyncExecutor);

        SessionManager sessionManager = new SessionManager();
        PlayerStateManager playerStateManager = new PlayerStateManager();
        serviceRegistry.register(SessionManager.class, sessionManager);
        serviceRegistry.register(PlayerStateManager.class, playerStateManager);

        File langFolder = new File(PluginIdentity.dataFolder(this), "lang");
        LocaleService localeService = new LocaleService(settings.defaultLocale(), langFolder);
        MessageService messageService = new MessageService(
                localeService,
                player -> sessionManager.getSession(player.getUniqueId())
                        .map(PlayerSession::locale)
                        .orElse(localeService.defaultLocale())
        );
        serviceRegistry.register(LocaleService.class, localeService);
        serviceRegistry.register(MessageService.class, messageService);

        if (!initializeDatabase(configService)) {
            return;
        }

        EloCalculator eloCalculator = new EloCalculator(
                settings.rankedProvisionalGames(),
                settings.rankedProvisionalK(),
                settings.rankedStandardK(),
                settings.rankedTopPercentK()
        );
        serviceRegistry.register(EloCalculator.class, eloCalculator);

        FaweBridge faweBridge = settings.faweEnabled()
                ? FaweBridgeImpl.createIfAvailable(this, asyncExecutor)
                : NoOpFaweBridge.INSTANCE;
        serviceRegistry.register(FaweBridge.class, faweBridge);
        if (faweBridge.isAvailable()) {
            getLogger().info("FastAsyncWorldEdit/WorldEdit detected - arena regeneration is enabled.");
        } else {
            getLogger().warning("FastAsyncWorldEdit/WorldEdit not detected - arena regeneration is disabled.");
        }

        this.featureBootstrap = new FeatureBootstrap(this, serviceRegistry);
        featureBootstrap.enable();
        getLogger().info("NARENA has been enabled (data folder plugins/"
                + PluginIdentity.DATA_FOLDER_NAME + ").");
    }

    private boolean initializeDatabase(ConfigService configService) {
        try {
            this.databaseService = new DatabaseService(configService.database(), PluginIdentity.dataFolder(this));
            SchemaMigrator migrator = new SchemaMigrator(databaseService, getLogger());
            int applied = migrator.migrate();
            getLogger().info(() -> "Database ready (backend=" + databaseService.type()
                    + ", " + applied + " migration(s) applied).");

            serviceRegistry.register(DatabaseService.class, databaseService);
            serviceRegistry.register(PlayerRepository.class, new PlayerRepository(databaseService));
            serviceRegistry.register(SettingsRepository.class, new SettingsRepository(databaseService));
            serviceRegistry.register(RankedStatsRepository.class, new RankedStatsRepository(databaseService));
            serviceRegistry.register(MatchHistoryRepository.class, new MatchHistoryRepository(databaseService));
            serviceRegistry.register(PunishmentRepository.class, new PunishmentRepository(databaseService));
            serviceRegistry.register(AuditLogRepository.class, new AuditLogRepository(databaseService));
            serviceRegistry.register(KitLayoutRepository.class, new KitLayoutRepository(databaseService));
            serviceRegistry.register(OriginalKitRepository.class, new OriginalKitRepository(databaseService));
            serviceRegistry.register(DailyRankedStatsRepository.class, new DailyRankedStatsRepository(databaseService));
            serviceRegistry.register(FfaStatsRepository.class, new FfaStatsRepository(databaseService));
            serviceRegistry.register(ObjectionRepository.class, new ObjectionRepository(databaseService));
            serviceRegistry.register(com.rumilance.practice.database.repository.PracticeLayoutRepository.class,
                    new com.rumilance.practice.database.repository.PracticeLayoutRepository(databaseService));
            serviceRegistry.register(com.rumilance.practice.database.repository.WinStreakRepository.class,
                    new com.rumilance.practice.database.repository.WinStreakRepository(databaseService));
            serviceRegistry.register(com.rumilance.practice.database.repository.PlayerReportRepository.class,
                    new com.rumilance.practice.database.repository.PlayerReportRepository(databaseService));
            serviceRegistry.register(com.rumilance.practice.database.repository.SpamDetectionRepository.class,
                    new com.rumilance.practice.database.repository.SpamDetectionRepository(databaseService));
            serviceRegistry.register(com.rumilance.practice.rank.RankRepository.class,
                    new com.rumilance.practice.rank.RankRepository(databaseService));
            return true;
        } catch (Exception e) {
            // Catch everything (not just SQLException): Hikari pool initialization and
            // data-folder creation can throw RuntimeException, and any of those must
            // disable the plugin with a clear, self-logged reason.
            getLogger().log(Level.SEVERE, "Failed to initialize the database. Disabling NARENA.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (featureBootstrap != null) {
            featureBootstrap.disable();
        }
        if (databaseService != null) {
            databaseService.close();
        }
        if (asyncExecutor != null) {
            asyncExecutor.shutdown(10L);
        }
        if (serviceRegistry != null) {
            serviceRegistry.unregisterAll();
        }
        getLogger().info("NARENA has been disabled.");
    }

    /**
     * Paper still creates {@code plugins/NARENA} from the plugin name. Point operators at the
     * real data directory.
     */
    private void writeBukkitFolderPointer() {
        File bukkitFolder = getDataFolder();
        if (!bukkitFolder.exists() && !bukkitFolder.mkdirs()) {
            return;
        }
        File pointer = new File(bukkitFolder, "USE_RumilancePractice_FOLDER.txt");
        if (pointer.exists()) {
            return;
        }
        try {
            java.nio.file.Files.writeString(pointer.toPath(),
                    "NARENA keeps all YAML and data in plugins/RumilancePractice/\n");
        } catch (java.io.IOException ignored) {
        }
    }

    public ServiceRegistry services() {
        return serviceRegistry;
    }
}
