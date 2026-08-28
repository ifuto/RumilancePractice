package com.rumilance.practice.bot;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.PlayerVitals;
import com.rumilance.practice.util.SafeTeleport;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword-specialised practice bot using vanilla {@link Mannequin} (player model, no Citizens).
 * Tick AI approximates modern sword PvP: cooldown swings, W-tap, S-tap, jump crits, strafe.
 * Not a real {@link Player}, so no join/quit broadcast.
 */
public final class SwordBotService {

    private final Plugin plugin;
    private final ArenaService arenaService;
    private final KitService kitService;
    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;
    private final SoundService soundService;
    private final Map<UUID, BotFight> fights = new ConcurrentHashMap<>();

    private record BotFight(UUID playerId, UUID arenaId, Mannequin bot, BotDifficulty difficulty,
                            String kitId, BukkitTask task, long startedAtMs) {
    }

    public SwordBotService(Plugin plugin, ArenaService arenaService, KitService kitService,
                           LobbyService lobbyService, PlayerStateManager stateManager,
                           SoundService soundService) {
        this.plugin = plugin;
        this.arenaService = arenaService;
        this.kitService = kitService;
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
        this.soundService = soundService;
    }

    public boolean isFighting(UUID playerId) {
        return fights.containsKey(playerId);
    }

