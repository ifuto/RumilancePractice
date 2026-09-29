package com.rumilance.practice.cosmetic;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 無料版プレイヤーの鍛冶型特別枠: {@code plugins/n-arena/trim-allowance.yml} に
 * {@code uuid -> kitId} として **無料プレイヤーが選んだ「好きな1キット」** を保持する。
 * そのキットのキット編集で開く鍛冶型GUIだけは、VIP+ と同じ範囲(VIP+ 素材・パターンフル)
 * を適用できる。2つ目のキットではゲートが通常通り効く。改名ツールなど他の VIP 機能は
 * この許可の対象外(従来どおり不可のまま)。
 *
 * <p>バインドは「無料プレイヤーが VIP 範囲の素材/パターンで初めて Apply に成功した
 * キット」に成立する(= 誤タップ1回で選考権を捨てない)。永続化は非同期で
 * `trim-allowance.yml` へ書き出す。クラッシュ時の欠落は最後の bind 1件のみ。</p>
 */
public final class TrimAllowanceService {

    private final File file;
    private final com.rumilance.practice.util.AsyncExecutor pool;
    private final Map<UUID, String> boundByPlayer = new ConcurrentHashMap<>();

    public TrimAllowanceService(JavaPlugin plugin, com.rumilance.practice.util.AsyncExecutor pool) {
        this.file = new File(plugin.getDataFolder(), "trim-allowance.yml");
        this.pool = pool;
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String kit = yaml.getString(key);
            if (kit == null || kit.isBlank()) {
                continue;
            }
            try {
                boundByPlayer.put(UUID.fromString(key), kit.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // malformed rows are dropped
            }
        }
    }

    /** The kit this player unlocked full VIP-range trims on, or {@code null}. */
    public String boundKit(UUID playerId) {
        return boundByPlayer.get(playerId);
    }

    /** True when this kit is the player's free VIP-trim slot. */
    public boolean allows(UUID playerId, String kitId) {
        String bound = boundByPlayer.get(playerId);
        return kitId != null && bound != null && bound.equalsIgnoreCase(kitId);
    }

    /**
     * Binds the player to {@code kitId} if (and only if) they have never bound another kit.
     *
     * @return the bound kit id (the one in effect after this call)
     */
    public String bindIfUnset(UUID playerId, String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return boundByPlayer.get(playerId);
        }
        String normalized = kitId.trim().toLowerCase(Locale.ROOT);
        String existing = boundByPlayer.get(playerId);
        if (existing != null) {
            return existing;
        }
        boundByPlayer.put(playerId, normalized);
        persistAsync();
        return normalized;
    }

    /** Operator override: clear a player's free slot (lets them pick again). */
    public boolean reset(UUID playerId) {
        if (boundByPlayer.remove(playerId) == null) {
            return false;
        }
        persistAsync();
        return true;
    }

    private void persistAsync() {
        Map<UUID, String> snapshot = Map.copyOf(boundByPlayer);
        pool.runAsync(() -> {
            YamlConfiguration yaml = new YamlConfiguration();
            snapshot.forEach((uuid, kit) -> yaml.set(uuid.toString(), kit));
            try {
                yaml.save(file);
            } catch (IOException ignored) {
                // next persist pass will try again
            }
        });
    }

    /** Player-side description helper for GUI lores. */
    public String describe(Player player) {
        return boundKit(player.getUniqueId());
    }
}
