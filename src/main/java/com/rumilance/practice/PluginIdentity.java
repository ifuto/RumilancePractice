package com.rumilance.practice;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * {@code plugin.yml} name is {@link #NAME}. YAML and other data stay under
 * {@code plugins/{@link #DATA_FOLDER_NAME}} because Paper's {@code JavaPlugin#getDataFolder()}
 * is final and would otherwise resolve to {@code plugins/NARENA}.
 * Item PDC stays on {@link #PDC_NAMESPACE} so existing kits keep their tags after the rename.
 */
public final class PluginIdentity {

    public static final String NAME = "NARENA";
    public static final String DATA_FOLDER_NAME = "RumilancePractice";
    public static final String PDC_NAMESPACE = "rumilancepractice";

    private PluginIdentity() {
    }

    public static Plugin plugin() {
        Plugin named = Bukkit.getPluginManager().getPlugin(NAME);
        if (named != null) {
            return named;
        }
        return Bukkit.getPluginManager().getPlugin(DATA_FOLDER_NAME);
    }

    /** Operator data root: always {@code plugins/RumilancePractice}, even when the plugin name is NARENA. */
    public static File dataFolder(Plugin plugin) {
        File bukkitFolder = plugin.getDataFolder();
        File parent = bukkitFolder.getParentFile();
        if (parent == null) {
            return bukkitFolder;
        }
        return new File(parent, DATA_FOLDER_NAME);
    }
}
