package com.rumilance.practice.alt;

import com.rumilance.practice.database.repository.AltRepository;
import com.rumilance.practice.util.AsyncExecutor;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 高度 Alt アカウント検知（非公開・管理者向け）。
 *
 * <p>海外文献で実績のある行動バイオメトリクス群——クリック（スイング）間隔分布
 * (mouse-dynamics; Ahmed&amp;Traore, Feher/Elovici 系)、アイドル/接続時間分布
 * (Chen&amp;Hong の RET 系、接続時間帯ヒストグラム)、同一 IP 共有と短時間の
 * アカウント切替カデンス——を独立信号として計測し、JS divergence / cosine 類似度で
 * z スコア相当に正規化後、超保守的に加重合成する。単独の信号では誰も特定できない
 * 組み合わせだけがスレッショルドを超える設計で、無関係プレイヤー（実力差のある
 * 対戦を含む）には反応しないことを運用上の最優先とする。</p>
 *
 * <p>誤検知ゼロ保証は原理的に存在しないため、動作は2段のみ:
 * FLAG = 管理者へ証拠付き警告キュー、RESTRICT = ランク戦マッチ制限（ペアを絶対に
 * ペアリングしない）。BAN や公開告知は一切行わない。さらに「無関連ペア（IP共有も
 * 切替もない大多数ペア）の最大スコア + マージン」をスキャンごとに較正し、それを
 * 下回るフラグは抑制する（null-floor 較正）。</p>
 */
public final class AltDetectionService {

    /** スイング間隔ヒストグラム: 20ms × 50bin (0..1000ms, overflowは最終bin)。 */
    public static final int INTERVAL_BINS = 50;
    public static final long INTERVAL_BIN_MS = 20L;
    /** RESTRICT の conjunctive-evidence ゲート用クリック類似度下限。 */
    private static final double RESTRICT_CLICK_SIM = 0.985;
    private static final double RESTRICT_HOUR_SIM = 0.99;
    private static final int MIN_LOGINS_FOR_HOUR_SIM = 8;

    private final Plugin plugin;
    private final AltRepository repository;
    private final AsyncExecutor asyncExecutor;

    private volatile boolean enabled = true;
    private volatile int flagThreshold = 70;
    private volatile int restrictThreshold = 85;
    private volatile long minSwings = 300;
    private volatile long scanIntervalMinutes = 30;

    /** In-memory behavior accumulator (flushed to DB periodically and on quit). */
    private final Map<UUID, BehaviorProfile> profiles = new ConcurrentHashMap<>();
    /** Canonical "aUuid<bUuid" pair keys restricted from ranked matching. */
    private final Set<String> restrictedPairs = ConcurrentHashMap.newKeySet();
    /** Alerted flag keys already announced this uptime (avoid chat spam per scan). */
    private final Set<String> announced = ConcurrentHashMap.newKeySet();

    private int taskId = -1;
    private int flushTaskId = -1;

    public AltDetectionService(Plugin plugin, AltRepository repository, AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
    }

    public void configure(boolean enabled, int flagThreshold, int restrictThreshold,
                          long minSwings, long scanIntervalMinutes) {
        this.enabled = enabled;
        this.flagThreshold = flagThreshold;
        this.restrictThreshold = restrictThreshold;
        this.minSwings = Math.max(50, minSwings);
        this.scanIntervalMinutes = Math.max(5, scanIntervalMinutes);
    }

