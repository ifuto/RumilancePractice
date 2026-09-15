package herospike;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Spike v8: the "self-hosted function engine" approach.
 *  Registers the herobot call names through Paper's Brigadier API, then executes Quantum-style
 *  .mcfunction files itself, line by line, through the LIVE dispatcher (Bukkit#dispatchCommand).
 *  `function <ns>:<name>` is resolved by the engine (recursion + context-prefix composition for
 *  `execute ... run function X`), so nothing depends on the datapack function loader. */
public class HeroSpike extends JavaPlugin {

    private static final String[] NAMES = {"player", "playerspawn", "herobot", "spikerun"};
    private final Map<String, List<String>> cache = new HashMap<>();
    private int depth;

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (String name : NAMES) {
                event.registrar().register(Commands.literal(name)
                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String args = StringArgumentType.getString(ctx, "args");
                                    getLogger().info("[BRIDGE] " + name + " " + args
                                            + "   (src=" + ctx.getSource().getSender().getName()
                                            + " entity=" + (ctx.getSource().getExecutor() != null) + ")");
                                    if (name.equals("spikerun")) {
                                        runFunction(args.strip(), "", "");
                                    }
                                    return 1;
                                })).build(), "herobot bridge: " + name);
            }
            getLogger().info("[SPIKE] registered bridge roots: " + String.join(",", NAMES));
        });
        getServer().getScheduler().runTaskLater(this, () -> {
            getLogger().info("[ENGINE] ==== run t1 ====");
            runFunction("t1", "", "");
            getLogger().info("[ENGINE] ==== done ====");
        }, 40L);
    }

    private List<String> lines(String name) {
        String key = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        return cache.computeIfAbsent(key, k -> {
            Path p = getDataFolder().toPath().resolve("functions").resolve(k + ".mcfunction");
            try {
                List<String> out = new ArrayList<>();
                for (String raw : Files.readAllLines(p)) {
                    String l = raw.strip();
                    if (!l.isEmpty() && !l.startsWith("#")) {
                        out.add(l);
                    }
                }
                return out;
            } catch (IOException e) {
                getLogger().warning("[ENGINE] cannot read " + p + ": " + e);
                return List.of();
            }
        });
    }

    /** Runs a function with an accumulated `execute ... run ` prefix (context composition). */
    private void runFunction(String name, String prefix, String macroArgs) {
        if (depth++ > 32) {
            getLogger().warning("[ENGINE] recursion guard hit at " + name);
            depth--;
            return;
        }
        getLogger().info("[ENGINE] " + "  ".repeat(Math.min(depth, 8)) + "-> " + name
                + (prefix.isEmpty() ? "" : "   [ctx: " + prefix.strip() + "]"));
        for (String line : lines(name)) {
            String l = line;
            if (l.startsWith("$")) { // macro line: substitute $(var) from macroArgs (unused in spike)
                l = l.substring(1);
            }
            int at = l.lastIndexOf(" run function ");
            if (at >= 0) {
                String clause = l.substring(0, at + " run ".length());
                String target = l.substring(at + " run function ".length()).strip();
                runFunction(target, prefix + clause, macroArgs);
                continue;
            }
            if (l.startsWith("function ")) {
                runFunction(l.substring("function ".length()).strip(), prefix, macroArgs);
                continue;
            }
            String full = prefix + l;
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), full);
            getLogger().info("[ENGINE] " + "  ".repeat(Math.min(depth, 8))
                    + (ok ? "ok   " : "MISS ") + full);
        }
        depth--;
    }
}
