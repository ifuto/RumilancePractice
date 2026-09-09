package com.rumilance.practice.practice;

import com.rumilance.practice.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Live practice session for one player.
 */
public final class PracticeSession {

    public enum Phase {
        WAIT,
        COUNTDOWN,
        ACTIVE,
        ENDED
    }

    private final UUID playerId;
    private final String practiceId;
    private final PracticeType type;
    private Phase phase;
    private int durationSeconds = 10;
    private String layoutKey = "anchor_first";
    private PracticeAnkerStats ankerStats;
    private long activeEndsAtMs;
    private boolean placeBlocked;
    private BukkitTask timerTask;
    private Mannequin maceBot;
    private boolean botShieldRaised;
    private long botStunUntilMs;
    private int maceDensity;
    private int maceBreach;
    private int maceWindBurst;
    /** Sword / crystal combat bot (ITEM 41, Quantum-style practice bots). */
    private Mannequin combatBot;
    /** Totem pops on the crystal bot / kills on the sword bot. */
    private int botPops;
    /** Last time the combat bot took damage (regen tag). */
    private long botLastDamagedMs;
    /** Next tick the sword bot may attack. */
    private long botNextAttackMs;
    /** Sword-bot strafe direction (-1 / +1) and when it flips next. */
    private int botStrafeDir = 1;
    private long botStrafeFlipMs;
    /** Home spot the combat bot respawns at. */
    private Location botHome;
    /** End crystals the CRYSTAL bot placed itself (it is immune to their blasts). */
    private final java.util.Set<java.util.UUID> botCrystals = new java.util.HashSet<>();
    /** Blocks the combat bots placed mid-fight (pedestal, cobweb, lava...), reverted by TTL. */
    private final java.util.Map<org.bukkit.block.Block, BotBlock> botPlacedBlocks = new java.util.LinkedHashMap<>();
    /** Cooldowns / counters for the Quantum-parity combat abilities. */
    private final BotAbilityState abilities = new BotAbilityState();
    /** Quantum parity ("もってるアイテムだけ使う"): every special item the bot may use is a
     * COUNTED stock restocked on spawn — no phantom webs/lava/potions/rails appear anymore. */
    private final java.util.Map<org.bukkit.Material, Integer> botStock =
            new java.util.EnumMap<>(org.bukkit.Material.class);
    /** Current A* waypoints followed by the bot (from {@link BotPathFinder}). */
    private transient java.util.List<BotPathFinder.Node> botPath = java.util.List.of();
    private transient int botPathIndex;
    private transient long botPathRefreshMs;
    private transient final int[] botPathLastGoal = new int[3];
    private transient boolean botPathGoalSet;

    /** A block a bot placed mid-fight: after {@code ttlMs} it is reverted to AIR. */
    public record BotBlock(org.bukkit.Material type, long atMs, long ttlMs) {
    }

    /**
     * Per-fight cooldown timers and use counters for the Quantum-parity combat abilities
     * (crits, jump resets, escape pearls, golden apples, cobweb / water / lava tricks, axe
     * shield disables, crossbow + anchor mixups, far pearls, elytra engages, defensive blocks).
     * Owned by the session so a respawned bot restarts with an empty bag of tricks.
     */
    public static final class BotAbilityState {
        /** Next jump-crit the sword/pot bot may attempt (Quantum: sword/crit + pcrit gate). */
        private long nextCritMs;
        /** Next combo jump-reset hop after a landed hit (Quantum: sword/combo/jumpreset). */
        private long nextJumpResetMs;
        /** Next escape pearl when hurt and cornered (Quantum: passive/escape/pearl). */
        private long nextPearlMs;
        /** Next golden-apple chomp under pressure (Quantum: passive/gap). */
        private long nextGapMs;
        /** Next bow shot at a far target (Quantum: sword bowcharge). */
        private long nextBowMs;
        /** Next cobweb placed at the player's feet (Quantum: cobwebs/cobweb). */
        private long nextCobwebMs;
        /** Next self water-bucket save from fire / cobwebs (Quantum: cobwebs/water_main). */
        private long nextWaterMs;
        /** Next lava bucket under an airborne player (Quantum: cobwebs/empty_lava). */
        private long nextLavaMs;
        /** Next axe swing that disables the player's shield (Quantum: shield/disable). */
        private long nextAxeMs;
        /** Next crystal-bot crossbow snipe (Quantum: crystal/passive/crossbow). */
        private long nextCrossbowMs;
        /** Next respawn-anchor mixup (Quantum: g1gc/anchor). */
        private long nextAnchorMs;
        /** Next defensive block wall (Quantum: crystal/passive/block, cart/defenseplace). */
        private long nextDefenseBlockMs;
        /** Next long-range pearl engage (Quantum: mace_new/far_pearl). */
        private long nextFarPearlMs;
        /** Next wind-charge + pearl burst engage (Quantum: mace_new/wind_pearl). */
        private long nextWindPearlMs;
        /** Next elytra-style rocket engage (Quantum: mace_new/elytra). */
        private long nextElytraMs;
        /** Golden apples eaten this life (map caps its sustain, so do we: max 2). */
        private int gapUses;
        /** Harming splashes thrown since the last restock drink (map cap: 2). */
        private int potUses;