    public void start(Player player, String kitId, BotDifficulty difficulty) {
        if (player == null || fights.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("既に Bot 対戦中です。", NamedTextColor.RED));
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            player.sendMessage(Component.text("ロビーでのみ Bot と対戦できます。", NamedTextColor.RED));
            return;
        }
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || !kit.enabled()) {
            player.sendMessage(Component.text("キットが見つかりません。", NamedTextColor.RED));
            return;
        }
        UUID matchId = UUID.randomUUID();
        var reserve = (kit.arenaName() == null || kit.arenaName().isBlank() || "any".equalsIgnoreCase(kit.arenaName()))
                ? arenaService.reserve(ArenaType.DUEL, matchId)
                : arenaService.reserveNamed(kit.arenaName(), matchId);
        reserve.thenAccept(opt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                opt.ifPresent(inst -> arenaService.release(inst.id()));
                return;
            }
            ArenaInstance arena = opt.orElse(null);
            if (arena == null) {
                player.sendMessage(Component.text("空きアリーナがありません。", NamedTextColor.RED));
                return;
            }
            beginFight(player, kit, difficulty, arena);
        }));
    }

    private void beginFight(Player player, KitDefinition kit, BotDifficulty difficulty, ArenaInstance arena) {
        try {
            stateManager.transition(player.getUniqueId(), PlayerState.FIGHTING);
        } catch (Exception e) {
            arenaService.release(arena.id());
            player.sendMessage(Component.text("今は開始できません。", NamedTextColor.RED));
            return;
        }
        Location spawnA = arenaService.spawnA(arena);
        Location spawnB = arenaService.spawnB(arena);
        SafeTeleport.teleport(player, spawnA);
        kitService.apply(player, kit, null);
        PlayerVitals.applyCombatStart(player, kit.maxHealth());

        Mannequin bot = spawnB.getWorld().spawn(spawnB, Mannequin.class, m -> {
            m.setImmovable(false);
            m.setGravity(true);
            m.setSilent(true);
            m.setCanPickupItems(false);
            m.setRemoveWhenFarAway(false);
            m.setPersistent(true);
            m.setCollidable(true);
            m.customName(Component.text("Bot [" + difficulty.name() + "]", NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            m.setCustomNameVisible(true);
            m.setDescription(heartLabel(kit.maxHealth()));
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            if (m.getAttribute(Attribute.MAX_HEALTH) != null) {
                m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(kit.maxHealth());
            }
            m.setHealth(kit.maxHealth());
            if (m.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                m.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.28);
            }
            equipSwordBot(m);
        });

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> tick(player.getUniqueId()), 1L, 1L);
        fights.put(player.getUniqueId(),
                new BotFight(player.getUniqueId(), arena.id(), bot, difficulty, kit.name(), task,
                        System.currentTimeMillis()));

        player.showTitle(Title.title(
                Component.text("⚔ START ⚔", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                Component.text("vs " + difficulty.name(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(200))));
        soundService.play(player, "match-start");
    }

    public void stop(UUID playerId, boolean victory) {
        BotFight fight = fights.remove(playerId);
        if (fight == null) {
            return;
        }
        fight.task().cancel();
        if (fight.bot() != null && fight.bot().isValid()) {
            fight.bot().remove();
        }
        if (fight.arenaId() != null) {
            arenaService.release(fight.arenaId());
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            if (victory) {
                player.showTitle(Title.title(
                        Component.text("🏆 WIN 🏆", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(400))));
                soundService.play(player, "match-end-levelup");
            } else {
                player.showTitle(Title.title(
                        Component.text("LOSE", NamedTextColor.BLUE).decorate(TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(400))));
                soundService.play(player, "match-end-anvil");
            }
            stateManager.resetToLobby(playerId);
            lobbyService.sendToLobby(player, true);
        }
    }

    public void onPlayerQuit(UUID playerId) {
        stop(playerId, false);
    }

    private void tick(UUID playerId) {
        BotFight fight = fights.get(playerId);
        if (fight == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        Mannequin bot = fight.bot();
        if (player == null || !player.isOnline() || bot == null || !bot.isValid() || bot.isDead()) {
            stop(playerId, bot != null && bot.isDead());
            return;
        }
        if (player.getHealth() <= 0.0d) {
            stop(playerId, false);
            return;
        }
        if (player.getWorld() != bot.getWorld()) {
            stop(playerId, false);
            return;
        }

        bot.setDescription(heartLabel(bot.getHealth()));

        BotDifficulty d = fight.difficulty();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location eye = bot.getEyeLocation();
        Location target = player.getLocation().add(0, 1.0, 0);
        Vector to = target.toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 0.0001) {
            return;
        }
        to.normalize();
        if (d.aimError() > 0) {
            to.add(new Vector(
                    (rng.nextDouble() - 0.5) * d.aimError(),
                    (rng.nextDouble() - 0.5) * d.aimError() * 0.4,
                    (rng.nextDouble() - 0.5) * d.aimError()));
            if (to.lengthSquared() > 0.0001) {
                to.normalize();
            }
        }
        Location look = eye.clone().setDirection(to);
        bot.setRotation(look.getYaw(), look.getPitch());

        double distance = bot.getLocation().distance(player.getLocation());
        boolean wantCrit = distance < 2.6 && rng.nextDouble() < d.critChance() && bot.isOnGround();
        boolean wantWtap = rng.nextDouble() < d.wtapChance();
        boolean wantStap = d.staple() && distance < 2.2 && rng.nextDouble() < 0.25;

        Vector motion = to.clone().setY(0);
        if (motion.lengthSquared() > 0.0001) {
            motion.normalize();
        }
        if (d.strafe() && distance < 3.5) {
            Vector side = new Vector(-motion.getZ(), 0, motion.getX());
            if (rng.nextBoolean()) {
                side.multiply(-1);
            }
            motion.add(side.multiply(0.55)).normalize();
        }
        if (wantStap) {
            motion.multiply(-0.35);
        } else if (distance > 2.8) {
            motion.multiply(0.28);
        } else if (wantWtap) {
            motion.multiply(0.08);
        } else {
            motion.multiply(0.18);
        }
        if (wantCrit) {
            motion.setY(0.42);
        } else {
            motion.setY(bot.getVelocity().getY());
        }
        bot.setVelocity(motion);

        long tick = (System.currentTimeMillis() - fight.startedAtMs()) / 50L;
        int period = Math.max(8, 10 + d.reactionTicks() - (int) (d.attackCharge() * 4));
        if (tick % period == 0 && distance < 3.2) {
            bot.attack(player);
            if (wantWtap) {
                Vector v = bot.getVelocity();
                bot.setVelocity(new Vector(v.getX() * 0.35, v.getY(), v.getZ() * 0.35));
            }
        }
    }

    /** Hearts remaining, e.g. {@code ♥2.4} in red (below-name mannequin description). */
    static Component heartLabel(double healthPoints) {
        double hearts = Math.max(0.0d, healthPoints) / 2.0d;
        return Component.text("♥" + formatHearts(hearts), NamedTextColor.RED);
    }

    static String formatHearts(double hearts) {
        if (Math.abs(hearts - Math.rint(hearts)) < 0.05d) {
            return String.valueOf((int) Math.rint(hearts));
        }
        return String.format(Locale.US, "%.1f", hearts);
    }

    private static void equipSwordBot(LivingEntity bot) {
        EntityEquipment eq = bot.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        eq.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        eq.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }
}
