package com.rumilance.practice.settings;

import com.rumilance.practice.database.repository.SettingsRepository;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cached player settings with async persistence.
 */
public final class SettingsService {

    private final SettingsRepository repository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final String defaultLocale;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public SettingsService(SettingsRepository repository, AsyncExecutor asyncExecutor, Logger logger, String defaultLocale) {
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
        this.defaultLocale = defaultLocale;
    }

    public PlayerSettings get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            try {
                return repository.findOrDefault(id, PlayerSettings.defaultsFor(id, defaultLocale));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed loading settings for " + id, e);
                return PlayerSettings.defaultsFor(id, defaultLocale);
            }
        });
    }

    public PlayerSettings get(Player player) {
        return get(player.getUniqueId());
    }

    public void update(PlayerSettings settings) {
        cache.put(settings.uuid(), settings);
        asyncExecutor.execute(() -> {
            try {
                repository.upsert(settings);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed saving settings for " + settings.uuid(), e);
            }
        });
    }

    public void unload(UUID uuid) {
        PlayerSettings settings = cache.remove(uuid);
        if (settings != null) {
            asyncExecutor.execute(() -> {
                try {
                    repository.upsert(settings);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed flushing settings for " + uuid, e);
                }
            });
        }
    }

    public void flushAll() {
        for (PlayerSettings settings : cache.values()) {
            try {
                repository.upsert(settings);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed flushing settings for " + settings.uuid(), e);
            }
        }
    }
}
