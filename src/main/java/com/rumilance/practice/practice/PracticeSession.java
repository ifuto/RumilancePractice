package com.rumilance.practice.practice;

import com.rumilance.practice.util.Cuboid;
import org.bukkit.Location;
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
    private BotBody maceBot;
    private boolean botShieldRaised;
    private long botStunUntilMs;
    private int maceDensity;
    private int maceBreach;
    private int maceWindBurst;
    /** Sword / crystal combat bot (ITEM 41, Quantum-style practice bots). */
    private BotBody combatBot;
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
    /** Objective timeline of the fight (bot swings/hits/combos/totems) for the end report. */
    private final java.util.List<String> fightLog = new java.util.ArrayList<>();
    /** Objective events kept per fight; chatter (swing/hit) has the smaller budget below. */
    private static final int FIGHT_LOG_CAP = 4000;
    private static final int CHATTER_LOG_CAP = 3000;
    private int chatterLogCount;
    /** 0.1 s state samples of the bot (pos/look/hp/hand) — console-only, parity comparison. */
    private final java.util.List<String> fightSamples = new java.util.ArrayList<>();
    private final long fightLogStart = System.currentTimeMillis();
    /** Materials present in the kit the bot cloned from the player (visible slot selects). */
    private final java.util.Set<org.bukkit.Material> botKitMaterials =
            java.util.EnumSet.noneOf(org.bukkit.Material.class);
    private final java.util.Map<org.bukkit.Material, Integer> botStock =
            new java.util.EnumMap<>(org.bukkit.Material.class);
    /** Current A* waypoints followed by the bot (from {@link BotPathFinder}). */
    private transient java.util.List<BotPathFinder.Node> botPath = java.util.List.of();
    private transient int botPathIndex;
    private transient long botPathRefreshMs;
    private transient final int[] botPathLastGoal = new int[3];
    private transient boolean botPathGoalSet;
    /** Active practice drill (Quantum mech_train mode), or {@link PracticeMode#NONE}. */
    private PracticeMode botMode = PracticeMode.NONE;
    /** Drill loop timers: next attempt may start after this epoch ms. */
    private long drillNextAtMs;
    /** Drill stage within the current attempt (mode-specific). */
    private int drillStage;
    /** Pops counted at the beginning of the current attempt (result comparison). */
    private int drillPopsAtStart;
    /** Player's chest item swapped away by the divebomb drill (restored on teardown). */
    private org.bukkit.inventory.ItemStack drillSavedChest;
    private boolean drillElytraDressed;

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
        /** Next crystal-bot melee swing (Quantum g1gc/hit: a fixed 7-tick hitcd). */
        private long nextMeleeMs;
        /** Fighting pause after the bot's own totem pop (Quantum totem_cd rung). */
        private long totemPauseUntilMs;
        /** Anchor cycle stage: 0 = idle, 1 = placed, 2 = charged (Quantum g1gc anchor chain). */
        private int anchorStage;
        /** The tracked anchor block of the running cycle. */
        private transient org.bukkit.Location anchorBlock;
        /**
         * Until this timestamp the target is inside its hurt frames — the exact window the map
         * reads from the target's NBT ({@code hurtTime}, {@code g1gc/can_hit}). The plugin drives
         * the window itself because the practice room cancels the attributed damage frame and
         * registers the health directly, so nothing else would ever set or expire it.
         */
        private long targetHurtUntilMs;

        /**
         * Map {@code state}: 1/2 = ATTACK ({@code bin/27}), 0/3 = PASSIVE ({@code bin/13}).
         * {@code eval/biased} rebuilds it every tick from {@code eval}: while the bot is ahead
         * (or its HP deficit is recoverable) it attacks, and the moment {@code eval <= -1} it
         * drops into the passive branch — the reference spends 77.5 % of run8 there.
         */
        private int botState = 1;
        /** Map {@code eval} after the +225 attack bonus; kept for the fight trace. */
        private int botEval;
        /** Next instant the map's 10-tick / 30 % roll may restore the ATTACK state. */
        private long nextStateRollMs;
        /** Chains (place -> charge -> detonate) this session, for the self-damage parity check. */
        private int botChains;

        /**
         * Until when the bot rides out a knockback from its own blast. Vanilla explosion push
         * is applied to every entity in radius, but the practice room cancels the blast's
         * damage frame, and the AI overwrites the bot's velocity on every tick — so the push
         * is recorded here and the movement override is skipped until it expires. Without it
         * the bot never leaves the spot it anchored from and fires the 12-tick ladder back to
         * back (measured 50.5 anchors/min against the reference's 18.1).
         */
        private long blastUntilMs;

        /**
         * Until this instant the bot is in the map's PASSIVE branch: after an escape pearl it
         * walks back in (map {@code bin/13} + {@code bin/28} `move forward`) and does NOT throw
         * the fight branch's chase pearl ({@code g1gc/pearl}). The reference's engagement cycle
         * is ~5 s long with one sword hit and 1.6 anchors per cycle; without this window our bot
         * teleported back in after 1 s and doubled every close-range count.
         */
        private long escapePassiveUntilMs;
        /** While set, the bot is visibly eating a golden apple (map gap_timer 35t). */
        private long gapEatUntilMs;
        /** Regen-II style heal window after a golden apple (vanilla: +8 HP over 5 s). */
        private long gapRegenUntilMs;
        /**
         * Map hotbar behaviour: the item the bot keeps in hand until {@code holdUntilMs}.
         * The map switches slots per module ({@code player @s hotbar N} + {@code swing once})
         * and falls back to the ender pearl, which is why the reference bot carries a pearl
         * for ~76 % of a 400 s match and only flashes the other slots (anchor 5.5 %,
         * glowstone 5.7 %, totem 7.2 %, crystal 2.7 %, sword 2.3 %).
         */
        private org.bukkit.Material holdItem;
        private long holdUntilMs;
        /** While set, the bot is mining the block at this location (crack particles each tick). */
        private long miningUntilMs;
        /** The block being mined. */
        private transient org.bukkit.Location miningBlock;
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
        public long nextMeleeMs() { return nextMeleeMs; }
        public void nextMeleeMs(long v) { nextMeleeMs = v; }
        public long totemPauseUntilMs() { return totemPauseUntilMs; }
        public void totemPauseUntilMs(long v) { totemPauseUntilMs = v; }
        public int anchorStage() { return anchorStage; }
        public void anchorStage(int v) { anchorStage = v; }
        public org.bukkit.Location anchorBlock() { return anchorBlock; }
        public void anchorBlock(org.bukkit.Location v) { anchorBlock = v; }
        public long targetHurtUntilMs() { return targetHurtUntilMs; }
        public void targetHurtUntilMs(long v) { targetHurtUntilMs = v; }

        public long blastUntilMs() { return blastUntilMs; }
        public void blastUntilMs(long v) { blastUntilMs = v; }

        public long escapePassiveUntilMs() { return escapePassiveUntilMs; }
        public void escapePassiveUntilMs(long v) { escapePassiveUntilMs = v; }
        public long gapEatUntilMs() { return gapEatUntilMs; }
        public void gapEatUntilMs(long v) { gapEatUntilMs = v; }
        public long gapRegenUntilMs() { return gapRegenUntilMs; }
        public void gapRegenUntilMs(long v) { gapRegenUntilMs = v; }
        public long miningUntilMs() { return miningUntilMs; }
        public void miningUntilMs(long v) { miningUntilMs = v; }
        public org.bukkit.Location miningBlock() { return miningBlock; }
        public void miningBlock(org.bukkit.Location v) { miningBlock = v; }
        public int gapUses() { return gapUses; }
        public void gapUses(int v) { gapUses = v; }
        public org.bukkit.Material holdItem() { return holdItem; }
        public int botState() { return botState; }
        public void botState(int value) { botState = value; }
        public int botEval() { return botEval; }
        public void botEval(int value) { botEval = value; }
        public long nextStateRollMs() { return nextStateRollMs; }
        public void nextStateRollMs(long value) { nextStateRollMs = value; }
        public int botChains() { return botChains; }
        public void botChains(int value) { botChains = value; }
        public long holdUntilMs() { return holdUntilMs; }
        public void hold(org.bukkit.Material item, long untilMs) {
            holdItem = item;
            holdUntilMs = untilMs;
        }
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
    /**
     * Duel-arena venue: BOT fights bound to a kit with arenas run inside that kit's arena
     * (BOT fights are NOT practice rooms). Holds the reserved {@code ArenaInstance} id —
     * released on leave/quit. {@code null} = classic practice-room venue.
     */
    private UUID arenaInstanceId;
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

    public BotBody maceBot() {
        return maceBot;
    }

    public void setMaceBot(BotBody maceBot) {
        this.maceBot = maceBot;
    }

    public BotBody combatBot() {
        return combatBot;
    }

    public void setCombatBot(BotBody combatBot) {
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

    public java.util.Set<org.bukkit.Material> botKitMaterials() {
        return botKitMaterials;
    }

    /** Records one fight event with its second-offset; capped so a long fight cannot leak. */
    /**
     * Objective fight trace, stamped in 0.1 s units (the qlog datapack resolves ticks, and the
     * anchor chain's place→charge is 4 ticks = 0.2 s — whole seconds cannot measure that).
     * Swing/hit chatter has its own (smaller) budget so a three minute run cannot push the
     * anchor/crystal/totem events out of the dump.
     */
    public void fightLog(String event) {
        boolean chatter = event.startsWith("swing") || event.startsWith("hit");
        if (chatter ? chatterLogCount >= CHATTER_LOG_CAP : fightLog.size() >= FIGHT_LOG_CAP) {
            return;
        }
        if (chatter) {
            chatterLogCount++;
        }
        long ds = (System.currentTimeMillis() - fightLogStart) / 100L;
        fightLog.add(String.format(java.util.Locale.ROOT, "%.1fs %s", ds / 10.0d, event));
    }

    public java.util.List<String> fightLog() {
        return fightLog;
    }

    /** Records one 0.1 s state sample; capped at ~3 minutes of fighting. */
    public void fightSample(String sample) {
        if (fightSamples.size() < 1800) {
            long t = (System.currentTimeMillis() - fightLogStart) / 100L;
            fightSamples.add(t + " " + sample);
        }
    }

    public java.util.List<String> fightSamples() {
        return fightSamples;
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

    public PracticeMode botMode() {
        return botMode;
    }

    public void setBotMode(PracticeMode botMode) {
        this.botMode = botMode == null ? PracticeMode.NONE : botMode;
    }

    public long drillNextAtMs() {
        return drillNextAtMs;
    }

    public void setDrillNextAtMs(long drillNextAtMs) {
        this.drillNextAtMs = drillNextAtMs;
    }

    public int drillStage() {
        return drillStage;
    }

    public void setDrillStage(int drillStage) {
        this.drillStage = drillStage;
    }

    public int drillPopsAtStart() {
        return drillPopsAtStart;
    }

    public void setDrillPopsAtStart(int drillPopsAtStart) {
        this.drillPopsAtStart = drillPopsAtStart;
    }

    public org.bukkit.inventory.ItemStack drillSavedChest() {
        return drillSavedChest;
    }

    public void setDrillSavedChest(org.bukkit.inventory.ItemStack drillSavedChest) {
        this.drillSavedChest = drillSavedChest;
    }

    public boolean drillElytraDressed() {
        return drillElytraDressed;
    }

    public void setDrillElytraDressed(boolean drillElytraDressed) {
        this.drillElytraDressed = drillElytraDressed;
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

    public UUID arenaInstanceId() {
        return arenaInstanceId;
    }

    public void setArenaInstanceId(UUID arenaInstanceId) {
        this.arenaInstanceId = arenaInstanceId;
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
