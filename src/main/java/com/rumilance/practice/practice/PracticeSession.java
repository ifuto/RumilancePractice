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
    /** Obsidian the CRYSTAL bot placed, with placement time (reverted after a while). */
    private final java.util.Map<org.bukkit.block.Block, Long> botPlacedBlocks = new java.util.LinkedHashMap<>();
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

    public Location botHome() {
        return botHome;
    }

    public void setBotHome(Location botHome) {
        this.botHome = botHome;
    }

    public java.util.Set<java.util.UUID> botCrystals() {
        return botCrystals;
    }

    public java.util.Map<org.bukkit.block.Block, Long> botPlacedBlocks() {
        return botPlacedBlocks;
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