        public long nextCritMs() { return nextCritMs; }
        public void nextCritMs(long v) { nextCritMs = v; }
        public long nextJumpResetMs() { return nextJumpResetMs; }
        public void nextJumpResetMs(long v) { nextJumpResetMs = v; }
        public long nextPearlMs() { return nextPearlMs; }
        public void nextPearlMs(long v) { nextPearlMs = v; }
        public long nextGapMs() { return nextGapMs; }
        public void nextGapMs(long v) { nextGapMs = v; }
        public long nextBowMs() { return nextBowMs; }
        public void nextBowMs(long v) { nextBowMs = v; }
        public long nextCobwebMs() { return nextCobwebMs; }
        public void nextCobwebMs(long v) { nextCobwebMs = v; }
        public long nextWaterMs() { return nextWaterMs; }
        public void nextWaterMs(long v) { nextWaterMs = v; }
        public long nextLavaMs() { return nextLavaMs; }
        public void nextLavaMs(long v) { nextLavaMs = v; }
        public long nextAxeMs() { return nextAxeMs; }
        public void nextAxeMs(long v) { nextAxeMs = v; }
        public long nextCrossbowMs() { return nextCrossbowMs; }
        public void nextCrossbowMs(long v) { nextCrossbowMs = v; }
        public long nextAnchorMs() { return nextAnchorMs; }
        public void nextAnchorMs(long v) { nextAnchorMs = v; }
        public long nextDefenseBlockMs() { return nextDefenseBlockMs; }
        public void nextDefenseBlockMs(long v) { nextDefenseBlockMs = v; }
        public long nextFarPearlMs() { return nextFarPearlMs; }
        public void nextFarPearlMs(long v) { nextFarPearlMs = v; }
        public long nextWindPearlMs() { return nextWindPearlMs; }
        public void nextWindPearlMs(long v) { nextWindPearlMs = v; }
        public long nextElytraMs() { return nextElytraMs; }
        public void nextElytraMs(long v) { nextElytraMs = v; }
        public int gapUses() { return gapUses; }
        public void gapUses(int v) { gapUses = v; }
        public int potUses() { return potUses; }
        public void potUses(int v) { potUses = v; }

