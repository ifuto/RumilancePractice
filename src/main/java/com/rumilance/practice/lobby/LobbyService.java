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
    /** Hotbar parking for open menus (user spec 2026-10-04); null = feature disabled. */
    private volatile com.rumilance.practice.gui.HotbarVacator hotbarVacator;

    /** Wired from bootstrap: lets the lobby drop a menu's parked hotbar on a full re-apply. */
    public void setHotbarVacator(com.rumilance.practice.gui.HotbarVacator hotbarVacator) {
        this.hotbarVacator = hotbarVacator;
    }

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
        // Reset first: a region removed from lobby.yml must not survive a reload, otherwise a
        // stale cuboid keeps gating hub features (lobby wear, glide) on a place that no longer
        // exists.
        region = null;
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
        com.rumilance.practice.util.PlayerVitals.resetMaxHealth(player);
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
            //
            // SafeTeleport rather than a raw teleportAsync: it loads the destination chunk off
            // the main thread exactly like teleportAsync did, but then lands the player on the
            // column's standable surface and watches the landing for late paste/chunk burial.
            // Returning from the AFK room (/afkc) buried players because this path skipped the
            // footing entirely; a failed move is retried once shortly after.
            com.rumilance.practice.util.SafeTeleport.teleport(player, safe).thenAccept(ok -> {
                player.setCompassTarget(destination);
                if (Boolean.TRUE.equals(ok)) {
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
            player.setCompassTarget(destination);
        } else if (hook != null) {
            hook.accept(player);
        }
        // Fight-grid TAB残留の解消: ロビー帰還のたびに TAB を確実に初期状態へ戻す
        // (アリーナの列並び・hidden 指定・リスト名がそのまま残るのを防ぐ — 即時 + 2tick後の
        // 追従で、テレポート/準備系パケットに後塗りされても最終状態がロビー側になる)。
        java.util.function.Consumer<Player> tabReset = tabResetHook;
        if (tabReset != null) {
            tabReset.accept(player);
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LobbyService.class),
                    () -> {
                        if (player.isOnline()) {
                            tabReset.accept(player);
                        }
                    }, 2L);
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

    private volatile java.util.function.Consumer<Player> tabResetHook;

    /**
     * Wires the fight-grid TAB reset (called from bootstrap): invoked on every lobby return —
     * clears arena grid leftovers (hidden entries, list order, list name) and re-applies the
     * lobby TAB for that viewer. In-match players keep showing in the lobby TAB as before.
     */
    public void setTabResetHook(java.util.function.Consumer<Player> tabResetHook) {
        this.tabResetHook = tabResetHook;
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
        // This call re-writes the whole inventory (hotbar included) from the saved lobby
        // standard, so any hotbar a menu parked is now redundant — drop it instead of restoring
        // it afterwards, which would fight the rows this method has just filled.
        if (hotbarVacator != null) {
            hotbarVacator.drop(player.getUniqueId());
        }
        // 1.92.32: the admin region selector (FFA/arena selection wand, PDC adminTool) must
        // survive the reset — since the functional-item reset shipped, opening any lobby GUI
        // wiped it from the operator's inventory ("FFAの選択ツールがなくなっちゃってる").
        java.util.List<ItemStack> adminTools = new java.util.ArrayList<>();
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (isAdminTool(stack)) {
                adminTools.add(stack.clone());
            }
        }
        player.getInventory().clear();
        if (hubInventoryCustomizer != null && Boolean.TRUE.equals(hubInventoryCustomizer.apply(player))) {
            restoreAdminTools(player, adminTools);
            return;
        }
        if (lobbyInventory == null) {
            restoreAdminTools(player, adminTools);
            return;
        }
        for (int i = 0; i < Math.min(36, lobbyInventory.length); i++) {
            if (lobbyInventory[i] != null && !isGameMenuItem(lobbyInventory[i])) {
                player.getInventory().setItem(i, lobbyInventory[i].clone());
            }
        }
        if (lobbyInventory.length >= 40) {
            // 置換先が空気(null)の際は置換しない — LobbyWearService が配布する
            // 革靴・エリトラを壊さないため。clear() で消えた防具スロットに、
            // /setlobbyitem で保存されたアイテムだけを戻す。null は「元から空」を意味する
            // ので、LobbyWearService の reconcile が後から空きスロットへ入れてくれる。
            for (int i = 0; i < 4; i++) {
                ItemStack saved = lobbyInventory[36 + i];
                if (saved != null && !saved.getType().isAir()) {
                    player.getInventory().setItem(
                            org.bukkit.inventory.EquipmentSlot.values()[
                                    org.bukkit.inventory.EquipmentSlot.FEET.ordinal() + i],
                            saved.clone());
                }
            }
        }
        if (lobbyInventory.length > 40 && lobbyInventory[40] != null && !isGameMenuItem(lobbyInventory[40])) {
            player.getInventory().setItemInOffHand(lobbyInventory[40].clone());
        }
        restoreAdminTools(player, adminTools);
    }

    /**
     * Puts the admin tools (region selector / setup menu, PDC {@code adminTool}) back after
     * a lobby-standard reset. Saved lobby inventories that already contain the same tool win
     * — no duplicates. Anything left lands in the first free slot (tools only ever occupy
     * storage slots, so armor is untouched).
     */
    private static void restoreAdminTools(Player player, java.util.List<ItemStack> tools) {
        if (tools.isEmpty()) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (ItemStack tool : tools) {
            String value = adminToolValue(tool);
            boolean present = false;
            for (ItemStack current : inv.getContents()) {
                if (current != null && current.getType() == tool.getType()
                        && Objects.equals(adminToolValue(current), value)) {
                    present = true;
                    break;
                }
            }
            if (present) {
                continue;
            }
            int first = inv.firstEmpty();
            if (first >= 0) {
                inv.setItem(first, tool);
            } else {
                // Nowhere to put it: drop it at the player's feet instead of deleting it.
                player.getWorld().dropItemNaturally(player.getLocation(), tool);
            }
        }
    }

    /** PDC {@code adminTool} value of a stack, or null when it is not an admin tool. */
    private static String adminToolValue(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(com.rumilance.practice.util.ItemKeys.adminTool(),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static boolean isAdminTool(ItemStack stack) {
        return adminToolValue(stack) != null;
    }

    /**
     * The Game Menu compass is intentionally never handed out on join. Old saved lobby
     * inventories ({@code /setlobbyitem}) may still contain one baked in by a previous build;
     * skip it so it is not restored on join / lobby return.
     */
    private static boolean isGameMenuItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(com.rumilance.practice.util.ItemKeys.functionType(),
                        org.bukkit.persistence.PersistentDataType.STRING);
        return "menu".equalsIgnoreCase(value);
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
