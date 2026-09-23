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
 * <p><b>Elevation model — resident helper.</b> {@code powercfg -setacvalueindex} writes under
 * {@code HKLM}, so it needs Administrator rights. This plugin never runs the whole server JVM as
 * Administrator. Instead the first privileged call spawns one hidden elevated PowerShell
 * ("turboc" resident helper) via {@code Start-Process -Verb RunAs} — exactly one UAC prompt for
 * the lifetime of the server. Every later apply/revert is posted to that helper through a small
 * command file, so automatic idle/wake transitions (see {@link TurboIdleManager}) never prompt
 * again. The helper exits on request at shutdown, or by itself after 6 hours of inactivity.</p>
 *
 * <p><b>EcoQoS.</b> Independently of the power plan, the server process itself can be put into
 * Windows "EcoQoS" (Power Throttling, {@code PROCESS_POWER_THROTTLING_EXECUTION_SPEED}) so the
 * scheduler allows it to run at a lower performance level while the server is empty. That is a
 * per-process hint and needs no elevation at all, so it works even when no UAC was ever granted.</p>
 *
 * <p><b>Reversibility.</b> Rather than editing the operator's current plan in place, the default
 * action duplicates the configured base plan (Ultimate Performance by default) into a dedicated
 * plan, applies the tuning there, and activates it. {@code /turbo off} (and auto idle) switches
 * back to the plan that was active before and clears the optional {@code IDLEDISABLE} flag. The
 * duplicated plan GUID is persisted and reused, so repeated on/off cycles do not pile up plans.
 * If the base plan cannot be duplicated the call is aborted before any setting is written.</p>
 */
public final class WindowsOptimizationService {

    /** Stock "Ultimate Performance" plan GUID (Windows 10 build 17101+). */
    public static final String ULTIMATE_PERFORMANCE_GUID = "e9a42b02-d5df-448d-aa00-03f14749eb61";

    private static final String GUID_REGEX =
            "(?i)GUID:\\s*([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";

