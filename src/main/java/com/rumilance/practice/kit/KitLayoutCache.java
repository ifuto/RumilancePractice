package com.rumilance.practice.kit;

import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory cache of official kit layout overrides so match start never blocks on DB I/O.
 */
public final class KitLayoutCache {

    private final KitLayoutRepository repository;
    private final AsyncExecutor asyncExecutor;
    private final Logger logger;
    private final Map<String, ItemStack[]> cache = new ConcurrentHashMap<>();

    public KitLayoutCache(KitLayoutRepository repository, AsyncExecutor asyncExecutor, Logger logger) {
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
        this.logger = logger;
    }

    private static String key(UUID uuid, String kit) {
        return uuid + ":" + kit.toLowerCase();
    }

    public Optional<ItemStack[]> get(UUID uuid, String kit) {
        return Optional.ofNullable(cache.get(key(uuid, kit)));
    }

    public void put(UUID uuid, String kit, ItemStack[] layout) {
        cache.put(key(uuid, kit), layout);
    }

    public void invalidate(UUID uuid, String kit) {
        cache.remove(key(uuid, kit));
    }

    public void unload(UUID uuid) {
        String prefix = uuid + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void preload(UUID uuid) {
        asyncExecutor.execute(() -> {
            try {
                repository.findAllForPlayer(uuid).forEach(snap -> {
                    try {
                        cache.put(key(uuid, snap.kit()), ItemSerializer.fromBase64(snap.itemDataBase64()));
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Bad kit layout for " + uuid + "/" + snap.kit(), e);
                    }
                });
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed preloading kit layouts for " + uuid, e);
            }
        });
    }

    public void loadSyncIfAbsent(UUID uuid, String kit) {
        if (cache.containsKey(key(uuid, kit))) {
            return;
        }
        try {
            repository.find(uuid, kit).ifPresent(snap ->
                    cache.put(key(uuid, kit), ItemSerializer.fromBase64(snap.itemDataBase64())));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed loading kit layout " + uuid + "/" + kit, e);
        }
    }
}
