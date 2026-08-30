package com.rumilance.practice.lobby;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.ItemSerializer;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.SafeTeleport;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;

/**
 * Lobby spawn, cuboid bounds, and lobby inventory application.
 */
public final class LobbyService {

    private final ConfigService configService;
    private volatile Location spawn;
    private volatile Cuboid region;
    private volatile double fallReturnY = 0.0d;
    private volatile ItemStack[] lobbyInventory = new ItemStack[41];
    /** Optional sight hook applied on every lobby send (per-player border + view distance). */
    private volatile java.util.function.Consumer<Player> sightHook;
    /** When true, default lobby inventory is skipped (e.g. party hotbar). */
    private volatile java.util.function.Function<Player, Boolean> hubInventoryCustomizer;

    public LobbyService(ConfigService configService) {
        this.configService = Objects.requireNonNull(configService);
        reload();
    }

    public void reload() {
        FileConfiguration lobby = configService.lobby();
        String worldName = lobby.getString("spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        spawn = new Location(
                world,
                lobby.getDouble("spawn.x", 0.5d),
                lobby.getDouble("spawn.y", 65.0d),
                lobby.getDouble("spawn.z", 0.5d),
                (float) lobby.getDouble("spawn.yaw", 0.0d),
                (float) lobby.getDouble("spawn.pitch", 0.0d)
        );
        fallReturnY = lobby.getDouble("fall-return-y", 0.0d);
        if (lobby.isSet("region.world")) {
            region = Cuboid.of(
                    lobby.getString("region.world", worldName),
                    lobby.getInt("region.pos1.x"),
                    lobby.getInt("region.pos1.y"),
                    lobby.getInt("region.pos1.z"),
                    lobby.getInt("region.pos2.x"),
                    lobby.getInt("region.pos2.y"),
                    lobby.getInt("region.pos2.z")
            );
        }
        String inventoryBase64 = lobby.getString("inventory-base64");
        if (inventoryBase64 != null && !inventoryBase64.isBlank()) {
            lobbyInventory = ItemSerializer.fromBase64(inventoryBase64);
        }
    }

    public Location spawn() {
        return spawn == null ? null : spawn.clone();
    }

    public Cuboid region() {
        return region;
    }

    public double fallReturnY() {
        return fallReturnY;
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();
        FileConfiguration lobby = configService.lobby();
        lobby.set("spawn.world", location.getWorld() != null ? location.getWorld().getName() : "world");
        lobby.set("spawn.x", location.getX());
        lobby.set("spawn.y", location.getY());
        lobby.set("spawn.z", location.getZ());
        lobby.set("spawn.yaw", location.getYaw());
        lobby.set("spawn.pitch", location.getPitch());
        configService.save(ConfigService.LOBBY);
    }

    public void setRegion(Cuboid cuboid) {
        this.region = cuboid;
        FileConfiguration lobby = configService.lobby();
        lobby.set("region.world", cuboid.worldName());
        lobby.set("region.pos1.x", cuboid.minX());
        lobby.set("region.pos1.y", cuboid.minY());
        lobby.set("region.pos1.z", cuboid.minZ());
        lobby.set("region.pos2.x", cuboid.maxX());
        lobby.set("region.pos2.y", cuboid.maxY());
        lobby.set("region.pos2.z", cuboid.maxZ());
        configService.save(ConfigService.LOBBY);
    }

    public void saveLobbyInventory(Player player) {
        ItemStack[] contents = new ItemStack[41];
        ItemStack[] storage = player.getInventory().getStorageContents();
        System.arraycopy(storage, 0, contents, 0, Math.min(storage.length, 36));
        ItemStack[] armor = player.getInventory().getArmorContents();
        System.arraycopy(armor, 0, contents, 36, Math.min(armor.length, 4));
        contents[40] = player.getInventory().getItemInOffHand();
        this.lobbyInventory = contents;
        configService.lobby().set("inventory-base64", ItemSerializer.toBase64(contents));
        configService.save(ConfigService.LOBBY);
    }

    /**
     * @param ignoreInventory unused compatibility flag from newer callers (always applies lobby inventory)
     */
    public void sendToLobby(Player player, boolean ignoreInventory) {
        sendToLobby(player);
    }

    public void sendToLobby(Player player) {
        ensureHubReturn(player);
    }