    private static final Pattern GUID = Pattern.compile(
            "(?i)\\bguid\\s*:\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    /** Resident elevated helper runner. {@code %1$s}/{@code %2$s} = PS-quoted cmd/res paths. */
    private static final String HELPER_RUNNER = """
$cmd = '%1$s'
$res = '%2$s'
'READY' | Set-Content -LiteralPath $res -Encoding ascii
$last = ''
$now = [DateTime]::UtcNow
$lastBeat = $now
$lastAct = $now
while ($true) {
  $lines = @(Get-Content -LiteralPath $cmd -ErrorAction SilentlyContinue)
  if ($lines.Count -ge 1) {
    $tok = $lines[0].Trim()
    if ($tok -eq 'STOP') {
      'STOPPED' | Set-Content -LiteralPath $res -Encoding ascii
      break
    }
    if ($tok.Length -gt 0 -and $tok -ne $last) {
      $last = $tok
      $restB64 = ''
      if ($lines.Count -ge 2) { $restB64 = ($lines[1..($lines.Count-1)] -join '') }
      try {
        $inner = [System.Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($restB64))
        & ([scriptblock]::Create($inner)) *>&1 | Out-Null
      } catch {
        ('DONE:' + $tok + ':FAIL') | Set-Content -LiteralPath $res -Encoding ascii
      }
      $lastAct = [DateTime]::UtcNow
      $lastBeat = [DateTime]::UtcNow
    }
  }
  if ((([DateTime]::UtcNow) - $lastAct).TotalHours -ge 6) {
    'STOPPED' | Set-Content -LiteralPath $res -Encoding ascii
    break
  }
  if ((([DateTime]::UtcNow) - $lastBeat).TotalSeconds -ge 20) {
    'BEAT' | Set-Content -LiteralPath $res -Encoding ascii
    $lastBeat = [DateTime]::UtcNow
  }
  Start-Sleep -Milliseconds 250
}
""";

    /** Unelevated EcoQoS (Power Throttling) setter. {@code %s} placeholders: pid, {@code $true}/{$false}. */
    private static final String ECO_SCRIPT = """
$targetPid = %s
$throttle = %s
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class EcoQos {
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool SetProcessInformation(IntPtr hProcess, int ProcessInformationClass, ref PROCESS_POWER_THROTTLING_STATE ProcessInformation, uint ProcessInformationSize);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern IntPtr OpenProcess(uint processAccess, bool bInheritHandle, uint processId);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool CloseHandle(IntPtr hObject);
    const uint PROCESS_SET_INFORMATION = 0x0200;
    const int ProcessPowerThrottling = 4;
    const uint PROCESS_POWER_THROTTLING_CURRENT_VERSION = 1;
    const uint PROCESS_POWER_THROTTLING_EXECUTION_SPEED = 0x1;
    [StructLayout(LayoutKind.Explicit, Size = 12)]
    public struct PROCESS_POWER_THROTTLING_STATE {
        [FieldOffset(0)] public uint Version;
        [FieldOffset(4)] public uint ControlMask;
        [FieldOffset(8)] public uint StateMask;
    }
    public static bool SetThrottling(uint pid, bool throttle) {
        IntPtr h = OpenProcess(PROCESS_SET_INFORMATION, false, pid);
        if (h == IntPtr.Zero) return false;
        PROCESS_POWER_THROTTLING_STATE s = new PROCESS_POWER_THROTTLING_STATE();
        s.Version = PROCESS_POWER_THROTTLING_CURRENT_VERSION;
        s.ControlMask = PROCESS_POWER_THROTTLING_EXECUTION_SPEED;
        s.StateMask = throttle ? PROCESS_POWER_THROTTLING_EXECUTION_SPEED : (uint)0;
        bool ok = SetProcessInformation(h, ProcessPowerThrottling, ref s, 12);
        CloseHandle(h);
        return ok;
    }
}
'@
$ok = [EcoQos]::SetThrottling($targetPid, $throttle)
if ($ok) { Write-Output 'ECO:OK' } else { Write-Output 'ECO:FAIL' }
""";

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
            boolean ecoThrottled,
            String note
    ) {
    }

    /** Persisted turbo state: which plan is the turbo plan, which was the original, and flags. */
    private record State(String attempted, String backup, boolean idleSet, boolean active) {
    }

    private record Snapshot(int exit, String output) {
    }

    private final JavaPlugin plugin;
    private final AsyncExecutor asyncExecutor;
    private final ConfigService configService;
    private final Path stateFile;
    private final Path cmdFile;
    private final Path resFile;
    private final long ownPid;
    private volatile boolean runtimeApplied;
    private volatile boolean ecoThrottled;

    public WindowsOptimizationService(JavaPlugin plugin, AsyncExecutor asyncExecutor,
                                      ConfigService configService) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.configService = configService;
        Path dataFolder = PluginIdentity.dataFolder(plugin).toPath();
        this.stateFile = dataFolder.resolve("turbo-state.txt");
        this.cmdFile = dataFolder.resolve("turbo-cmd.txt");
        this.resFile = dataFolder.resolve("turbo-result.txt");
        this.ownPid = ProcessHandle.current().pid();
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

    /** Whether the configured recipe changes anything (used to skip pointless privileged calls). */
    public boolean recipeTouchesSettings() {
        return currentParameters().touchesSettings();
    }

    /** Master switch of the presence-based auto idle/wake (config {@code turbo.auto.enabled}). */
    public boolean autoEnabled() {
        return configService.config().getBoolean("turbo.auto.enabled", true);
    }

