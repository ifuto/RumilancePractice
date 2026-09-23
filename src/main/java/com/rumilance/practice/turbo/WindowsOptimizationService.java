package com.rumilance.practice.turbo;

import com.rumilance.practice.PluginIdentity;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows 10 system-level "turbo" optimization, implemented by delegating to Microsoft's own
 * {@code powercfg.exe} instead of compiling any native code.
 *
 * <p><b>Why delegate to {@code powercfg}?</b> The scheduling latency that matters for a Minecraft
 * server is dominated by power management: deep CPU C-states, processor frequency (P-state)
 * dropping, and Windows core parking. All three are tunable with {@code powercfg}, which ships on
 * every Windows 10 machine. Writing these registry values ourselves (or via JNI) would reproduce
 * exactly what {@code powercfg} does, with far more risk.</p>
 *
 * <p><b>Elevation model.</b> {@code powercfg -setacvalueindex} writes under {@code HKLM}, so it
 * needs Administrator rights. This plugin never runs the whole server JVM as Administrator.
 * Instead every mutation is executed inside a single throw-away elevated PowerShell process
 * (launched with {@code Start-Process -Verb RunAs}), so the operator sees exactly one UAC prompt
 * per {@code /turbo on|off}. If the server console is already elevated the work runs directly
 * with no prompt. Read-only queries ({@code -getactivescheme}, net session probe) run unelevated.
 * Each call is bounded by a timeout, runs off the server main thread, and never hangs.</p>
 *
 * <p><b>Reversibility.</b> Rather than editing the operator's current plan in place, the default
 * action duplicates the configured base plan (Ultimate Performance by default) into a dedicated
 * plan, applies the tuning there, and activates it. {@code /turbo off} switches back to the plan
 * that was active before and clears the optional {@code IDLEDISABLE} flag. The duplicated plan is
 * reused on subsequent runs, so repeated {@code /turbo on} does not pile up plans. If the base
 * plan cannot be duplicated the call is aborted before any setting is written — the operator's
 * current plan is never silently modified.</p>
 */
public final class WindowsOptimizationService {

    /** Stock "Ultimate Performance" plan GUID (Windows 10 build 17101+). */
    public static final String ULTIMATE_PERFORMANCE_GUID = "e9a42b02-d5df-448d-aa00-03f14749eb61";

    private static final Pattern GUID = Pattern.compile(
            "(?i)\\bguid\\s*:\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    /** Whole optimization recipe; negative core-parking / utility values mean "leave alone". */
    public record Parameters(
            String accentPowerPlan,
            boolean procThrottleMin100,
            int coreParkingMinCores,
            int utilityDistribution,
            boolean disableIdle,
            boolean revertIdleOnOff,
            int uacWaitSeconds
    ) {
        public Parameters validated() {
            String accent = (accentPowerPlan == null || accentPowerPlan.isBlank())
                    ? null : accentPowerPlan.trim();
            int park = coreParkingMinCores < 0 ? -1 : clamp(coreParkingMinCores, 0, 100);
            int util = utilityDistribution < 0 ? -1 : clamp(utilityDistribution, 0, 100);
            return new Parameters(accent, procThrottleMin100, park, util,
                    disableIdle, revertIdleOnOff, clamp(uacWaitSeconds, 10, 300));
        }

        public boolean touchesSettings() {
            return procThrottleMin100 || coreParkingMinCores >= 0
                    || utilityDistribution >= 0 || disableIdle;
        }
    }

    /** Outcome of {@link #applyAsync()}. {@code lines} are human-readable status reports. */
    public record ApplyResult(boolean success, boolean cancelled, List<String> lines) {
    }

    /** Outcome of {@link #revertAsync()}. */
    public record RevertResult(boolean ok, boolean cancelled, List<String> lines) {
    }

    /** Snapshot for {@code /turbo status}. */
    public record StateReport(
            boolean windows,
            boolean privileged,
            boolean applied,
            String activeScheme,
            String appliedScheme,
            String backupScheme,
            boolean idleWasSet,
            String note
    ) {
    }

    private record State(String attempted, String backup, boolean idleSet) {
    }

    private record Snapshot(int exit, String output) {
    }

    private final JavaPlugin plugin;
    private final AsyncExecutor asyncExecutor;
    private final ConfigService configService;
    private final Path stateFile;
    private volatile boolean runtimeApplied;

    public WindowsOptimizationService(JavaPlugin plugin, AsyncExecutor asyncExecutor,
                                      ConfigService configService) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.configService = configService;
        this.stateFile = PluginIdentity.dataFolder(plugin).toPath().resolve("turbo-state.txt");
    }

