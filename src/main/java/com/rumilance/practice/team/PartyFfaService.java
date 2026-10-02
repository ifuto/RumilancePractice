package com.rumilance.practice.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Party Fight 専用 Sword FFA — 倒されたプレイヤーが参加できる 100×100 の闘技場。
 *
 * <ul>
 *   <li>Party Fight 開始時にプラットフォームを1つ確保（専用ワールド {@code party-ffa} 内）</li>
 *   <li>デス → チャットボタン「Sword FFAに参加する」で中央に TP</li>
 *   <li>FFA 内デス → 3 秒後に自動リスポーン（FFA の中央）</li>
 *   <li>キルで回復（通常 FFA ルール）</li>
 * </ul>
 */
public final class PartyFfaService implements Listener, CommandExecutor {

    private static final int PLATFORM_SIZE = 100;       // 100×100
    private static final int PLATFORM_SPACING = 250;    // プラットフォーム間の間隔
    private static final int PLATFORM_Y = 64;
    private static final int RESPAWN_DELAY_TICKS = 60;  // 3 秒

    private final Plugin plugin;
    private final Map<String, PartyFfaZone> zones = new ConcurrentHashMap<>();
    /** player UUID → zone id (FFA 参加中) */
    private final Map<UUID, String> playerZone = new ConcurrentHashMap<>();
    /** player UUID → リスポーンタスク */
    private final Map<UUID, BukkitTask> respawnTasks = new ConcurrentHashMap<>();

    private World ffaWorld;
    private int nextSlot;

    public PartyFfaService(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---- zone management ----

    /** Party Fight 開始時に呼ぶ: 専用 FFA ゾーンを確保して返す。 */
    public PartyFfaZone allocateZone(String matchId) {
        World world = ensureWorld();
        int slot = nextSlot++;
        int originX = slot * PLATFORM_SPACING;
        int originZ = 0;
        Location center = new Location(world, originX + PLATFORM_SIZE / 2.0,
                PLATFORM_Y + 1, originZ + PLATFORM_SIZE / 2.0);

        // プラットフォーム生成（非同期でブロックを置く）
        generatePlatform(world, originX, originZ);

        PartyFfaZone zone = new PartyFfaZone(matchId, center, world, originX, originZ);
        zones.put(matchId, zone);
        return zone;
    }

    /** Party Fight 終了時に呼ぶ: ゾーンを解放。 */
    public void releaseZone(String matchId) {
        PartyFfaZone zone = zones.remove(matchId);
        if (zone == null) return;
        // ゾーン内のプレイヤーを全退出
        for (UUID uid : zone.joinedPlayers()) {
            playerZone.remove(uid);
            cancelRespawn(uid);
        }
        zone.clear();
    }

    /** プレイヤーを FFA に参加させる（デス後ボタンから呼ばれる）。 */
    public void joinFfa(Player player, String matchId) {
        PartyFfaZone zone = zones.get(matchId);
        if (zone == null) {
            player.sendMessage(Component.text("FFA zone is no longer available.",
                    NamedTextColor.RED));
            return;
        }
        playerZone.put(player.getUniqueId(), matchId);
        zone.addPlayer(player.getUniqueId());

        // コンテント復元: ソード + 防具
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        player.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        player.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        player.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));

