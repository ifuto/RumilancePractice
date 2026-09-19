package com.rumilance.practice.quantum;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rumilance.practice.herobot.HeroBotPlayer;
import com.rumilance.practice.herobot.HeroBotRegistry;
import com.rumilance.practice.herobot.HeroBotSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runs the Quantum Practicebot datapack on Paper.
 *
 * <p>The idea is the one that replaces hand-porting behaviour function by function: the map's
 * own {@code .mcfunction} files stay the source of truth and execute unmodified, on top of</p>
 * <ul>
 *   <li>{@link QuantumFunctionRegistry} — compiles the pack against the live dispatcher and
 *       installs it into the vanilla function library (so {@code function}, {@code execute if
 *       function}, {@code schedule function} and the {@code #minecraft:tick} tag are vanilla's),</li>
 *   <li>{@link HeroBotRegistry} — carpet-style fake players with HeroBot's verb set, so
 *       {@code player @s move/look/use/attack/hotbar/itemCd …} mean what they mean on the
 *       reference Fabric server.</li>
 * </ul>
 *
 * <p>Everything else the map asks for already exists on Paper: scoreboards, objectives, item
 * components, {@code forceload}, structures, predicates and advancements (the latter two come
 * from the vanilla pack loader when the pack sits in a world's {@code datapacks/} folder — see
 * {@code include-world-datapacks}).</p>
 *
 * <p>The runtime's job is therefore narrow: load the pack, keep the functions installed across
 * datapack reloads, spawn/remove the named bot, and drive the map's own option functions
 * ({@code quantum:options/*}, {@code quantum:options/toggles/*}, {@code quantum:difficulty/*},
 * {@code .start}).</p>
 */
public final class QuantumRuntime {

    private final Plugin plugin;
    private final HeroBotRegistry bots;
    private final QuantumFunctionRegistry functions;
    private final QuantumCommands commands;

    private QuantumPack pack = new QuantumPack();
    private File configFile;
    private YamlConfiguration config;
    private boolean enabled;
    private BukkitTask watchdog;
    /** Drives each private qbot_N:tick once per server tick; no shared quantum:tick tag is used. */
    private BukkitTask instanceTicker;
    private final Map<UUID, QuantumInstance> instances = new LinkedHashMap<>();
    private final AtomicInteger nextInstanceNumber = new AtomicInteger(1);
    /** Watchdog pacing: a healthy install goes back to the 5 s cadence, a broken one backs off. */
    private volatile long watchdogIntervalMs = 5_000L;
    private volatile long nextWatchdogAt;
    private volatile long lastReinstallLogAt;
    /** Warn once that the pack roots contain no .mcfunction files. */
    private volatile boolean zeroPackWarned;
    /**
     * PvP サーバー側のキット (および {@code /botadmin} の紐づけ) への橋。null なら
     * キット連携なし = マップ側のキットチェストが唯一の供給元 (パリティ計測時はこちら)。
     */
    private com.rumilance.practice.practice.PracticeService practice;

    public QuantumRuntime(Plugin plugin, HeroBotRegistry bots) {
        this.plugin = plugin;
        this.bots = bots;
        this.functions = new QuantumFunctionRegistry(plugin);
        this.commands = new QuantumCommands(plugin, bots);
    }

    public QuantumCommands commands() {
        return this.commands;
    }

    /**
     * サーバーのキットサービスを繋ぐ ({@code FeatureBootstrap})。
     *
     * <p>PvP サーバーとしての役割: BOT の装備は参照マップのキットチェストではなく
     * <b>サーバーのキット</b>から与える。管理者の紐づけは {@code /botadmin} の2形式 —
     * {@code /botadmin <SWORD|MACE|CRYSTAL|NETHERITE_POT|CART> <キット>} (装備) と
     * {@code /botadmin <botkit> <arenakit>} (開催アリーナ) — で、どちらも
     * {@link com.rumilance.practice.practice.PracticeService} の同じストアに入る。</p>
     */
    public void setPracticeService(com.rumilance.practice.practice.PracticeService practice) {
        this.practice = practice;
        for (HeroBotPlayer bot : this.bots.all()) {
            this.applyConfiguredBotLoadout(bot);
        }
    }

    public HeroBotRegistry bots() {
        return this.bots;
    }

    public QuantumFunctionRegistry functions() {
        return this.functions;
    }

    public YamlConfiguration config() {
        return this.config;
    }

    public boolean enabled() {
        return this.enabled;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Loads config + pack and installs the functions. Called from the plugin bootstrap. */
    public void enable() {
        this.configFile = new File(this.plugin.getDataFolder(), "quantum.yml");
        this.writeDefaultConfig();
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
        HeroBotSettings.loadFrom(this.configFile, this.plugin.getSLF4JLogger());
        this.enabled = this.config.getBoolean("enabled", true);
        if (!this.enabled) {
            this.plugin.getLogger().info("[Quantum] disabled in quantum.yml — runtime idle");
            return;
        }
        // Deploy the map pack that ships inside the jar before the install can read it.
        this.extractBundledPack();
        // The verbs must be in the dispatcher before anything compiles a 'player …' line, so the
        // install hangs off the command registration rather than running right here.
        this.commands.listen(this::installWhenReady);
        // ★ /reload 対策。サーバーのリソース読み直しは「新しい dispatcher を作る」→
        // 「その dispatcher で .mcfunction をコンパイルする」順で進むので、`player …` を
        // 呼ぶ行は動詞が無い状態でコンパイルされて *必ず* 落ちる(Fabric 参照では起きない)。
        // これまでは 5 秒おきの watchdog が拾っていたが、読み直しが非同期に終わるため
        // 「自前ロードが先・vanilla の差し替えが後」の競合で負けることがあり、その回の
        // ラウンドは動詞なし状態のまま計測されていた(cry6 の Paper 側ゼロ)。
        // Paper のこのイベントは *読み直しが終わった後* に走るので、そこで動詞を戻し、
        // 自前ローダでパックを入れ直す = Fabric の /reload と同じ状態に即座に揃う。
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(
                    priority = org.bukkit.event.EventPriority.MONITOR)
            public void onResourcesReloaded(
                    io.papermc.paper.event.server.ServerResourcesReloadedEvent event) {
                reinstallAfterResourceReload(event.getCause());
            }
        }, this.plugin);
        // Self-healing, for the two ways this can be undone behind our back: /reload rebuilds the
        // function library from the packs on disk (dropping the functions that only compiled with
        // 'player' present), and a dispatcher swap takes the verbs away from the map's lines.
        //
        // The cadence is adaptive: a healthy install returns to 5 s; a broken state (verbs never
        // register, pack unreadable, ...) doubles up to 2 min instead of hammering the log every
        // 5 s forever — and the "reinstalling" line itself is capped at one per 30 s.
        this.watchdog = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!this.enabled) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now < this.nextWatchdogAt) {
                return;
            }
            this.nextWatchdogAt = now + this.watchdogIntervalMs;
            if (!this.commands.areRootsRegistered()) {
                this.commands.ensureRoots();
            }
            boolean roots = this.commands.areRootsRegistered();
            if (this.functions.isInstalled() && roots) {
                this.watchdogIntervalMs = 5_000L;
                return;
            }
            if (now - this.lastReinstallLogAt >= 30_000L) {
                this.lastReinstallLogAt = now;
                this.plugin.getLogger().info("[Quantum] reinstalling the Quantum functions (roots="
                        + roots + ")");
            }
            QuantumFunctionRegistry.Result result = this.installWhenReady();
            if (!this.zeroPackWarned && result.functions() == 0 && result.tags() == 0) {
                this.zeroPackWarned = true;
                this.plugin.getLogger().warning("[Quantum] no .mcfunction files under the pack roots "
                        + this.packRoots()
                        + " — the bundled map pack should deploy to plugins/RumilancePractice/quantum"
                        + " at boot; check quantum.yml ('packs', 'include-world-datapacks') if the map"
                        + " should live elsewhere. Set 'enabled: false' in"
                        + " plugins/RumilancePractice/quantum.yml to silence the runtime.");
            }
            if (this.functions.isInstalled() && roots) {
                this.watchdogIntervalMs = 5_000L;
            } else {
                this.watchdogIntervalMs = Math.min(this.watchdogIntervalMs * 2, 120_000L);
            }
        }, 100L, 100L);
        // The owner is the instance target. A quit must tear down the fake player and its private
        // function namespace instead of leaving a tagged target for the next online player.
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                List<UUID> owned = new ArrayList<>();
                for (QuantumInstance instance : instances.values()) {
                    if (event.getPlayer().getUniqueId().equals(instance.targetUuid())) {
                        owned.add(instance.botUuid());
                    }
                }
                for (UUID bot : owned) {
                    despawnInstance(bot);
                }
            }
        }, this.plugin);
    }

    /**
     * リソース読み直し(サーバーの {@code /reload} やプラグインからの {@code reloadResources})が
     * 終わった直後の復旧処理。vanilla が作った新しい関数ライブラリには {@code player …} を
     * 呼ぶ関数が入っていないので、動詞を戻してから自前ローダで入れ直す。
     *
     * <p>ここで再コンパイル(= {@code reloadResources})を投げないのが重要: このイベントは
     * 読み直しの *結果* なので、投げると自分自身を永久に呼び続ける。</p>
     */
    private void reinstallAfterResourceReload(Object cause) {
        if (!this.enabled) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.commands.areRootsRegistered()) {
                this.commands.ensureRoots(false);
            }
            this.plugin.getLogger().info("[Quantum] resource reload (" + cause
                    + ") -- re-asserted the herobot verbs and re-loaded the pack's functions "
                    + "(vanilla's own pass cannot compile lines that call /player)");
            this.installWhenReady();
        });
    }

    public void disable() {
        if (this.watchdog != null) {
            this.watchdog.cancel();
            this.watchdog = null;
        }
        if (this.instanceTicker != null) {
            this.instanceTicker.cancel();
            this.instanceTicker = null;
        }
        for (QuantumInstance instance : List.copyOf(this.instances.values())) {
            Player target = Bukkit.getPlayer(instance.targetUuid());
            if (target != null) {
                target.removeScoreboardTag(instance.targetTag());
                target.removeScoreboardTag(instance.participantTag());
            }
        }
        this.instances.clear();
        this.bots.despawnAll();
    }

    private void writeDefaultConfig() {
        if (this.configFile.exists()) {
            return;
        }
        File parent = this.configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (InputStream in = this.plugin.getResource("quantum.yml")) {
            if (in == null) {
                return;
            }
            Files.copy(in, this.configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            this.plugin.getLogger().warning("[Quantum] could not write quantum.yml: " + e.getMessage());
        }
    }

    /** Re-reads quantum.yml, re-reads the packs from disk and reinstalls every function. */
    public QuantumFunctionRegistry.Result reload() {
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
        this.enabled = this.config.getBoolean("enabled", true);
        HeroBotSettings.loadFrom(this.configFile, this.plugin.getSLF4JLogger());
        return this.installWhenReady();
    }

    /**
     * Compiles the pack — but only once the {@code player}/{@code playerspawn}/{@code herobot}
     * verbs are in the dispatcher, because every function that calls one of them (i.e. nearly all
     * of the map's) fails to compile without them.
     */
    private QuantumFunctionRegistry.Result installWhenReady() {
        if (!this.enabled) {
            return new QuantumFunctionRegistry.Result(0, 0, List.of("disabled"));
        }
        if (!this.commands.areRootsRegistered()) {
            this.commands.ensureRoots();
        }
        if (!this.commands.areRootsRegistered()) {
            this.plugin.getLogger().warning("[Quantum] herobot verbs are not registered yet; "
                    + "deferring the function install");
            return new QuantumFunctionRegistry.Result(this.functions.installedCount(),
                    this.functions.tagCount(), List.of("deferred: verbs not registered"));
        }
        return this.install();
    }

    private QuantumFunctionRegistry.Result install() {
        List<Path> roots = this.packRoots();
        this.pack = QuantumPack.load(roots);
        QuantumFunctionRegistry.Result result = this.functions.install(this.pack);
        this.plugin.getLogger().info("[Quantum] loaded " + result.functions() + " function(s) and "
                + result.tags() + " tag(s) from " + roots.size() + " source(s)"
                + (this.functions.rewrittenLines() == 0 ? "" : " ("
                + this.functions.rewrittenLines()
                + " line(s) translated from the mod's distanceH=/distanceV= selector options)")
                + (result.failures().isEmpty() ? "" : " (" + result.failures().size()
                + " could not be compiled, see /quantum list failures)"));
        for (String failure : result.failures()) {
            this.plugin.getLogger().warning("[Quantum] function not compiled: " + failure);
        }
        return result;
    }

    private List<Path> packRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        for (String entry : this.config.getStringList("packs")) {
            Path path = Path.of(entry);
            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir", ".")).resolve(entry);
            }
            roots.add(path);
        }
        // The bundled pack always deploys to <dataFolder>/quantum — read it no matter what the
        // (possibly stale, e.g. pre-rename 'plugins/RumilancePractice/quantum') config says.
        roots.add(this.plugin.getDataFolder().toPath().resolve("quantum"));
        if (this.config.getBoolean("include-world-datapacks", true)) {
            for (World world : Bukkit.getWorlds()) {
                File folder = new File(world.getWorldFolder(), "datapacks");
                if (folder.isDirectory()) {
                    roots.add(folder.toPath());
                }
            }
            File container = new File(System.getProperty("user.dir", "."),
                    this.config.getString("world.name", "quantum") + "/datapacks");
            if (container.isDirectory()) {
                roots.add(container.toPath());
            }
        }
        return new ArrayList<>(roots);
    }

    // ------------------------------------------------------------------ bundled pack

    /** Jar resource prefix of the map pack that ships inside the plugin. */
    private static final String BUNDLED_PACK_ROOT = "quantum-pack/";
    /** Marker recording the hash of the pack last deployed from the jar. */
    private static final String BUNDLED_PACK_MARKER = ".bundled-pack-sha256";

    /**
     * Deploys the map pack bundled inside the jar (resource {@code quantum-pack/Practicebot/})
     * into {@code plugins/RumilancePractice/quantum/} — the default {@code packs} entry of
     * quantum.yml — so a server that only has the jar finds the map's {@code .mcfunction} files
     * without any manual zip placement.
     *
     * <p>Deployment is marker-based ({@code .bundled-pack-sha256}): the hash of the bundled
     * content is compared against the marker, and a boot with an unchanged bundle writes
     * nothing (local edits to extracted files are never clobbered). When the bundle changes —
     * i.e. the plugin was updated with a newer map — only the files that differ are rewritten.</p>
     *
     * <p>Only the function side is self-contained this way: dimensions, structures and
     * advancements still need the pack (or the imported world) in a world's {@code datapacks/}
     * folder, exactly as before.</p>
     */
    private void extractBundledPack() {
        List<BundledPackFile> bundled;
        try {
            bundled = readBundledPack();
        } catch (Exception e) {
            this.plugin.getLogger().warning("[Quantum] cannot read the bundled map pack: " + e);
            return;
        }
        if (bundled.isEmpty()) {
            return; // this build ships no bundled pack
        }
        String hash;
        try {
            hash = sha256Of(bundled);
        } catch (Exception e) {
            this.plugin.getLogger().warning("[Quantum] cannot hash the bundled map pack: " + e);
            return;
        }
        Path targetRoot = this.plugin.getDataFolder().toPath().resolve("quantum");
        Path marker = targetRoot.resolve(BUNDLED_PACK_MARKER);
        try {
            if (Files.exists(marker) && hash.equals(Files.readString(marker).trim())) {
                return; // already deployed
            }
            int written = 0;
            for (BundledPackFile file : bundled) {
                Path out = targetRoot.resolve(file.relativePath());
                if (Files.exists(out) && java.util.Arrays.equals(Files.readAllBytes(out), file.content())) {
                    continue;
                }
                if (out.getParent() != null) {
                    Files.createDirectories(out.getParent());
                }
                Files.write(out, file.content());
                written++;
            }
            Files.createDirectories(targetRoot);
            Files.writeString(marker, hash);
            this.plugin.getLogger().info("[Quantum] deployed the bundled map pack (Practicebot): "
                    + written + " file(s) written under " + targetRoot
                    + " (" + bundled.size() + " bundled)");
        } catch (IOException e) {
            this.plugin.getLogger().warning("[Quantum] could not deploy the bundled map pack: " + e);
        }
    }

    /** Every bundled pack file as (relative path, content); empty list when the jar ships none. */
    private List<BundledPackFile> readBundledPack() throws Exception {
        // ClassLoader (not Plugin#getResource, which only yields an InputStream): the URL tells
        // us whether the bundle lives in the plugin jar or on a dev classpath directory.
        java.net.URL root = this.plugin.getClass().getClassLoader().getResource("quantum-pack");
        if (root == null) {
            return List.of();
        }
        List<BundledPackFile> files = new ArrayList<>();
        if ("jar".equals(root.getProtocol())) {
            java.net.JarURLConnection connection = (java.net.JarURLConnection) root.openConnection();
            try (java.util.jar.JarFile jar = connection.getJarFile()) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(BUNDLED_PACK_ROOT)) {
                        continue;
                    }
                    try (InputStream in = jar.getInputStream(entry)) {
                        files.add(new BundledPackFile(name.substring(BUNDLED_PACK_ROOT.length()),
                                in.readAllBytes()));
                    }
                }
            }
        } else {
            // dev/test classpath: a plain directory
            Path dir = Path.of(root.toURI());
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                    files.add(new BundledPackFile(
                            dir.relativize(p).toString().replace('\\', '/'),
                            Files.readAllBytes(p)));
                }
            }
        }
        return files;
    }

    private static String sha256Of(List<BundledPackFile> files) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        for (BundledPackFile file : files) {
            digest.update(file.relativePath().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(file.content());
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }

    private record BundledPackFile(String relativePath, byte[] content) {
    }

    // ------------------------------------------------------------------ map driving

    /**
     * Runs one command line through the server dispatcher — the same path the map's own
     * functions take, which is what keeps {@code return}/{@code execute} semantics identical.
     */
    public int run(CommandSender sender, String commandLine) throws CommandSyntaxException {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        CommandSourceStack source;
        String line = net.minecraft.commands.Commands.trimOptionalPrefix(commandLine);
        if (sender instanceof Player player && com.rumilance.practice.packetbot.PacketBot.isBot(player)) {
            // A fake player is deliberately not op. Run through the server-owned source so
            // datapack `function` commands retain permission 4, then switch only the execution
            // entity and position to the bot. This avoids the 1.21.11 PermissionSet internals
            // while preserving @s, execute, return, and scheduled-function semantics.
            source = server.createCommandSourceStack();
            line = "execute as " + player.getUniqueId() + " at @s run " + line;
        } else if (sender instanceof Player player) {
            source = ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle()
                    .createCommandSourceStack();
        } else {
            source = server.createCommandSourceStack();
        }
        // Through vanilla's *execution-context* engine, not CommandDispatcher#execute: commands
        // like `function` are CustomCommandExecutors, and the legacy entry point hits their
        // CommandAdapter#run, which throws "This function should not run". The console takes the
        // same path as this code (Commands#performCommand), which is why typing the line by hand
        // works while the legacy call would not.
        line = net.minecraft.commands.Commands.trimOptionalPrefix(line);
        net.minecraft.commands.Commands commands = server.getCommands();
        com.mojang.brigadier.ParseResults<CommandSourceStack> parsed =
                commands.getDispatcher().parse(line, source);
        commands.performCommand(parsed, line, true);
        return 1;
    }

    public boolean hasFunction(String id) {
        return this.functions.installedIds().contains(expand(id));
    }

    private static String expand(String id) {
        return id.contains(":") ? id : "quantum:" + id;
    }

    private HeroBotPlayer instanceBot(QuantumInstance instance) {
        for (HeroBotPlayer bot : this.bots.all()) {
            if (bot.getUUID().equals(instance.botUuid())) {
                return bot;
            }
        }
        return null;
    }

    private boolean runOnInstances(String relativeFunction) {
        if (this.instances.isEmpty()) {
            return false;
        }
        boolean ok = true;
        for (QuantumInstance instance : List.copyOf(this.instances.values())) {
            HeroBotPlayer bot = this.instanceBot(instance);
            if (bot == null) {
                ok = false;
                continue;
            }
            ok &= this.runQuietly(bot.getBukkitEntity(),
                    "function " + instance.namespace() + ":" + relativeFunction);
        }
        return ok;
    }

    /** {@code function quantum:options/<name>} — the map's mode switch. */
    public boolean setOption(CommandSender sender, String name) {
        boolean ok = this.instances.isEmpty()
                ? this.runQuietly(sender, "function " + expand("options/" + name))
                : this.runOnInstances("options/" + name);
        if (ok) {
            // モード切替はマップ側のキットチェストから装備を読み直す。/botadmin でサーバー
            // キットを紐づけているサーバーでは、その *あと* に BOT の装備をサーバーキットへ
            // 戻す (そうしないとサーバーキットがチェストの内容に上書きされる)。
            for (HeroBotPlayer bot : this.bots.all()) {
                this.applyConfiguredBotLoadout(bot);
            }
        }
        return ok;
    }

    /** {@code function quantum:options/toggles/<name>on|off}. */
    public boolean setToggle(CommandSender sender, String name, boolean on) {
        String path = "options/toggles/" + name + (on ? "on" : "off");
        if (this.instances.isEmpty()) {
            if (this.hasFunction(path)) {
                return this.runQuietly(sender, "function " + expand(path));
            }
            return this.runQuietly(sender, "scoreboard players set ." + name + " toggles " + (on ? 1 : 0));
        }
        return this.runOnInstances(path);
    }

    /** {@code function quantum:difficulty/<n>} (0 NPC … 5 MASTER). */
    public boolean setDifficulty(CommandSender sender, int difficulty) {
        return this.instances.isEmpty()
                ? this.runQuietly(sender, "function quantum:difficulty/" + difficulty)
                : this.runOnInstances("difficulty/" + difficulty);
    }

    /** The reference measurement's start switch: {@code scoreboard players set .start start 1}. */
    public boolean start(CommandSender sender) {
        if (this.instances.isEmpty()) {
            return this.runQuietly(sender, "scoreboard players set .start start 1");
        }
        boolean ok = true;
        for (QuantumInstance instance : List.copyOf(this.instances.values())) {
            HeroBotPlayer bot = this.instanceBot(instance);
            ok &= bot != null && this.runQuietly(bot.getBukkitEntity(), "scoreboard players set @s start 1");
        }
        return ok;
    }

    public boolean stop(CommandSender sender) {
        if (this.instances.isEmpty()) {
            return this.runQuietly(sender, "scoreboard players set .start start 0");
        }
        boolean ok = true;
        for (QuantumInstance instance : List.copyOf(this.instances.values())) {
            HeroBotPlayer bot = this.instanceBot(instance);
            ok &= bot != null && this.runQuietly(bot.getBukkitEntity(), "scoreboard players set @s start 0");
        }
        return ok;
    }

    /** Applies {@code scores:} and {@code toggles:} from quantum.yml (the world's saved setup). */
    public int seed(CommandSender sender) {
        int applied = 0;
        for (String key : this.config.getConfigurationSection("scores") == null
                ? Set.<String>of() : this.config.getConfigurationSection("scores").getKeys(false)) {
            String target = this.config.getString("scores." + key, "");
            if (target.isBlank()) {
                continue;
            }
            int dot = key.indexOf('.');
            String objective = dot < 0 ? key : key.substring(0, dot);
            String holder = dot < 0 ? ".seed" : key.substring(dot + 1);
            if (this.runQuietly(sender, "scoreboard players set " + holder + " " + objective + " " + target)) {
                applied++;
            }
        }
        for (String toggle : this.config.getStringList("toggles")) {
            if (this.setToggle(sender, toggle, true)) {
                applied++;
            }
        }
        return applied;
    }

    private boolean runQuietly(CommandSender sender, String line) {
        try {
            this.run(sender, line);
            return true;
        } catch (CommandSyntaxException e) {
            this.plugin.getLogger().warning("[Quantum] '" + line + "' failed: " + e.getMessage());
            return false;
        } catch (RuntimeException e) {
            this.plugin.getLogger().warning("[Quantum] '" + line + "' failed: " + e);
            return false;
        }
    }

    // ------------------------------------------------------------------ bot control

    /** Spawns one fully isolated QuantumBOT instance at the next free configured spawn slot. */
    public HeroBotPlayer spawnBot(Location fallback, Player skinTemplate) {
        String configuredName = this.config.getString("bot.name",
                com.rumilance.practice.packetbot.PacketBotFactory.DEFAULT_DISPLAY_NAME);
        String name = this.nextBotName(configuredName);
        Location where = this.botSpawn(fallback);
        GameType mode = GameType.byName(this.config.getString("bot.gamemode", "survival").toLowerCase(Locale.ROOT));
        HeroBotPlayer bot = this.bots.spawn(name, where,
                (float) this.config.getDouble("bot.yaw", where.getYaw()),
                (float) this.config.getDouble("bot.pitch", where.getPitch()),
                mode == null ? GameType.SURVIVAL : mode, skinTemplate);
        bot.ping = this.config.getInt("bot.ping", 100);

        int number = this.nextInstanceNumber.getAndIncrement();
        UUID targetUuid = skinTemplate == null ? bot.getUUID() : skinTemplate.getUniqueId();
        QuantumInstance instance = new QuantumInstance(number, bot.getUUID(), targetUuid,
                "qbot_" + number,
                "qbot_" + number,
                "qtarget_" + number,
                "qpart_" + number,
                "qent_" + number,
                "q" + number + "_");
        bot.addTag(instance.botTag());
        bot.addTag(instance.participantTag());
        if (skinTemplate != null) {
            skinTemplate.addScoreboardTag(instance.targetTag());
            skinTemplate.addScoreboardTag(instance.participantTag());
        }
        this.instances.put(bot.getUUID(), instance);
        try {
            if (!this.hasFunction("quantum:tick")) {
                this.installWhenReady();
            }
            QuantumFunctionRegistry.Result result = this.functions.registerInstance(instance);
            this.plugin.getLogger().info("[Quantum] registered instance " + instance.namespace()
                    + " for profile " + bot.profileName() + " (functions=" + result.functions()
                    + ", failures=" + result.failures().size() + ")");
            this.runQuietly(bot.getBukkitEntity(), "function " + instance.initFunction());
            this.runQuietly(bot.getBukkitEntity(), "scoreboard players set @s mode "
                    + this.config.getInt("mode", 0));
            this.runQuietly(bot.getBukkitEntity(), "scoreboard players set @s difficulty "
                    + this.config.getInt("difficulty", 0));
            this.applyConfiguredBotLoadout(bot);
            this.ensureInstanceTicker();
            return bot;
        } catch (RuntimeException error) {
            this.instances.remove(bot.getUUID());
            if (skinTemplate != null) {
                skinTemplate.removeScoreboardTag(instance.targetTag());
                skinTemplate.removeScoreboardTag(instance.participantTag());
            }
            this.bots.despawn(bot.profileName());
            throw error;
        }
    }

    /**
     * BOT にサーバーキットを着せる (設定があるときだけ)。
     *
     * <p>決定順:</p>
     * <ol>
     *   <li>{@code bot.kit: <キット名>} — 明示指定 (どのモードでもこれを使う)。</li>
     *   <li>{@code bot.mode: <SWORD|MACE|CRYSTAL|NETHERITE_POT|CART>} に
     *       {@code /botadmin <モード> <キット>} で紐づいたキット。</li>
     * </ol>
     * <p>どちらも無ければ何もしない = マップのキットチェスト (パリティ計測と同じ挙動)。</p>
     */
    private void applyConfiguredBotLoadout(HeroBotPlayer bot) {
        if (bot == null || this.practice == null || this.config == null) {
            return;
        }
        String kit = this.config.getString("bot.kit", "");
        String mode = this.config.getString("bot.mode", "");
        if ((kit == null || kit.isBlank()) && mode != null && !mode.isBlank()) {
            com.rumilance.practice.practice.PracticeType type =
                    com.rumilance.practice.practice.PracticeType.parse(mode);
            if (type != null && type.botMode()) {
                kit = this.practice.botKitFor(type);
                if (kit == null || kit.isBlank()) {
                    return;
                }
            }
        }
        if (kit == null || kit.isBlank()) {
            return;
        }
        org.bukkit.entity.Player who = bot.getBukkitEntity();
        if (this.practice.applyServerKitToBot(who, kit)) {
            this.plugin.getLogger().info("[Quantum] bot " + bot.profileName()
                    + " wears the server kit '" + kit + "'"
                    + (mode == null || mode.isBlank() ? "" : " (mode " + mode + ", /botadmin)"));
        } else {
            this.plugin.getLogger().warning("[Quantum] bot kit '" + kit
                    + "' was configured but could not be applied (kit missing?)");
        }
    }

    private Location botSpawn(Location fallback) {
        String worldName = this.config.getString("world.name", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (fallback != null && fallback.getWorld() != null) {
            world = fallback.getWorld();
        }
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            throw new IllegalStateException("no world loaded to spawn the bot in");
        }
        List<Double> spawn = this.config.getDoubleList("bot.spawn");
        double x = spawn.size() > 0 ? spawn.get(0) : 0.5;
        double y = spawn.size() > 1 ? spawn.get(1) : 34;
        double z = spawn.size() > 2 ? spawn.get(2) : 0.5;
        // /bot is a per-player command: the invoking player is the instance target, so never
        // force every fight into one configured global coordinate. The configured point remains
        // the console/automation fallback used when no owner location exists.
        if (fallback != null && fallback.getWorld() != null) {
            x = fallback.getX() + 2.5;
            y = fallback.getY();
            z = fallback.getZ();
        }
        // Do not stack multiple QuantumBOT instances in one block. The function runtime uses
        // the private instance tag to drive every instance; this deterministic grid keeps their
        // hitboxes and nearby-entity scope separate at spawn time.
        int slot = this.bots.all().size();
        if (slot > 0) {
            x += (slot % 4) * 3.0;
            z += (slot / 4) * 3.0;
        }
        return new Location(world, x, y, z,
                (float) this.config.getDouble("bot.yaw", 0.0),
                (float) this.config.getDouble("bot.pitch", 0.0));
    }

    /** Generates a unique legal profile name while keeping the visible name fixed to NARENA BOT. */
    private String nextBotName(String configuredName) {
        String displayName = configuredName == null || configuredName.isBlank()
                ? com.rumilance.practice.packetbot.PacketBotFactory.DEFAULT_DISPLAY_NAME : configuredName;
        for (int attempt = 0; attempt < 100_000; attempt++) {
            String candidate = com.rumilance.practice.packetbot.BotNames.uniqueProfileName(displayName);
            if (this.bots.byName(candidate) == null) {
                return candidate;
            }
        }
        throw new IllegalStateException("too many QuantumBOT instances");
    }

    private void ensureInstanceTicker() {
        if (this.instanceTicker != null) {
            return;
        }
        this.instanceTicker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickInstances, 1L, 1L);
    }

    /** Runs the private function for every live instance, sequentially on the server thread. */
    private void tickInstances() {
        if (this.instances.isEmpty()) {
            if (this.instanceTicker != null) {
                this.instanceTicker.cancel();
                this.instanceTicker = null;
            }
            return;
        }
        for (QuantumInstance instance : List.copyOf(this.instances.values())) {
            HeroBotPlayer bot = null;
            for (HeroBotPlayer candidate : this.bots.all()) {
                if (candidate.getUUID().equals(instance.botUuid())) {
                    bot = candidate;
                    break;
                }
            }
            Player target = Bukkit.getPlayer(instance.targetUuid());
            if (bot == null || bot.isRemoved() || target == null || !target.isOnline()) {
                this.despawnInstance(instance.botUuid());
                continue;
            }
            // Newly created vanilla projectiles/items and map markers acquire the instance entity
            // tag before the scoped function sees them. Summons from the compiled function also
            // receive the tag directly in the source transformer.
            this.runQuietly(bot.getBukkitEntity(), "tag @e[distance=..64,tag=!"
                    + instance.entityTag() + "] add " + instance.entityTag());
            this.runQuietly(bot.getBukkitEntity(), "function " + instance.tickFunction());
        }
    }

    private void despawnInstance(UUID botUuid) {
        QuantumInstance instance = this.instances.remove(botUuid);
        if (instance == null) {
            return;
        }
        Player target = Bukkit.getPlayer(instance.targetUuid());
        if (target != null) {
            target.removeScoreboardTag(instance.targetTag());
            target.removeScoreboardTag(instance.participantTag());
        }
        HeroBotPlayer bot = null;
        for (HeroBotPlayer candidate : this.bots.all()) {
            if (candidate.getUUID().equals(botUuid)) {
                bot = candidate;
                break;
            }
        }
        if (bot != null) {
            this.bots.despawn(bot.profileName());
        }
        this.functions.unregisterInstance(botUuid);
    }

    public boolean despawnBot() {
        boolean removed = !this.instances.isEmpty() || !this.bots.all().isEmpty();
        for (UUID botUuid : List.copyOf(this.instances.keySet())) {
            this.despawnInstance(botUuid);
        }
        // /playerspawn-created bots are outside /bot's Quantum instance manager, but the explicit
        // command still promises to remove all fake players.
        this.bots.despawnAll();
        return removed;
    }

    // ------------------------------------------------------------------ worlds

    /**
     * Extracts the world portion of the reference zip (the Quantum map save: {@code level.dat},
     * {@code region/}, {@code datapacks/} …) into the server's world container and loads it.
     * The map's own arena, markers and scoreboards are part of that save, so running it is what
     * makes the functions behave exactly like the reference beyond the bot itself.
     */
    public String importWorld(Path zip, String worldName) {
        File target = new File(System.getProperty("user.dir", "."), worldName);
        if (target.exists()) {
            return "world folder already exists: " + target;
        }
        if (!Files.isRegularFile(zip)) {
            return "zip not found: " + zip;
        }
        int extracted = 0;
        try (ZipFile file = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : file.stream().filter(e -> !e.isDirectory()).toList()) {
                String name = entry.getName();
                if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")
                        || name.endsWith(".DS_Store")) {
                    continue;
                }
                if (!isWorldEntry(name)) {
                    continue;
                }
                Path destination = target.toPath().resolve(name).normalize();
                if (!destination.startsWith(target.toPath())) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (InputStream in = file.getInputStream(entry)) {
                    Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                extracted++;
            }
        } catch (IOException e) {
            return "import failed: " + e.getMessage();
        }
        return "extracted " + extracted + " file(s) into " + target;
    }

    private static boolean isWorldEntry(String name) {
        return name.equals("level.dat") || name.equals("level.dat_old") || name.equals("session.lock")
                || name.startsWith("region/") || name.startsWith("entities/") || name.startsWith("poi/")
                || name.startsWith("data/") || name.startsWith("datapacks/")
                || name.startsWith("playerdata/") || name.startsWith("advancements/")
                || name.startsWith("stats/") || name.startsWith("DIM") || name.startsWith("dimensions/");
    }

    /** Loads a world folder by name (Bukkit world container). */
    public String loadWorld(String worldName) {
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            return "world already loaded: " + worldName;
        }
        org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(worldName);
        creator.keepSpawnLoaded(net.kyori.adventure.util.TriState.TRUE);
        World world = creator.createWorld();
        return world == null ? "could not load world " + worldName
                : "loaded world " + world.getName() + " (spawn " + world.getSpawnLocation() + ")";
    }

    // ------------------------------------------------------------------ status

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("quantum: " + (this.enabled ? "enabled" : "disabled")
                + " | functions=" + this.functions.installedCount()
                + " tags=" + this.functions.tagCount()
                + " failures=" + this.functions.failures().size());
        lines.add("packs: " + this.pack.sources());
        Set<String> namespaces = new LinkedHashSet<>();
        for (String id : this.functions.installedIds()) {
            int colon = id.indexOf(':');
            if (colon > 0) {
                namespaces.add(id.substring(0, colon));
            }
        }
        lines.add("namespaces: " + namespaces);
        lines.add("mode=" + this.config.getString("mode", "?")
                + " difficulty=" + this.config.getInt("difficulty", -1)
                + " botDisplay=NARENA BOT"
                + " instances=" + this.instances.size()
                + " profiles=" + this.bots.names());
        return lines;
    }
}