        /** A fresh life after a pop / takedown: keeps cooldowns, restores consumables. */
        public void resetConsumables() {
            gapUses = 0;
            potUses = 0;
        }
    }
    /** Until this timestamp the crystal bot is recovering (sprinting away). */
    private long botRetreatUntilMs;
    /**
     * Fight tuning: coarse presets + fully detailed parameters. Defaults to the map's
     * INTERMEDIATE rung — the one for a beginner-leaning intermediate player.
     */
    private BotDifficulty difficulty = BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE);
    /** When the ACTIVE bot fight started (results / console log). */
    private long matchStartMs;
    /** Primed TNT the CART bot threw (it is immune to their blasts). */
    private final java.util.Set<java.util.UUID> botTnt = new java.util.HashSet<>();
    /** Next time the netherite-pot bot may drink / throw. */
    private long botPotionUntilMs;
    /** Next time the cart bot may roll TNT. */
    private long botNextCartMs;
    /** Next time the mace bot may lunge (sprint-jump into a smash attack). */
    private long botNextLungeMs;
    /** Next time the mace bot may wind-charge itself into the air. */
    private long botNextWindMs;

    /** Disposable FAWE copy id; null when using shared template teleport. */
    private UUID cloneInstanceId;
    /** Active playable cuboid (pasted copy or shared template region). */
    private Cuboid activeRegion;
    /** Remapped spawn for this session's copy (or template spawn). */
    private Location activeSpawn;

    public PracticeSession(UUID playerId, String practiceId, PracticeType type) {
        this.playerId = playerId;
        this.practiceId = practiceId;
        this.type = type;
        // All rooms open in WAIT: bot fights run as proper matches (countdown on demand).
        this.phase = Phase.WAIT;
        if (type == PracticeType.ANKER) {
            this.ankerStats = new PracticeAnkerStats();
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public String practiceId() {
        return practiceId;
    }

    public PracticeType type() {
        return type;
    }

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public void cycleDuration() {
        durationSeconds = switch (durationSeconds) {
            case 5 -> 10;
            case 10 -> 15;
            case 15 -> 30;
            default -> 5;
        };
    }

    public String layoutKey() {
        return layoutKey;
    }

    public void setLayoutKey(String layoutKey) {
        this.layoutKey = layoutKey == null ? "anchor_first" : layoutKey;
    }

    public PracticeAnkerStats ankerStats() {
        return ankerStats;
    }

    public void resetAnkerStats() {
        this.ankerStats = new PracticeAnkerStats();
    }

    public long activeEndsAtMs() {
        return activeEndsAtMs;
    }

    public void setActiveEndsAtMs(long activeEndsAtMs) {
        this.activeEndsAtMs = activeEndsAtMs;
    }

    public boolean placeBlocked() {
        return placeBlocked;
    }

    public void setPlaceBlocked(boolean placeBlocked) {
        this.placeBlocked = placeBlocked;
    }

    public BukkitTask timerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public void cancelTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    public Mannequin maceBot() {
        return maceBot;
    }

    public void setMaceBot(Mannequin maceBot) {
        this.maceBot = maceBot;
    }

    public Mannequin combatBot() {
        return combatBot;
    }

    public void setCombatBot(Mannequin combatBot) {
        this.combatBot = combatBot;
    }

    public int botPops() {
        return botPops;
    }

    public void setBotPops(int botPops) {
        this.botPops = botPops;
    }

    public void incrementBotPops() {
        this.botPops++;
    }

    public long botLastDamagedMs() {
        return botLastDamagedMs;
    }

    public void setBotLastDamagedMs(long botLastDamagedMs) {
        this.botLastDamagedMs = botLastDamagedMs;
    }

    public long botNextAttackMs() {
        return botNextAttackMs;
    }

    public void setBotNextAttackMs(long botNextAttackMs) {
        this.botNextAttackMs = botNextAttackMs;
    }

    public int botStrafeDir() {
        return botStrafeDir;
    }

    public void setBotStrafeDir(int botStrafeDir) {
        this.botStrafeDir = botStrafeDir;
    }

    public long botStrafeFlipMs() {
        return botStrafeFlipMs;
    }

    public void setBotStrafeFlipMs(long botStrafeFlipMs) {
        this.botStrafeFlipMs = botStrafeFlipMs;
    }

    public long botNextLungeMs() {
        return botNextLungeMs;
    }

    public void setBotNextLungeMs(long botNextLungeMs) {
        this.botNextLungeMs = botNextLungeMs;
    }

    public long botNextWindMs() {
        return botNextWindMs;
    }

    public void setBotNextWindMs(long botNextWindMs) {
        this.botNextWindMs = botNextWindMs;
    }

    public Location botHome() {
        return botHome;
    }

    public void setBotHome(Location botHome) {
        this.botHome = botHome;
    }

    public java.util.Set<java.util.UUID> botCrystals() {
        return botCrystals;
    }

    public java.util.Map<org.bukkit.block.Block, BotBlock> botPlacedBlocks() {
        return botPlacedBlocks;
    }

    public BotAbilityState abilities() {
        return abilities;
    }

    // ---- item-bounded abilities & path state ----

    public java.util.Map<org.bukkit.Material, Integer> botStock() {
        return botStock;
    }

    /** Consumes {@code count} of {@code material} from the bot's stock; false when out. */
    public boolean botConsume(org.bukkit.Material material, int count) {
        if (material == null || count <= 0) {
            return false;
        }
        Integer left = botStock.get(material);
        if (left == null || left < count) {
            return false;
        }
        if (left == count) {
            botStock.remove(material);
        } else {
            botStock.put(material, left - count);
        }
        return true;
    }

    public java.util.List<BotPathFinder.Node> botPath() {
        return botPath;
    }

    public void setBotPath(java.util.List<BotPathFinder.Node> path) {
        this.botPath = path == null ? java.util.List.of() : path;
        this.botPathIndex = 0;
    }

    public int botPathIndex() {
        return botPathIndex;
    }

    public void setBotPathIndex(int botPathIndex) {
        this.botPathIndex = botPathIndex;
    }

    public long botPathRefreshMs() {
        return botPathRefreshMs;
    }

    public void setBotPathRefreshMs(long botPathRefreshMs) {
        this.botPathRefreshMs = botPathRefreshMs;
    }

    public int[] botPathLastGoal() {
        return botPathLastGoal;
    }

    public boolean botPathGoalSet() {
        return botPathGoalSet;
    }

    public void setBotPathGoalSet(boolean botPathGoalSet) {
        this.botPathGoalSet = botPathGoalSet;
    }

    public long botRetreatUntilMs() {
        return botRetreatUntilMs;
    }

    public void setBotRetreatUntilMs(long botRetreatUntilMs) {
        this.botRetreatUntilMs = botRetreatUntilMs;
    }

    public BotDifficulty difficulty() {
        return difficulty;
    }

    public void setDifficulty(BotDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public long matchStartMs() {
        return matchStartMs;
    }

    public void setMatchStartMs(long matchStartMs) {
        this.matchStartMs = matchStartMs;
    }

    public java.util.Set<java.util.UUID> botTnt() {
        return botTnt;
    }

    public long botPotionUntilMs() {
        return botPotionUntilMs;
    }

    public void setBotPotionUntilMs(long botPotionUntilMs) {
        this.botPotionUntilMs = botPotionUntilMs;
    }

    public long botNextCartMs() {
        return botNextCartMs;
    }

    public void setBotNextCartMs(long botNextCartMs) {
        this.botNextCartMs = botNextCartMs;
    }

    public boolean botShieldRaised() {
        return botShieldRaised;
    }

    public void setBotShieldRaised(boolean botShieldRaised) {
        this.botShieldRaised = botShieldRaised;
    }

    public long botStunUntilMs() {
        return botStunUntilMs;
    }

    public void setBotStunUntilMs(long botStunUntilMs) {
        this.botStunUntilMs = botStunUntilMs;
    }

    public int maceDensity() {
        return maceDensity;
    }

    public void setMaceDensity(int maceDensity) {
        this.maceDensity = Math.max(0, Math.min(5, maceDensity));
    }

    public int maceBreach() {
        return maceBreach;
    }

    public void setMaceBreach(int maceBreach) {
        this.maceBreach = Math.max(0, Math.min(4, maceBreach));
    }

    public int maceWindBurst() {
        return maceWindBurst;
    }

    public void setMaceWindBurst(int maceWindBurst) {
        this.maceWindBurst = Math.max(0, Math.min(3, maceWindBurst));
    }

    public UUID cloneInstanceId() {
        return cloneInstanceId;
    }

    public void setCloneInstanceId(UUID cloneInstanceId) {
        this.cloneInstanceId = cloneInstanceId;
    }

    public Cuboid activeRegion() {
        return activeRegion;
    }

    public void setActiveRegion(Cuboid activeRegion) {
        this.activeRegion = activeRegion;
    }

    public Location activeSpawn() {
        return activeSpawn;
    }

    public void setActiveSpawn(Location activeSpawn) {
        this.activeSpawn = activeSpawn;
    }
}
