package com.rumilance.practice.practice.afk;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitDefinition;
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
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * AFK BOT Crystal: a second AFK-practice room built around crystal PvP. The room is a
 * private 100x100 netherite platform generated in the void, with a cherry button on the
 * player spawn centre that re-fills the whole kit and snaps the bot (position, health,
 * effects, shield) back to its initial state. The bot wears an unbreakable netherite
 * set — protection 4 helmet/chest, blast-protection 4 (max) leggings/boots, fire
 * protection on every piece, plus an infinite Fire Resistance effect — and it carries
 * totems with an instant restock, so it can never actually die: health drops, a totem
 * pops, and the fight continues. A per-player setting toggles whether the bot raises a
 * shield in the offhand (blocking frontal melee/projectiles/explosions like vanilla)
 * and how fast a shield broken by an axe comes back: 2s or the vanilla-default 5s.
 *
 * <p>Everything inside the arena may be placed and broken freely — only the netherite
 * floor and the reset button are protected, and explosion block damage never removes
 * them. Completely self-contained (no practice session, no match context) so no other
 * listener can interfere; guarded against running alongside the regular
 * {@link AfkPracticeManager} room.</p>
 */
public final class AfkCrystalManager implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {

    private static final int FLOOR_RADIUS = 50;          // 100x100 netherite floor (spec)
    private static final int BOUNDARY_HEIGHT = 40;       // build/escape cap above the floor
    private static final int BOT_HOME_OFFSET_Z = -8;     // bot spawn: 8 blocks north of centre
    private static final double BOT_MAX_HEALTH = 20.0d;  // vanilla HP at every difficulty
    private static final double BOT_HOME_MAX_DIST = 10.0d;   // statue leash from its home
    private static final long BOT_AIRBORNE_GRACE_MS = 1_200L; // knock arcs settle inside this
    private static final double TOTEM_POP_KB = 0.4d;         // vanilla melee knockback on the pop hit
    private static final double TOTEM_POP_KB_Y = 0.36d;      // vanilla melee knockback Y
    private static final double WIND_BURST_KB = 1.6d;        // wind-charge style shove on the pop hit
    private static final int PLAYER_SHIELD_DISABLE_TICKS = 100; // vanilla axe disable (5s)
    private static final int SHIELD_RETURN_DEFAULT_S = 5;       // vanilla default
    private static final int SHIELD_RETURN_ALT_S = 2;           // practice option
    private static final int MAX_BOUND_KITS = 5;                // admin-bound kit slots (spec)

    private static final int EDITOR_ROWS = 6;
    private static final int SLOT_INFO = 0;
    private static final int SLOT_SAVE = 4;
    private static final int SLOT_SETTINGS = 5;
    private static final int SLOT_PROTOTYPE_LOAD = 7;
    private static final int SLOT_PROTOTYPE_SAVE = 8;
    private static final int KIT_AREA_START = 18;        // rows 3-5 hold the kit items
    private static final int[] GIVE_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private static final int CMD_SAVE_ITEM = 992002;
    private static final int CMD_PROTOTYPE_SAVE = 992003;
    private static final int CMD_PROTOTYPE_LOAD = 992004;
    private static final int CMD_SETTINGS_TILE = 992005; // editor settings tile only

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final MessageService messages;

    private final Map<UUID, AfkSession> sessions = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedKits = new HashMap<>();   // uuid -> 41-slot snapshot
    private final Map<UUID, Boolean> shieldPrefs = new HashMap<>();     // uuid -> offhand shield
    private final Map<UUID, Integer> shieldDelayPrefs = new HashMap<>();// uuid -> return seconds
    private final Map<UUID, Location> arenaAnchors = new HashMap<>();   // uuid -> private arena centre (全員別々)
    private ItemStack[] prototypeKit;                                   // admin-set default kit
    private KitService kitService;                                      // match-style kit application
    private java.util.function.BiConsumer<Player, String> lobbySender;  // real lobby return for /hub
    private final List<String> boundKits = new ArrayList<>();           // admin-bound kits (max 5)
    private final Map<UUID, String> kitSelections = new HashMap<>();    // uuid -> selected bound kit
    private Predicate<UUID> otherSessionGuard;                          // cross-room guard
    private BukkitTask ticker;
    private int boxSequence = 0;
    private World world;
    private File kitsFile;
    private File settingsFile;

    public AfkCrystalManager(JavaPlugin plugin, ConfigService configService,
                             MessageService messages) {
        this.plugin = plugin;
        this.configService = configService;
        this.messages = messages;
    }

