/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.NamespacedKey
 *  org.bukkit.plugin.Plugin
 */
package com.rumilance.practice.util;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

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
    private static volatile Plugin plugin;

    private ItemKeys() {
    }

    public static void init(Plugin pluginInstance) {
        plugin = Objects.requireNonNull(pluginInstance, "pluginInstance");
    }

    public static NamespacedKey kitName() {
        return ItemKeys.key(KIT_NAME);
    }

    public static NamespacedKey arenaId() {
        return ItemKeys.key(ARENA_ID);
    }

    public static NamespacedKey guiAction() {
        return ItemKeys.key(GUI_ACTION);
    }

    public static NamespacedKey arrowEffect() {
        return ItemKeys.key(ARROW_EFFECT);
    }

    public static NamespacedKey originalKitMarker() {
        return ItemKeys.key(ORIGINAL_KIT_MARKER);
    }

    public static NamespacedKey functionType() {
        return ItemKeys.key(FUNCTION_TYPE);
    }

    public static NamespacedKey adminTool() {
        return ItemKeys.key(ADMIN_TOOL);
    }

    public static NamespacedKey leaveQueue() {
        return ItemKeys.key(LEAVE_QUEUE);
    }

    public static NamespacedKey rematch() {
        return ItemKeys.key(REMATCH);
    }

    public static NamespacedKey returnLobby() {
        return ItemKeys.key(RETURN_LOBBY);
    }

    public static NamespacedKey targetUuid() {
        return ItemKeys.key(TARGET_UUID);
    }

    public static NamespacedKey goldenHead() {
        return ItemKeys.key(GOLDEN_HEAD);
    }

    public static NamespacedKey matchReport() {
        return ItemKeys.key(MATCH_REPORT);
    }

    public static NamespacedKey key(String name) {
        Plugin current = plugin;
        if (current == null) {
            throw new IllegalStateException("ItemKeys.init(Plugin) must be called before requesting keys");
        }
        return new NamespacedKey(current, name);
    }
}
