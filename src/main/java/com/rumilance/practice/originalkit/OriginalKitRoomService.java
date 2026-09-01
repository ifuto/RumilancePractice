package com.rumilance.practice.originalkit;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.SafeTeleport;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * The shared "original kit room".
 *
 * <p>One configured room (like the lobby) where players build/edit an original kit in creative.
 * Everyone who edits is sent to the same room, but players cannot see or collide with each other:
 * each editor hides every other editor and has collision disabled, so the room feels private.
 * Blocks in the room are permanent (placed by the admin — anvils, grindstone, the save button):
 * players can't break room blocks or place blocks. The edit session begins only after the
 * teleport lands, and the player is switched to creative THEN (never mid-teleport).</p>
 */
public final class OriginalKitRoomService {

    private final ConfigService configService;
    private final org.bukkit.plugin.Plugin plugin;

    private Location spawn;
    private Cuboid region;

    /** Players currently inside an edit session. */
    private final Set<UUID> editors = ConcurrentHashMap.newKeySet();

    /** Configured SAVE button block key ("x;y;z" in the room world). Admin-registered. */
    private volatile String saveButtonKey;

    public OriginalKitRoomService(ConfigService configService, org.bukkit.plugin.Plugin plugin) {
        this.configService = configService;
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration lobby = configService.lobby();
        String world = lobby.getString("ekit-room.spawn.world", "world");
        World w = Bukkit.getWorld(world);
        if (w != null && lobby.contains("ekit-room.spawn.x")) {
            spawn = new Location(w,
                    lobby.getDouble("ekit-room.spawn.x"),
                    lobby.getDouble("ekit-room.spawn.y"),
                    lobby.getDouble("ekit-room.spawn.z"),
                    (float) lobby.getDouble("ekit-room.spawn.yaw", 0.0),
                    (float) lobby.getDouble("ekit-room.spawn.pitch", 0.0));
        } else {
            spawn = null;
        }
        region = Cuboid.fromConfig(lobby, "ekit-room.region");
        saveButtonKey = lobby.getString("ekit-room.save-button", null);
    }

    /** Registers the block the player is looking at (or standing on) as the SAVE button. */
    public void registerSaveButton(Location location) {
        this.saveButtonKey = keyOf(location);
        FileConfiguration lobby = configService.lobby();
        lobby.set("ekit-room.save-button", saveButtonKey);
        configService.save(ConfigService.LOBBY);
    }

    public boolean hasSaveButton() {
        return saveButtonKey != null && !saveButtonKey.isBlank();
    }

    /** True only if the room has a save button AND the clicked block is that button. */
    public boolean isSaveButton(Location location) {
        return saveButtonKey != null && saveButtonKey.equals(keyOf(location));
    }

    private static String keyOf(Location location) {
        return location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    public boolean isConfigured() {
        return spawn != null;
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    public Cuboid region() {
        return region;
    }

    public boolean isEditing(UUID playerId) {
        return editors.contains(playerId);
    }

    public Set<UUID> editors() {
        return Set.copyOf(editors);
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();
        FileConfiguration lobby = configService.lobby();
        lobby.set("ekit-room.spawn.world", location.getWorld() != null ? location.getWorld().getName() : "world");
        lobby.set("ekit-room.spawn.x", location.getX());
        lobby.set("ekit-room.spawn.y", location.getY());
        lobby.set("ekit-room.spawn.z", location.getZ());
        lobby.set("ekit-room.spawn.yaw", location.getYaw());
        lobby.set("ekit-room.spawn.pitch", location.getPitch());
        configService.save(ConfigService.LOBBY);
    }

    public void setRegion(Cuboid cuboid) {
        this.region = cuboid;
        FileConfiguration lobby = configService.lobby();
        lobby.set("ekit-room.region.world", cuboid.worldName());
        lobby.set("ekit-room.region.pos1.x", cuboid.minX());
        lobby.set("ekit-room.region.pos1.y", cuboid.minY());
        lobby.set("ekit-room.region.pos1.z", cuboid.minZ());
        lobby.set("ekit-room.region.pos2.x", cuboid.maxX());
        lobby.set("ekit-room.region.pos2.y", cuboid.maxY());
        lobby.set("ekit-room.region.pos2.z", cuboid.maxZ());
        configService.save(ConfigService.LOBBY);
    }

    public boolean inRoom(Location location) {
        return region != null && region.contains(location);
    }

    /** Sends a player into the room; switches to creative only AFTER the teleport lands. */
    public void enter(Player player) {
        if (spawn == null) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "Original kit room is not set up yet.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        // Enter the editor set first so the hide-listener picks the player up on arrival.
        editors.add(player.getUniqueId());
        applyIsolation(player, true);
        SafeTeleport.teleport(player, com.rumilance.practice.util.LocationUtil.safeTeleportLocation(spawn))
                .whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !editors.contains(player.getUniqueId())) {
                        return;
                    }
                    // Creative only once the teleport has actually completed.
                    player.setGameMode(GameMode.CREATIVE);
                    // Hide all other editors from this player and vice-versa.
                    refreshVisibility();
                }));
    }

    /** Removes a player from the room and restores normal visibility. */
    public void exit(Player player) {
        boolean was = editors.remove(player.getUniqueId());
        if (was) {
            applyIsolation(player, false);
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            }
        }
    }

    private void applyIsolation(Player player, boolean enable) {
        try {
            player.setCollidable(!enable);
        } catch (RuntimeException ignored) {
        }
    }

    /** Re-runs hide/show for every editor so editors never see each other. */
    public void refreshVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean viewerEditor = editors.contains(viewer.getUniqueId());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }
                boolean targetEditor = editors.contains(target.getUniqueId());
                if (viewerEditor && targetEditor) {
                    viewer.hidePlayer(plugin, target);
                } else {
                    viewer.showPlayer(plugin, target);
                }
            }
        }
    }
}
