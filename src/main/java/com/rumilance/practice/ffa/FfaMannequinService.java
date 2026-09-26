package com.rumilance.practice.ffa;

import com.rumilance.practice.ffa.FfaMannequinLoadout.Piece;
import com.rumilance.practice.ffa.FfaMannequinLoadout.Slot;
import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /bot} inside FFA: a mannequin training dummy instead of the Quantum map bot.
 *
 * <p>One dummy per player, spawned two blocks in front of them and facing back at them:
 * full unbreakable netherite (Protection 4 everywhere, Blast Protection 4 on the leggings only),
 * a totem of undying in <b>both</b> hands, and a bold aqua {@code NARENA BOT} nametag — the exact
 * numbers live in {@link FfaMannequinLoadout} so a plain JUnit suite can pin them. Running
 * {@code /bot} again removes it, and so does leaving FFA, being pulled into a match or
 * disconnecting: a dummy must never outlive the session that asked for it.</p>
 *
 * <p><b>Why it cannot be killed.</b> Both hands hold real totems, so a lethal frame runs the
 * genuine vanilla pop (real sound + particles, the item is consumed, the body revives at half a
 * heart) and {@link #onResurrect} restocks the hand on the next tick. Should the hands ever be
 * empty, {@link #onDeath} cancels the death and re-creates the body: a Paper Mannequin that
 * survives a cancelled lethal frame freezes client-side (the client already played the death) and
 * ignores every later hit and knockback — the exact bug the AFK BOT Crystal room documents. The
 * slow heal in {@link #tickHeal} brings a popped dummy back to 20 HP a few seconds after the
 * player stops hitting it, so it is ready for the next combo without a re-spawn.</p>
 *
 * <p><b>Default OFF, opt-in per arena.</b> The dummy is an arena feature: {@code /practiceadmin} →
 * FFA Config → the arena's detail page has an {@code FFA Bot} toggle persisted as
 * {@code arenas.<id>.settings.bot} in ffa.yml, OFF for every arena until an admin turns it on (and
 * OFF for arenas created later). Inside an arena whose toggle is OFF, {@code /bot} spawns nothing at
 * all — neither the mannequin nor the Quantum combat bot — and says why.</p>
 *
 * <p><b>Damage is untouched.</b> Every frame lands exactly as vanilla computed it: crystals,
 * respawn anchors, beds, TNT, arrows, potions and melee — from the owner <i>and</i> from anybody
 * else in the arena. Nothing is filtered by attacker and no attribution lookup is involved, so a
 * dummy in a shared FFA is a shared punching bag. The only frames this service interferes with are
 * one another plugin already cancelled (re-opened, so the dummy can never be made immune by a
 * global guard) and the void (cancelled and the dummy handed back to its owner, because a dummy
 * that falls out of the arena is gone for good and its death rebuild would loop).</p>
 */
public final class FfaMannequinService implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;
    private final FfaService ffaService;
    private final MessageService messages;
    /** owner -> dummy. One per player; {@code /bot} again removes it. */
    private final Map<UUID, Mannequin> bots = new ConcurrentHashMap<>();
    /** dummy uuid -> owner, so the damage/death listeners stay O(1). */
    private final Map<UUID, UUID> owners = new ConcurrentHashMap<>();
    /** dummy uuid -> last time it took damage, for the out-of-combat heal. */
    private final Map<UUID, Long> lastHurtMs = new ConcurrentHashMap<>();
    private BukkitTask healTask;

    public FfaMannequinService(Plugin plugin, FfaService ffaService, MessageService messages) {
        this.plugin = plugin;
        this.ffaService = ffaService;
        this.messages = messages;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Any FFA exit (leave, disconnect, pull into a match) drops the dummy with it.
        ffaService.addLeaveListener(this::removeFor);
        this.healTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeal, 20L, 20L);
    }

    /** True while the player is inside an FFA arena, whatever that arena's FFA Bot toggle says. */
    public boolean inFfa(Player player) {
        return player != null && ffaService.isInFfa(player.getUniqueId());
    }

    /**
     * True when the arena the player is standing in has FFA Bot switched on
     * ({@code /practiceadmin} → FFA Config → FFA Bot, default OFF, persisted per arena).
     */
    public boolean arenaAllows(Player player) {
        return player != null && arenaAllows(player.getUniqueId());
    }

    /** Same check by id, for the upkeep sweep that takes dummies away from a switched-off arena. */
    private boolean arenaAllows(UUID playerId) {
        return playerId != null
                && ffaService.arenaOf(playerId).map(ffaService::botEnabled).orElse(false);
    }

    /**
     * {@code /bot} inside FFA. Returns true when the command was consumed, i.e. whenever the player
     * is in an FFA arena: with the arena's FFA Bot toggle ON it spawns/removes the dummy, with it
     * OFF it explains that the feature is off for this arena. Returning false is what lets
     * {@code /bot} outside FFA fall through to the Quantum bot unchanged.
     */
    public boolean handleBotCommand(Player player) {
        if (!inFfa(player)) {
            return false;
        }
        if (!arenaAllows(player)) {
            // Turning the arena's toggle off also takes any dummy that is already out with it.
            removeFor(player.getUniqueId());
            send(player, "ffa-bot.arena-disabled");
            return true;
        }
        toggle(player);
        return true;
    }

    /** {@code /bot} in FFA: spawn the dummy, or remove it when this player already has one. */
    public void toggle(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (bots.containsKey(id)) {
            removeFor(id);
            send(player, "ffa-bot.removed");
            return;
        }
        spawn(player);
    }

    /** Spawns this player's dummy (removing any stale one first). */
    public void spawn(Player player) {
        UUID id = player.getUniqueId();
        removeFor(id);
        Location at = spawnLocation(player);
        World world = at.getWorld();
        if (world == null) {
            send(player, "ffa-bot.failed");
            return;
        }
        Mannequin bot = spawnBody(world, at);
        if (bot == null) {
            send(player, "ffa-bot.failed");
            return;
        }
        // Registered BEFORE the equipment goes on: the damage/death listeners match by uuid and a
        // hit landing in the same tick must already find its owner.
        track(id, bot);
        try {
            bot.setCustomNameVisible(true);
            bot.customName(LEGACY.deserialize(FfaMannequinLoadout.NAMETAG_LEGACY));
            AttributeInstance maxHealth = bot.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(FfaMannequinLoadout.MAX_HEALTH);
            }
            bot.setHealth(FfaMannequinLoadout.MAX_HEALTH);
            bot.setFireTicks(0);
            equip(bot);
        } catch (Throwable ignored) {
            // A running server keeps the dummy even if one cosmetic call is unavailable.
        }
        send(player, "ffa-bot.spawned");
    }

    /** Removes this player's dummy (no-op when there is none). */
    public void removeFor(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Mannequin bot = bots.remove(playerId);
        if (bot == null) {
            return;
        }
        untrack(bot);
        if (bot.isValid()) {
            bot.remove();
        }
    }

    /** True when this player currently has a dummy out. */
    public boolean hasBot(UUID playerId) {
        return playerId != null && bots.containsKey(playerId);
    }

    /** Removes every dummy (plugin disable / reload). */
    public void shutdown() {
        for (UUID id : List.copyOf(bots.keySet())) {
            removeFor(id);
        }
        bots.clear();
        owners.clear();
        lastHurtMs.clear();
        if (healTask != null) {
            healTask.cancel();
            healTask = null;
        }
    }

    // ---------------------------------------------------------------- placement

    /**
     * Two blocks along the player's facing (never upward — the dummy stands on their level),
     * turned around to look back at them, and kept inside the arena when it declares a region.
     * Falls back to the player's own spot when the landing block is not passable.
     */
    private Location spawnLocation(Player player) {
        Location base = player.getLocation();
        Vector facing = base.getDirection().setY(0);
        Location at = base.clone();
        if (facing.lengthSquared() > 1.0e-4d) {
            at.add(facing.normalize().multiply(FfaMannequinLoadout.SPAWN_DISTANCE));
        }
        Optional<FfaService.FfaArena> arena = ffaService.arenaOf(player.getUniqueId())
                .flatMap(ffaService::get);
        if (arena.isPresent() && arena.get().region() != null) {
            at = arena.get().region().clampHorizontal(at);
        }
        if (!at.getBlock().isPassable() || !at.clone().add(0, 1, 0).getBlock().isPassable()) {
            at = base.clone();
        }
        at.setYaw(base.getYaw() + 180.0f);   // face the player who spawned it
        at.setPitch(0.0f);
        return at;
    }

    /** The Paper mannequin body, configured the way the AFK BOT Crystal room configures it. */
    private Mannequin spawnBody(World world, Location at) {
        try {
            return world.spawn(at, Mannequin.class, m -> {
                m.setImmovable(false);        // knockback must move it, like any other body
                m.setGravity(true);           // never a floating statue
                m.setSilent(true);
                m.setCanPickupItems(false);
                m.setRemoveWhenFarAway(false);
                m.setCollidable(true);
                m.setPersistent(false);       // never written to disk: no dummies after a restart
            });
        } catch (RuntimeException error) {   // IllegalArgumentException is one of these
            plugin.getLogger().warning("[N Arena][FfaBot] mannequin spawn failed: " + error);
            return null;
        }
    }

    private void track(UUID owner, Mannequin bot) {
        bots.put(owner, bot);
        owners.put(bot.getUniqueId(), owner);
        lastHurtMs.put(bot.getUniqueId(), System.currentTimeMillis());
    }

    private void untrack(Mannequin bot) {
        owners.remove(bot.getUniqueId());
        lastHurtMs.remove(bot.getUniqueId());
    }

    // ---------------------------------------------------------------- equipment

    /** Full unbreakable netherite + a totem in each hand. */
    private void equip(Mannequin bot) {
        EntityEquipment equipment = bot.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setHelmet(piece(FfaMannequinLoadout.piece(Slot.HELMET)));
        equipment.setChestplate(piece(FfaMannequinLoadout.piece(Slot.CHESTPLATE)));
        equipment.setLeggings(piece(FfaMannequinLoadout.piece(Slot.LEGGINGS)));
        equipment.setBoots(piece(FfaMannequinLoadout.piece(Slot.BOOTS)));
        Material totem = Material.matchMaterial(FfaMannequinLoadout.HAND_MATERIAL);
        if (totem != null) {
            equipment.setItemInMainHand(new ItemStack(totem));
            equipment.setItemInOffHand(new ItemStack(totem));
        }
        zeroDropChances(bot, equipment);
    }

    private static ItemStack piece(Piece spec) {
        Material material = Material.matchMaterial(spec.material());
        ItemStack item = new ItemStack(material == null ? Material.NETHERITE_CHESTPLATE : material);
        Enchantment enchantment = FfaMannequinLoadout.BLAST_PROTECTION.equals(spec.enchantment())
                ? Enchantment.BLAST_PROTECTION
                : Enchantment.PROTECTION;
        item.addUnsafeEnchantment(enchantment, spec.level());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (spec.unbreakable()) {
                meta.setUnbreakable(true);                 // 耐久無限
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Zeroes equipment drop chances — but only for Mob equipment. Paper's Mannequin equipment
     * rejects drop chances ("Cannot set drop chance for non-Mob entity"), and a Mannequin never
     * drops gear anyway (its death is cancelled and the drops cleared).
     */
    private static void zeroDropChances(Entity owner, EntityEquipment equipment) {
        if (!(owner instanceof org.bukkit.entity.Mob)) {
            return;
        }
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
        equipment.setItemInOffHandDropChance(0f);
    }

    // ---------------------------------------------------------------- damage

    /**
     * Damage lands exactly as vanilla computed it: crystals, respawn anchors, beds, TNT, arrows,
     * potions and melee — from the owner <b>and</b> from anybody else in the arena. No attacker is
     * filtered out and nothing is attributed, so the dummy is a shared punching bag. HIGHEST with no
     * {@code ignoreCancelled} on purpose: a global guard may have cancelled the frame first, and
     * then the dummy would be immune to the very practice it exists for, so it is re-opened here.
     * The only frame cancelled is the void, which would carry the dummy out of the arena for good.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin bot)) {
            return;
        }
        UUID owner = owners.get(bot.getUniqueId());
        if (owner == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            // Below the arena the dummy is gone for good (and its death rebuild would loop in the
            // void): cancel and hand it back to its owner on the next tick.
            event.setCancelled(true);
            pullBackToOwner(bot, owner);
            return;
        }
        if (event.isCancelled()) {
            event.setCancelled(false);  // nobody may switch the dummy's damage off
        }
        lastHurtMs.put(bot.getUniqueId(), System.currentTimeMillis());
        // Both hands hold totems, so a lethal frame is left ALIVE here on purpose: vanilla pops
        // the real totem and onResurrect restocks it. onDeath is the net for empty hands.
    }

    /** Returns a void-saved dummy to its owner (or to the arena spawn when they are gone). */
    private void pullBackToOwner(Mannequin bot, UUID owner) {
        UUID botId = bot.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!owners.containsKey(botId) || !bot.isValid()) {
                return;
            }
            Player player = Bukkit.getPlayer(owner);
            Location to = player != null
                    ? player.getLocation()
                    : ffaService.arenaOf(owner).flatMap(ffaService::get)
                            .map(FfaService.FfaArena::spawn)
                            .orElse(null);
            if (to != null && to.getWorld() != null) {
                bot.teleport(to);
            }
        });
    }

    /** A vanilla pop consumed one totem — put a fresh one back in each hand on the next tick. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Mannequin bot)) {
            return;
        }
        UUID owner = owners.get(bot.getUniqueId());
        if (owner == null) {
            return;
        }
        if (event.isCancelled()) {
            event.setCancelled(false);  // a global guard must not void the practice totem
        }
        UUID botId = bot.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (owners.containsKey(botId) && bot.isValid()) {
                restock(bot);
            }
        });
    }

    /**
     * The net for a dummy that died with empty hands: cancel the death, clear the drops and
     * re-create the body — a Mannequin that survives a cancelled lethal frame freezes client-side
     * and stops reacting to hits, so the body is rebuilt instead of being kept.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mannequin bot)) {
            return;
        }
        UUID owner = owners.get(bot.getUniqueId());
        if (owner == null) {
            return;
        }
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        rebuild(bot, owner);
    }

    /** Pops a totem that is no longer there: fresh body at the same spot, HP 1, hands restocked. */
    private void rebuild(Mannequin old, UUID owner) {
        Location at = old.getLocation().clone();
        World world = at.getWorld();
        EntityEquipment previous = old.getEquipment();
        ItemStack helmet = previous == null ? null : previous.getHelmet();
        ItemStack chestplate = previous == null ? null : previous.getChestplate();
        ItemStack leggings = previous == null ? null : previous.getLeggings();
        ItemStack boots = previous == null ? null : previous.getBoots();
        untrack(old);
        bots.remove(owner);
        if (old.isValid()) {
            old.remove();
        }
        if (world == null) {
            return;
        }
        // Never rebuild inside the void: the death that got us here would just repeat forever.
        if (at.getY() < world.getMinHeight()) {
            Player player = Bukkit.getPlayer(owner);
            if (player == null) {
                return;
            }
            at = player.getLocation().clone();
            world = at.getWorld();
            if (world == null) {
                return;
            }
        }
        Mannequin fresh = spawnBody(world, at);
        if (fresh == null) {
            return;
        }
        track(owner, fresh);
        try {
            fresh.setCustomNameVisible(true);
            fresh.customName(LEGACY.deserialize(FfaMannequinLoadout.NAMETAG_LEGACY));
            AttributeInstance maxHealth = fresh.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(FfaMannequinLoadout.MAX_HEALTH);
            }
            fresh.setHealth(1.0d);      // exactly where a totem pop leaves you
            fresh.setFireTicks(0);
            EntityEquipment equipment = fresh.getEquipment();
            if (equipment != null) {
                equipment.setHelmet(orDefault(helmet, Slot.HELMET));
                equipment.setChestplate(orDefault(chestplate, Slot.CHESTPLATE));
                equipment.setLeggings(orDefault(leggings, Slot.LEGGINGS));
                equipment.setBoots(orDefault(boots, Slot.BOOTS));
                zeroDropChances(fresh, equipment);
            }
            restock(fresh);
        } catch (Throwable ignored) {
        }
        world.playSound(at, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 80, 0.5, 1.0, 0.5, 0.4);
    }

    private static ItemStack orDefault(ItemStack carried, Slot slot) {
        return carried != null ? carried : piece(FfaMannequinLoadout.piece(slot));
    }

    /** A totem in each hand again, whatever a pop consumed. */
    private static void restock(Mannequin bot) {
        EntityEquipment equipment = bot.getEquipment();
        if (equipment == null) {
            return;
        }
        Material totem = Material.matchMaterial(FfaMannequinLoadout.HAND_MATERIAL);
        if (totem == null) {
            return;
        }
        ItemStack main = equipment.getItemInMainHand();
        if (main == null || main.getType() != totem) {
            equipment.setItemInMainHand(new ItemStack(totem));
        }
        ItemStack off = equipment.getItemInOffHand();
        if (off == null || off.getType() != totem) {
            equipment.setItemInOffHand(new ItemStack(totem));
        }
        zeroDropChances(bot, equipment);
    }

    // ---------------------------------------------------------------- upkeep

    /**
     * Once a second: drop dummies that are no longer wanted (dead body, or an arena whose FFA Bot
     * toggle was switched off while one was out) and heal the rest — a popped dummy is back to full
     * a few seconds after the hits stop.
     */
    private void tickHeal() {
        if (bots.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Mannequin> entry : bots.entrySet()) {
            Mannequin bot = entry.getValue();
            if (bot == null || !bot.isValid()) {
                removeFor(entry.getKey());
                continue;
            }
            // Arena switched off under a live dummy. Checked only while the owner is still inside
            // FFA: leaving is the FFA leave listener's job, and an arena lookup that comes up empty
            // mid-teleport must not throw away a dummy that is still wanted.
            if (ffaService.isInFfa(entry.getKey()) && !arenaAllows(entry.getKey())) {
                removeFor(entry.getKey());
                continue;
            }
            Long hurt = lastHurtMs.get(bot.getUniqueId());
            if (hurt != null && now - hurt < FfaMannequinLoadout.HEAL_DELAY_MS) {
                continue;
            }
            if (bot.getHealth() < FfaMannequinLoadout.MAX_HEALTH) {
                bot.setHealth(Math.min(FfaMannequinLoadout.MAX_HEALTH,
                        bot.getHealth() + FfaMannequinLoadout.HEAL_PER_SECOND));
            }
            if (bot.getFireTicks() > 0) {
                bot.setFireTicks(0);
            }
            restock(bot);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeFor(event.getPlayer().getUniqueId());
    }

    private void send(Player player, String key) {
        if (messages != null && player != null) {
            messages.send(player, key);
        }
    }
}
