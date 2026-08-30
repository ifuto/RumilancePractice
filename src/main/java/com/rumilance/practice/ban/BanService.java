package com.rumilance.practice.ban;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import com.rumilance.practice.PluginIdentity;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class BanService {

    private final Plugin plugin;
    private final BanStore store;

    public BanService(Plugin plugin) {
        this.plugin = plugin;
        this.store = new BanStore(Path.of(PluginIdentity.dataFolder(plugin).getPath(), "bans.rpb"));
        try {
            store.load();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bans.rpb", e);
        }
    }

    public BanStore store() {
        return store;
    }

    public BanRecord ban(UUID playerId, String playerName, String reason, Duration duration,
                         String durationToken, String staffName) {
        long now = System.currentTimeMillis();
        store.deactivate(playerId, now);
        long expires = duration == null ? 0L : now + duration.toMillis();
        String label = BanDuration.labelFromToken(durationToken, duration);
        BanRecord record = new BanRecord(
                UUID.randomUUID(), playerId, playerName, reason, label, now, expires, true,
                staffName == null ? "" : staffName);
        store.add(record);
        persist();
        Player online = Bukkit.getPlayer(playerId);
        if (online != null && online.isOnline()) {
            online.kick(BanScreens.banned(reason, label));
        }
        Bukkit.broadcast(BanAnnounce.ban(playerName, reason, label));
        return record;
    }

    public void kick(Player target, String staffName) {
        kick(target, staffName, "Kicked");
    }

    public void kick(Player target, String staffName, String reason) {
        String kickReason = reason == null || reason.isBlank() ? "Kicked" : reason;
        Bukkit.broadcast(BanAnnounce.kick(target.getName()));
        target.kick(BanScreens.kicked(kickReason));
        plugin.getLogger().info("Kicked " + target.getName() + " by " + staffName);
    }

    public boolean unban(UUID playerId) {
        boolean changed = store.deactivate(playerId, System.currentTimeMillis());
        if (changed) {
            persist();
        }
        return changed;
    }

    public BanRecord activeBan(UUID playerId) {
        return store.activeOf(playerId, System.currentTimeMillis());
    }

    public List<BanRecord> activeNewestFirst() {
        return store.activeNewestFirst(System.currentTimeMillis());
    }

    public List<BanRecord> history(UUID playerId) {
        return store.historyNewestFirst(playerId);
    }

    public int banCount(UUID playerId) {
        return history(playerId).size();
    }

    public void persist() {
        try {
            store.save();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save bans.rpb", e);
        }
    }

    public static String nameOf(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null || name.isBlank() ? fallback : name;
    }
}
