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
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * RumilancePractice main plugin entry.
 */
public final class RumilancePractice extends JavaPlugin {

    private ServiceRegistry serviceRegistry;
    private AsyncExecutor asyncExecutor;
    private DatabaseService databaseService;
    private FeatureBootstrap featureBootstrap;

    @Override
    public void onEnable() {
        this.serviceRegistry = new ServiceRegistry();
        ItemKeys.init(this);

        ConfigService configService = new ConfigService(this);
        configService.loadAll();
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

        File langFolder = new File(getDataFolder(), "lang");
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
        getLogger().info("RumilancePractice has been enabled.");
    }

    private boolean initializeDatabase(ConfigService configService) {
        try {
            this.databaseService = new DatabaseService(configService.database(), getDataFolder());
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
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize the database. Disabling RumilancePractice.", e);
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
        getLogger().info("RumilancePractice has been disabled.");
    }

    public ServiceRegistry services() {
        return serviceRegistry;
    }
}
