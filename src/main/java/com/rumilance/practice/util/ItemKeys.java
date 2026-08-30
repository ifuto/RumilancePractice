package com.rumilance.practice.util;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Central registry of {@link NamespacedKey}s used to tag items/inventory metadata
 * via {@code PersistentDataContainer}. Must be initialized once with {@link #init(Plugin)}
 * during {@code onEnable} before any key is requested.
 */
public final class ItemKeys {

    public static final String KIT_NAME = "kit_name";
    public static final String ARENA_ID = "arena_id";
    public static final String GUI_ACTION = "gui_action";
    public static final String ARROW_EFFECT = "arrow_effect";
    public static final String ORIGINAL_KIT_MARKER = "original_kit_marker";
    public static final String FUNCTION_TYPE = "function_type";
    public static final String ADMIN_TOOL = "admin_tool";
    public static final String LEAVE_QUEUE = "leave_queue";
    public static final String REMATCH = "rematch";
    public static final String RETURN_LOBBY = "return_lobby";
    public static final String TARGET_UUID = "target_uuid";
    public static final String GOLDEN_HEAD = "golden_head";
    public static final String MATCH_REPORT = "match_report";
    public static final String TRIM_NOTE = "trim_note";
    public static final String EDITOR_HINT_COUNT = "editor_hint_count";

    private static volatile Plugin plugin;

    private ItemKeys() {
    }

    public static void init(Plugin pluginInstance) {
        plugin = Objects.requireNonNull(pluginInstance, "pluginInstance");
    }

    public static NamespacedKey kitName() {
        return key(KIT_NAME);
    }

    public static NamespacedKey arenaId() {
        return key(ARENA_ID);
    }

    public static NamespacedKey guiAction() {
        return key(GUI_ACTION);
    }

    public static NamespacedKey arrowEffect() {
        return key(ARROW_EFFECT);
    }

    public static NamespacedKey originalKitMarker() {
        return key(ORIGINAL_KIT_MARKER);
    }

    public static NamespacedKey functionType() {
        return key(FUNCTION_TYPE);
    }

    public static NamespacedKey adminTool() {
        return key(ADMIN_TOOL);
    }

    public static NamespacedKey leaveQueue() {
        return key(LEAVE_QUEUE);
    }

    public static NamespacedKey rematch() {
        return key(REMATCH);
    }

    public static NamespacedKey returnLobby() {
        return key(RETURN_LOBBY);
    }

    public static NamespacedKey targetUuid() {
        return key(TARGET_UUID);
    }

    public static NamespacedKey goldenHead() {
        return key(GOLDEN_HEAD);
    }

    public static NamespacedKey matchReport() {
        return key(MATCH_REPORT);
    }

    public static NamespacedKey trimNote() {
        return key(TRIM_NOTE);
    }

    public static NamespacedKey editorHintCount() {
        return key(EDITOR_HINT_COUNT);
    }

    public static NamespacedKey key(String name) {
        if (plugin == null) {
            throw new IllegalStateException("ItemKeys.init(Plugin) must be called before requesting keys");
        }
        return new NamespacedKey(PluginIdentity.PDC_NAMESPACE, name);
    }
}
