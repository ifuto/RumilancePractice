package com.rumilance.practice.cosmetic.kill;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * A paid kill-effect cosmetic played at a victim's death position when the killer has one
 * selected. Effects are intentionally short and self-contained (a handful of particle rings /
 * bursts, an optional sound) so they never affect gameplay or linger. All kill effects are
 * VIP+ only ({@link #NONE} is the free "off" choice).
 *
 * <p>Each effect is rendered in two phases: {@link #play} drives the main burst immediately;
 * {@link #durationTicks()} / {@link #playTick} allow a short multi-tick expansion when needed.
 */
public record KillEffect(
        String id,
        String displayName,
        Material icon,
        Particle particle,
        Sound sound,
        float volume,
        float pitch,
        Shape shape,
        double radius,
        int durationTicks
) {

    public enum Shape {
        /** Nothing beyond the initial burst (one-shot). */
        NONE,
        /** Expanding horizontal ring. */
        RING,
        /** Vertical helix twisting upward. */
        HELIX,
        /** Straight column rising from the ground. */
        COLUMN,
        /** Filled sphere shell. */
        SPHERE,
        /** Simple one-shot explosion burst. */
        BURST
    }

    public static final KillEffect NONE =
            new KillEffect("none", "<gray>None", Material.BARRIER, null, null, 0f, 0f, Shape.NONE, 0d, 0);

    /** The 30 paid effects. Particle names use the Bukkit 1.21 enum. */
    private static final List<KillEffect> PAID = List.of(
            mk("flame",         "<gold>Flame",          Material.BLAZE_POWDER,     Particle.FLAME,                 Sound.ENTITY_BLAZE_SHOOT,    Shape.RING,   1.4, 12),
            mk("soul",          "<aqua>Soul Fire",      Material.SOUL_TORCH,       Particle.SOUL_FIRE_FLAME,       Sound.PARTICLE_SOUL_ESCAPE,  Shape.HELIX,  1.3, 16),
            mk("heart",         "<light_purple>Hearts", Material.POPPY,            Particle.HEART,                 Sound.ENTITY_PLAYER_LEVELUP, Shape.COLUMN, 1.0, 14),
            mk("portal",        "<dark_purple>Portal",  Material.ENDER_EYE,        Particle.PORTAL,                Sound.BLOCK_PORTAL_TRAVEL,   Shape.SPHERE, 1.5, 14),
            mk("ender",         "<dark_green>Ender",    Material.ENDER_PEARL,      Particle.REVERSE_PORTAL,        Sound.ENTITY_ENDERMAN_TELEPORT, Shape.BURST, 1.2, 10),
            mk("crit",          "<white>Crit Storm",    Material.IRON_SWORD,       Particle.CRIT,                  Sound.ENTITY_PLAYER_ATTACK_CRIT, Shape.RING, 1.5, 12),
            mk("magiccrit",     "<purple>Magic Crit",   Material.NETHER_STAR,      Particle.ENCHANTED_HIT,         Sound.ENCHANT_THORNS_HIT,    Shape.SPHERE, 1.4, 12),
            mk("witch",         "<dark_purple>Witch",   Material.SPLASH_POTION,    Particle.WITCH,                 Sound.ENTITY_WITCH_THROW,    Shape.BURST,  1.2, 10),
            mk("happy",         "<yellow>Happy Villager", Material.EMERALD,       Particle.HAPPY_VILLAGER,        Sound.ENTITY_VILLAGER_YES,   Shape.COLUMN, 1.0, 14),
            mk("angry",         "<red>Angry Villager",  Material.FIRE_CHARGE,      Particle.ANGRY_VILLAGER,        Sound.ENTITY_VILLAGER_NO,    Shape.BURST,  1.2, 8),
            mk("note",          "<aqua>Music",          Material.NOTE_BLOCK,       Particle.NOTE,                  Sound.BLOCK_NOTE_BLOCK_PLING, Shape.RING,  1.6, 12),
            mk("enchant",       "<light_purple>Enchant", Material.ENCHANTING_TABLE, Particle.ENCHANT,              Sound.BLOCK_ENCHANTMENT_TABLE_USE, Shape.HELIX, 1.3, 16),
            mk("cloud",         "<gray>Cloud",          Material.WHITE_WOOL,       Particle.CLOUD,                 Sound.ENTITY_FIREWORK_ROCKET_BLAST, Shape.RING, 1.5, 12),
            mk("poof",          "<white>Poof",          Material.SNOWBALL,         Particle.POOF,                  Sound.ENTITY_ITEM_PICKUP,    Shape.BURST,  1.2, 8),
            mk("explosion",     "<dark_red>Explosion",  Material.TNT,              Particle.EXPLOSION,             Sound.ENTITY_GENERIC_EXPLODE, Shape.BURST, 1.6, 6),
            mk("ember",         "<gold>Ember",          Material.FLINT_AND_STEEL,  Particle.LAVA,                  Sound.BLOCK_LAVA_POP,        Shape.COLUMN, 1.1, 14),
            mk("smoke",         "<dark_gray>Smoke",     Material.COAL,             Particle.LARGE_SMOKE,           Sound.BLOCK_FIRE_EXTINGUISH, Shape.COLUMN, 1.2, 16),
            mk("drip",          "<blue>Drip",           Material.WATER_BUCKET,     Particle.DRIPPING_DRIPSTONE_WATER, Sound.BLOCK_POINTED_DRIPSTONE_LAND, Shape.RING, 1.3, 12),
            mk("glow",          "<yellow>Glow",         Material.GLOWSTONE_DUST,   Particle.GLOW,                  Sound.BLOCK_AMETHYST_BLOCK_CHIME, Shape.SPHERE, 1.4, 12),
            mk("sculk",         "<dark_teal>Sculk",     Material.SCULK,            Particle.SCULK_SOUL,            Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, Shape.SPHERE, 1.5, 14),
            mk("wax",           "<gold>Wax On",         Material.HONEYCOMB,        Particle.WAX_ON,                Sound.BLOCK_HONEY_BLOCK_BREAK, Shape.BURST, 1.2, 10),
            mk("totem",         "<gold>Totem",          Material.TOTEM_OF_UNDYING, Particle.TOTEM_OF_UNDYING,      Sound.ITEM_TOTEM_USE,        Shape.HELIX,  1.4, 18),
            mk("trial",         "<aqua>Trial Spawner",  Material.TRIAL_KEY,        Particle.TRIAL_SPAWNER_DETECTION, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, Shape.RING, 1.5, 12),
            mk("cherry",        "<pink>Cherry Blossom", Material.CHERRY_SAPLING,   Particle.CHERRY_LEAVES,         Sound.BLOCK_CHERRY_LEAVES_BREAK, Shape.HELIX, 1.3, 16),
            mk("spore",         "<green>Spore Blossom", Material.SPORE_BLOSSOM,    Particle.SPORE_BLOSSOM_AIR,     Sound.BLOCK_SPORE_BLOSSOM_BREAK, Shape.COLUMN, 1.1, 14),
            mk("white_ash",     "<gray>White Ash",      Material.BONE_MEAL,        Particle.WHITE_ASH,             Sound.BLOCK_BASALT_BREAK,    Shape.COLUMN, 1.1, 14),
            mk("electric",      "<aqua>Electric",       Material.LIGHTNING_ROD,    Particle.ELECTRIC_SPARK,        Sound.ENTITY_LIGHTNING_BOLT_IMPACT, Shape.BURST, 1.5, 8),
            mk("firework",      "<red>Firework",        Material.FIREWORK_ROCKET,  Particle.FIREWORK,              Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, Shape.SPHERE, 1.6, 16),
            mk("damage",        "<dark_red>Damage",     Material.REDSTONE,         Particle.DAMAGE_INDICATOR,      Sound.ENTITY_PLAYER_HURT,    Shape.BURST,  1.3, 8),
            mk("dragonbreath",  "<purple>Dragon Breath", Material.DRAGON_BREATH,  Particle.DRAGON_BREATH,         Sound.ENTITY_ENDER_DRAGON_SHOOT, Shape.SPHERE, 1.5, 16)
    );

    private static KillEffect mk(String id, String displayName, Material icon, Particle particle,
                                 Sound sound, Shape shape, double radius, int durationTicks) {
        return new KillEffect(id, displayName, icon, particle, sound, 1.0f, 1.0f, shape, radius, durationTicks);
    }

    /** All selectable effects including the free "None" option first. */
    public static List<KillEffect> all() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(NONE), PAID.stream()).toList();
    }

    public static KillEffect byId(String id) {
        if (id == null || id.isBlank() || "none".equalsIgnoreCase(id)) {
            return NONE;
        }
        for (KillEffect effect : PAID) {
            if (effect.id().equalsIgnoreCase(id)) {
                return effect;
            }
        }
        return NONE;
    }

    public boolean isNone() {
        return this == NONE || particle == null;
    }

    /** Immediately render the effect at {@code origin} (victim location, eye/feet height). */
    public void play(Location origin) {
        if (isNone() || origin.getWorld() == null) {
            return;
        }
        World world = origin.getWorld();
        Location at = origin.clone().add(0, 1.0, 0);
        if (sound != null) {
            world.playSound(at, sound, volume, pitch);
        }
        // Every effect starts with a small central burst for impact.
        world.spawnParticle(particle, at, 12, 0.3, 0.4, 0.3, 0.01);
    }

    /**
     * Drives the multi-tick expansion (called once per tick for {@link #durationTicks()} ticks).
     * {@code progress} is 0..1 across the lifetime. Kept light so 30+ simultaneous finishes on a
     * crowded FFA stay cheap.
     */
    public void playTick(Location origin, int tick) {
        if (isNone() || origin.getWorld() == null || shape == Shape.NONE || shape == Shape.BURST) {
            return;
        }
        World world = origin.getWorld();
        double progress = durationTicks <= 0 ? 1.0 : (double) tick / durationTicks;
        double r = radius * (shape == Shape.RING || shape == Shape.SPHERE ? progress : radius);
        Location base = origin.clone().add(0, 0.2, 0);
        switch (shape) {
            case RING -> ring(world, base, r, tick);
            case HELIX -> helix(world, base, progress, tick);
            case COLUMN -> column(world, base, progress);
            case SPHERE -> sphere(world, base, r);
            default -> { }
        }
    }

    private void ring(World world, Location base, double r, int tick) {
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i + tick * 12.0);
            world.spawnParticle(particle,
                    base.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r),
                    1, 0, 0, 0, 0);
        }
    }

    private void helix(World world, Location base, double progress, int tick) {
        int points = 12;
        double height = 1.8 * progress;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i + tick * 24.0);
            double rr = radius * (1.0 - progress * 0.6);
            world.spawnParticle(particle,
                    base.clone().add(Math.cos(angle) * rr, height, Math.sin(angle) * rr),
                    1, 0, 0, 0, 0);
        }
    }

    private void column(World world, Location base, double progress) {
        double height = 2.0 * progress;
        for (double y = 0; y < height; y += 0.25) {
            world.spawnParticle(particle, base.clone().add(0, y, 0), 1, 0.12, 0, 0.12, 0);
        }
    }

    private void sphere(World world, Location base, double r) {
        for (int i = 0; i < 20; i++) {
            Vector v = new Vector(
                    (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 2).normalize().multiply(r);
            world.spawnParticle(particle, base.clone().add(v), 1, 0, 0, 0, 0);
        }
    }
}