    /** Reads the recipe straight out of {@code config.yml} (section {@code turbo:}). */
    public static Parameters fromConfig(FileConfiguration cfg) {
        return new Parameters(
                cfg.getString("turbo.accent-power-plan", ULTIMATE_PERFORMANCE_GUID),
                cfg.getBoolean("turbo.proc-throttle-min-100", true),
                cfg.getInt("turbo.core-parking-min-cores", 100),
                cfg.getInt("turbo.utility-distribution", 0),
                cfg.getBoolean("turbo.disable-idle", true),
                cfg.getBoolean("turbo.revert-idle-on-off", true),
                cfg.getInt("turbo.uac-wait-seconds", 90)
        ).validated();
    }

    private Parameters currentParameters() {
        return fromConfig(configService.config()).validated();
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Cheap, promptless administrative check ({@code net session} succeeds only when elevated). */
    public static boolean hasAdminPrivileges() {
        if (!isWindows()) {
            return false;
        }
        Snapshot snapshot = capture(List.of(systemRoot() + "\\System32\\net.exe", "session"), 15);
        return snapshot != null && snapshot.exit() == 0;
    }

    public boolean isRuntimeApplied() {
        return runtimeApplied;
    }

    public CompletableFuture<ApplyResult> applyAsync() {
        final Parameters p = currentParameters();
        return asyncExecutor.supplyAsync(() -> applyBlocking(p));
    }

    public CompletableFuture<RevertResult> revertAsync() {
        final Parameters p = currentParameters();
        return asyncExecutor.supplyAsync(() -> revertBlocking(p));
    }

    public CompletableFuture<StateReport> statusAsync() {
        return asyncExecutor.supplyAsync(this::statusBlocking);
    }

    // ---------------------------------------------------------------------------------------------
    // apply on / off
    // ---------------------------------------------------------------------------------------------

    private ApplyResult applyBlocking(Parameters p) {
        List<String> lines = new ArrayList<>();
        if (!isWindows()) {
            lines.add("このサーバは Windows ではないため、システム最適化機能は利用できません。");
            runtimeApplied = false;
            return new ApplyResult(false, false, lines);
        }
        if (!p.touchesSettings()) {
            lines.add("適用する項目がすべて無効です。config.yml の turbo: セクションを確認してください。");
            return new ApplyResult(false, false, lines);
        }

        State previous = readState();
        Map<String, String> markers = runElevated(
                logPath -> buildApplyScript(logPath, p, previous), p.uacWaitSeconds());
        if (markers == null || markers.containsKey("MISSING")) {
            lines.add("特権昇格の承認が得られませんでした (UAC ダイアログをキャンセルしたか、"
                    + "PowerShell を起動できませんでした)。");
            lines.add("この機能を使うには /turbo on で表示される UAC ダイアログで「はい」を押してください。");
            return new ApplyResult(false, true, lines);
        }

        String original = markers.get("ORIGGUID");
        String target = markers.get("TARGETGUID");
        boolean duplicateFailed = !"0".equals(markers.get("DUPEXIT"));

        lines.add("適用前の電源プラン: " + printable(original));
        if (duplicateFailed && previous.attempted() == null && p.accentPowerPlan() != null) {
            lines.add("✗ ベースプラン '" + p.accentPowerPlan() + "' を複製できませんでした (存在しないか、"
                    + "権限不足です)。現在のプランには変更を加えていません。");
            lines.add("  対策: この PC が Ultimate Performance を搭載していない場合は、"
                    + "config.yml の turbo.accent-power-plan を別のプラン GUID (例: High Performance "
                    + "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c) に変更するか、空 (\"\" ) にすると"
                    + "現在のプランを直接編集するモードになります。");
            return new ApplyResult(false, false, lines);
        }

        if (previous.attempted() != null) {
            lines.add("既存のTurboプランを再アクティブ化: " + printable(target));
        } else if (p.accentPowerPlan() != null) {
            lines.add("ベースプラン '" + p.accentPowerPlan() + "' を複製しアクティブ化: "
                    + printable(target));
        } else {
            lines.add("現在のプランを直接変更します (accent-power-plan が空の明示指定)。");
        }

        boolean allOk = true;
        if (p.procThrottleMin100()) {
            allOk &= noteSetting(lines, markers, "PROCTHROTTLEMIN",
                    "プロセッサ最小状態 100% (P-state下限=定格維持)");
        }
        if (p.coreParkingMinCores() >= 0) {
            allOk &= noteSetting(lines, markers, "CPMINCORES",
                    "コアパーキング最小コア " + p.coreParkingMinCores() + "% (全コア駐車解除)");
        }
        if (p.utilityDistribution() >= 0) {
            allOk &= noteSetting(lines, markers, "DISTRIBUTEUTIL",
                    "ユーティリティ分散 " + p.utilityDistribution() + " (低負荷でも全コアへ分散)");
        }
        if (p.disableIdle()) {
            allOk &= noteSetting(lines, markers, "IDLEDISABLE",
                    "プロセッサ・アイドル無効化=1 (深いC-state回避・任意)");
        }
        boolean activeOk = "0".equals(markers.get("ACTIVEEXIT"));
        lines.add((activeOk ? "✓ " : "✗ ") + "プラン適用: " + printable(target));

        boolean succeeded = activeOk && allOk && target != null && !target.isBlank();
        if (succeeded) {
            persistState(new State(target, blankToNull(original), p.disableIdle()));
            runtimeApplied = true;
            lines.add("保持: サーバ再起動前は維持されます。解除は /turbo off で。");
        } else {
            runtimeApplied = false;
            lines.add("一部の設定に失敗しました。詳細は上記の ✗ 項目を確認してください。");
        }
        return new ApplyResult(succeeded, false, lines);
    }

    private boolean noteSetting(List<String> lines, Map<String, String> markers, String alias, String label) {
        boolean ok = "0".equals(markers.get(alias + "EXIT"));
        lines.add((ok ? "✓ " : "✗ ") + label);
        return ok;
    }

    private RevertResult revertBlocking(Parameters p) {
        List<String> lines = new ArrayList<>();
        if (!isWindows()) {
            lines.add("このサーバは Windows ではないため、システム最適化機能は利用できません。");
            return new RevertResult(false, false, lines);
        }
        State state = readState();
        if (state.attempted() == null && state.backup() == null) {
            lines.add("適用中のTurbo状態が見つかりません (turbo-state が存在しません)。");
            runtimeApplied = false;
            return new RevertResult(true, false, lines);
        }

        Map<String, String> markers = runElevated(
                logPath -> buildRevertScript(logPath, state, p.revertIdleOnOff()),
                p.uacWaitSeconds());
        if (markers == null || markers.containsKey("MISSING")) {
            lines.add("特権昇格の承認が得られませんでした (UAC がキャンセルされました)。");
            return new RevertResult(false, true, lines);
        }

        boolean ok = true;
        if (state.idleSet() && p.revertIdleOnOff()) {
            boolean idleReset = "0".equals(markers.get("IDLERESETEXIT"));
            ok &= idleReset;
            lines.add((idleReset ? "✓ " : "✗ ") + "アイドル無効化を解除 (IDLEDISABLE=0): "
                    + printable(state.attempted()));
        } else if (state.idleSet()) {
            lines.add("- アイドル無効化は設定に従いそのまま残します (revert-idle-on-off: false)。");
        }
        if (state.backup() != null && !state.backup().equalsIgnoreCase(state.attempted())) {
            boolean restored = "0".equals(markers.get("RESTOREEXIT"));
            ok &= restored;
            lines.add((restored ? "✓ " : "✗ ") + "元の電源プランへ復元: " + printable(state.backup()));
        } else {
            lines.add("アクティブプランは元のままです (設定値のみ解除・復元)。");
        }

        if (ok) {
            deleteState();
            runtimeApplied = false;
            lines.add("Turbo最適化を解除しました。");
        } else {
            lines.add("一部の解除に失敗しました。管理者権限を確認して再度 /turbo off を実行してください。");
        }
        return new RevertResult(ok, false, lines);
    }

    private StateReport statusBlocking() {
        if (!isWindows()) {
            return new StateReport(false, false, false, null, null, null, false,
                    "Windows 専用機能です");
        }
        String active = null;
        Snapshot activeSnap = capture(List.of(powercfgExe(), "-getactivescheme"), 15);
        if (activeSnap != null) {
            active = parseGuid(activeSnap.output());
        }
        State state = readState();
        boolean applied = state.attempted() != null;
        boolean privileged = hasAdminPrivileges();
        String note = privileged
                ? null
                : "/turbo on 実行時に UAC 昇格ダイアログが表示されます。";
        return new StateReport(true, privileged, applied, active,
                state.attempted(), state.backup(), state.idleSet(), note);
    }

    // ---------------------------------------------------------------------------------------------
    // elevated PowerShell plumbing
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the inner script for the given temp log path, then runs it: directly when the server
     * console is already elevated, or inside one elevated PowerShell ({@code Start-Process -Verb
     * RunAs}) otherwise. Returns the {@code #KEY:value} markers the inner script wrote, or
     * {@code null} on I/O failure, or {@code {MISSING -> true}} when the elevated child never
     * produced the log (UAC cancelled).
     */
    private Map<String, String> runElevated(Function<String, String> innerBuilder, int waitSeconds) {
        Path log = Path.of(System.getProperty("java.io.tmpdir"),
                "narena-turbo-" + UUID.randomUUID() + ".log");
        String logPath = log.toString();
        String inner = innerBuilder.apply(logPath);
        Snapshot snapshot;
        try {
            if (hasAdminPrivileges()) {
                snapshot = capture(List.of(powershellExe(),
                        "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-EncodedCommand", encode(inner)), waitSeconds);
            } else {
                String outer = "$arg = \"-NoProfile -NonInteractive -ExecutionPolicy Bypass"
                        + " -EncodedCommand " + encode(inner) + "\"\n"
                        + "Start-Process -Verb RunAs -Wait -WindowStyle Hidden -FilePath '"
                        + singleQuote(powershellExe()) + "' -ArgumentList $arg\n";
                snapshot = capture(List.of(powershellExe(),
                        "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-EncodedCommand", encode(outer)), waitSeconds);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "turbo: elevated call failed", e);
            return null;
        }
        if (snapshot == null) {
            plugin.getLogger().warning("turbo: could not launch PowerShell");
            return null;
        }
        try {
            if (!Files.exists(log)) {
                return Map.of("MISSING", "1");
            }
            return parseMarkers(Files.readString(log, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "turbo: could not read marker log", e);
            return null;
        } finally {
            try {
                Files.deleteIfExists(log);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
    }

    /** {@code powercfg} commands executed by the elevated child. {@code logPath} is single-quoted. */
    private String buildApplyScript(String logPath, Parameters p, State previous) {
        StringBuilder b = new StringBuilder();
        b.append("$pcf = '").append(singleQuote(powercfgExe())).append("'\n");
        b.append("$log = '").append(singleQuote(logPath)).append("'\n");
        b.append("'' | Out-File $log -Encoding ascii\n");

        // Original active scheme (before any change).
        b.append("$t = & $pcf -getactivescheme 2>&1 | Out-String\n");
        b.append(regexMatch("GUID:\\s*([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"))
                .append("$orig = if ($m.Success) { $m.Groups[1].Value } else { '' }\n");
        b.append("Add-Content $log \"#ORIGGUID:$orig\"\n");

        if (previous.attempted() != null) {
            // Reuse the plan created last time (no new duplicate => no plan pile-up).
            b.append("$target = '").append(singleQuote(previous.attempted())).append("'\n");
            b.append("$t = & $pcf -setactive $target 2>&1 | Out-String\n");
            b.append("$dupFail = 0\n");
            b.append("if ($LASTEXITCODE -ne 0) { $dupFail = 1 }\n");
            b.append("Add-Content $log \"#DUPEXIT:$dupFail\"\n");
        } else if (p.accentPowerPlan() != null) {
            // -duplicatescheme prints the NEW scheme GUID on success and does NOT auto-activate.
            b.append("$t = & $pcf -duplicatescheme '").append(singleQuote(p.accentPowerPlan())).append("' 2>&1 | Out-String\n");
            b.append("$dupFail = 0\n");
            b.append("if ($LASTEXITCODE -ne 0) { $dupFail = 1; $target = '' } else {\n");
            b.append("  ").append(regexMatch(
                    "GUID:\\s*([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"))
                    .append("\n");
            b.append("  $target = if ($m.Success) { $m.Groups[1].Value } else { '' }\n");
            b.append("}\n");
            b.append("Add-Content $log \"#DUPEXIT:$dupFail\"\n");
        } else {
            b.append("$target = $orig\n");
            b.append("Add-Content $log \"#DUPEXIT:0\"\n");
        }
        b.append("Add-Content $log \"#TARGETGUID:$target\"\n");

        b.append("if ($dupFail -eq 0 -and $target -ne '') {\n");
        if (p.procThrottleMin100()) {
            setAcIndex(b, "PROCTHROTTLEMIN", "100");
        }
        if (p.coreParkingMinCores() >= 0) {
            setAcIndex(b, "CPMINCORES", Integer.toString(p.coreParkingMinCores()));
        }
        if (p.utilityDistribution() >= 0) {
            setAcIndex(b, "DISTRIBUTEUTIL", Integer.toString(p.utilityDistribution()));
        }
        if (p.disableIdle()) {
            setAcIndex(b, "IDLEDISABLE", "1");
        }
        b.append("  $t = & $pcf -setactive $target 2>&1 | Out-String\n");
        b.append("  Add-Content $log \"#ACTIVEEXIT:$LASTEXITCODE\"\n");
        b.append("} else {\n");
        b.append("  Add-Content $log \"#ACTIVEEXIT:-1\"\n");
        b.append("}\n");
        return b.toString();
    }

    private String buildRevertScript(String logPath, State state, boolean revertIdleOnOff) {
        StringBuilder b = new StringBuilder();
        b.append("$pcf = '").append(singleQuote(powercfgExe())).append("'\n");
        b.append("$log = '").append(singleQuote(logPath)).append("'\n");
        b.append("'' | Out-File $log -Encoding ascii\n");

        if (state.idleSet() && revertIdleOnOff && state.attempted() != null) {
            b.append("$t = & $pcf -setacvalueindex '").append(singleQuote(state.attempted()))
                    .append("' sub_processor IDLEDISABLE 0 2>&1 | Out-String\n");
            b.append("Add-Content $log \"#IDLERESETEXIT:$LASTEXITCODE\"\n");
        } else {
            b.append("Add-Content $log \"#IDLERESETEXIT:0\"\n");
        }
        if (state.backup() != null && !state.backup().equalsIgnoreCase(state.attempted())) {
            b.append("$t = & $pcf -setactive '").append(singleQuote(state.backup())).append("' 2>&1 | Out-String\n");
            b.append("Add-Content $log \"#RESTOREEXIT:$LASTEXITCODE\"\n");
        } else {
            b.append("Add-Content $log \"#RESTOREEXIT:0\"\n");
        }
        return b.toString();
    }

    /** Helper that opens a regex match against {@code $t} for the given pattern. */
    private static String regexMatch(String pattern) {
        return "$m = [regex]::Match($t,'" + pattern + "')\n";
    }

    private static void setAcIndex(StringBuilder b, String alias, String value) {
        b.append("  $t = & $pcf -setacvalueindex $target sub_processor ").append(alias)
                .append(' ').append(value).append(" 2>&1 | Out-String\n");
        b.append("  Add-Content $log \"#").append(alias).append("EXIT:$LASTEXITCODE\"\n");
    }

    // ---------------------------------------------------------------------------------------------
    // state persistence
    // ---------------------------------------------------------------------------------------------

    private void persistState(State state) {
        String text = "attempted: " + state.attempted() + "\n"
                + "backup: " + (state.backup() == null ? "" : state.backup()) + "\n"
                + "idle: " + state.idleSet() + "\n";
        try {
            Files.writeString(stateFile, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "turbo: could not persist state", e);
        }
    }

    private State readState() {
        try {
            if (!Files.exists(stateFile)) {
                return new State(null, null, false);
            }
            String attempted = null;
            String backup = null;
            boolean idle = false;
            for (String line : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("attempted:")) {
                    attempted = trimValue(line);
                } else if (line.startsWith("backup:")) {
                    backup = trimValue(line);
                } else if (line.startsWith("idle:")) {
                    idle = Boolean.parseBoolean(trimValue(line));
                }
            }
            return new State(blankToNull(attempted), blankToNull(backup), idle);
        } catch (IOException e) {
            return new State(null, null, false);
        }
    }

    private void deleteState() {
        try {
            Files.deleteIfExists(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "turbo: could not delete state", e);
        }
    }

    private static String trimValue(String line) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon == line.length() - 1) {
            return "";
        }
        return line.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ---------------------------------------------------------------------------------------------
    // process / path helpers
    // ---------------------------------------------------------------------------------------------

    private static String systemRoot() {
        String root = System.getenv("SystemRoot");
        return root == null || root.isBlank() ? "C:\\Windows" : root;
    }

    private static String powercfgExe() {
        return systemRoot() + "\\System32\\powercfg.exe";
    }

    private static String powershellExe() {
        return systemRoot() + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
    }

    private static String encode(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    /** PowerShell-safe single-quoted literal (doubles any embedded single quote). */
    private static String singleQuote(String text) {
        return text.replace("'", "''");
    }

    private static String parseGuid(String output) {
        if (output == null) {
            return null;
        }
        Matcher matcher = GUID.matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String printable(String value) {
        return value == null || value.isBlank() ? "(不明)" : value;
    }

    private static Map<String, String> parseMarkers(String output) {
        Map<String, String> markers = new LinkedHashMap<>();
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon > 1) {
                markers.put(trimmed.substring(1, colon), trimmed.substring(colon + 1).trim());
            }
        }
        return markers;
    }

    /** Bounded capture of a native process's combined stdout/stderr. {@code null} on I/O failure. */
    private static Snapshot capture(List<String> command, int waitSeconds) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            Thread drain = new Thread(() -> {
                try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // process died mid-read; whatever we captured is fine
                }
            }, "narena-turbo-drain");
            drain.setDaemon(true);
            drain.start();
            boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                drain.join(2000);
                return null;
            }
            drain.join(2000);
            return new Snapshot(process.exitValue(), out.toString());
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