    /**
     * Full lobby reset: gamemode, vitals, inventory, compass, sight hook, and a guaranteed
     * teleport to the configured lobby spawn (retries once if the first teleport fails).
     */
    public void ensureHubReturn(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        com.rumilance.practice.util.PlayerVitals.clearCombatState(player);
        com.rumilance.practice.util.PlayerVitals.refillHealth(player);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGlowing(false);
        player.setCollidable(true);
        try {
            player.setInvisible(false);
        } catch (NoSuchMethodError ignored) {
        }
        applyLobbyResistance(player);
        applyLobbyInventory(player);
        Location destination = spawn();
        java.util.function.Consumer<Player> hook = sightHook;
        if (destination != null && destination.getWorld() != null) {
            Location safe = LocationUtil.safeTeleportLocation(destination);
            // Teleport FIRST (SafeTeleport clears the stale arena/personal border), then
            // apply the lobby border/view only after the move landed. Applying the lobby
            // border before the teleport used to leave players clamped into a stale wall
            // (and burying on return) because the border present during the teleport was
            // the OLD arena/lobby one.
            player.teleportAsync(safe).thenAccept(ok -> {
                player.setCompassTarget(destination);
                if (Boolean.TRUE.equals(ok)) {
                    applySightAfterTeleport(player, hook);
                    return;
                }
                com.rumilance.practice.util.SafeTeleport.teleport(player, safe).thenAccept(retry -> {
                    if (Boolean.TRUE.equals(retry)) {
                        applySightAfterTeleport(player, hook);
                        return;
                    }
                    org.bukkit.Bukkit.getScheduler().runTaskLater(
                            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LobbyService.class),
                            () -> {
                                if (player.isOnline()) {
                                    com.rumilance.practice.util.SafeTeleport.teleport(player, safe);
                                    applySightAfterTeleport(player, hook);
                                }
                            }, 8L);
                });
            });
            player.setCompassTarget(destination);
        } else if (hook != null) {
            hook.accept(player);
        }
    }

    private void applySightAfterTeleport(Player player, java.util.function.Consumer<Player> hook) {
        if (hook != null && player != null && player.isOnline()) {
            hook.accept(player);
        }
    }

    /**
     * Wires the per-player border / view-distance hook (called from bootstrap). The hook
     * receives the player; the lobby region itself is exposed via {@link #region()}.
     */
    public void setSightHook(java.util.function.Consumer<Player> sightHook) {
        this.sightHook = sightHook;
    }

    public void setHubInventoryCustomizer(java.util.function.Function<Player, Boolean> customizer) {
        this.hubInventoryCustomizer = customizer;
    }

    /**
     * Resistance 255 while in the lobby region / kit editor — belt-and-suspenders with
     * {@link LobbyListener} damage cancel so Match Found punches never chip lobby players.
     */
    public void applyLobbyResistance(Player player) {
        if (player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE, PotionEffect.INFINITE_DURATION, 255, false, false, false));
    }

    public void applyLobbyInventory(Player player) {
        player.getInventory().clear();
        if (hubInventoryCustomizer != null && Boolean.TRUE.equals(hubInventoryCustomizer.apply(player))) {
            return;
        }
        if (lobbyInventory == null) {
            return;
        }
        for (int i = 0; i < Math.min(36, lobbyInventory.length); i++) {
            if (lobbyInventory[i] != null) {
                player.getInventory().setItem(i, lobbyInventory[i].clone());
            }
        }
        if (lobbyInventory.length >= 40) {
            ItemStack[] armor = new ItemStack[4];
            for (int i = 0; i < 4; i++) {
                armor[i] = lobbyInventory[36 + i] == null ? null : lobbyInventory[36 + i].clone();
            }
            player.getInventory().setArmorContents(armor);
        }
        if (lobbyInventory.length > 40 && lobbyInventory[40] != null) {
            player.getInventory().setItemInOffHand(lobbyInventory[40].clone());
        }
    }

    public boolean isConfigured() {
        return spawn != null && spawn.getWorld() != null && region != null;
    }

    public String validate() {
        if (spawn == null || spawn.getWorld() == null) {
            return "Lobby spawn is not set or world is unloaded.";
        }
        if (region == null) {
            return "Lobby region is not set.";
        }
        if (!region.contains(spawn)) {
            return "Lobby spawn is outside the lobby region.";
        }
        if (!LocationUtil.isInsideWorldBorder(spawn, null)) {
            return "Lobby spawn is outside the world border (will be clamped on teleport).";
        }
        return null;
    }
}
