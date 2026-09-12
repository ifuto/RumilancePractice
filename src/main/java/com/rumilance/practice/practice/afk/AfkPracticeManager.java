package com.rumilance.practice.practice.afk;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AFK practice: a solo, never-ending duel against an armorless totem bot inside a
 * private void box with a netherite floor. Completely self-contained — no practice
 * session, no match context, so no other listener can interfere. The floor
 * netherite, its start button and grave beacons are the only protected blocks;
 * everything else (player-built cobwebs, water, lava, obsidian...) may be placed
 * and broken freely inside the box.
 *
 * <p>Losing to the bot triggers the burial ceremony: the floor under the corpse
 * briefly turns to lava, grave beacons accumulate with the death count, nearby
 * player structures are blown apart, a cool-coloured firework marks the respawn,
 * and the kit editor re-opens. Killing the bot showers flame and a wither-skeleton
 * roar, drops the bot's remaining totems, records a kill and respawns the bot a
 * second later. Any exchange of damage resets a 10-minute inactivity timer; if it
 * expires the player is judged dead exactly like a disconnect.</p>
 */
public final class AfkPracticeManager implements Listener, CommandExecutor {

    private static final int BOX_HALF = 11;           // 23x23 walkable cube (spec: limited area)
    private static final int BOX_HEIGHT = 38;
    private static final long FLOOR_REBUILD_MS = 24L * 60 * 60 * 1000; // ネザライト床の再生成は24時間毎
    private static final long INACTIVITY_LIMIT_MS = 10L * 60 * 1000;   // 10分ダメージ無し = 死亡判定
    private static final long BOT_RESPAWN_MS = 1_000L;
    private static final long BOT_TOTEM_RESTOCK_MS = 5_000L;
    private static final int BOT_TOTEM_MAX = 2;
    private static final int PICKUP_ITEM_COUNT = 12;   // メニューを開く度にランダム補充される拾得アイテム数

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messages;

    private final Map<UUID, AfkSession> sessions = new HashMap<>();
    private final Map<UUID, int[]> stats = new HashMap<>();           // uuid -> [kills, deaths]
    private final Map<UUID, ItemStack[]> savedKits = new HashMap<>(); // uuid -> 41-slot snapshot
    private ItemStack[] prototypeKit;                                 // admin-set default kit
    private final Deque<PendingRestore> restores = new ArrayDeque<>();
    private BukkitTask ticker;
    private java.util.function.Predicate<UUID> otherSessionGuard;
    private int boxSequence = 0;
    private World world;
    private File statsFile;
    private File kitsFile;

    public AfkPracticeManager(JavaPlugin plugin, ConfigService configService,
                              MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
    }

    public void start() {
        statsFile = new File(plugin.getDataFolder(), "afk-stats.yml");
        kitsFile = new File(plugin.getDataFolder(), "afk-kits.yml");
        loadStats();
        loadKits();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** True while the player owns a room here (used as the cross-room guard). */
    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** Set by the bootstrap so the AFK room and the AFK BOT Crystal room never overlap. */
    public void setOtherSessionGuard(java.util.function.Predicate<UUID> guard) {
        this.otherSessionGuard = guard;
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            endSession(id, p, false);
        }
        saveStats();
        saveKits();
    }