    public void start() {
        kitsFile = new File(plugin.getDataFolder(), "afk-crystal-kits.yml");
        settingsFile = new File(plugin.getDataFolder(), "afk-crystal-settings.yml");
        loadKits();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 30L, 2L);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            endSession(id, p, false);
        }
        saveKits();
        saveSettings();
    }

    /** True while the player owns a crystal room; the bootstrap wires the cross-guard. */
    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /** Set by the bootstrap so the two AFK rooms can never run at the same time. */
    public void setOtherSessionGuard(Predicate<UUID> guard) {
        this.otherSessionGuard = guard;
    }

    /** Wired by the bootstrap: applies bound kits exactly like real matches do. */
    public void setKitService(KitService kitService) {
        this.kitService = kitService;
    }

    /** Wired by the bootstrap: LobbyService's real lobby return (used by /hub / /lobby). */
    public void setLobbySender(java.util.function.BiConsumer<Player, String> sender) {
        this.lobbySender = sender;
    }

    // ---------------------------------------------------------------- command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("players only");
            return true;
        }
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start", "join" -> beginSession(player);
            // Leaving ALWAYS returns to the lobby (now a real LobbyService send), so
            // /afkc stop — and /hub, /lobby, /spawn which route through the same manager —
            // can actually interrupt the session (中断できない問題の修正).
            case "stop", "leave", "quit" -> {
                if (endSession(player.getUniqueId(), player, true)) {
                    msg(player, "stopped");
                } else {
                    msg(player, "not-in-session");
                }
            }
            case "kit" -> {
                AfkSession s = sessions.get(player.getUniqueId());
                if (s == null) {
                    msg(player, "not-in-session");
                } else if (boundMode()) {
                    openKitSelector(player, s);
                } else {
                    openKitEditor(player, s);
                }
            }
            case "settings" -> {
                AfkSession s = sessions.get(player.getUniqueId());
                if (s == null) {
                    msg(player, "not-in-session");
                } else {
                    openSettings(player, s);
                }
            }
            case "reset" -> {
                AfkSession s = sessions.get(player.getUniqueId());
                if (s == null) {
                    msg(player, "not-in-session");
                } else {
                    resetRound(player, s, true);
                }
            }
            case "bindkit" -> bindKitCommand(player, args);
            case "unbindkit" -> unbindKitCommand(player, args);
            case "boundkits", "kitlist" -> sendBoundList(player);
            default -> msg(player, "usage");
        }
        return true;
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public java.util.List<String> onTabComplete(@org.jetbrains.annotations.NotNull CommandSender sender,
                                                @org.jetbrains.annotations.NotNull Command command,
                                                @org.jetbrains.annotations.NotNull String alias,
                                                @org.jetbrains.annotations.NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "start", "stop", "kit", "settings", "reset"));
            if (sender.hasPermission("rumilance.admin")) {
                subs.add("bindkit");
                subs.add("unbindkit");
                subs.add("boundkits");
            }
            return filterPrefix(args[0], subs);
        }
        if (args.length == 2 && sender.hasPermission("rumilance.admin")) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("bindkit")) {
                List<String> options = new ArrayList<>();
                if (kitService != null) {
                    options.addAll(kitService.all().stream()
                            .map(KitDefinition::name)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList());
                }
                options.add("list");
                options.add("clear");
                return filterPrefix(args[1], options);
            }
            if (sub.equals("unbindkit")) {
                List<String> options = new ArrayList<>(boundKits);
                options.add("all");
                return filterPrefix(args[1], options);
            }
        }
        return List.of();
    }

    /** Case-insensitive startsWith filter, input order preserved. */
    private static List<String> filterPrefix(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .toList();
    }

    // ---------------------------------------------------------------- session

    private void beginSession(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            msg(player, "already");
            return;
        }
        if (otherSessionGuard != null && otherSessionGuard.test(player.getUniqueId())) {
            msg(player, "other-session");
            return;
        }
        if (world == null) {
            world = player.getWorld();
        }
        int index = boxSequence++;
        // Every player gets their OWN private arena on the +60k line (全員別々): a fixed
        // anchor per player is carved on first entry and reused forever, and the arena is
        // fully cleared + rebuilt on every entry (破った床も初期状態に戻る).
        Location anchor = arenaAnchors.get(player.getUniqueId());
        if (anchor == null) {
            int spacing = (FLOOR_RADIUS + 16) * 2;   // 132-block pitch — no two arenas touch
            int baseX = configService.config().getInt("arena.placement-center-x", 0)
                    + 60_000 + index * spacing;
            int baseZ = configService.config().getInt("arena.placement-center-z", 0) + 60_000;
            anchor = new Location(world, baseX + 0.5, world.getMinHeight() + 12, baseZ + 0.5);
            arenaAnchors.put(player.getUniqueId(), anchor);
        }
        Location center = anchor.clone();

        AfkSession s = new AfkSession(player.getUniqueId(), center, index);
        s.shieldOn = shieldPref(player.getUniqueId());
        s.shieldReturnSeconds = shieldDelayPref(player.getUniqueId());
        sessions.put(player.getUniqueId(), s);
        buildFloor(s);
        s.bounds = new BoundingBox(center.getX() - FLOOR_RADIUS, center.getY(),
                center.getZ() - FLOOR_RADIUS, center.getX() + FLOOR_RADIUS + 1,
                center.getY() + BOUNDARY_HEIGHT, center.getZ() + FLOOR_RADIUS + 1);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.teleport(s.spawn());
        player.getInventory().clear();
        giveStarterLoadout(player); // 入室したらまず装備を配る
        msg(player, "welcome");
        if (boundMode()) {
            openKitSelector(player, s);
        } else {
            openKitEditor(player, s);
        }
    }

    /** Ends the session. When {@code toLobby} the player is sent back to the lobby. */
    private boolean endSession(UUID id, Player player, boolean toLobby) {
        AfkSession s = sessions.remove(id);
        if (s == null) {
            return false;
        }
        despawnBot(s);
        if (player != null && player.isOnline()) {
            player.closeInventory();
            if (toLobby) {
                returnToLobby(player);
            }
        }
        clearFloor(s);
        return true;
    }

    // ---------------------------------------------------------------- arena

    /** The 100x100 netherite ground + the reset button on the spawn centre. */
    private void buildFloor(AfkSession s) {
        Location c = s.center;
        for (int x = -FLOOR_RADIUS; x < FLOOR_RADIUS; x++) {
            for (int z = -FLOOR_RADIUS; z < FLOOR_RADIUS; z++) {
                setBlock(c, x, 0, z, Material.NETHERITE_BLOCK);
            }
        }
        placeButton(s);
    }

    /** Floor-facing cherry button standing on the spawn centre block. */
    private void placeButton(AfkSession s) {
        Block b = s.center.clone().add(0, 1, 0).getBlock();
        FaceAttachable data = (FaceAttachable) Material.CHERRY_BUTTON.createBlockData();
        data.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        b.setBlockData(data, false);
    }

    private void clearFloor(AfkSession s) {
        Location c = s.center;
        for (int x = -FLOOR_RADIUS; x < FLOOR_RADIUS; x++) {
            for (int z = -FLOOR_RADIUS; z < FLOOR_RADIUS; z++) {
                setBlock(c, x, 0, z, Material.AIR);
            }
        }
        Block button = c.clone().add(0, 1, 0).getBlock();
        if (button.getType() == Material.CHERRY_BUTTON) {
            button.setType(Material.AIR, false);
        }
    }

    private void setBlock(Location center, int dx, int dy, int dz, Material type) {
        Block b = center.clone().add(dx, dy, dz).getBlock();
        if (b.getType() != type) {
            b.setType(type, false);
        }
    }

    // ---------------------------------------------------------------- ticking

    private void tick() {
        long now = System.currentTimeMillis();
        for (AfkSession s : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(s.playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            // Shield restore after the configured 2s / 5s window.
            if (s.shieldDown && now >= s.shieldDownUntilMs && s.bot != null && s.bot.isValid()) {
                s.shieldDown = false;
                equipBot(s);
                s.bot.getWorld().playSound(s.bot.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                msg(player, "shield-restored");
            }
            tickBoundaries(player, s);
            tickBot(player, s, now);
            if (now - s.lastSlowTickMs >= 1000L) {
                s.lastSlowTickMs = now;
                tickSlow(player, s);
            }
        }
    }

    private void tickBoundaries(Player player, AfkSession s) {
        // Escape guard: leaving the platform (or falling past it) snaps the player back.
        if (player.isDead()) {
            return;
        }
        Location loc = player.getLocation();
        if (!s.bounds.contains(loc.toVector()) || loc.getY() < s.center.getY() - 0.5) {
            player.setFallDistance(0f);
            player.teleport(s.spawn());
            msg(player, "boundary");
        }
    }

    private void tickBot(Player player, AfkSession s, long now) {
        Mannequin bot = s.bot;
        if (bot == null || !bot.isValid() || player.isDead()) {
            return;
        }
        if (now - s.lastBotMoveMs < 100L) {
            return;
        }
        s.lastBotMoveMs = now;
        // Static sparring dummy: it never attacks and never chases (AFK BOTは動かない・殴らない)
        // and its velocity is never written, so player swings land as real vanilla knockback.
        Location bl = bot.getLocation();
        boolean offPlatform = Math.abs(bl.getX() - s.center.getX()) > FLOOR_RADIUS - 1
                || Math.abs(bl.getZ() - s.center.getZ()) > FLOOR_RADIUS - 1;
        boolean farFromHome = bl.distanceSquared(s.home()) > BOT_HOME_MAX_DIST * BOT_HOME_MAX_DIST;
        if (offPlatform || farFromHome) {
            bot.teleport(s.home());
            bot.setVelocity(new Vector(0, 0, 0));
            s.airborneSinceMs = 0L;
            return;
        }
        // Anti-float: a knock arc settles well inside the grace window. If the entity is
        // still hanging in the air with ~no vertical speed after that (Mannequin gravity
        // quirk — the reported "Swordで空飛ぶ"), snap it back down to its spot.
        if (!bot.isOnGround() && Math.abs(bot.getVelocity().getY()) < 0.06d) {
            if (s.airborneSinceMs == 0L) {
                s.airborneSinceMs = now;
            } else if (now - s.airborneSinceMs > BOT_AIRBORNE_GRACE_MS) {
                bot.teleport(s.home());
                bot.setVelocity(new Vector(0, 0, 0));
                s.airborneSinceMs = 0L;
                return;
            }
        } else {
            s.airborneSinceMs = 0L;
        }
        // It keeps facing the player — with the shield up the frontal block is a real wall,
        // so the fight is "break the shield or come from behind".
        Location eye = bot.getEyeLocation();
        Vector to = player.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
        double dist = to.length();
        if (dist > 0.001) {
            Vector dir = to.clone().normalize();
            bot.setRotation((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())),
                    (float) Math.toDegrees(Math.asin(-dir.getY())));
        }
    }

    /** Once-a-second upkeep: totem regen, live health in the name, button integrity. */
    private void tickSlow(Player player, AfkSession s) {
        Mannequin bot = s.bot;
        if (bot != null && bot.isValid()
                && System.currentTimeMillis() - s.lastBotHurtMs >= 5_000L
                && bot.getHealth() < bot.getMaxHealth()) {
            bot.setHealth(Math.min(bot.getMaxHealth(), bot.getHealth() + 0.5d));
        }
        // The reset button is the only fragile protected block — keep it alive.
        if (s.center.clone().add(0, 1, 0).getBlock().getType() != Material.CHERRY_BUTTON) {
            placeButton(s);
        }
    }

    // ---------------------------------------------------------------- bot

    private void spawnBot(Player player, AfkSession s) {
        despawnBot(s);
        Location at = s.home();
        World world = at.getWorld();
        if (world == null) {
            msg(player, "bot-failed");
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
            bot.setGlowing(true);
            // Vanilla mannequin profile: always 20 HP, whatever the difficulty rung.
            if (bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(BOT_MAX_HEALTH);
            }
            bot.setHealth(BOT_MAX_HEALTH);
            bot.setGravity(true); // never a floating statue
            bot.setFireTicks(0);
            equipBotArmor(bot);
            equipBot(s);
        } catch (Throwable ignored) {
        }
        s.bot = bot;
        s.airborneSinceMs = 0L;
        s.shieldDown = false;
        s.shieldDownUntilMs = 0L;
        s.lastBotHurtMs = System.currentTimeMillis();
        updateName(s);
    }

    private void despawnBot(AfkSession s) {
        if (s.bot != null && s.bot.isValid()) {
            s.bot.remove();
        }
        s.bot = null;
    }

    /**
     * The bot's set: protection 4 helmet/chest, blast-protection 4 (max) leggings/boots,
     * fire protection on every piece, all unbreakable (耐久無限).
     */
    private void equipBotArmor(Mannequin bot) {
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(armored(Material.NETHERITE_HELMET, Enchantment.PROTECTION, false));
        eq.setChestplate(armored(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, false));
        eq.setLeggings(armored(Material.NETHERITE_LEGGINGS, Enchantment.BLAST_PROTECTION, false));
        eq.setBoots(armored(Material.NETHERITE_BOOTS, Enchantment.BLAST_PROTECTION, true));
        zeroDropChances(eq);
    }

    private ItemStack armored(Material material, Enchantment main, boolean featherFalling) {
        ItemStack it = new ItemStack(material);
        it.addUnsafeEnchantment(main, 4);
        it.addUnsafeEnchantment(Enchantment.FIRE_PROTECTION, 4); // 火炎耐性付き
        if (featherFalling) {
            it.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 4);
        }
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true); // 耐久無限
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        it.setItemMeta(meta);
        return it;
    }

    /** Hands per the shield setting: offhand shield (when up) or an ever-present totem. */
    private void equipBot(AfkSession s) {
        Mannequin bot = s.bot;
        if (bot == null) {
            return;
        }
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) {
            return;
        }
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        axe.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        eq.setItemInMainHand(axe);
        eq.setItemInOffHand(s.shieldOn && !s.shieldDown
                ? new ItemStack(Material.SHIELD)
                : new ItemStack(Material.TOTEM_OF_UNDYING));
        zeroDropChances(eq);
    }

    private void zeroDropChances(EntityEquipment eq) {
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    private void updateName(AfkSession s) {
        Mannequin bot = s.bot;
        if (bot == null || !bot.isValid()) {
            return;
        }
        double hp = Math.max(0.0d, bot.getHealth());
        bot.customName(LEGACY.deserialize("§bAFK BOT Crystal §f[§c"
                + String.format(Locale.US, "%.1f", hp) + "§7/§f"
                + String.format(Locale.US, "%.0f", bot.getMaxHealth()) + "§f]"));
    }

    // ---------------------------------------------------------------- bot damage

    // ignoreCancelled deliberately false + HIGHEST: other listeners (global blast guards,
    // mannequin protectors) run earlier and cancel the frame, which is exactly why the bot
    // "took no damage from crystals". We re-arm the frame and let the vanilla pipeline
    // (armor math, i-frames, knockback) run after us.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin)) {
            return;
        }
        UUID id = event.getEntity().getUniqueId();
        for (AfkSession s : sessions.values()) {
            if (s.bot == null || !s.bot.getUniqueId().equals(id)) {
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
            if (event.isCancelled()) {
                event.setCancelled(false); // the room must hurt the bot
            }
            onBotDamaged(owner, s, event);
            return;
        }
    }

    /** Central damage handler: shield block, axe disable, totem pop with instant restock. */
    private void onBotDamaged(Player player, AfkSession s, EntityDamageEvent event) {
        Mannequin bot = s.bot;
        if (bot == null) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean melee = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        boolean explosion = cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
        boolean projectile = cause == EntityDamageEvent.DamageCause.PROJECTILE;
        if (!melee && !explosion && !projectile) {
            // Only the player's weapons count; fire/lava never hurt it anyway (fire res).
            event.setCancelled(true);
            return;
        }
        s.lastBotHurtMs = System.currentTimeMillis();
        boolean fromFront = inFrontOf(bot, damageSourceLocation(event, player));
        // An axe hit on a raised shield disables it first — the disabling hit pierces.
        if (fromFront && s.shieldOn && !s.shieldDown && isPlayerAxeHit(event, s)) {
            breakBotShield(player, s);
        } else if (fromFront && s.shieldOn && !s.shieldDown) {
            event.setCancelled(true);
            World w = bot.getWorld();
            w.playSound(bot.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            w.spawnParticle(Particle.CRIT, bot.getLocation().add(0, 1.2, 0), 12, 0.3, 0.4, 0.3, 0.02);
            return;
        }
        double after = bot.getHealth() - event.getFinalDamage();
        if (after > 0.5d) {
            // Health genuinely drops — vanilla damage/knockback/armor math applies.
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            updateName(s);
            return;
        }
        // Lethal blow: cancel the killing frame and pop the totem. The entity must be
        // re-created here: a Paper Mannequin that survived a cancelled lethal hit freezes
        // client-side (the client played the death) and ignores every later hit and KB —
        // the "totem pops once, then nothing happens" bug. Same pattern as the practice
        // bots' respawnCombatBot. The knockback of the killing frame is re-applied onto
        // the fresh entity (a pop must never eat KB / wind burst).
        event.setCancelled(true);
        Location at = bot.getLocation().clone();
        respawnBot(s, at);
        totemPop(s);
        applyPopKnockback(s.bot, event);
    }

    /** Re-creates the bot at {@code at} with the same armor/hands/profile, HP 1. */
    private void respawnBot(AfkSession s, Location at) {
        Mannequin old = s.bot;
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        Mannequin bot = w.spawn(at, Mannequin.class, m -> {
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
            bot.setGlowing(true);
            if (bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(BOT_MAX_HEALTH);
            }
            bot.setHealth(1.0d); // fresh totem: popped down to half a heart
            bot.setFireTicks(0);
            if (old != null && old.isValid()) {
                EntityEquipment oldEq = old.getEquipment();
                EntityEquipment eq = bot.getEquipment();
                if (oldEq != null && eq != null) {
                    eq.setHelmet(oldEq.getHelmet());
                    eq.setChestplate(oldEq.getChestplate());
                    eq.setLeggings(oldEq.getLeggings());
                    eq.setBoots(oldEq.getBoots());
                    eq.setItemInMainHand(oldEq.getItemInMainHand());
                    eq.setItemInOffHand(oldEq.getItemInOffHand());
                }
            } else {
                equipBotArmor(bot);
            }
        } catch (Throwable ignored) {
        }
        if (old != null && old.isValid()) {
            old.remove();
        }
        s.bot = bot;
        s.airborneSinceMs = 0L;
        updateName(s);
    }

    private boolean isPlayerAxeHit(EntityDamageEvent event, AfkSession s) {
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return false;
        }
        if (!(by.getDamager() instanceof Player p) || !p.getUniqueId().equals(s.playerId)) {
            return false;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        return hand != null && hand.getType() == Material.NETHERITE_AXE;
    }

    private Location damageSourceLocation(EntityDamageEvent event, Player fallback) {
        if (event instanceof EntityDamageByEntityEvent by) {
            return by.getDamager().getLocation();
        }
        return fallback.getLocation();
    }

    /** Vanilla-like ~±96° frontal cone in front of the bot's facing. */
    private boolean inFrontOf(Mannequin bot, Location source) {
        Vector facing = bot.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0e-4) {
            return true;
        }
        Vector to = source.toVector().setY(0)
                .subtract(bot.getLocation().toVector().setY(0));
        if (to.lengthSquared() < 1.0e-4) {
            return true;
        }
        return facing.normalize().dot(to.normalize()) > -0.1d;
    }

    /** The bot's shield is broken: down for the configured 2s / 5s, then auto-return. */
    private void breakBotShield(Player player, AfkSession s) {
        s.shieldDown = true;
        s.shieldDownUntilMs = System.currentTimeMillis() + s.shieldReturnSeconds * 1000L;
        EntityEquipment eq = s.bot.getEquipment();
        if (eq != null) {
            eq.setItemInOffHand(null);
        }
        World w = s.bot.getWorld();
        w.playSound(s.bot.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.9f);
        w.spawnParticle(Particle.CRIT, s.bot.getLocation().add(0, 1.2, 0), 20, 0.3, 0.4, 0.3, 0.05);
        msg(player, "shield-broken", msgTags("seconds", String.valueOf(s.shieldReturnSeconds)));
    }

    /**
     * Totem of Undying simulation with an instant restock (補充は即時). Mannequins neither
     * show nor reliably carry potion effects, so the totem's Absorption gold hearts and
     * Regeneration are simulated: a damage-absorbing pool plus a timed regen window, both
     * made visible by hand in {@link #tickBotEffectVisuals}.
     */
    private void totemPop(AfkSession s) {
        Mannequin bot = s.bot;
        World w = bot.getWorld();
        bot.setFireTicks(0);
        w.playSound(bot.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, bot.getLocation().add(0, 1, 0), 80, 0.5, 1.0, 0.5, 0.4);
        s.totemPops++;
        restockTotem(s);
        updateName(s);
    }

    /**
     * The cancelled killing frame would have carried knockback (and wind-charge shoves).
     * Re-apply it by hand: vanilla melee strength for a normal hit, a strong horizontal
     * shove when the pop was caused by a wind charge / projectile.
     */
    private void applyPopKnockback(Mannequin bot, EntityDamageEvent event) {
        if (bot == null || !bot.isValid()
                || !(event instanceof EntityDamageByEntityEvent by)) {
            return;
        }
        org.bukkit.entity.Entity damager = by.getDamager();
        if (damager == null) {
            return;
        }
        Location from = damager.getLocation();
        Vector push = bot.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() < 1.0e-4d) {
            return;
        }
        boolean wind = damager instanceof org.bukkit.entity.Projectile
                || damager.getType() == org.bukkit.entity.EntityType.WIND_CHARGE
                || damager.getType() == org.bukkit.entity.EntityType.BREEZE_WIND_CHARGE;
        if (wind) {
            push.normalize().multiply(WIND_BURST_KB);
            push.setY(0.35d);
        } else {
            push.normalize().multiply(TOTEM_POP_KB);
            push.setY(TOTEM_POP_KB_Y);
        }
        Vector v = bot.getVelocity();
        bot.setVelocity(new Vector(v.getX() + push.getX(), push.getY(), v.getZ() + push.getZ()));
    }

    private void restockTotem(AfkSession s) {
        if (s.shieldOn && !s.shieldDown) {
            return; // the shield rides the offhand; the totem is carried inside
        }
        EntityEquipment eq = s.bot.getEquipment();
        if (eq != null) {
            eq.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        }
    }

    // ---------------------------------------------------------------- reset round

    /**
     * The spawn-centre button: re-fills the whole kit and returns the bot (position,
     * health, effects, fire, shield) to its initial state.
     */
    private void resetRound(Player player, AfkSession s, boolean announce) {
        // Full arena reset: every player-placed block is wiped and the pristine 100x100
        // netherite floor + button are rebuilt (戦う場所も初期状態に戻る).
        buildFloor(s);
        equipPlayerKit(player);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        if (s.bot != null && s.bot.isValid()) {
            Mannequin bot = s.bot;
            bot.teleport(s.home());
            bot.setVelocity(new Vector(0, 0, 0));
            bot.setFallDistance(0f);
            bot.setFireTicks(0);
            if (bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                bot.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(BOT_MAX_HEALTH);
            }
            bot.setHealth(BOT_MAX_HEALTH);
            for (PotionEffect effect : new ArrayList<>(bot.getActivePotionEffects())) {
                bot.removePotionEffect(effect.getType());
            }
            s.airborneSinceMs = 0L;
            s.shieldDown = false;
            s.shieldDownUntilMs = 0L;
            equipBot(s);
            updateName(s);
        }
        if (announce) {
            World w = player.getWorld();
            w.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            w.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
            msg(player, "reset");
        }
    }

    // ---------------------------------------------------------------- player damage

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null || player.isDead()) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            player.teleport(s.spawn());
            return;
        }
        // FALL is REAL here: fall-stacked crystal / mace plays need the landing damage
        // (落下溜め). Only the void below the platform is a rescue.
        // Explosion self-damage (own crystals/anchors) is authentic — it stays.
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
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        AfkSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        event.setRespawnLocation(s.spawn());
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!sessions.containsKey(player.getUniqueId()) || !player.isOnline()) {
                return;
            }
            // 死ぬたびに戦う場所をリセット: rebuild + rekit + bot back home.
            firework(player.getLocation(), false);
            resetRound(player, s, false);
            msg(player, "died");
        }, 10L);
    }

    // ---------------------------------------------------------------- interaction

    // ignoreCancelled deliberately false: lobby/sign guards run first and cancel room
    // interacts, which used to swallow the settings/kit buttons ("設定が開かないことがある").
    // Main-hand only so the two-hand event pair never double-fires the reset.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        Block b = event.getClickedBlock();
        boolean rightClick = event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR;
        if (!rightClick) {
            return;
        }
        boolean buttonBlock = b != null && b.getType() == Material.CHERRY_BUTTON
                && isProtected(s, b);
        if (buttonBlock) {
            event.setCancelled(true);
            resetRound(player, s, true);
        }
        // Kit menu and settings live on /afkc kit and /afkc settings (plus the editor's
        // settings tile) — no hotbar slots are ever reserved, so nothing can vanish.
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && sessions.containsKey(p.getUniqueId())) {
            event.setCancelled(true); // food never runs out inside the room
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

    /** The netherite floor, the reset button (and its exact cell) stay untouchable. */
    private boolean isProtected(AfkSession s, Block b) {
        Material t = b.getType();
        if (t == Material.CHERRY_BUTTON) {
            return true;
        }
        if (t == Material.NETHERITE_BLOCK && b.getY() == (int) s.center.getY()) {
            return true;
        }
        Block button = s.center.clone().add(0, 1, 0).getBlock();
        return b.getX() == button.getX() && b.getY() == button.getY()
                && b.getZ() == button.getZ();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        AfkSession s = sessions.get(event.getPlayer().getUniqueId());
        if (s == null) {
            return;
        }
        if (!inside(s, event.getBlock()) || isProtected(s, event.getBlock())) {
            event.setCancelled(true);
            msg(event.getPlayer(), "protected");
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
        if (isProtected(s, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Explosions keep hurting entities but never eat the floor or the button. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        AfkSession s = sessionNear(event.getLocation());
        if (s == null) {
            return;
        }
        event.blockList().removeIf(b -> isProtected(s, b));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        AfkSession s = sessionNear(event.getBlock().getLocation());
        if (s == null) {
            return;
        }
        event.blockList().removeIf(b -> isProtected(s, b));
    }

    private AfkSession sessionNear(Location loc) {
        for (AfkSession s : sessions.values()) {
            if (loc.getWorld() != s.center.getWorld()) {
                continue;
            }
            double dx = loc.getX() - s.center.getX();
            double dz = loc.getZ() - s.center.getZ();
            double reach = FLOOR_RADIUS + 8;
            if (dx * dx + dz * dz <= reach * reach) {
                return s;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- kit editor

    private ItemStack settingsHotbarButton() {
        ItemStack it = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEGACY.deserialize("§eAFK Crystal 設定 §7(クリック)"));
        meta.setCustomModelData(CMD_SETTINGS_TILE);
        meta.setLore(List.of(LEGACY.serialize(LEGACY.deserialize(
                "§7盾のON/OFF・盾の復帰時間(2秒/5秒)を設定できます"))));
        it.setItemMeta(meta);
        return it;
    }

    /** Opens the kit editor: save / settings / quick-gives / admin prototype. */
    private void openKitEditor(Player player, AfkSession s) {
        // Re-equipping ALWAYS rebuilds the inventory from the saved kit first.
        equipSavedKit(player);
        Inventory inv = Bukkit.createInventory(new CrystalEditorHolder(), EDITOR_ROWS * 9,
                LEGACY.deserialize("§8AFK BOT Crystal キット編集"));
        inv.setItem(SLOT_INFO, infoItem());
        inv.setItem(1, named(Material.PAPER, "§e/afkc kit §7/ §e/afkc settings",
                "§7ホットバーはもう専用スロットではありません",
                "§7(スロットに入れた物が消えることはありません)"));
        inv.setItem(SLOT_SAVE, saveItem());
        inv.setItem(SLOT_SETTINGS, settingsHotbarButton());
        inv.setItem(SLOT_PROTOTYPE_LOAD, prototypeLoadItem());
        if (player.hasPermission("rumilance.admin")) {
            inv.setItem(SLOT_PROTOTYPE_SAVE, prototypeSaveItem());
        }
        ItemStack[] gives = {
                new ItemStack(Material.OBSIDIAN, 64),
                new ItemStack(Material.END_CRYSTAL, 64),
                new ItemStack(Material.RESPAWN_ANCHOR, 1),
                new ItemStack(Material.GLOWSTONE, 16),
                new ItemStack(Material.TOTEM_OF_UNDYING, 1),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
                new ItemStack(Material.ENDER_PEARL, 16),
                practiceAxe(),
                new ItemStack(Material.COBWEB, 16)};
        String[] names = {"§f黒曜石 §7x64", "§dエンドクリスタル §7x64", "§5リスポーンアンカー",
                "§eグロウストーン §7x16", "§6不死のトーテム", "§6金のリンゴ(エンチャント)",
                "§aエンダーパール §7x16", "§bネザライト斧", "§f糸 §7x16"};
        for (int i = 0; i < GIVE_SLOTS.length; i++) {
            ItemStack it = gives[i].clone();
            ItemMeta meta = it.getItemMeta();
            meta.displayName(LEGACY.deserialize(names[i]));
            meta.setLore(List.of(LEGACY.serialize(LEGACY.deserialize("§7クリックで入手"))));
            it.setItemMeta(meta);
            inv.setItem(GIVE_SLOTS[i], it);
        }
        // Current kit content mirrored into the chest for drag-free editing.
        ItemStack[] snap = snapshotOf(player);
        for (int i = 0; i < 27; i++) {
            inv.setItem(KIT_AREA_START + i, i < snap.length ? snap[i] : null);
        }
        player.openInventory(inv);
        s.editing = true;
    }

    private ItemStack practiceAxe() {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        axe.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        return axe;
    }

    private ItemStack named(Material mat, String name, String lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.setLore(List.of(LEGACY.serialize(LEGACY.deserialize(lore))));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack named(Material mat, String name, String... loreLines) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        List<String> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(LEGACY.serialize(LEGACY.deserialize(line)));
        }
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack infoItem() {
        return named(Material.PAPER, "§eAFK BOT Crystal ルール",
                "§7床は100x100ネザライト/スポーン中央ボタンでキット再装填+BOT初期化");
    }

    private ItemStack saveItem() {
        ItemStack it = named(Material.EMERALD_BLOCK, "§a§l保存して装備", "§7キットを保存し BOT と再開");
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(CMD_SAVE_ITEM);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack prototypeSaveItem() {
        ItemStack it = named(Material.NETHER_STAR, "§d§lプロトタイプ保存 (管理)",
                "§7この内容をサーバー既定キットとして保存");
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(CMD_PROTOTYPE_SAVE);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack prototypeLoadItem() {
        ItemStack it = named(Material.BEACON, "§b既定キットを読み込む",
                "§7サーバーのプロトタイプキットに置き換え");
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(CMD_PROTOTYPE_LOAD);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrystalEditorHolder)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= EDITOR_ROWS * 9) {
            return; // bottom (player inventory) moves freely
        }
        event.setCancelled(true);
        if (slot == SLOT_SAVE) {
            persistKitFromView(player, event.getInventory());
            player.closeInventory();
            return;
        }
        if (slot == SLOT_SETTINGS) {
            openSettings(player, s);
            return;
        }
        if (slot == SLOT_PROTOTYPE_LOAD && prototypeKit != null) {
            applySnapshot(player, prototypeKit);
            openKitEditor(player, s);
            msg(player, "prototype-loaded");
            return;
        }
        if (slot == SLOT_PROTOTYPE_SAVE && player.hasPermission("rumilance.admin")) {
            prototypeKit = snapshotOf(player);
            saveKits();
            msg(player, "prototype-saved");
            return;
        }
        for (int idx = 0; idx < GIVE_SLOTS.length; idx++) {
            if (slot == GIVE_SLOTS[idx]) {
                ItemStack give = event.getInventory().getItem(slot);
                if (give != null) {
                    ItemStack one;
                    if (give.getType() == Material.NETHERITE_AXE) {
                        one = practiceAxe(); // clean enchanted axe (no menu display meta)
                    } else {
                        one = give.clone();
                        one.setItemMeta(null); // plain stack
                    }
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
        if (!(event.getInventory().getHolder() instanceof CrystalEditorHolder)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null || !s.editing) {
            return;
        }
        s.editing = false;
        persistKitFromView(player, event.getInventory());
        if (s.bot != null && s.bot.isValid()) {
            resetRound(player, s, false);
        } else {
            spawnBot(player, s);
        }
    }

    /** Save the editor chest area + live player inventory as the player's crystal kit. */
    private void persistKitFromView(Player player, Inventory editor) {
        savedKits.put(player.getUniqueId(), snapshotOf(player));
        saveKits();
        // Return chest-area items to the inventory, then re-equip the saved kit; any
        // overflow is dropped at the player's feet so nothing is ever lost.
        List<ItemStack> returned = new ArrayList<>();
        for (int i = KIT_AREA_START; i < KIT_AREA_START + 27; i++) {
            ItemStack it = editor.getItem(i);
            if (it != null) {
                returned.add(it);
                editor.setItem(i, null);
            }
        }
        equipSavedKit(player);
        for (ItemStack it : returned) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(it);
            for (ItemStack rest : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        msg(player, "kit-saved");
    }

    /** Full clear then apply the saved kit (hotbar GUI slots stay reserved). */
    private void equipSavedKit(Player player) {
        ItemStack[] snap = savedKits.get(player.getUniqueId());
        if (snap == null) {
            snap = prototypeKit != null ? prototypeKit : defaultKit();
        }
        applySnapshot(player, snap);
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
        // No reserved hotbar slots: every slot the player filled survives 1:1.
    }

    private ItemStack[] snapshotOf(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] snap = new ItemStack[41];
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            snap[i] = it == null ? null : it.clone();
        }
        // No hotbar slot is special anymore — slots 8/9 keep whatever the player put there.
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < 4; i++) {
            snap[36 + i] = armor[i] == null ? null : armor[i].clone();
        }
        snap[40] = inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone();
        return snap;
    }

    /** Default crystal kit: axe + crystals + obsidian + the same armor set as the bot. */
    private ItemStack[] defaultKit() {
        ItemStack[] kit = new ItemStack[41];
        kit[0] = practiceAxe();
        kit[1] = new ItemStack(Material.END_CRYSTAL, 64);
        kit[2] = new ItemStack(Material.OBSIDIAN, 64);
        kit[3] = new ItemStack(Material.TOTEM_OF_UNDYING, 4);
        kit[4] = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8);
        kit[5] = new ItemStack(Material.ENDER_PEARL, 16);
        kit[6] = new ItemStack(Material.RESPAWN_ANCHOR, 1);
        kit[7] = new ItemStack(Material.GLOWSTONE, 16);
        kit[36] = armored(Material.NETHERITE_BOOTS, Enchantment.BLAST_PROTECTION, true);
        kit[37] = armored(Material.NETHERITE_LEGGINGS, Enchantment.BLAST_PROTECTION, false);
        kit[38] = armored(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, false);
        kit[39] = armored(Material.NETHERITE_HELMET, Enchantment.PROTECTION, false);
        kit[40] = new ItemStack(Material.TOTEM_OF_UNDYING);
        return kit;
    }

    /**
     * Entry gift: a ready-to-fight crystal loadout so a fresh player can start swinging
     * immediately. Everything here is destroyed by the first kit equip (selector choice,
     * save, reset) — it is a loaner, not a forced kit (装備配って).
     */
    private void giveStarterLoadout(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(armored(Material.NETHERITE_HELMET, Enchantment.PROTECTION, false));
        inv.setChestplate(armored(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, false));
        inv.setLeggings(armored(Material.NETHERITE_LEGGINGS, Enchantment.BLAST_PROTECTION, false));
        inv.setBoots(armored(Material.NETHERITE_BOOTS, Enchantment.BLAST_PROTECTION, true));
        inv.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        inv.setItem(0, practiceAxe());
        inv.setItem(1, new ItemStack(Material.END_CRYSTAL, 64));
        inv.setItem(2, new ItemStack(Material.OBSIDIAN, 64));
        inv.setItem(3, new ItemStack(Material.TOTEM_OF_UNDYING, 4));
        inv.setItem(4, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8));
        inv.setItem(5, new ItemStack(Material.ENDER_PEARL, 16));
        inv.setItem(6, new ItemStack(Material.WIND_CHARGE, 16));
        inv.setItem(7, new ItemStack(Material.TOTEM_OF_UNDYING, 2));
        player.updateInventory();
    }

    // ---------------------------------------------------------------- bound kits (admin)

    /** True while the admin has bound at least one kit: the room fights with bound kits. */
    private boolean boundMode() {
        return kitService != null && !boundKits.isEmpty();
    }

    // ------------------------------------------------- admin binding commands

    private void bindKitCommand(Player player, String[] args) {
        if (!player.hasPermission("rumilance.admin")) {
            msg(player, "no-admin");
            return;
        }
        if (args.length < 2) {
            msg(player, "bindkit-usage");
            return;
        }
        String first = args[1].toLowerCase(Locale.ROOT);
        if (first.equals("list")) {
            sendBoundList(player);
            return;
        }
        if (first.equals("clear")) {
            int count = boundKits.size();
            boundKits.clear();
            saveSettings();
            msg(player, "bound-cleared", msgTags("count", String.valueOf(count)));
            return;
        }
        String input = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        KitDefinition kit = kitService.get(input).orElse(null);
        if (kit == null) {
            msg(player, "bound-unknown", msgTags("kit", input));
            return;
        }
        if (boundKits.contains(kit.name())) {
            msg(player, "bound-exists", msgTags("kit", kit.name()));
            return;
        }
        if (boundKits.size() >= MAX_BOUND_KITS) {
            msg(player, "bound-full", msgTags("max", String.valueOf(MAX_BOUND_KITS)));
            return;
        }
        boundKits.add(kit.name());
        saveSettings();
        msg(player, "bound-added", msgTags("kit", kit.name(),
                "count", String.valueOf(boundKits.size()),
                "max", String.valueOf(MAX_BOUND_KITS)));
    }

    private void unbindKitCommand(Player player, String[] args) {
        if (!player.hasPermission("rumilance.admin")) {
            msg(player, "no-admin");
            return;
        }
        if (args.length < 2) {
            msg(player, "bindkit-usage");
            return;
        }
        String input = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (input.equalsIgnoreCase("all") || input.equalsIgnoreCase("clear")) {
            int count = boundKits.size();
            boundKits.clear();
            saveSettings();
            msg(player, "bound-cleared", msgTags("count", String.valueOf(count)));
            return;
        }
        String canonical = null;
        for (String name : boundKits) {
            if (name.equalsIgnoreCase(input)) {
                canonical = name;
                break;
            }
        }
        if (canonical == null || !boundKits.remove(canonical)) {
            msg(player, "bound-not-bound", msgTags("kit", input));
            return;
        }
        saveSettings();
        msg(player, "bound-removed", msgTags("kit", canonical,
                "count", String.valueOf(boundKits.size()),
                "max", String.valueOf(MAX_BOUND_KITS)));
    }

    private void sendBoundList(Player player) {
        if (!player.hasPermission("rumilance.admin")) {
            msg(player, "no-admin");
            return;
        }
        if (boundKits.isEmpty()) {
            msg(player, "bound-none", msgTags("max", String.valueOf(MAX_BOUND_KITS)));
            return;
        }
        msg(player, "bound-list", msgTags("list", String.join(", ", boundKits),
                "count", String.valueOf(boundKits.size()),
                "max", String.valueOf(MAX_BOUND_KITS)));
    }

    // ------------------------------------------------- bound-kit equipping

    /** The player's selected bound kit, lazily defaulting to the first available one. */
    private String selectedKitOf(UUID playerId) {
        String sel = kitSelections.get(playerId);
        if (sel != null && boundKits.contains(sel) && kitService.get(sel).isPresent()) {
            return sel;
        }
        for (String name : boundKits) {
            if (kitService.get(name).isPresent()) {
                kitSelections.put(playerId, name);
                return name;
            }
        }
        return null;
    }

    /** Applies the selected bound kit match-style; the two GUI hotbar slots stay reserved. */
    private boolean applySelectedKit(Player player) {
        if (!boundMode()) {
            return false;
        }
        String sel = selectedKitOf(player.getUniqueId());
        if (sel == null) {
            return false;
        }
        KitDefinition kit = kitService.get(sel).orElse(null);
        if (kit == null) {
            return false;
        }
        kitService.apply(player, kit); // KitLoadout.give clears the inventory first
        // The room needs block place/break: a forceAdventure kit must not brick the arena.
        player.setGameMode(GameMode.SURVIVAL);
        return true;
    }

    /** Records the player's bound-kit choice, equips it and persists the selection. */
    private void selectKit(Player player, String kitName, boolean announce) {
        kitSelections.put(player.getUniqueId(), kitName);
        AfkSession s = sessions.get(player.getUniqueId());
        if (s != null) {
            s.kitChosen = true;
        }
        saveSettings();
        applySelectedKit(player);
        if (announce) {
            msg(player, "kit-selected", msgTags("kit", kitName));
        }
    }

    /** Equips the effective kit: the admin-bound selection, else the personal saved kit. */
    private void equipPlayerKit(Player player) {
        if (applySelectedKit(player)) {
            return;
        }
        equipSavedKit(player);
    }

    // ------------------------------------------------- kit selector GUI

    private static final int[] KIT_SELECT_SLOTS = {11, 12, 13, 14, 15};
    private static final int KIT_SELECT_CLOSE = 22;

    /** The bound-kit chooser shown in place of the personal editor while kits are bound. */
    private void openKitSelector(Player player, AfkSession s) {
        // ESC only auto-starts with the first kit when nothing valid is selected yet;
        // peeking at the selector mid-fight must never reset the bot.
        s.kitChosen = selectedKitOf(player.getUniqueId()) != null;
        Inventory inv = Bukkit.createInventory(new CrystalKitSelectHolder(), 27,
                LEGACY.deserialize("§8AFK BOT Crystal キット選択"));
        inv.setItem(4, named(Material.BOOK, "§e紐づけキットから選択",
                "§7運営が紐づけたキット(最大5)をクリックして装備します",
                "§7スポーン中央ボタンの再装坏(rekit)でも同じキットが再装備されます"));
        String sel = selectedKitOf(player.getUniqueId());
        int n = Math.min(boundKits.size(), KIT_SELECT_SLOTS.length);
        int placed = 0;
        for (int i = 0; i < n; i++) {
            String name = boundKits.get(i);
            KitDefinition kit = kitService.get(name).orElse(null);
            if (kit == null) {
                continue;
            }
            boolean active = name.equals(sel);
            inv.setItem(KIT_SELECT_SLOTS[placed], boundKitIcon(kit, active));
            placed++;
        }
        inv.setItem(KIT_SELECT_CLOSE, named(Material.BARRIER, "§c閉じる",
                "§7未選択のまま閉じると最初のキットで開始します"));
        player.openInventory(inv);
    }

    private ItemStack boundKitIcon(KitDefinition kit, boolean active) {
        Material icon = Material.matchMaterial(kit.icon() == null ? "" : kit.icon());
        if (icon == null || !icon.isItem()) {
            icon = Material.NETHERITE_CHESTPLATE;
        }
        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        String disp = kit.displayName() == null || kit.displayName().isBlank()
                ? kit.name() : kit.displayName();
        meta.displayName(LEGACY.deserialize((active ? "§a▶ " : "§f") + disp
                + (active ? " §7(選択中)" : "")));
        List<String> lore = new ArrayList<>();
        lore.add(LEGACY.serialize(LEGACY.deserialize(
                "§7クリックで装備してBOT戦開始")));
        lore.add(LEGACY.serialize(LEGACY.deserialize(
                "§7試合と同じ方式で適用されます")));
        if (active) {
            lore.add(LEGACY.serialize(LEGACY.deserialize("§a現在のキットです")));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(active ? Boolean.TRUE : null);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKitSelectClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrystalKitSelectHolder)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 27) {
            return; // bottom inventory moves freely
        }
        if (slot == KIT_SELECT_CLOSE) {
            player.closeInventory();
            return;
        }
        int limit = Math.min(boundKits.size(), KIT_SELECT_SLOTS.length);
        for (int i = 0; i < limit; i++) {
            if (slot != KIT_SELECT_SLOTS[i]) {
                continue;
            }
            String name = boundKits.get(i);
            if (kitService.get(name).isEmpty()) {
                return;
            }
            selectKit(player, name, true);
            player.closeInventory();
            if (s.bot != null && s.bot.isValid()) {
                resetRound(player, s, false);
            } else {
                spawnBot(player, s);
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKitSelectClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrystalKitSelectHolder)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        AfkSession s = sessions.get(player.getUniqueId());
        if (s == null || s.editing || s.kitChosen) {
            return;
        }
        // Closed without a choice (ESC): auto-equip the first bound kit so the fight starts.
        if (!applySelectedKit(player)) {
            return;
        }
        if (s.bot != null && s.bot.isValid()) {
            resetRound(player, s, false);
        } else {
            spawnBot(player, s);
        }
    }

    // ---------------------------------------------------------------- settings GUI

    private void openSettings(Player player, AfkSession s) {
        Inventory inv = Bukkit.createInventory(new CrystalSettingsHolder(), 27,
                LEGACY.deserialize("§8AFK BOT Crystal 設定"));
        inv.setItem(11, shieldToggleItem(s));
        inv.setItem(13, delayToggleItem(s));
        inv.setItem(15, named(Material.TOTEM_OF_UNDYING, "§6トーテムについて",
                "§7BOTは常にトーテムを保持し、§a即時に補充§7されます。\n§7そのためBOTは倒せません(体力はしっかり減ります)"));
        inv.setItem(22, named(Material.BARRIER, "§c閉じる", "§7クリックで閉じます"));
        player.openInventory(inv);
    }

    private ItemStack shieldToggleItem(AfkSession s) {
        ItemStack it = named(Material.SHIELD, "§eオフハンドの盾 §8[ON / OFF]",
                "§a現在: " + (s.shieldOn ? "§aON" : "§cOFF")
                        + "\n§7ON: BOTが盾を構え、正面の攻撃/爆発をブロックします\n§7OFF: オフハンドはトーテムです\n§fクリックで切替");
        return it;
    }

    private ItemStack delayToggleItem(AfkSession s) {
        boolean two = s.shieldReturnSeconds == SHIELD_RETURN_ALT_S;
        ItemStack it = named(Material.CLOCK, "§e盾の復帰時間 §8(2秒 / 5秒)",
                "§a現在: " + s.shieldReturnSeconds + "秒"
                        + (two ? "" : " §8(デフォルト)")
                        + "\n§7斧で割られた盾が戻るまでの時間です\n§fクリックで切替");
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSettingsClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrystalSettingsHolder)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        AfkSession s = sessions.get(player.getUniqueId());
        event.setCancelled(true);
        if (s == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 27) {
            return;
        }
        if (slot == 11) {
            toggleShield(player, s);
        } else if (slot == 13) {
            toggleShieldDelay(player, s);
        } else if (slot == 22) {
            player.closeInventory();
        }
    }

    private void toggleShield(Player player, AfkSession s) {
        s.shieldOn = !s.shieldOn;
        s.shieldDown = false;
        shieldPrefs.put(player.getUniqueId(), s.shieldOn);
        saveSettings();
        equipBot(s);
        msg(player, s.shieldOn ? "settings-shield-on" : "settings-shield-off");
        openSettings(player, s);
    }

    private void toggleShieldDelay(Player player, AfkSession s) {
        s.shieldReturnSeconds = s.shieldReturnSeconds == SHIELD_RETURN_ALT_S
                ? SHIELD_RETURN_DEFAULT_S
                : SHIELD_RETURN_ALT_S;
        shieldDelayPrefs.put(player.getUniqueId(), s.shieldReturnSeconds);
        saveSettings();
        msg(player, "settings-delay", msgTags("seconds", String.valueOf(s.shieldReturnSeconds)));
        openSettings(player, s);
    }

    private boolean shieldPref(UUID id) {
        return shieldPrefs.getOrDefault(id, false);
    }

    private int shieldDelayPref(UUID id) {
        int value = shieldDelayPrefs.getOrDefault(id, SHIELD_RETURN_DEFAULT_S);
        return value == SHIELD_RETURN_ALT_S ? SHIELD_RETURN_ALT_S : SHIELD_RETURN_DEFAULT_S;
    }

    // ---------------------------------------------------------------- fireworks/persist

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
        meta.setPower(win ? 1 : 2);
        fw.setFireworkMeta(meta);
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
            yml.set(e.getKey().toString() + ".items", Arrays.asList(e.getValue()));
        }
        if (prototypeKit != null) {
            yml.set("prototype.items", Arrays.asList(prototypeKit));
        }
        try {
            yml.save(kitsFile);
        } catch (IOException ignored) {
        }
    }

    private void loadSettings() {
        if (!settingsFile.exists()) {
            return;
        }
        FileConfiguration yml = YamlConfiguration.loadConfiguration(settingsFile);
        for (String key : yml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                shieldPrefs.put(id, yml.getBoolean(key + ".shield", false));
                shieldDelayPrefs.put(id, yml.getInt(key + ".return-seconds", SHIELD_RETURN_DEFAULT_S));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String name : yml.getStringList("bound-kits")) {
            if (name != null && !name.isBlank() && boundKits.size() < MAX_BOUND_KITS
                    && !boundKits.contains(name)) {
                boundKits.add(name);
            }
        }
        ConfigurationSection sel = yml.getConfigurationSection("kit-selection");
        if (sel != null) {
            for (String key : sel.getKeys(false)) {
                try {
                    String name = sel.getString(key);
                    if (name != null && !name.isBlank()) {
                        kitSelections.put(UUID.fromString(key), name);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveSettings() {
        FileConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Boolean> e : shieldPrefs.entrySet()) {
            yml.set(e.getKey() + ".shield", e.getValue());
        }
        for (Map.Entry<UUID, Integer> e : shieldDelayPrefs.entrySet()) {
            yml.set(e.getKey() + ".return-seconds", e.getValue());
        }
        yml.set("bound-kits", boundKits.isEmpty() ? null : new ArrayList<>(boundKits));
        for (Map.Entry<UUID, String> e : kitSelections.entrySet()) {
            yml.set("kit-selection." + e.getKey(), e.getValue());
        }
        try {
            yml.save(settingsFile);
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
        player.sendMessage(messages.render(player, "afkcrystal." + key,
                resolvers.toArray(new TagResolver[0])));
    }

    private Map<String, String> msgTags(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static final class CrystalEditorHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class CrystalKitSelectHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class CrystalSettingsHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class AfkSession {
        final UUID playerId;
        final Location center;
        final int index;
        BoundingBox bounds;
        Mannequin bot;
        boolean shieldOn;              // setting: shield raised in the offhand
        boolean shieldDown;            // shield broken window
        int shieldReturnSeconds = SHIELD_RETURN_DEFAULT_S;
        long shieldDownUntilMs;
        long lastBotMoveMs;
        long lastBotHurtMs;
        long lastSlowTickMs;
        long airborneSinceMs; // floating statue guard
        long totemPops;
        boolean editing;
        boolean kitChosen; // a bound kit was explicitly picked in the selector

        AfkSession(UUID playerId, Location center, int index) {
            this.playerId = playerId;
            this.center = center;
            this.index = index;
        }

        Location spawn() {
            return center.clone().add(0, 2.0, 0); // standing on the reset button
        }

        Location home() {
            return center.clone().add(0, 1.0, BOT_HOME_OFFSET_Z);
        }
    }

    /**
     * Lobby return: the real LobbyService send wired by the bootstrap (state restoration +
     * proper spawn) with a world-spawn fallback if that wiring is missing. Without this the
     * endSession teleport raced the lobby state machine and /hub looked dead.
     */
    private void returnToLobby(Player player) {
        if (lobbySender != null) {
            try {
                lobbySender.accept(player, "crystal");
                return;
            } catch (Throwable ignored) {
                // fall through to the safe default
            }
        }
        World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w != null) {
            player.teleport(w.getSpawnLocation());
        }
        player.setGameMode(GameMode.SURVIVAL);
    }
}
