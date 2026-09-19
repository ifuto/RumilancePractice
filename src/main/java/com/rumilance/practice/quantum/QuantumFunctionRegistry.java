package com.rumilance.practice.quantum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import com.rumilance.practice.herobot.HeroBotDistanceCommand;
import com.rumilance.practice.herobot.HeroBotLineRewriter;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the pack's {@code .mcfunction} files against the <b>live</b> command dispatcher and
 * installs them into the server's function library, so that {@code function}, {@code execute if
 * function}, {@code schedule function} and the {@code #minecraft:tick}/{@code #minecraft:load}
 * tags all run through vanilla's own machinery.
 *
 * <p>This is the piece the feasibility spike identified as mandatory: the vanilla pack loader
 * compiles functions before plugins exist, so every {@code player …} line dies there. Compiling
 * here — after {@link com.rumilance.practice.herobot.HeroBotCommands} registered {@code player},
 * {@code playerspawn} and {@code herobot} — produces exactly the same function objects the
 * reference server ends up with, using the same vanilla entry point
 * ({@code CommandFunction.fromLines}) and the same compilation context
 * ({@code Commands.createCompilationContext}).</p>
 *
 * <p>Functions the vanilla loader already registered are overwritten by the complete ones; that
 * is intentional — the vanilla half-registered copy is exactly the one that loses every
 * {@code player} line.</p>
 */
public final class QuantumFunctionRegistry {

    private final org.bukkit.plugin.Plugin plugin;

    /** Active fights whose functions are compiled under a private namespace. */
    private final Map<java.util.UUID, QuantumInstance> instances = new LinkedHashMap<>();
    private final Set<String> ensuredObjectiveCommands = new HashSet<>();
    private QuantumPack sourcePack = new QuantumPack();

    private static final Pattern SELECTOR = Pattern.compile("@([aensp])(?:\\[([^]]*)])?");
    private static final Pattern DOT_HOLDER =
            Pattern.compile("(?<![A-Za-z0-9_])\\.([A-Za-z][A-Za-z0-9_.]*)");
    private static final Pattern FUNCTION_REFERENCE = Pattern.compile(
            "(?<![A-Za-z0-9_:])((?:function|schedule\\s+(?:function|clear)|(?:if|unless)\\s+function)\\s+)"
                    + "([a-z0-9_.-]+):");

    private Map<Identifier, CommandFunction<CommandSourceStack>> installed = Map.of();
    private Map<Identifier, List<CommandFunction<CommandSourceStack>>> installedTags = Map.of();
    private List<String> failures = List.of();
    private int installedCount;
    private int tagCount;
    private int rewrittenLines;
    /**
     * True once a real install pass has run against the live server. An EMPTY pack is then a
     * fixed point — the server cannot "uninstall" functions that were never installed — so the
     * watchdog must treat it as stable instead of reinstalling (and logging) forever.
     */
    private volatile boolean installAttempted;

    public QuantumFunctionRegistry(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    /** Active instance identities, used by the runtime tick driver and cleanup path. */
    public List<QuantumInstance> instances() {
        return List.copyOf(this.instances.values());
    }

    /**
     * Adds one fight and rebuilds the live function library with its private function namespace.
     * The rebuild is deliberate: Paper's function library is immutable from the command path, so
     * replacing it is the only safe way to make a newly spawned instance callable by
     * {@code schedule} and {@code function}.
     */
    public synchronized Result registerInstance(QuantumInstance instance) {
        this.instances.put(instance.botUuid(), instance);
        return install(this.sourcePack);
    }

    /** Removes an instance and drops all of its compiled functions on the next rebuild. */
    public synchronized Result unregisterInstance(java.util.UUID botUuid) {
        this.instances.remove(botUuid);
        return install(this.sourcePack);
    }

    /** Ids of the functions we installed ({@code namespace:path}). */
    public Set<String> installedIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Identifier id : this.installed.keySet()) {
            ids.add(id.toString());
        }
        return ids;
    }

    public int installedCount() {
        return this.installedCount;
    }

    public int tagCount() {
        return this.tagCount;
    }

    /**
     * Lines that needed translating from the reference mod's selector extensions
     * ({@code distanceH=}/{@code distanceV=}) into the {@code hfilter} pair — see
     * {@link com.rumilance.practice.herobot.HeroBotLineRewriter}.
     */
    public int rewrittenLines() {
        return this.rewrittenLines;
    }

    /** Lines that failed to compile, with the vanilla error text (e.g. an unknown verb). */
    public List<String> failures() {
        return this.failures;
    }

    public boolean isInstalled() {
        if (this.installed.isEmpty()) {
            // See installAttempted: an empty pack that has been installed is stable, not broken.
            return this.installAttempted;
        }
        MinecraftServer server = server();
        if (server == null) {
            return false;
        }
        ServerFunctionManager manager = server.getFunctions();
        for (Map.Entry<Identifier, CommandFunction<CommandSourceStack>> entry : this.installed.entrySet()) {
            Optional<CommandFunction<CommandSourceStack>> current = manager.get(entry.getKey());
            if (current.isEmpty() || current.get() != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compiles and installs the pack. Safe to call repeatedly (it rebuilds the library and
     * asks the function manager to re-read the {@code tick}/{@code load} tags).
     */
    public synchronized Result install(QuantumPack pack) {
        if (pack != this.sourcePack) {
            this.ensuredObjectiveCommands.clear();
        }
        this.sourcePack = pack == null ? new QuantumPack() : pack;
        MinecraftServer server = server();
        if (server == null) {
            return new Result(0, 0, List.of("no server"));
        }
        ServerFunctionManager manager = server.getFunctions();
        CommandDispatcher<CommandSourceStack> dispatcher = manager.getDispatcher();
        // The exact source vanilla uses when it compiles a datapack function.
        CommandSourceStack compileSource = net.minecraft.commands.Commands
                .createCompilationContext(server.getFunctionCompilationPermissions());

        ensureTempObjective();
        ensurePackObjectives(this.sourcePack);

        Map<Identifier, CommandFunction<CommandSourceStack>> functions = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        int rewritten = 0;

        // Keep the original namespace available for compatibility with operator commands and
        // world datapacks. The vanilla tick/load tags are filtered below: the runtime drives the
        // private copies instead of allowing the old one-global-bot entry points to run.
        for (Map.Entry<Identifier, List<String>> entry : this.sourcePack.functions().entrySet()) {
            CompileResult result = compile(dispatcher, compileSource, entry.getKey(), entry.getValue(), null,
                    failures);
            if (result.function() != null) {
                functions.put(entry.getKey(), result.function());
            }
            rewritten += result.rewritten();
        }

        // Compile one complete, privately scoped copy per live fight. All custom function
        // references are rewritten to the same namespace, so delayed schedule/function calls stay
        // inside the originating fight instead of resetting another player's map.
        Set<String> sourceNamespaces = new HashSet<>();
        for (Identifier id : this.sourcePack.functions().keySet()) {
            sourceNamespaces.add(id.getNamespace());
        }
        for (QuantumInstance instance : this.instances.values()) {
            for (Map.Entry<Identifier, List<String>> entry : this.sourcePack.functions().entrySet()) {
                Identifier id = Identifier.fromNamespaceAndPath(instance.namespace(), entry.getKey().getPath());
                CompileResult result = compile(dispatcher, compileSource, id, entry.getValue(),
                        new InstanceTransform(instance, sourceNamespaces), failures);
                if (result.function() != null) {
                    functions.put(id, result.function());
                }
                rewritten += result.rewritten();
            }
            List<String> init = instanceInit(this.sourcePack, instance);
            Identifier initId = Identifier.fromNamespaceAndPath(instance.namespace(), "instance_init");
            CompileResult result = compile(dispatcher, compileSource, initId, init,
                    new InstanceTransform(instance, sourceNamespaces), failures);
            if (result.function() != null) {
                functions.put(initId, result.function());
            }
            rewritten += result.rewritten();
        }
        this.rewrittenLines = rewritten;

        Map<Identifier, List<CommandFunction<CommandSourceStack>>> tags = new LinkedHashMap<>();
        Map<Identifier, List<String>> rawTags = filteredTags(this.sourcePack.tagMembers());
        for (Identifier tag : rawTags.keySet()) {
            resolveTag(tag, rawTags, functions, tags, new ArrayDeque<>(), failures);
        }

        Map<Identifier, CommandFunction<CommandSourceStack>> merged =
                new HashMap<>(baseFunctions(manager));
        merged.putAll(functions);
        Map<Identifier, List<CommandFunction<CommandSourceStack>>> mergedTags =
                unionTags(baseTags(manager), tags);

        ServerFunctionLibrary library =
                new ServerFunctionLibrary(server.getFunctionCompilationPermissions(), dispatcher);
        if (!setLibraryField(library, "functions", merged) || !setLibraryField(library, "tags", mergedTags)) {
            return new Result(0, 0, List.of("could not install into ServerFunctionLibrary "
                    + "(field layout changed in this Paper build)"));
        }
        manager.replaceLibrary(library);

        this.installed = functions;
        this.installedTags = tags;
        this.failures = List.copyOf(failures);
        this.installedCount = functions.size();
        this.tagCount = tags.size();
        this.installAttempted = true;
        return new Result(this.installedCount, this.tagCount, this.failures);
    }

    private record CompileResult(CommandFunction<CommandSourceStack> function, int rewritten) {
    }

    private static CompileResult compile(CommandDispatcher<CommandSourceStack> dispatcher,
                                         CommandSourceStack source, Identifier id, List<String> raw,
                                         InstanceTransform transform, List<String> failures) {
        List<String> lines = new ArrayList<>();
        int rewritten = 0;
        for (String original : raw) {
            if (transform != null && original.contains("playerspawn")) {
                // Bot lifecycle belongs to QuantumRuntime. Keeping the old datapack command would
                // recreate a global bot and undo instance ownership on every reload.
                lines.add("# QuantumRuntime owns QuantumBOT spawning for this instance");
                continue;
            }
            if (transform != null && id.getPath().equals("miscellaneous/tags")) {
                // This legacy helper clears and reassigns the global xlib_* tags. Java already
                // assigned qtarget_N/qpart_N, so running it would reintroduce cross-instance tags.
                lines.add("# QuantumRuntime owns per-instance bot and target tags");
                continue;
            }
            if (transform != null && (original.trim().startsWith("tellraw ")
                    || original.trim().startsWith("say ")
                    || original.trim().contains(" run tellraw ")
                    || original.trim().contains(" run say "))) {
                // QuantumBOT never speaks. The reference pack has tutorial/debug broadcasts and
                // one legacy `say yo`; those are not part of combat and must not reach chat.
                lines.add("# QuantumBOT chat/tellraw output intentionally disabled");
                continue;
            }
            if (transform != null && (original.trim().startsWith("forceload ")
                    || original.trim().startsWith("team modify ")
                    || original.contains(" setblock -646 ")
                    || original.contains(" -657 55 76"))) {
                // These are single-map global operations in the reference save. An instance must
                // not reset another player's arena or shared forceload/team state.
                lines.add("# QuantumRuntime keeps world/map state instance-local");
                continue;
            }
            String line = transform == null ? original : transform.line(original);
            HeroBotLineRewriter.Result rewrite = HeroBotLineRewriter.rewrite(line);
            if (rewrite == null) {
                lines.add(line);
                continue;
            }
            if (rewrite.note() != null) {
                failures.add(id + " -> selector option not translated (" + rewrite.note() + ")");
                lines.add(line);
                continue;
            }
            for (String rewrittenLine : rewrite.lines()) {
                // HeroBotLineRewriter creates its temporary `.qd` holder after the instance
                // transform. Run the transform once more so distance filters are isolated too.
                lines.add(transform == null ? rewrittenLine : transform.line(rewrittenLine));
            }
            rewritten++;
        }
        try {
            return new CompileResult(CommandFunction.fromLines(id, dispatcher, source, lines), rewritten);
        } catch (RuntimeException e) {
            failures.add(id + " -> " + shorten(e.getMessage()));
            return new CompileResult(null, rewritten);
        }
    }

    private static Map<Identifier, List<String>> filteredTags(
            Map<Identifier, List<String>> source) {
        Map<Identifier, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, List<String>> entry : source.entrySet()) {
            if (entry.getKey().toString().equals("minecraft:tick")
                    || entry.getKey().toString().equals("minecraft:load")) {
                // The old global entry points are intentionally not put back into the vanilla
                // tags. QuantumRuntime invokes qbot_N:tick and initializes objectives itself.
                continue;
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Objective definitions are global server resources, so install them once without running
     * the map's old global load function (which also teleported every player and spawned one bot). */
    private void ensurePackObjectives(QuantumPack pack) {
        Set<String> commands = new LinkedHashSet<>();
        for (List<String> lines : pack.functions().values()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("scoreboard objectives add ")) {
                    commands.add(trimmed);
                }
            }
        }
        for (String command : commands) {
            if (!this.ensuredObjectiveCommands.add(command)) {
                continue;
            }
            try {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), command);
            } catch (RuntimeException ignored) {
                // Existing objectives and criteria unavailable on a particular Paper build are
                // reported by the function compiler/command log; do not abort other instances.
            }
        }
    }

    private static List<String> instanceInit(QuantumPack pack, QuantumInstance instance) {
        List<String> lines = new ArrayList<>();
        // The load function is the authoritative objective list. Only copy per-entity defaults
        // and constants; its old world teleport/reset/spawn tail is intentionally excluded.
        List<String> load = pack.functions().get(
                Identifier.fromNamespaceAndPath("quantum", "load"));
        if (load != null) {
            for (String raw : load) {
                String line = raw.trim();
                if (line.startsWith("scoreboard players set @a ")
                        || line.startsWith("scoreboard players reset * ")
                        || line.startsWith("scoreboard players set .")) {
                    lines.add(line);
                }
            }
        }
        lines.add("scoreboard players set @s mode 0");
        lines.add("scoreboard players set @s start 0");
        lines.add("scoreboard players set @s difficulty 0");
        lines.add("scoreboard players set @s bots 1");
        lines.add("scoreboard players set @s toggles 0");
        lines.add("scoreboard players set @s death 0");
        lines.add("scoreboard players set @s resetcd 0");
        lines.add("scoreboard players set @s Health 20");
        return lines;
    }

    /**
     * Transforms one reference function into an instance-local function. The transformation is
     * deliberately text based, before vanilla compilation: selectors and scoreboard holders are
     * then parsed by the same live dispatcher as the original pack.
     */
    private static final class InstanceTransform {
        private final QuantumInstance instance;
        private final Set<String> namespaces;

        private InstanceTransform(QuantumInstance instance, Set<String> namespaces) {
            this.instance = instance;
            this.namespaces = namespaces;
        }

        private String line(String original) {
            String line = original;
            line = rewriteFunctionReferences(line);
            line = rewriteHolders(line);
            line = line.replace("$seconds", this.instance.holderPrefix() + "seconds");
            line = rewriteSelectors(line);
            line = rewriteScoreHolders(line);
            line = line.replace("scoreboard players reset * ",
                    "scoreboard players reset @a[tag=" + this.instance.participantTag() + "] ");
            line = rewriteSummonTags(line);
            // A scheduled function keeps the command source (and therefore the bot executor), but
            // a raw `/playerspawn` line was removed by compile() before this point.
            return line;
        }

        private String rewriteFunctionReferences(String line) {
            Matcher matcher = FUNCTION_REFERENCE.matcher(line);
            StringBuffer out = new StringBuffer();
            while (matcher.find()) {
                String namespace = matcher.group(2);
                if (this.namespaces.contains(namespace)) {
                    matcher.appendReplacement(out,
                            Matcher.quoteReplacement(matcher.group(1) + this.instance.namespace() + ":"));
                }
            }
            matcher.appendTail(out);
            return out.toString();
        }

        private String rewriteHolders(String line) {
            Matcher matcher = DOT_HOLDER.matcher(line);
            StringBuffer out = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(out,
                        Matcher.quoteReplacement(this.instance.holderPrefix() + matcher.group(1)));
            }
            matcher.appendTail(out);
            return out.toString();
        }

        private String rewriteScoreHolders(String line) {
            // These two readable holders are used for the winner score in map/reset. Make them
            // per-instance just like the dotted pseudo players. Use capturing groups rather than
            // a variable-length look-behind (which differs between Java runtimes).
            String prefix = Matcher.quoteReplacement(this.instance.holderPrefix());
            return line.replaceAll("(\\bscoreboard players (?:set|add|remove|reset|operation|enable) )(bot|player)(?=\\s)",
                    "$1" + prefix + "$2")
                    .replaceAll("(\\b(?:if|unless) score )(bot|player)(?=\\s)",
                            "$1" + prefix + "$2");
        }

        private String rewriteSelectors(String line) {
            Matcher matcher = SELECTOR.matcher(line);
            StringBuffer out = new StringBuffer();
            while (matcher.find()) {
                String kind = matcher.group(1);
                String options = matcher.group(2);
                String replacement = selector(kind, options);
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(out);
            return out.toString();
        }

        private String selector(String kind, String options) {
            if (options == null) {
                if (kind.equals("a") || kind.equals("p")) {
                    return "@" + kind + "[tag=" + this.instance.participantTag() + "]";
                }
                if (kind.equals("e") || kind.equals("n")) {
                    return "@" + kind + "[tag=" + this.instance.entityTag() + "]";
                }
                return "@" + kind;
            }
            String value = options
                    .replace("tag=xlib_bot", "tag=" + this.instance.botTag())
                    .replace("tag=!xlib_bot", "tag=!" + this.instance.botTag())
                    .replace("tag=xlib_target", "tag=" + this.instance.targetTag())
                    .replace("tag=!xlib_target", "tag=!" + this.instance.targetTag())
                    .replace("tag=quantum_bot", "tag=" + this.instance.botTag());
            if (kind.equals("a") || kind.equals("p")) {
                if (!value.contains("tag=" + this.instance.botTag())
                        && !value.contains("tag=" + this.instance.targetTag())
                        && !value.contains("tag=" + this.instance.participantTag())) {
                    value = "tag=" + this.instance.participantTag() + "," + value;
                }
            } else if (kind.equals("e") || kind.equals("n")) {
                if (!value.contains("tag=" + this.instance.entityTag())) {
                    value = "tag=" + this.instance.entityTag() + "," + value;
                }
            }
            return "@" + kind + "[" + value + "]";
        }

        private String rewriteSummonTags(String line) {
            int summon = line.indexOf("summon ");
            if (summon < 0) {
                return line;
            }
            int nbt = line.indexOf('{', summon);
            if (nbt < 0) {
                return line + " {Tags:[\\\"" + this.instance.entityTag() + "\\\"]}";
            }
            int tags = line.indexOf("Tags:[", nbt);
            if (tags >= 0) {
                int end = line.indexOf(']', tags);
                if (end >= 0 && !line.substring(tags, end).contains(this.instance.entityTag())) {
                    return line.substring(0, end) + ",\\\"" + this.instance.entityTag()
                            + "\\\"" + line.substring(end);
                }
                return line;
            }
            return line.substring(0, nbt + 1) + "Tags:[\\\"" + this.instance.entityTag()
                    + "\\\"]," + line.substring(nbt + 1);
        }
    }

    /** Nested tag references ({@code #ns:other}) are expanded once, like the vanilla tag loader. */
    private static void resolveTag(Identifier tag, Map<Identifier, List<String>> rawTags,
                                   Map<Identifier, CommandFunction<CommandSourceStack>> functions,
                                   Map<Identifier, List<CommandFunction<CommandSourceStack>>> out,
                                   Deque<Identifier> stack, List<String> failures) {
        if (out.containsKey(tag) || stack.contains(tag)) {
            return;
        }
        List<String> members = rawTags.get(tag);
        if (members == null) {
            return;
        }
        stack.push(tag);
        List<CommandFunction<CommandSourceStack>> resolved = new ArrayList<>();
        for (String member : members) {
            boolean nested = member.startsWith("#");
            Identifier id = Identifier.parse(nested ? member.substring(1) : member);
            if (nested) {
                resolveTag(id, rawTags, functions, out, stack, failures);
                resolved.addAll(out.getOrDefault(id, List.of()));
                continue;
            }
            CommandFunction<CommandSourceStack> function = functions.get(id);
            if (function == null) {
                failures.add(tag + " -> unknown function in tag: " + id);
                continue;
            }
            resolved.add(function);
        }
        stack.pop();
        out.put(tag, List.copyOf(resolved));
    }

    private static Map<Identifier, CommandFunction<CommandSourceStack>> baseFunctions(
            ServerFunctionManager manager) {
        Map<Identifier, CommandFunction<CommandSourceStack>> base = new LinkedHashMap<>();
        for (Identifier id : manager.getFunctionNames()) {
            // A previous rebuild may have left qbot_N functions in the live manager. They are
            // replaced from the current instance set below, never treated as an external base.
            if (id.getNamespace().startsWith("qbot_")) {
                continue;
            }
            manager.get(id).ifPresent(function -> base.put(id, function));
        }
        return base;
    }

    /**
     * Tags we install keep the members other datapacks contributed (their functions still exist in
     * the library we merge over) and take ours for the same ids — otherwise installing
     * {@code #minecraft:tick} would silently stop every other datapack's tick function.
     */
    private static Map<Identifier, List<CommandFunction<CommandSourceStack>>> unionTags(
            Map<Identifier, List<CommandFunction<CommandSourceStack>>> base,
            Map<Identifier, List<CommandFunction<CommandSourceStack>>> ours) {
        Map<Identifier, List<CommandFunction<CommandSourceStack>>> merged = new HashMap<>(base);
        for (Map.Entry<Identifier, List<CommandFunction<CommandSourceStack>>> entry : ours.entrySet()) {
            List<CommandFunction<CommandSourceStack>> combined = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (CommandFunction<CommandSourceStack> function : entry.getValue()) {
                if (seen.add(function.id().toString())) {
                    combined.add(function);
                }
            }
            for (CommandFunction<CommandSourceStack> function
                    : merged.getOrDefault(entry.getKey(), List.of())) {
                if (seen.add(function.id().toString())) {
                    combined.add(function);
                }
            }
            merged.put(entry.getKey(), List.copyOf(combined));
        }
        return merged;
    }

    private static Map<Identifier, List<CommandFunction<CommandSourceStack>>> baseTags(
            ServerFunctionManager manager) {
        Map<Identifier, List<CommandFunction<CommandSourceStack>>> base = new LinkedHashMap<>();
        for (Identifier id : manager.getTagNames()) {
            if (!id.toString().equals("minecraft:tick") && !id.toString().equals("minecraft:load")) {
                base.put(id, List.copyOf(manager.getTag(id)));
                continue;
            }
            List<CommandFunction<CommandSourceStack>> kept = new ArrayList<>();
            for (CommandFunction<CommandSourceStack> function : manager.getTag(id)) {
                String functionId = function.id().toString();
                if (functionId.equals("quantum:tick") || functionId.equals("quantum:load")
                        || functionId.equals("stats:tick")) {
                    continue;
                }
                kept.add(function);
            }
            if (!kept.isEmpty()) {
                base.put(id, List.copyOf(kept));
            }
        }
        return base;
    }

    /**
     * The rewritten lines park their match count in {@code quantum_tmp}:.qd — one objective for
     * the whole server, created before anything is compiled.
     */
    private static void ensureTempObjective() {
        try {
            org.bukkit.scoreboard.Scoreboard board =
                    org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
            if (board.getObjective(HeroBotDistanceCommand.TEMP_OBJECTIVE) == null) {
                board.registerNewObjective(HeroBotDistanceCommand.TEMP_OBJECTIVE,
                        org.bukkit.scoreboard.Criteria.DUMMY,
                        net.kyori.adventure.text.Component.text("quantum temp"));
            }
        } catch (RuntimeException e) {
            // an existing objective with the same name is fine; anything else surfaces on compile
        }
    }

    private static boolean setLibraryField(ServerFunctionLibrary library, String name, Object value) {
        try {
            Field field = ServerFunctionLibrary.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(library, value);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static MinecraftServer server() {
        org.bukkit.Server bukkit = org.bukkit.Bukkit.getServer();
        if (bukkit instanceof org.bukkit.craftbukkit.CraftServer craft) {
            return craft.getServer();
        }
        return null;
    }

    private static String shorten(String message) {
        if (message == null) {
            return "(no message)";
        }
        String single = message.replace('\n', ' ').trim();
        return single.length() > 220 ? single.substring(0, 220) + "…" : single;
    }

    public org.bukkit.plugin.Plugin plugin() {
        return this.plugin;
    }

    /** Install summary for logs and {@code /quantum status}. */
    public record Result(int functions, int tags, List<String> failures) {
    }
}