    // ---------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("players only");
            return true;
        }
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "start", "join" -> beginSession(player);
            case "stop", "leave", "quit" -> {
                if (endSession(player.getUniqueId(), player, true)) {
                    msg(player, "afk.stopped");
                } else {
                    msg(player, "afk.not-in-session");
                }
            }
            case "kit" -> {
                AfkSession s = sessions.get(player.getUniqueId());
                if (s == null) {
                    msg(player, "afk.not-in-session");
                } else {
                    openKitEditor(player, s);
                }
            }
            case "top" -> sendTop(player);
            default -> msg(player, "afk.usage");
        }
        return true;
    }

    // ---------------------------------------------------------------- session

    private void beginSession(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            msg(player, "afk.already");
            return;
        }
        if (otherSessionGuard != null && otherSessionGuard.test(player.getUniqueId())) {
            msg(player, "afk.crystal-busy");
            return;
        }
        if (world == null) {
            world = player.getWorld();
        }
        int index = boxSequence++;
        int spacing = 512;
        int baseX = configService.config().getInt("arena.placement-center-x", 0) + 40_000 + index * spacing;
        int baseZ = configService.config().getInt("arena.placement-center-z", 0) + 40_000;
        int floorY = world.getMinHeight() + 12;
        Location center = new Location(world, baseX + 0.5, floorY, baseZ + 0.5);

        AfkSession s = new AfkSession(player.getUniqueId(), center, index);
        sessions.put(player.getUniqueId(), s);
        buildBox(s);
        buildFloor(s, true);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.teleport(s.spawn());
        player.getInventory().clear();
        player.getInventory().setItem(8, guiButton());
        msg(player, "afk.welcome");
        openKitEditor(player, s);
    }

    /** Ends the session. When {@code toLobby} the player is sent back to the lobby. */
    private boolean endSession(UUID id, Player player, boolean toLobby) {
        AfkSession s = sessions.remove(id);
        if (s == null) {
            return false;
        }
        if (s.bot != null && s.bot.isValid()) {
            s.bot.remove();
        }
        if (player != null && player.isOnline()) {
            player.closeInventory();
            if (toLobby) {
                LobbyReturn.send(plugin, player);
            }
        }
        // Tear the whole box down (packet-mode rooms restore instantly; block mode
        // clears to air — the AFK box contains no player structures worth keeping).
        clearBox(s);
        return true;
    }

    // ---------------------------------------------------------------- arena

    private void buildBox(AfkSession s) {
        Location c = s.center;
        // Walls, ceiling and an under-floor shell made of indestructible barrier-like
        // bedrock so explosions / fire can never escape the cube.
        for (int x = -BOX_HALF - 1; x <= BOX_HALF + 1; x++) {
            for (int y = 0; y <= BOX_HEIGHT; y++) {
                for (int z = -BOX_HALF - 1; z <= BOX_HALF + 1; z++) {
                    boolean shell = Math.abs(x) == BOX_HALF + 1 || Math.abs(z) == BOX_HALF + 1
                            || y == 0 || y == BOX_HEIGHT;
                    if (shell) {
                        setBlock(c, x, y - 1, z, Material.BLACK_STAINED_GLASS);
                    } else {
                        setBlock(c, x, y - 1, z, Material.AIR);
                    }
                }
            }
        }
        s.bounds = new BoundingBox(c.getX() - BOX_HALF, c.getY(), c.getZ() - BOX_HALF,
                c.getX() + BOX_HALF + 1, c.getY() + BOX_HEIGHT - 1, c.getZ() + BOX_HALF + 1);
    }

    private void clearBox(AfkSession s) {
        Location c = s.center;
        for (int x = -BOX_HALF - 1; x <= BOX_HALF + 1; x++) {
            for (int y = -1; y <= BOX_HEIGHT - 1; y++) {
                for (int z = -BOX_HALF - 1; z <= BOX_HALF + 1; z++) {
                    setBlock(c, x, y, z, Material.AIR);
                }
            }
        }
    }

    /** The 9x9 netherite floor + red start button; also the 24h rebuild. */
    private void buildFloor(AfkSession s, boolean resetBeacons) {
        Location c = s.center;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                setBlock(c, x, 0, z, Material.NETHERITE_BLOCK);
            }
        }
        setBlock(c, 0, 1, 0, Material.CHERRY_BUTTON); // 床ネザライト上部のボタン
        if (resetBeacons) {
            s.beacons.clear();
        } else {
            for (Block b : s.beacons) {
                b.setType(Material.AIR);
            }
            s.beacons.clear();
        }
        // Cosmetic netherite-ore pool embedded around the floor rim.
        int pool = configService.config().getInt("afk.netherite-ore-pool", 6);
        for (int i = 0; i < pool; i++) {
            int x = ThreadLocalRandom.current().nextInt(-BOX_HALF, BOX_HALF + 1);
            int z = ThreadLocalRandom.current().nextInt(-BOX_HALF, BOX_HALF + 1);
            if (Math.abs(x) <= 4 && Math.abs(z) <= 4) {
                continue;
            }
            setBlock(c, x, 0, z, Material.ANCIENT_DEBRIS);
        }
        s.floorBuiltAt = System.currentTimeMillis();
    }

    private void setBlock(Location center, int dx, int dy, int dz, Material type) {
        Block b = center.clone().add(dx, dy, dz).getBlock();
        if (b.getType() != type) {
            b.setType(type, false);
        }
    }

    // ---------------------------------------------------------------- ticking

    private void tick() {
        runRestores();
        long now = System.currentTimeMillis();
        for (AfkSession s : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(s.playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            tickBoundaries(player, s);
            tickFloorRebuild(s, now);
            tickInactivity(player, s, now);
            tickBot(player, s, now);
        }
    }

    private void tickBoundaries(Player player, AfkSession s) {
        // Escape guard: leaving (or falling past) the cube snaps the player back in.
        if (player.isDead()) {
            return;
        }
        Location loc = player.getLocation();
        if (!s.bounds.contains(loc.toVector()) || loc.getY() < s.center.getY() - 0.5) {
            player.setFallDistance(0f);
            player.teleport(s.spawn());
            msg(player, "afk.boundary");
        }
    }

    private void tickFloorRebuild(AfkSession s, long now) {
        if (now - s.floorBuiltAt >= FLOOR_REBUILD_MS) {
            buildFloor(s, false);
        }
    }

    private void tickInactivity(Player player, AfkSession s, long now) {
        if (s.bot == null || !s.bot.isValid() || player.isDead()) {
            return;
        }
        if (now - s.lastDamageExchangeMs >= INACTIVITY_LIMIT_MS) {
            msg(player, "afk.inactive-death");
            s.lastDamageExchangeMs = now;
            killPlayerWithCeremony(player, s, false);
        }
    }

    private void tickBot(Player player, AfkSession s, long now) {
        Mannequin bot = s.bot;
        if (bot == null || !bot.isValid() || player.isDead()) {
            return;
        }
        // Totem restock: back up to 2 held totems at most every 5 seconds.
        if (now - s.lastTotemRestockMs >= BOT_TOTEM_RESTOCK_MS && s.botTotems < BOT_TOTEM_MAX) {
            s.botTotems++;
            s.lastTotemRestockMs = now;
            equipBotHands(bot, s.botTotems > 0);
        }
        // Slow regen 5+ seconds after the last hit.
        if (now - s.lastBotHurtMs >= 5_000L && bot.getHealth() < bot.getMaxHealth()) {
            bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + 0.5d));
        }
        // Chase + melee (crystal-bot style pursuit, armorless).
        if (now - s.lastBotMoveMs >= 100L) {
            s.lastBotMoveMs = now;
            Location eye = bot.getEyeLocation();
            Vector to = player.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist > 0.001) {
                Vector dir = to.clone().normalize();
                bot.setRotation((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())),
                        (float) Math.toDegrees(Math.asin(-dir.getY())));
                if (dist > 1.6d) {
                    Vector v = dir.multiply(0.32d);
                    v.setY(bot.getVelocity().getY());
                    bot.setVelocity(v);
                }
                if (dist < 2.6d && now - s.lastBotSwingMs >= 650L) {
                    s.lastBotSwingMs = now;
                    bot.swingMainHand();
                    player.damage(6.0d, bot); // armorless but hits like a crystal setup
                    s.lastDamageExchangeMs = now;
                }
            }
        }
    }

    // ---------------------------------------------------------------- bot

    private void spawnBot(Player player, AfkSession s) {
        despawnBot(s);
        Location at = s.center.clone().add(ThreadLocalRandom.current().nextInt(-3, 4),
                1, ThreadLocalRandom.current().nextInt(-3, 4));
        World world = at.getWorld();
        if (world == null) {
            msg(player, "afk.bot-failed");
            return;
        }
        Mannequin bot = world.spawn(at, Mannequin.class, m -> {
            m.setImmovable(false);
            m.setGravity(true);
            m.setSilent(true);
            m.setCanPickupItems(false);
            m.setRemoveWhenFarAway(false);
            m.setCollidable(true);
            m.setPersistent(false);
        });
        try {
            bot.setCustomNameVisible(true);
            bot.customName(LEGACY.deserialize("§cAFKシミュレーション訓練BOT"));
            bot.setGlowing(true);
            bot.setGravity(false);
            bot.setHealth(bot.getMaxHealth());
            equipBotHands(bot, true);
        } catch (Throwable ignored) {
        }
        s.bot = bot;
        s.botTotems = BOT_TOTEM_MAX;
        s.lastBotRespawnMs = System.currentTimeMillis();
        s.lastDamageExchangeMs = s.lastBotRespawnMs;
    }

    private void despawnBot(AfkSession s) {
        if (s.bot != null && s.bot.isValid()) {
            s.bot.remove();
        }
        s.bot = null;
    }

    private void equipBotHands(Mannequin bot, boolean totem) {
        try {
            bot.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            bot.getEquipment().setItemInOffHand(totem ? new ItemStack(Material.TOTEM_OF_UNDYING) : null);
        } catch (Throwable ignored) {
        }
    }

    private boolean isAfkBot(AfkSession s, UUID entityId) {
        return s.bot != null && s.bot.getUniqueId().equals(entityId);
    }

    /** Central damage handler for hits landing on the AFK bot. */
    private void onBotDamaged(Player player, AfkSession s, EntityDamageEvent event) {
        Mannequin bot = s.bot;
        if (bot == null) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        // Fire / lava / void on the bot: ignore entirely — only the player's weapons count.
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.PROJECTILE
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true); // beds/anchors the bot didn't trigger can't snipe it
            return;
        }
        s.lastDamageExchangeMs = System.currentTimeMillis();
        s.lastBotHurtMs = s.lastDamageExchangeMs;
        // ワンショット防止: メイスの一撃や爆発の大ダメージは 1.0 に正規化する
        boolean heavy = cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || isMaceHit(event);
        if (heavy && event.getDamage() > 1.0d) {
            event.setDamage(Math.min(event.getDamage(), 1.0d));
        }
        double after = bot.getHealth() - event.getFinalDamage();
        if (after > 0.5d) {
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            return;
        }
        event.setCancelled(true);
        if (s.botTotems > 0) {
            // Totem pop: the bot survives on 1 heart, consumes one totem.
            s.botTotems--;
            bot.setHealth(Math.min(bot.getMaxHealth(), 4.0d));
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            if (bot.getWorld() != null) {
                bot.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                        bot.getLocation().add(0, 1, 0), 80, 0.5, 1.0, 0.5, 0.4);
            }
            equipBotHands(bot, s.botTotems > 0);
            return;
        }
        // Real kill: phantom celebration, totem drop, stats, respawn in 1 second.
        killBotCeremony(player, s);
    }

    private boolean isMaceHit(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            return hand != null && hand.getType() == Material.MACE
                    && p.getFallDistance() > 1.5f;
        }
        return false;
    }

    /** Bot died to the player: fire ring + wither-skeleton roar, drop totems, statskit. */
    private void killBotCeremony(Player player, AfkSession s) {
        Mannequin bot = s.bot;
        Location at = bot != null ? bot.getLocation() : player.getLocation();
        World w = at.getWorld();
        int kills = addKill(player.getUniqueId());
        // The bot's remaining totems drop as loot — grabbing them does NOT restock the
        // player's kit: the next editor open rebuilds the inventory from the saved kit,
        // so picked-up totems vanish (kit外トーテムは0化).
        if (w != null) {
            w.dropItemNaturally(at, new ItemStack(Material.TOTEM_OF_UNDYING, 2));
            for (int i = 0; i < 3; i++) {
                w.playSound(at, Sound.ENTITY_WITHER_SKELETON_DEATH, 1.0f, 0.7f + i * 0.15f);
            }
            w.spawnParticle(Particle.FLAME, at.clone().add(0, 0.5, 0), 120, 1.2, 0.4, 1.2, 0.02);
        }
        // Ring of temporary fire on the floor around the corpse (火がつくブロック).
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0d;
            Location f = at.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
            f.setY(s.center.getY() + 1);
            Block b = f.getBlock();
            if (b.getType() == Material.AIR) {
                b.setType(Material.FIRE, false);
                restores.add(new PendingRestore(b, Material.AIR, System.currentTimeMillis() + 2_000L));
            }
        }
        firework(player.getLocation(), true); // win colours for the killer
        msg(player, "afk.bot-killed", msgTags("kills", String.valueOf(kills)));
        despawnBot(s);
        s.pendingBotRespawnAt = System.currentTimeMillis() + BOT_RESPAWN_MS;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(s.playerId);
            if (sessions.containsKey(s.playerId) && p != null && p.isOnline()) {
                spawnBot(p, s);
            }
        }, BOT_RESPAWN_MS / 50L + 1);
    }

    // ---------------------------------------------------------------- player death

    /** Burial ceremony + death record; the fight simply continues afterwards. */
    private void killPlayerWithCeremony(Player player, AfkSession s, boolean realDeath) {
        int deaths = addDeath(player.getUniqueId());
        Location grave = player.getLocation().clone();
        World w = grave.getWorld();
        if (w != null) {
            // 1) 地面の破壊: blast apart player structures around the grave (never the floor).
            for (int x = -4; x <= 4; x++) {
                for (int y = 0; y <= 5; y++) {
                    for (int z = -4; z <= 4; z++) {
                        Block b = grave.clone().add(x, y, z).getBlock();
                        Material t = b.getType();
                        if (t.isAir() || t == Material.NETHERITE_BLOCK || t == Material.CHERRY_BUTTON
                                || t == Material.BEACON || t == Material.BLACK_STAINED_GLASS
                                || t == Material.ANCIENT_DEBRIS) {
                            continue;
                        }
                        b.setType(Material.AIR, false);
                    }
                }
            }
            // 2) 床下溶岩: netherite cell under the grave turns to lava for 3 seconds.
            int gx = clampCell((int) Math.floor(grave.getX() - s.center.getX()));
            int gz = clampCell((int) Math.floor(grave.getZ() - s.center.getZ()));
            Block lava = s.center.clone().add(gx, 0, gz).getBlock();
            if (lava.getType() == Material.NETHERITE_BLOCK) {
                lava.setType(Material.LAVA, false);
                restores.add(new PendingRestore(lava, Material.NETHERITE_BLOCK,
                        System.currentTimeMillis() + 3_000L));
            }
            // 3) ビーコン墓碑: one more beacon grave marker per death (死亡数に応じて多数).
            int beaconCount = Math.min(deaths, 12);
            while (s.beacons.size() < beaconCount) {
                int bx = ThreadLocalRandom.current().nextInt(-4, 5);
                int bz = ThreadLocalRandom.current().nextInt(-4, 5);
                if (bx == 0 && bz == 0) {
                    continue; // never cover the start button
                }
                Block cell = s.center.clone().add(bx, 0, bz).getBlock();
                if (cell.getType() != Material.NETHERITE_BLOCK) {
                    continue;
                }
                Block marker = s.center.clone().add(bx, 1, bz).getBlock();
                if (marker.getType() != Material.AIR) {
                    continue;
                }
                marker.setType(Material.BEACON, false);
                s.beacons.add(marker);
            }
            w.playSound(grave, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.5f);
            w.playSound(grave, Sound.BLOCK_LAVA_POP, 1.0f, 0.6f);
        }
        msg(player, "afk.you-died", msgTags("deaths", String.valueOf(deaths)));
        if (realDeath) {
            s.pendingKitReopen = true;
        } else {
            // Inactivity judgement: execute on the spot, ceremony included.
            player.damage(10_000.0d, s.bot != null && s.bot.isValid() ? s.bot : null);
            s.pendingKitReopen = true;
        }
    }

    private int clampCell(int v) {
        return Math.max(-4, Math.min(4, v));
    }

    private void runRestores() {
        long now = System.currentTimeMillis();
        while (!restores.isEmpty() && restores.peekFirst().atMs <= now) {
            PendingRestore r = restores.pollFirst();
            if (r.block.getType() != r.restoreTo) {
                r.block.setType(r.restoreTo, false);
            }
        }
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin)) {
            return;
        }
        UUID id = event.getEntity().getUniqueId();
        for (AfkSession s : sessions.values()) {
            if (!isAfkBot(s, id)) {
                continue;
            }
            Player owner = Bukkit.getPlayer(s.playerId);
            if (owner == null) {
                return;
            }
            if (event instanceof EntityDamageByEntityEvent byEntity
                    && byEntity.getDamager() instanceof Player p
                    && !p.getUniqueId().equals(s.playerId)) {
                event.setCancelled(true); // outsiders may never touch someone else's bot
                return;
            }
            onBotDamaged(owner, s, event);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null || player.isDead()) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            player.teleport(s.spawn());
            return;
        }
        if (event.getFinalDamage() < player.getHealth()) {
            s.lastDamageExchangeMs = System.currentTimeMillis();
            return;
        }
        // Would kill: vanilla death flows through PlayerDeathEvent below.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.deathMessage(null);
        killPlayerWithCeremony(player, s, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        AfkSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        event.setRespawnLocation(s.spawn());
        Player player = event.getPlayer();
        // 敗北の花火 (青白のロス花火) + 3秒後にキットメニューを自動で開き直す
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!sessions.containsKey(player.getUniqueId()) || !player.isOnline()) {
                return;
            }
            firework(player.getLocation(), false);
            player.getInventory().setItem(8, guiButton());
            if (s.pendingKitReopen) {
                s.pendingKitReopen = false;
                openKitEditor(player, s);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        Block b = event.getClickedBlock();
        ItemStack item = event.getItem();
        boolean buttonBlock = b != null && b.getType() == Material.CHERRY_BUTTON
                && isProtected(s, b);
        boolean buttonItem = item != null && item.getType() == Material.STONE_BUTTON
                && item.hasItemMeta() && item.getItemMeta().hasCustomModelData()
                && item.getItemMeta().getCustomModelData() == 991001;
        if ((buttonBlock || buttonItem)
                && (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR)) {
            event.setCancelled(true);
            openKitEditor(player, s);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) {
            event.setCancelled(true); // food never runs out inside AFK practice
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        endSession(event.getPlayer().getUniqueId(), null, false);
    }

    // ---------------------------------------------------------------- blocks

    private boolean inside(AfkSession s, Block b) {
        return b.getWorld() == s.center.getWorld() && s.bounds.contains(
                b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
    }

    /** Floor netherite, the button and grave beacons stay untouchable. */
    private boolean isProtected(AfkSession s, Block b) {
        Material t = b.getType();
        if (t == Material.CHERRY_BUTTON || t == Material.BEACON
                || t == Material.BLACK_STAINED_GLASS || t == Material.ANCIENT_DEBRIS) {
            return true;
        }
        return t == Material.NETHERITE_BLOCK && b.getY() == (int) s.center.getY();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        AfkSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        if (!inside(s, event.getBlock()) || isProtected(s, event.getBlock())) {
            event.setCancelled(true);
            msg(event.getPlayer(), "afk.protected");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        AfkSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        if (!inside(s, event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        // Everything inside the cube may be built — except over the button cell and onto
        // grave beacons, and never replacing the protected shell.
        Block b = event.getBlock();
        if (isProtected(s, b) || b.getType() == Material.BEACON || b.getType() == Material.CHERRY_BUTTON) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- kit editor

    private static final int EDITOR_ROWS = 6;
    private static final int SLOT_SAVE = 4;
    private static final int SLOT_INFO = 0;
    private static final int KIT_AREA_START = 18; // rows 3-5 of the chest hold the kit items
    private static final int[] BLOCK_GIVE_SLOTS = {9, 10, 11, 12, 13};

    private ItemStack guiButton() {
        ItemStack it = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEGACY.deserialize("§eAFK練習キットメニュー §7(クリック)"));
        meta.setCustomModelData(991001);
        meta.setLore(List.of(LEGACY.serialize(LEGACY.deserialize(
                "§7クリックでキットメニューを開き、保存した装備を再装備します"))));
        it.setItemMeta(meta);
        return it;
    }

    /** Opens the kit editor: save / info / web-water-lava-obsidian-XP quick-gives / admin prototype. */
    private void openKitEditor(Player player, AfkSession s) {
        // Re-equipping ALWAYS rebuilds the inventory from the saved kit first — this is
        // what zeroes any totems looted off the bot (kit外トーテム0ルール).
        equipSavedKit(player);
        Inventory inv = Bukkit.createInventory(new AfkEditorHolder(), EDITOR_ROWS * 9,
                LEGACY.deserialize("§8AFK練習キット編集"));
        inv.setItem(SLOT_INFO, infoItem());
        inv.setItem(1, guiButton());
        inv.setItem(SLOT_SAVE, saveItem());
        inv.setItem(7, prototypeLoadItem());
        if (player.hasPermission("rumilance.admin")) {
            inv.setItem(8, prototypeSaveItem());
        }
        ItemStack[] blocks = {
                new ItemStack(Material.COBWEB),
                new ItemStack(Material.WATER_BUCKET),
                new ItemStack(Material.LAVA_BUCKET),
                new ItemStack(Material.OBSIDIAN),
                new ItemStack(Material.EXPERIENCE_BOTTLE)};
        String[] names = {"§f糸 (設置可)", "§b水バケツ", "§c溶岩バケツ", "§5黒曜石", "§aエンチャントの瓶"};
        for (int i = 0; i < BLOCK_GIVE_SLOTS.length; i++) {
            ItemStack it = blocks[i].clone();
            ItemMeta meta = it.getItemMeta();
            meta.displayName(LEGACY.deserialize(names[i]));
            meta.setLore(List.of(LEGACY.serialize(LEGACY.deserialize("§7クリックで1つ入手"))));
            it.setItemMeta(meta);
            inv.setItem(BLOCK_GIVE_SLOTS[i], it);
        }
        // Current kit content mirrored into the chest for drag-free editing.
        ItemStack[] snap = snapshotOf(player);
        for (int i = 0; i < 27; i++) {
            inv.setItem(KIT_AREA_START + i, i < snap.length ? snap[i] : null);
        }
        player.openInventory(inv);
        s.editing = true;
    }

    private ItemStack named(Material mat, String name, String lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.setLore(List.of(LEGACY.serialize(LEGACY.deserialize(lore))));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack infoItem() {
        return named(Material.PAPER, "§eAFK練習ルール",
                "§7床は9x9ネザライト/中央ボタンで開始・再装備");
    }

    private ItemStack saveItem() {
        ItemStack it = named(Material.EMERALD_BLOCK, "§a§l保存して装備", "§7キットを保存し BOT と再開");
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(991002);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack prototypeSaveItem() {
        ItemStack it = named(Material.NETHER_STAR, "§d§lプロトタイプ保存 (管理)",
                "§7この内容をサーバー既定キットとして保存");
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(991003);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack prototypeLoadItem() {
        ItemStack it = named(Material.BEACON, "§b既定キットを読み込む",
                "§7サーバーのプロトタイプキットに置き換え");
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(991004);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AfkEditorHolder)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            event.setCancelled(true);
            return;
        }
        boolean top = event.getClickedInventory() != null
                && event.getClickedInventory().getType() == InventoryType.CHEST
                && event.getRawSlot() < EDITOR_ROWS * 9;
        if (!top) {
            return; // bottom (player inventory) moves freely
        }
        int slot = event.getRawSlot();
        event.setCancelled(true);
        if (slot == SLOT_SAVE) {
            persistKitFromView(player, event.getInventory());
            player.closeInventory();
            return;
        }
        if (slot == 7 && prototypeKit != null) {
            applySnapshot(player, prototypeKit);
            openKitEditor(player, s);
            msg(player, "afk.prototype-loaded");
            return;
        }
        if (slot == 8 && player.hasPermission("rumilance.admin")) {
            prototypeKit = snapshotOf(player);
            saveKits();
            msg(player, "afk.prototype-saved");
            return;
        }
        for (int idx = 0; idx < BLOCK_GIVE_SLOTS.length; idx++) {
            if (slot == BLOCK_GIVE_SLOTS[idx]) {
                ItemStack give = event.getInventory().getItem(slot);
                if (give != null) {
                    ItemStack one = give.clone();
                    one.setAmount(give.getType() == Material.EXPERIENCE_BOTTLE ? 8 : 1);
                    one.setItemMeta(null);
                    player.getInventory().addItem(one);
                }
                return;
            }
        }
        // Kit area slots (18..44) behave like a normal chest.
        if (slot >= KIT_AREA_START && slot < KIT_AREA_START + 27) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEditorClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AfkEditorHolder)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.editing) {
            return;
        }
        s.editing = false;
        persistKitFromView(player, event.getInventory());
        // Give the 12 random pickup items, then (re)spawn the opponent.
        giveRandomPickups(player);
        spawnBot(player, s);
    }

    /** Save the editor chest area + live player inventory as the player's AFK kit. */
    private void persistKitFromView(Player player, Inventory editor) {
        ItemStack[] snap = snapshotOf(player);
        savedKits.put(player.getUniqueId(), snap);
        saveKits();
        // Chest-area items are dropped back into the player inventory before re-equip.
        for (int i = KIT_AREA_START; i < KIT_AREA_START + 27; i++) {
            ItemStack it = editor.getItem(i);
            if (it != null) {
                player.getInventory().addItem(it);
            }
            editor.setItem(i, null);
        }
        equipSavedKit(player);
        msg(player, "afk.kit-saved");
    }

    /** Full clear then apply the saved kit (this is the kit外トーテム0 enforcement). */
    private void equipSavedKit(Player player) {
        ItemStack[] snap = savedKits.get(player.getUniqueId());
        if (snap == null) {
            snap = prototypeKit != null ? prototypeKit : defaultKit();
        }
        applySnapshot(player, snap);
        player.getInventory().setItem(8, guiButton());
    }

    private void applySnapshot(Player player, ItemStack[] snap) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        for (int i = 0; i < Math.min(36, snap.length); i++) {
            inv.setItem(i, snap[i]);
        }
        if (snap.length >= 41) {
            inv.setArmorContents(new ItemStack[]{snap[36] == null ? null : snap[36].clone(),
                    snap[37], snap[38], snap[39]});
            inv.setItemInOffHand(snap[40]);
        }
        inv.setItem(8, guiButton());
    }

    private ItemStack[] snapshotOf(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] snap = new ItemStack[41];
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            snap[i] = it == null ? null : it.clone();
        }
        snap[8] = null; // hotbar slot 8 is reserved for the menu button
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < 4; i++) {
            snap[36 + i] = armor[i] == null ? null : armor[i].clone();
        }
        snap[40] = inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone();
        return snap;
    }

    private ItemStack[] defaultKit() {
        ItemStack[] kit = new ItemStack[41];
        kit[0] = new ItemStack(Material.NETHERITE_SWORD);
        kit[1] = new ItemStack(Material.END_CRYSTAL, 64);
        kit[2] = new ItemStack(Material.OBSIDIAN, 64);
        kit[3] = new ItemStack(Material.TOTEM_OF_UNDYING, 8);
        kit[4] = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8);
        kit[5] = new ItemStack(Material.ENDER_PEARL, 8);
        kit[6] = new ItemStack(Material.EXPERIENCE_BOTTLE, 32);
        kit[36] = new ItemStack(Material.NETHERITE_BOOTS);
        kit[37] = new ItemStack(Material.NETHERITE_LEGGINGS);
        kit[38] = new ItemStack(Material.NETHERITE_CHESTPLATE);
        kit[39] = new ItemStack(Material.NETHERITE_HELMET);
        kit[40] = new ItemStack(Material.TOTEM_OF_UNDYING);
        return kit;
    }

    /** 12 random pickup stacks dropped into free slots every time the menu (re)opens. */
    private void giveRandomPickups(Player player) {
        ItemStack[] pool = {
                new ItemStack(Material.END_CRYSTAL, 4),
                new ItemStack(Material.TOTEM_OF_UNDYING, 1),
                new ItemStack(Material.OBSIDIAN, 8),
                new ItemStack(Material.RESPAWN_ANCHOR, 1),
                new ItemStack(Material.GLOWSTONE, 4),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
                new ItemStack(Material.ENDER_PEARL, 2),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 8),
                new ItemStack(Material.COBWEB, 2),
                new ItemStack(Material.WATER_BUCKET, 1),
                new ItemStack(Material.LAVA_BUCKET, 1),
                new ItemStack(Material.WIND_CHARGE, 4),
                new ItemStack(Material.NETHERITE_AXE, 1),
                new ItemStack(Material.SHIELD, 1)};
        List<ItemStack> bag = new ArrayList<>(List.of(pool));
        java.util.Collections.shuffle(bag);
        for (int i = 0; i < Math.min(PICKUP_ITEM_COUNT, bag.size()); i++) {
            player.getInventory().addItem(bag.get(i).clone());
        }
    }

    private static final class AfkEditorHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    // ---------------------------------------------------------------- fireworks/stats

    private void firework(Location at, boolean win) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        Firework fw = (Firework) w.spawnEntity(at.clone().add(0, 1, 0), EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect.Builder effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(win ? Color.LIME : Color.AQUA)
                .withColor(win ? Color.YELLOW : Color.WHITE)
                .withFade(win ? Color.ORANGE : Color.BLUE)
                .flicker(true).trail(true);
        meta.addEffect(effect.build());
        meta.setPower(win ? 1 : 2); // 敗北ロス花火は大きめに
        fw.setFireworkMeta(meta);
    }

    private int addKill(UUID id) {
        int[] row = stats.computeIfAbsent(id, k -> new int[2]);
        row[0]++;
        saveStats();
        return row[0];
    }

    private int addDeath(UUID id) {
        int[] row = stats.computeIfAbsent(id, k -> new int[2]);
        row[1]++;
        saveStats();
        return row[1];
    }

    private void sendTop(Player player) {
        msg(player, "afk.top-header");
        stats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .forEach(e -> {
                    String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    msg(player, "afk.top-row", msgTags(
                            "player", name == null ? "?" : name,
                            "kills", String.valueOf(e.getValue()[0]),
                            "deaths", String.valueOf(e.getValue()[1])));
                });
        int[] own = stats.get(player.getUniqueId());
        msg(player, "afk.top-self", msgTags(
                "kills", String.valueOf(own == null ? 0 : own[0]),
                "deaths", String.valueOf(own == null ? 0 : own[1])));
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            return;
        }
        FileConfiguration yml = YamlConfiguration.loadConfiguration(statsFile);
        for (String key : yml.getKeys(false)) {
            try {
                stats.put(UUID.fromString(key),
                        new int[]{yml.getInt(key + ".kills"), yml.getInt(key + ".deaths")});
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveStats() {
        FileConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, int[]> e : stats.entrySet()) {
            yml.set(e.getKey() + ".kills", e.getValue()[0]);
            yml.set(e.getKey() + ".deaths", e.getValue()[1]);
        }
        try {
            yml.save(statsFile);
        } catch (IOException ignored) {
        }
    }

    private void loadKits() {
        if (!kitsFile.exists()) {
            return;
        }
        FileConfiguration yml = YamlConfiguration.loadConfiguration(kitsFile);
        for (String key : yml.getKeys(false)) {
            try {
                List<?> raw = yml.getList(key + ".items");
                if (raw == null) {
                    continue;
                }
                ItemStack[] snap = new ItemStack[41];
                for (int i = 0; i < Math.min(41, raw.size()); i++) {
                    if (raw.get(i) instanceof ItemStack it) {
                        snap[i] = it;
                    }
                }
                if ("prototype".equals(key)) {
                    prototypeKit = snap;
                } else {
                    savedKits.put(UUID.fromString(key), snap);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveKits() {
        FileConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack[]> e : savedKits.entrySet()) {
            yml.set(e.getKey().toString() + ".items", java.util.Arrays.asList(e.getValue()));
        }
        if (prototypeKit != null) {
            yml.set("prototype.items", java.util.Arrays.asList(prototypeKit));
        }
        try {
            yml.save(kitsFile);
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- misc

    private void msg(Player player, String key) {
        msg(player, key, Map.of());
    }

    private void msg(Player player, String key, Map<String, String> tags) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> e : tags.entrySet()) {
            resolvers.add(Placeholder.unparsed(e.getKey(), e.getValue()));
        }
        player.sendMessage(messages.render(player, key,
                resolvers.toArray(new TagResolver[0])));
    }

    private Map<String, String> msgTags(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private record PendingRestore(Block block, Material restoreTo, long atMs) {
    }

    private static final class AfkSession {
        final UUID playerId;
        final Location center;
        final int index;
        BoundingBox bounds;
        Mannequin bot;
        int botTotems;
        long lastDamageExchangeMs = System.currentTimeMillis();
        long lastBotHurtMs;
        long lastBotMoveMs;
        long lastBotSwingMs;
        long lastBotRespawnMs;
        long lastTotemRestockMs;
        long pendingBotRespawnAt;
        long floorBuiltAt;
        boolean editing;
        boolean pendingKitReopen;
        final Set<Block> beacons = new HashSet<>();

        AfkSession(UUID playerId, Location center, int index) {
            this.playerId = playerId;
            this.center = center;
            this.index = index;
            this.floorBuiltAt = System.currentTimeMillis();
        }

        Location spawn() {
            return center.clone().add(0, 2.0, 0);
        }
    }

    /** Lobby return without depending on LobbyService's API surface. */
    private static final class LobbyReturn {
        static void send(JavaPlugin plugin, Player player) {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w != null) {
                player.teleport(w.getSpawnLocation());
            }
            player.setGameMode(GameMode.SURVIVAL);
        }
    }
}