    /** Seconds to wait after the last player leaves before entering idle (config, 0 = immediate). */
    public int autoIdleDelaySeconds() {
        return Math.max(0, configService.config().getInt("turbo.auto.idle-delay-seconds", 0));
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

    public boolean isEcoThrottled() {
        return ecoThrottled;
    }

    public CompletableFuture<ApplyResult> applyAsync() {
        final Parameters p = currentParameters();
        return asyncExecutor.supplyAsync(() -> applyBlocking(p, true));
    }

    public CompletableFuture<RevertResult> revertAsync() {
        final Parameters p = currentParameters();
        return asyncExecutor.supplyAsync(() -> revertBlocking(p, true));
    }

    /**
     * Same as {@link #applyAsync()} but never starts the resident helper: the plan is re-applied
     * ONLY when the helper (elevated once by an administrator) is already alive. Used by the
     * presence-based auto transition so it can never pop an unsolicited UAC dialog.
     */
    public CompletableFuture<ApplyResult> applyQuietAsync() {
        final Parameters p = currentParameters();
        return asyncExecutor.supplyAsync(() -> applyBlocking(p, false));
    }

    /**
     * Same as {@link #revertAsync()} but never starts the resident helper — used by the auto idle
     * transition so leaving the server empty can never pop an unsolicited UAC dialog.
     */
    public CompletableFuture<RevertResult> revertQuietAsync() {
        final Parameters p = currentParameters();
        return asyncExecutor.supplyAsync(() -> revertBlocking(p, false));
    }

    public CompletableFuture<StateReport> statusAsync() {
        return asyncExecutor.supplyAsync(this::statusBlocking);
    }

    public CompletableFuture<Boolean> setEcoQosAsync(boolean throttle) {
        return asyncExecutor.supplyAsync(() -> setEcoQosBlocking(throttle));
    }

    // ---------------------------------------------------------------------------------------------
    // apply on / off
    // ---------------------------------------------------------------------------------------------

    private ApplyResult applyBlocking(Parameters p, boolean allowHelperStart) {
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
        // Quiet mode (auto transitions) never raises a UAC prompt: it answers only when the
        // resident helper — elevated once by an administrator — is still alive.
        if (!allowHelperStart && !helperLooksAlive()) {
            lines.add("常駐の昇格ヘルパーが起動していないため、自動でのフルパワー復帰はスキップしました。"
                    + "管理者が一度 /turbo on を実行すると自動切替も有効になります。");
            return new ApplyResult(false, false, lines);
        }
        Map<String, String> markers = executePrivileged(
                token -> buildApplyInner(token, p, previous), p.uacWaitSeconds());
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

        if (p.procThrottleMin100()) {
            noteSetting(lines, markers, "PROCTHROTTLEMIN",
                    "プロセッサ最小状態 100% (P-state下限=定格維持)");
        }
        if (p.coreParkingMinCores() >= 0) {
            noteSetting(lines, markers, "CPMINCORES",
                    "コアパーキング最小コア " + p.coreParkingMinCores() + "% (全コア駐車解除)");
        }
        if (p.utilityDistribution() >= 0) {
            noteSetting(lines, markers, "DISTRIBUTEUTIL",
                    "ユーティリティ分散 " + p.utilityDistribution() + " (低負荷でも全コアへ分散)");
        }
        if (p.disableIdle()) {
            noteSetting(lines, markers, "IDLEDISABLE",
                    "プロセッサ・アイドル無効化=1 (深いC-state回避・任意)");
        }
        boolean activeOk = "0".equals(markers.get("ACTIVEEXIT"));
        lines.add((activeOk ? "✓ " : "✗ ") + "プラン適用: " + printable(target));

        boolean succeeded = "OK".equals(markers.get("RESULT"));
        if (succeeded) {
            persistState(new State(target, blankToNull(original), p.disableIdle(), true));
            runtimeApplied = true;
            lines.add("保持: サーバ再起動前は維持されます。解除は /turbo off (または自動省電力) で。");
        } else {
            runtimeApplied = false;
            lines.add("一部の設定に失敗しました。詳細は上記の ✗ 項目を確認してください。");
        }
        return new ApplyResult(succeeded, false, lines);
    }

    private void noteSetting(List<String> lines, Map<String, String> markers, String alias, String label) {
        boolean ok = "0".equals(markers.get(alias + "EXIT"));
        lines.add((ok ? "✓ " : "✗ ") + label);
    }

    private RevertResult revertBlocking(Parameters p, boolean allowHelperStart) {
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
        // Quiet mode: never raise a UAC prompt (auto idle transition only).
        if (!allowHelperStart && !helperLooksAlive()) {
            lines.add("常駐の昇格ヘルパーが起動していないため、自動でのプラン復元はスキップしました。"
                    + "(サーバーが乗っ取られて勝手に UAC を出せないよう、自動切替は無プロンプトで動きます。)");
            return new RevertResult(true, false, lines);
        }

        Map<String, String> markers = executePrivileged(
                token -> buildRevertInner(token, state, p.revertIdleOnOff()),
                p.uacWaitSeconds());
        if (markers == null || markers.containsKey("MISSING")) {
            lines.add("特権昇格の承認が得られませんでした (UAC がキャンセルされました)。");
            return new RevertResult(false, true, lines);
        }

        boolean ok = "OK".equals(markers.get("RESULT"));
        if (state.idleSet() && p.revertIdleOnOff()) {
            boolean idleReset = "0".equals(markers.get("IDLERESETEXIT"));
            lines.add((idleReset ? "✓ " : "✗ ") + "アイドル無効化を解除 (IDLEDISABLE=0): "
                    + printable(state.attempted()));
        } else if (state.idleSet()) {
            lines.add("- アイドル無効化は設定に従いそのまま残します (revert-idle-on-off: false)。");
        }
        if (state.backup() != null && !state.backup().equalsIgnoreCase(state.attempted())) {
            boolean restored = "0".equals(markers.get("RESTOREEXIT"));
            lines.add((restored ? "✓ " : "✗ ") + "元の電源プランへ復元: " + printable(state.backup()));
        } else {
            lines.add("アクティブプランは元のままです (設定値のみ解除・復元)。");
        }

        if (ok) {
            // Keep the duplicated plan GUID so the next apply reuses it instead of piling plans up.
            persistState(new State(state.attempted(), state.backup(), false, false));
            runtimeApplied = false;
            lines.add("Turbo最適化を解除しました。");
        } else {
            lines.add("一部の解除に失敗しました。管理者権限を確認して再度 /turbo off を実行してください。");
        }
        return new RevertResult(ok, false, lines);
    }

    private StateReport statusBlocking() {
        if (!isWindows()) {
            return new StateReport(false, false, false, null, null, null, false, false,
                    "Windows 専用機能です");
        }
        String active = null;
        Snapshot activeSnap = capture(List.of(powercfgExe(), "-getactivescheme"), 15);
        if (activeSnap != null) {
            active = parseGuid(activeSnap.output());
        }
        State state = readState();
        boolean applied = state.active();
        boolean privileged = hasAdminPrivileges();
        String note = privileged
                ? null
                : "権限は常駐ヘルパー経由 (初回のみ UAC)。サーバーJVM 自体は非昇格です。";
        return new StateReport(true, privileged, applied, active,
                state.attempted(), state.backup(), state.idleSet(), ecoThrottled, note);
    }

    // ---------------------------------------------------------------------------------------------
    // EcoQoS (unelevated process power throttling)
    // ---------------------------------------------------------------------------------------------

    private synchronized boolean setEcoQosBlocking(boolean throttle) {
        if (!isWindows()) {
            return false;
        }
        boolean ok = runEcoQos(throttle);
        if (ok) {
            ecoThrottled = throttle;
        }
        return ok;
    }

    private boolean runEcoQos(boolean throttle) {
        String script = String.format(ECO_SCRIPT, Long.toString(ownPid), throttle ? "$true" : "$false");
        Snapshot snapshot = capture(List.of(powershellExe(),
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encode(script)), 60);
        if (snapshot == null) {
            return false;
        }
        return snapshot.output().contains("ECO:OK");
    }

    // ---------------------------------------------------------------------------------------------
    // elevated helper plumbing
    // ---------------------------------------------------------------------------------------------

    /**
     * Ensures the resident elevated helper is alive, posts the command built by {@code innerBuilder}
     * and awaits its {@code DONE:<token>:<result>} line. Returns the markers written by the inner
     * script, or {@code {MISSING -> true}} when the helper could not be started / did not answer.
     */
    private synchronized Map<String, String> executePrivileged(Function<String, String> innerBuilder,
                                                               int waitSeconds) {
        if (!isWindows()) {
            return Map.of("MISSING", "1");
        }
        if (!helperLooksAlive() && !startHelper(waitSeconds)) {
            return Map.of("MISSING", "1");
        }
        String token = UUID.randomUUID().toString();
        String inner = innerBuilder.apply(token);
        try {
            Files.writeString(cmdFile, token + "\n" + encode(inner) + "\n", StandardCharsets.US_ASCII);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "turbo: could not post command to helper", e);
            return Map.of("MISSING", "1");
        }
        return pollResult(token, waitSeconds + 30L);
    }

