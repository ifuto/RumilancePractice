package com.rumilance.practice.quantum;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;

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

    private Map<Identifier, CommandFunction<CommandSourceStack>> installed = Map.of();
    private Map<Identifier, List<CommandFunction<CommandSourceStack>>> installedTags = Map.of();
    private List<String> failures = List.of();
    private int installedCount;
    private int tagCount;

    public QuantumFunctionRegistry(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
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

    /** Lines that failed to compile, with the vanilla error text (e.g. an unknown verb). */
    public List<String> failures() {
        return this.failures;
    }

    public boolean isInstalled() {
        if (this.installed.isEmpty()) {
            return false;
        }
        MinecraftServer server = server();
        if (server == null) {
            return false;
        }
        ServerFunctionManager manager = server.getFunctions();
        for (Map.Entry<Identifier, CommandFunction<CommandSourceStack>> entry : this.installed.entrySet()) {
            Optional<CommandFunction<CommandSourceStack>> current = manager.get(entry.getKey());
            return current.isPresent() && current.get() == entry.getValue();
        }
        return false;
    }

    /**
     * Compiles and installs the pack. Safe to call repeatedly (it rebuilds the library and
     * asks the function manager to re-read the {@code tick}/{@code load} tags).
     */
    public Result install(QuantumPack pack) {
        MinecraftServer server = server();
        if (server == null) {
            return new Result(0, 0, List.of("no server"));
        }
        ServerFunctionManager manager = server.getFunctions();
        CommandDispatcher<CommandSourceStack> dispatcher = manager.getDispatcher();
        // The exact source vanilla uses when it compiles a datapack function.
        CommandSourceStack compileSource = net.minecraft.commands.Commands
                .createCompilationContext(server.getFunctionCompilationPermissions());

        Map<Identifier, CommandFunction<CommandSourceStack>> functions = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<Identifier, List<String>> entry : pack.functions().entrySet()) {
            Identifier id = entry.getKey();
            try {
                functions.put(id, CommandFunction.fromLines(id, dispatcher, compileSource,
                        entry.getValue()));
            } catch (RuntimeException e) {
                failures.add(id + " -> " + shorten(e.getMessage()));
            }
        }

        Map<Identifier, List<CommandFunction<CommandSourceStack>>> tags = new LinkedHashMap<>();
        Map<Identifier, List<String>> rawTags = pack.tagMembers();
        for (Identifier tag : rawTags.keySet()) {
            resolveTag(tag, rawTags, functions, tags, new ArrayDeque<>(), failures);
        }

        Map<Identifier, CommandFunction<CommandSourceStack>> merged =
                new HashMap<>(baseFunctions(manager));
        merged.putAll(functions);
        Map<Identifier, List<CommandFunction<CommandSourceStack>>> mergedTags =
                new HashMap<>(baseTags(manager));
        mergedTags.putAll(tags);

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
        return new Result(this.installedCount, this.tagCount, this.failures);
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
            manager.get(id).ifPresent(function -> base.put(id, function));
        }
        return base;
    }

    private static Map<Identifier, List<CommandFunction<CommandSourceStack>>> baseTags(
            ServerFunctionManager manager) {
        Map<Identifier, List<CommandFunction<CommandSourceStack>>> base = new LinkedHashMap<>();
        for (Identifier id : manager.getTagNames()) {
            base.put(id, List.copyOf(manager.getTag(id)));
        }
        return base;
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