    public void start() {
        asyncExecutor.runAsync(() -> {
            try {
                for (AltRepository.FlagRow row : repository.loadActiveFlags()) {
                    if ("RESTRICT".equals(row.level())) {
                        restrictedPairs.add(pairKey(row.a(), row.b()));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "alt-detection: failed to load flags", e);
            }
        });
        long tick = 20L;
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll,
                10 * 60 * tick, 10 * 60 * tick).getTaskId();
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::scanSafely,
                5 * 60 * tick, scanIntervalMinutes * 60 * tick).getTaskId();
    }

    public void shutdown() {
        if (flushTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
        }
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        flushAll();
    }

    // ------------------------------------------------------------------ sampling

    /** Login hook (AsyncPlayerPreLoginEvent/PlayerJoinEvent): record the IP + switch cadence. */
    public void onLogin(Player player) {
        if (!enabled || player == null) {
            return;
        }
        java.net.InetSocketAddress address = player.getAddress();
        if (address == null) {
            return;
        }
        String ip = address.getAddress().getHostAddress();
        UUID id = player.getUniqueId();
        long now = Instant.now().getEpochSecond();
        profileOf(id).recordLoginHour(jstHour(now));
        asyncExecutor.runAsync(() -> {
            try {
                repository.recordLogin(id, ip, now);
                // 切替カデンス: 同一IPから30分以内に別アカウントが活動していたら回数を積む。
                for (UUID other : repository.othersSharingIp(ip, id, now - 30 * 60)) {
                    repository.bumpSwitch(ip, id, other, now);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "alt-detection: login record failed", e);
            }
        });
    }

    /** Arm-swing sample taken during real combat (throttled to at most one per source tick). */
    public void onSwing(UUID playerId, long nowMs, long lastSwingMs) {
        if (!enabled || playerId == null) {
            return;
        }
        long delta = nowMs - lastSwingMs;
        if (lastSwingMs <= 0 || delta > 10_000) {
            // 10秒超の間隔は「別の握り直し」として分布から外す（アイドル混入防止）。
            return;
        }
        profileOf(playerId).recordInterval(delta);
    }

    public void onQuit(UUID playerId) {
        BehaviorProfile profile = profiles.get(playerId);
        if (profile != null) {
            flush(playerId, profile);
        }
    }

    // ------------------------------------------------------------------ restriction

    /** Ranked マッチングから除外すべきフラグ済みペアかどうか。 */
    public boolean restrictedPair(UUID a, UUID b) {
        return enabled && a != null && b != null && restrictedPairs.contains(pairKey(a, b));
    }

    /** 管理者がフラグを棄却（誤検知扱い）したときの解除。 */
    public void dismiss(UUID a, UUID b) {
        restrictedPairs.remove(pairKey(a, b));
        asyncExecutor.runAsync(() -> {
            try {
                repository.dismissFlag(a, b);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "alt-detection: dismiss failed", e);
            }
        });
    }

    public boolean enabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ scoring

    private void scanSafely() {
        try {
            if (enabled) {
                scan();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "alt-detection: scan failed", e);
        }
    }

    /**
     * Runs the full similarity scan async. Pulls behavior rows, IP overlaps and switch pairs,
     * scores every candidate pair, applies the null-floor calibration and raises flags.
     */
    void scan() throws Exception {
        long now = Instant.now().getEpochSecond();
        long activeSince = now - 30L * 24 * 3600;
        Map<UUID, BehaviorProfile> all = new HashMap<>(profiles);
        for (AltRepository.BehaviorRow row : repository.loadBehaviors(activeSince)) {
            all.computeIfAbsent(row.playerId(), id -> new BehaviorProfile())
                    .merge(row.swings(), row.intervalBins(), row.hourBins());
        }

        Map<String, Double> sharedIp = new HashMap<>();
        for (AltRepository.IpOverlap overlap : repository.sharedIpPairs()) {
            sharedIp.put(pairKey(overlap.a(), overlap.b()), 45.0);
        }
        Map<String, Integer> switches = new HashMap<>();
        for (AltRepository.SwitchPair pair : repository.switchPairs(1)) {
            switches.put(pairKey(pair.a(), pair.b()), pair.switches());
        }

        // Candidate pairs: anything with IP/switch evidence + top behavior-similarity pairs.
        Set<UUID> ids = new LinkedHashSet<>(all.keySet());
        List<UUID> players = new ArrayList<>(ids);
        double nullFloor = 0.0; // 無関連ペア（IP・切替証拠ゼロ）の最大スコア + マージン

        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                UUID a = players.get(i);
                UUID b = players.get(j);
                String key = pairKey(a, b);
                BehaviorProfile pa = all.get(a);
                BehaviorProfile pb = all.get(b);
                double clickScore = 0;
                double clickSim = -1;
                if (pa != null && pb != null
                        && pa.swings >= minSwings && pb.swings >= minSwings) {
                    clickSim = histogramSimilarity(pa.interval, pb.interval);
                    clickScore = 25 * clickSim;
                }
                double hourScore = 0;
                double hourSim = -1;
                if (pa != null && pb != null
                        && pa.logins >= MIN_LOGINS_FOR_HOUR_SIM
                        && pb.logins >= MIN_LOGINS_FOR_HOUR_SIM) {
                    hourSim = cosine(pa.hours, pb.hours);
                    hourScore = 15 * hourSim;
                }
                double ipScore = sharedIp.getOrDefault(key, 0.0);
                int switchCount = switches.getOrDefault(key, 0);
                double switchScore = Math.min(switchCount, 4) * 10;
                double score = Math.min(100, ipScore + switchScore + clickScore + hourScore);
                boolean hasNetworkEvidence = ipScore > 0 || switchCount > 0;
                if (!hasNetworkEvidence) {
                    // 「同一人物による完璧な擬態」でない限り到達できない行動類似度のみの
                    // 候補も拾うが、これは null-floor 較正側の母集団でもある。
                    if (clickSim >= 0.97 && hourSim >= 0.96) {
                        candidates.add(new Candidate(key, a, b, score, ipScore > 0,
                                switchCount, clickSim, hourSim));
                    }
                    nullFloor = Math.max(nullFloor, score);
                    continue;
                }
                candidates.add(new Candidate(key, a, b, score, ipScore > 0,
                        switchCount, clickSim, hourSim));
            }
        }

        double floor = Math.max(flagThreshold, nullFloor + 8);
        for (Candidate c : candidates) {
            if (c.score < floor) {
                continue;
            }
            boolean conjunctive = c.hasIp || c.switches >= 2
                    || (c.clickSim >= RESTRICT_CLICK_SIM && c.hourSim >= RESTRICT_HOUR_SIM);
            String level = c.score >= restrictThreshold && conjunctive ? "RESTRICT" : "FLAG";
            String evidence = "score=" + (int) c.score
                    + " ip=" + (c.hasIp ? "共有" : "なし")
                    + " switch=" + c.switches
                    + " clickSim=" + sim(c.clickSim)
                    + " hourSim=" + sim(c.hourSim)
                    + " floor=" + (int) floor;
            repository.upsertFlag(c.a, c.b, c.score, level, evidence, now);
            if ("RESTRICT".equals(level)) {
                restrictedPairs.add(c.key);
            }
            if (announced.add(c.key) || "RESTRICT".equals(level)) {
                alertAdmins(c, level, evidence);
            }
        }
        Set<String> stillActive = ConcurrentHashMap.newKeySet();
        for (AltRepository.FlagRow row : repository.loadActiveFlags()) {
            if ("RESTRICT".equals(row.level())) {
                stillActive.add(pairKey(row.a(), row.b()));
            }
        }
        // downgrade: スコア低下で RESTRICT 条件未達が 3 回続いたペアは昇格抑止ではなく解除
        // するのが理想だが、初版は保守側に倒し「管理者が dismiss するまで RESTRICT 維持」とする。
        restrictedPairs.retainAll(union(stillActive, restrictedPairs));
    }

    private void alertAdmins(Candidate c, String level, String evidence) {
        String nameA = nameOf(c.a);
        String nameB = nameOf(c.b);
        String line = "§8[§6Alt検知§8] §f" + nameA + " §7⇔ §f" + nameB
                + " §7(" + level + ") §8" + evidence;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("rumilance.admin") || online.isOp()) {
                online.sendMessage(line);
            }
        }
        plugin.getLogger().info("[alt-detection] " + nameA + " <-> " + nameB
                + " " + level + " " + evidence);
    }

    public List<AltRepository.FlagRow> activeFlags() throws Exception {
        return repository.loadActiveFlags();
    }

    // ------------------------------------------------------------------ profiles

    private BehaviorProfile profileOf(UUID id) {
        return profiles.computeIfAbsent(id, x -> new BehaviorProfile());
    }

    private void flush(UUID id, BehaviorProfile profile) {
        asyncExecutor.runAsync(() -> {
            try {
                synchronized (profile) {
                    if (profile.dirty) {
                        repository.saveBehavior(id, profile.swings, csv(profile.interval),
                                csv(profile.hours), Instant.now().getEpochSecond());
                        profile.dirty = false;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "alt-detection: behavior flush failed", e);
            }
        });
    }

    private void flushAll() {
        for (Map.Entry<UUID, BehaviorProfile> entry : profiles.entrySet()) {
            flush(entry.getKey(), entry.getValue());
        }
    }

    // ------------------------------------------------------------------ math helpers

    /** JS divergence ベースの分布類似度 (0..1)。 */
    static double histogramSimilarity(long[] a, long[] b) {
        long sumA = 0;
        long sumB = 0;
        for (long v : a) {
            sumA += v;
        }
        for (long v : b) {
            sumB += v;
        }
        if (sumA <= 0 || sumB <= 0) {
            return 0;
        }
        double js = 0;
        for (int i = 0; i < a.length; i++) {
            double pa = a[i] / (double) sumA;
            double pb = b[i] / (double) sumB;
            if (pa <= 0 && pb <= 0) {
                continue;
            }
            double m = (pa + pb) / 2;
            if (pa > 0) {
                js += pa * Math.log(pa / m);
            }
            if (pb > 0) {
                js += pb * Math.log(pb / m);
            }
        }
        js /= 2; // JSD ∈ [0, ln2]
        return Math.max(0, 1 - js / Math.log(2));
    }

    static double cosine(long[] a, long[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na <= 0 || nb <= 0) {
            return 0;
        }
        return dot / Math.sqrt(na * nb);
    }

    private static String csv(long[] values) {
        StringBuilder sb = new StringBuilder(values.length * 3);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private static long[] parse(String csv, int length) {
        long[] out = new long[length];
        if (csv == null || csv.isEmpty()) {
            return out;
        }
        String[] parts = csv.split(",");
        for (int i = 0; i < length && i < parts.length; i++) {
            try {
                out[i] = Long.parseLong(parts[i].trim());
            } catch (NumberFormatException ignored) {
                // keep zero
            }
        }
        return out;
    }

    private static String pairKey(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) <= 0 ? sa + "<" + sb : sb + "<" + sa;
    }

    private static String nameOf(UUID id) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        String name = offline.getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private static int jstHour(long epochSeconds) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds),
                ZoneOffset.ofHours(9)).getHour();
    }

    private static String sim(double value) {
        return value < 0 ? "-" : String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    private record Candidate(String key, UUID a, UUID b, double score, boolean hasIp,
                             int switches, double clickSim, double hourSim) {
    }

    /** 1プレイヤー分の行動プロファイル (スイング間隔分布 + JST接続時間帯分布)。 */
    static final class BehaviorProfile {
        final long[] interval = new long[INTERVAL_BINS];
        final long[] hours = new long[24];
        long swings;
        long logins;
        boolean dirty;

        synchronized void recordInterval(long deltaMs) {
            int bin = (int) Math.min(INTERVAL_BINS - 1, deltaMs / INTERVAL_BIN_MS);
            interval[bin]++;
            swings++;
            dirty = true;
        }

        synchronized void recordLoginHour(int hour) {
            hours[Math.max(0, Math.min(23, hour))]++;
            logins++;
            dirty = true;
        }

        synchronized void merge(long swings, String intervalCsv, String hourCsv) {
            long[] iv = parse(intervalCsv, INTERVAL_BINS);
            long[] hr = parse(hourCsv, 24);
            for (int i = 0; i < INTERVAL_BINS; i++) {
                interval[i] += iv[i];
            }
            for (int i = 0; i < 24; i++) {
                hours[i] += hr[i];
            }
            this.swings += swings;
            this.logins += sum(hr);
        }

        private static long sum(long[] values) {
            long s = 0;
            for (long v : values) {
                s += v;
            }
            return s;
        }
    }
}