        player.teleport(zone.center());
        player.sendMessage(Component.text("⚔ Sword FFA に参加しました！キルで回復します。",
                NamedTextColor.GOLD));
    }

    // ---- death handling ----

    /** Party Fight 内デス時に呼ぶ。FFA ボタンを表示。 */
    public void onPartyFightDeath(Player dead, String matchId) {
        PartyFfaZone zone = zones.get(matchId);
        if (zone == null) return;

        dead.sendMessage(Component.text()
                .append(Component.text("☠ あなたは倒されました！ ", NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text("Sword FFAに参加する", NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand("/partyffa join " + matchId))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("クリックでFFAに参加", NamedTextColor.YELLOW))))
                .append(Component.text("]", NamedTextColor.GRAY))
                .build());
    }

    // ---- FFA mechanics (kill to heal + 3s respawn) ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFfaDeath(PlayerDeathEvent event) {
        Player dead = event.getPlayer();
        UUID uid = dead.getUniqueId();
        String matchId = playerZone.get(uid);
        if (matchId == null) return;

        // FFA 内のデス: ドロップ消去、デスメッセージ抑制
        event.getDrops().clear();
        event.setDeathMessage(null);

        // キラーを回復
        Player killer = dead.getKiller();
        if (killer != null && playerZone.containsKey(killer.getUniqueId())) {
            killer.setHealth(20.0);
            killer.setFoodLevel(20);
            killer.setSaturation(20f);
            killer.sendMessage(Component.text("♥ キルで回復！", NamedTextColor.GREEN));
        }

        // 3 秒後にリスポーン
        dead.setGameMode(GameMode.SPECTATOR);
        cancelRespawn(uid);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            respawnTasks.remove(uid);
            if (!playerZone.containsKey(uid)) return;
            Player p = Bukkit.getPlayer(uid);
            if (p == null || !p.isOnline()) return;
            PartyFfaZone zone = zones.get(matchId);
            if (zone == null) { leaveFfa(p); return; }

            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.getInventory().clear();
            p.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
            p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
            p.teleport(zone.center());
            p.sendMessage(Component.text("⚔ リスポーン！", NamedTextColor.YELLOW));
        }, RESPAWN_DELAY_TICKS);
        respawnTasks.put(uid, task);
    }

    /** FFA 退出 */
    public void leaveFfa(Player player) {
        UUID uid = player.getUniqueId();
        playerZone.remove(uid);
        cancelRespawn(uid);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        player.sendMessage(Component.text("FFA から退出しました。", NamedTextColor.GRAY));
    }

    /** プレイヤーが FFA 参加中かどうか */
    public boolean isInFfa(UUID playerId) {
        return playerZone.containsKey(playerId);
    }

    private void cancelRespawn(UUID uid) {
        BukkitTask t = respawnTasks.remove(uid);
        if (t != null) t.cancel();
    }

    // ---- world / platform generation ----

    private World ensureWorld() {
        if (ffaWorld != null) return ffaWorld;
        ffaWorld = Bukkit.getWorld("party-ffa");
        if (ffaWorld != null) return ffaWorld;
        WorldCreator wc = new WorldCreator("party-ffa");
        wc.environment(World.Environment.NORMAL);
        wc.type(org.bukkit.WorldType.FLAT);
        wc.generatorSettings("{\"layers\":[{\"block\":\"stone\",\"height\":1},{\"block\":\"air\",\"height\":1}],\"biome\":\"plains\"}");
        ffaWorld = wc.createWorld();
        if (ffaWorld != null) {
            ffaWorld.setKeepSpawnInMemory(false);
            ffaWorld.setAutoSave(false);
            ffaWorld.setDifficulty(org.bukkit.Difficulty.NORMAL);
            ffaWorld.setPVP(true);
            ffaWorld.setTime(6000); // noon
        }
        return ffaWorld;
    }

    private void generatePlatform(World world, int originX, int originZ) {
        // 100×100 の石ブロックプラットフォームを Y=63 に生成
        // バッチ処理: 20行ずつ同期タスクで処理して lag を分散
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            generatePlatformBatch(world, originX, originZ, 0);
        }, 1L);
    }

    private void generatePlatformBatch(World world, int originX, int originZ, int rowOffset) {
        int batchSize = 20; // 1 tick あたり 20 行
        for (int row = rowOffset; row < rowOffset + batchSize && row < PLATFORM_SIZE; row++) {
            int x = originX + row;
            for (int z = originZ; z < originZ + PLATFORM_SIZE; z++) {
                Block block = world.getBlockAt(x, PLATFORM_Y, z);
                block.setType(Material.STONE, false);
                // 境界は石レンガで視覚的に区切る
                if (row == 0 || row == PLATFORM_SIZE - 1
                        || z == originZ || z == originZ + PLATFORM_SIZE - 1) {
                    Block wall = world.getBlockAt(x, PLATFORM_Y + 1, z);
                    wall.setType(Material.STONE_BRICKS, false);
                }
            }
        }
        int next = rowOffset + batchSize;
        if (next < PLATFORM_SIZE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                generatePlatformBatch(world, originX, originZ, next);
            }, 1L);
        }
    }

    // ---- zone data ----

    public static final class PartyFfaZone {
        private final String matchId;
        private final Location center;
        private final World world;
        private final int originX, originZ;
        private final Set<UUID> players = ConcurrentHashMap.newKeySet();

        PartyFfaZone(String matchId, Location center, World world, int originX, int originZ) {
            this.matchId = matchId;
            this.center = center;
            this.world = world;
            this.originX = originX;
            this.originZ = originZ;
        }

        public String matchId() { return matchId; }
        public Location center() { return center.clone(); }
        public Set<UUID> joinedPlayers() { return players; }
        void addPlayer(UUID uid) { players.add(uid); }
        void clear() { players.clear(); }
    }

    /** Party Fight 終了時のクリーンアップ */
    public void onMatchEnd(String matchId) {
        // ゾーン内のプレイヤーを退出させてから解放
        PartyFfaZone zone = zones.get(matchId);
        if (zone != null) {
            for (UUID uid : Set.copyOf(zone.joinedPlayers())) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.isOnline()) {
                    leaveFfa(p);
                }
            }
        }
        releaseZone(matchId);
    }

    // ---- command ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 2 || !args[0].equalsIgnoreCase("join")) {
            player.sendMessage(Component.text("Usage: /partyffa join <matchId>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String matchId = args[1];
        if (!zones.containsKey(matchId)) {
            player.sendMessage(Component.text("FFA zone is no longer available.",
                    NamedTextColor.RED));
            return true;
        }
        joinFfa(player, matchId);
        return true;
    }
}