    private boolean helperLooksAlive() {
        try {
            if (!Files.exists(resFile)) {
                return false;
            }
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(resFile).toMillis();
            if (age > 30_000L) {
                return false;
            }
            String content = Files.readString(resFile);
            return content != null && !content.isBlank() && !content.contains("STOPPED");
        } catch (IOException e) {
            return false;
        }
    }

    private boolean startHelper(int waitSeconds) {
        deleteQuiet(cmdFile);
        deleteQuiet(resFile);
        String runner = String.format(HELPER_RUNNER,
                singleQuote(cmdFile.toString()), singleQuote(resFile.toString()));
        String outer = "Start-Process -Verb RunAs -WindowStyle Hidden -FilePath '"
                + singleQuote(powershellExe())
                + "' -ArgumentList '-NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand "
                + encode(runner) + "'";
        capture(List.of(powershellExe(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encode(outer)), 30);
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String content = readResQuiet();
            if (content != null && !content.isBlank() && !content.contains("STOPPED")) {
                return true;
            }
            sleep(300);
        }
        return false;
    }

    private Map<String, String> pollResult(String token, long timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            String content = readResQuiet();
            if (content != null) {
                if (content.contains("DONE:" + token + ":")) {
                    return parseMarkers(content);
                }
                if (content.trim().startsWith("STOPPED")) {
                    return Map.of("MISSING", "1");
                }
            }
            sleep(200);
        }
        plugin.getLogger().warning("turbo: helper did not answer within " + timeoutSeconds + "s");
        return Map.of("MISSING", "1");
    }

    // ---------------------------------------------------------------------------------------------
    // inner PowerShell builders (run inside the elevated helper)
    // ---------------------------------------------------------------------------------------------

    private String buildApplyInner(String token, Parameters p, State previous) {
        StringBuilder b = new StringBuilder();
        b.append("$pcf = '").append(singleQuote(powercfgExe())).append("'\n");
        b.append("$res = '").append(singleQuote(resFile.toString())).append("'\n");
        b.append("$sb = New-Object System.Text.StringBuilder\n");
        b.append("$anyFail = 0\n");
        b.append("$m = $null\n");
        b.append("$t = & $pcf -getactivescheme 2>&1 | Out-String\n");
        b.append("if ($LASTEXITCODE -ne 0) { $anyFail = 1 }\n");
        b.append(matchGUID());
        b.append("$orig = if ($m.Success) { $m.Groups[1].Value } else { '' }\n");
        b.append("[void]$sb.AppendLine('#ORIGGUID:' + $orig)\n");

        if (previous.attempted() != null) {
            b.append("$target = '").append(singleQuote(previous.attempted())).append("'\n");
            b.append("$t = & $pcf -setactive $target 2>&1 | Out-String\n");
            b.append("$dupFail = 0\n");
            b.append("if ($LASTEXITCODE -ne 0) { $dupFail = 1; $anyFail = 1 }\n");
            b.append("[void]$sb.AppendLine('#DUPEXIT:' + $dupFail)\n");
        } else if (p.accentPowerPlan() != null) {
            b.append("$t = & $pcf -duplicatescheme '").append(singleQuote(p.accentPowerPlan()))
                    .append("' 2>&1 | Out-String\n");
            b.append("$dupFail = 0\n");
            b.append("if ($LASTEXITCODE -ne 0) { $dupFail = 1; $target = '' } else {\n");
            b.append("  ").append(matchGUID());
            b.append("  $target = if ($m.Success) { $m.Groups[1].Value } else { '' }\n");
            b.append("  if ($target -eq '') { $dupFail = 1; $anyFail = 1 }\n");
            b.append("}\n");
            b.append("[void]$sb.AppendLine('#DUPEXIT:' + $dupFail)\n");
        } else {
            b.append("$target = $orig\n");
            b.append("[void]$sb.AppendLine('#DUPEXIT:0')\n");
        }
        b.append("[void]$sb.AppendLine('#TARGETGUID:' + $target)\n");

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
        b.append("  if ($LASTEXITCODE -ne 0) { $anyFail = 1 }\n");
        b.append("  [void]$sb.AppendLine('#ACTIVEEXIT:' + $LASTEXITCODE)\n");
        b.append("} else {\n");
        b.append("  $anyFail = 1\n");
        b.append("  [void]$sb.AppendLine('#ACTIVEEXIT:-1')\n");
        b.append("}\n");

        b.append("if ($dupFail -eq 0 -and $anyFail -eq 0 -and $target -ne '') { $result = 'OK' } ")
                .append("else { $result = 'FAIL' }\n");
        b.append("[void]$sb.AppendLine('DONE:' + '").append(token).append("' + ':' + $result)\n");
        b.append("Set-Content -LiteralPath $res -Value $sb.ToString() -Encoding ascii\n");
        return b.toString();
    }

    private String buildRevertInner(String token, State state, boolean revertIdleOnOff) {
        StringBuilder b = new StringBuilder();
        b.append("$pcf = '").append(singleQuote(powercfgExe())).append("'\n");
        b.append("$res = '").append(singleQuote(resFile.toString())).append("'\n");
        b.append("$sb = New-Object System.Text.StringBuilder\n");
        b.append("$anyFail = 0\n");

        if (state.idleSet() && revertIdleOnOff && state.attempted() != null) {
            b.append("$t = & $pcf -setacvalueindex '").append(singleQuote(state.attempted()))
                    .append("' sub_processor IDLEDISABLE 0 2>&1 | Out-String\n");
            b.append("if ($LASTEXITCODE -ne 0) { $anyFail = 1 }\n");
            b.append("[void]$sb.AppendLine('#IDLERESETEXIT:' + $LASTEXITCODE)\n");
        } else {
            b.append("[void]$sb.AppendLine('#IDLERESETEXIT:0')\n");
        }
        if (state.backup() != null && !state.backup().equalsIgnoreCase(state.attempted())) {
            b.append("$t = & $pcf -setactive '").append(singleQuote(state.backup()))
                    .append("' 2>&1 | Out-String\n");
            b.append("if ($LASTEXITCODE -ne 0) { $anyFail = 1 }\n");
            b.append("[void]$sb.AppendLine('#RESTOREEXIT:' + $LASTEXITCODE)\n");
        } else {
            b.append("[void]$sb.AppendLine('#RESTOREEXIT:0')\n");
        }
        b.append("if ($anyFail -eq 0) { $result = 'OK' } else { $result = 'FAIL' }\n");
        b.append("[void]$sb.AppendLine('DONE:' + '").append(token).append("' + ':' + $result)\n");
        b.append("Set-Content -LiteralPath $res -Value $sb.ToString() -Encoding ascii\n");
        return b.toString();
    }

    private static String matchGUID() {
        return "$m = [regex]::Match($t,'" + GUID_REGEX + "')\n";
    }

    private static void setAcIndex(StringBuilder b, String alias, String value) {
        b.append("  $t = & $pcf -setacvalueindex $target sub_processor ").append(alias)
                .append(' ').append(value).append(" 2>&1 | Out-String\n");
        b.append("  if ($LASTEXITCODE -ne 0) { $anyFail = 1 }\n");
        b.append("  [void]$sb.AppendLine('#").append(alias).append("EXIT:' + $LASTEXITCODE)\n");
    }

    // ---------------------------------------------------------------------------------------------
    // shutdown
    // ---------------------------------------------------------------------------------------------

    /** Best-effort cleanup on plugin disable: un-throttle, revert the plan, stop the helper. */
    public void shutdown() {
        if (!isWindows()) {
            return;
        }
        try {
            if (ecoThrottled) {
                runEcoQos(false);
                ecoThrottled = false;
            }
        } catch (Exception ignored) {
            // never break plugin shutdown
        }
        try {
            State state = readState();
            if (state.active() && state.attempted() != null) {
                if (helperLooksAlive()) {
                    Map<String, String> markers = executePrivileged(
                            token -> buildRevertInner(token, state, true), 20);
                    if ("OK".equals(markers.get("RESULT"))) {
                        persistState(new State(state.attempted(), state.backup(), false, false));
                        runtimeApplied = false;
                    }
                } else {
                    plugin.getLogger().warning(
                            "turbo: 常駐ヘルパーが見つからないため電源プランを復元できませんでした。"
                                    + "次回起動時に /turbo off または /turbo status を確認してください。");
                }
            }
        } catch (Exception ignored) {
            // best effort only
        }
        try {
            Files.writeString(cmdFile, "STOP\n", StandardCharsets.US_ASCII);
        } catch (IOException ignored) {
            // helper exits on its own after 6h regardless
        }
    }

    // ---------------------------------------------------------------------------------------------
    // state persistence
    // ---------------------------------------------------------------------------------------------

    private void persistState(State state) {
        String text = "attempted: " + state.attempted() + "\n"
                + "backup: " + (state.backup() == null ? "" : state.backup()) + "\n"
                + "idledisable: " + state.idleSet() + "\n"
                + "active: " + state.active() + "\n";
        try {
            Files.writeString(stateFile, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "turbo: could not persist state", e);
        }
    }

    private State readState() {
        try {
            if (!Files.exists(stateFile)) {
                return new State(null, null, false, false);
            }
            String attempted = null;
            String backup = null;
            boolean idle = false;
            boolean active = false;
            for (String line : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("attempted:")) {
                    attempted = trimValue(line);
                } else if (line.startsWith("backup:")) {
                    backup = trimValue(line);
                } else if (line.startsWith("idledisable:") || line.startsWith("idle:")) {
                    idle = Boolean.parseBoolean(trimValue(line));
                } else if (line.startsWith("active:")) {
                    active = Boolean.parseBoolean(trimValue(line));
                }
            }
            return new State(blankToNull(attempted), blankToNull(backup), idle, active);
        } catch (IOException e) {
            return new State(null, null, false, false);
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

    private void deleteQuiet(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private String readResQuiet() {
        try {
            if (!Files.exists(resFile)) {
                return null;
            }
            return Files.readString(resFile);
        } catch (IOException e) {
            return null;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

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

    private static Map<String, String> parseMarkers(String content) {
        Map<String, String> markers = new LinkedHashMap<>();
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                int colon = trimmed.indexOf(':');
                if (colon > 1) {
                    markers.put(trimmed.substring(1, colon), trimmed.substring(colon + 1).trim());
                }
            } else if (trimmed.startsWith("DONE:")) {
                String[] parts = trimmed.split(":", 3);
                if (parts.length == 3) {
                    markers.put("TOKEN", parts[1]);
                    markers.put("RESULT", parts[2]);
                }
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
