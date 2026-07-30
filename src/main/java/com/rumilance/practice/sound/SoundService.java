package com.rumilance.practice.sound;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.settings.SettingsService;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays config-driven sounds via NamespacedKey, with safe fallbacks.
 * Respects per-player {@code soundsEnabled} setting when a {@link SettingsService} is provided.
 */
public final class SoundService {

    private final ConfigService configService;
    private final SettingsService settingsService;
    private final Map<String, SoundDefinition> cache = new ConcurrentHashMap<>();

    public SoundService(ConfigService configService) {
        this(configService, null);
    }

    public SoundService(ConfigService configService, SettingsService settingsService) {
        this.configService = configService;
        this.settingsService = settingsService;
        reload();
    }

    public void reload() {
        cache.clear();
        ConfigurationSection root = configService.sounds().getConfigurationSection("sounds");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            cache.put(id, new SoundDefinition(
                    section.getString("key", "ui.button.click"),
                    (float) section.getDouble("volume", 1.0d),
                    (float) section.getDouble("pitch", 0.0d)
            ));
        }
    }

    public void play(Player player, String soundId) {
        if (player == null || !soundsAllowed(player)) {
            return;
        }
        SoundDefinition definition = cache.getOrDefault(soundId, SoundDefinition.FALLBACK);
        if (isSilent(definition)) {
            return;
        }
        Sound sound = resolve(definition.key());
        player.playSound(player.getLocation(), sound, definition.volume(), definition.pitch());
    }

    public void play(Player player, String soundId, float pitchOverride) {
        if (player == null || !soundsAllowed(player)) {
            return;
        }
        SoundDefinition definition = cache.getOrDefault(soundId, SoundDefinition.FALLBACK);
        if (isSilent(definition)) {
            return;
        }
        Sound sound = resolve(definition.key());
        player.playSound(player.getLocation(), sound, definition.volume(), pitchOverride);
    }

    /**
     * A sound definition whose key is {@code none} (or blank) is treated as intentionally
     * silent — the configured event simply does not play. Used for menu open/back where an
     * audible cue is undesirable.
     */
    private boolean isSilent(SoundDefinition definition) {
        return definition.key() == null || definition.key().isBlank() || definition.key().equalsIgnoreCase("none");
    }

    private boolean soundsAllowed(Player player) {
        return settingsService == null || settingsService.get(player).soundsEnabled();
    }

    private Sound resolve(String key) {
        try {
            NamespacedKey namespacedKey = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
            if (namespacedKey != null) {
                Sound sound = Registry.SOUNDS.get(namespacedKey);
                if (sound != null) {
                    return sound;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return Sound.UI_BUTTON_CLICK;
    }

    private record SoundDefinition(String key, float volume, float pitch) {
        static final SoundDefinition FALLBACK = new SoundDefinition("ui.button.click", 1.0f, 1.0f);
    }
}
