package com.rumilance.practice.quantum;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.rumilance.practice.herobot.HeroBotCommands;
import com.rumilance.practice.herobot.HeroBotRegistry;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts {@code /player}, {@code /playerspawn} and {@code /herobot} where both the map and a human
 * can reach them.
 *
 * <p>Where that is took the feasibility spike to work out
 * ({@code tools/paper-bridge-spike/README.md}): the commands the map's functions call must live in
 * the <i>server's own</i> {@code CommandDispatcher}, because that is the tree the vanilla function
 * loader compiles {@code .mcfunction} lines against — and the supported way to put them there is
 * {@code LifecycleEvents.COMMANDS}: Paper's registrar converts the registered tree into vanilla
 * nodes and adds them to that same dispatcher (see Paper's {@code ApiMirrorRootNode}). Registering
 * on the dispatcher by hand also works, but only until Paper swaps the dispatcher on a reload, so
 * that path exists as {@link #ensureRoots()} and is re-asserted by the runtime's watchdog.</p>
 *
 * <p>The nodes are built with vanilla argument types on purpose: a {@code .mcfunction} line is
 * parsed by the vanilla dispatcher, and the vanilla source object a function executes with is the
 * same object Paper's {@code CommandSourceStack} interface is implemented by — so one node serves
 * {@code player @s move forward} in a function, in chat, and on the console. That is also why the
 * generic type of the node is cast when handing it to Paper's registrar: the types differ on
 * paper, the runtime objects do not.</p>
 */
public final class QuantumCommands {

    private final Plugin plugin;
    private final HeroBotRegistry bots;
    private final List<LiteralCommandNode<CommandSourceStack>> roots = new ArrayList<>();
    private boolean registered;

    public QuantumCommands(Plugin plugin, HeroBotRegistry bots) {
        this.plugin = plugin;
        this.bots = bots;
    }

    /**
     * Registers the tree for the server's lifecycle command event. The handler is what registers
     * (and, on {@code /reload}, re-registers) the roots; {@code afterRegister} runs once they are
     * in the dispatcher — the map's functions must be compiled after that, which is why the pack
     * install is driven from here and not only from {@code onEnable}.
     */
    public void listen(Runnable afterRegister) {
        this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            this.buildRoots();
            if (!this.registered) {
                // Paper's registrar wraps a node in CustomCommandExecutor, and vanilla refuses to
                // run those *inside functions* ("This function should not run") — which is exactly
                // where the map calls /player, /playerspawn and /herobot from. The nodes therefore
                // go into the server's own CommandDispatcher, the tree the function loader compiles
                // against; a /reload rebuilds that tree, so the watchdog re-adds them (and the pack
                // is only recompiled once they are back, see Runtime#installWhenReady).
                if (this.areRootsRegistered() || this.ensureRoots()) {
                    this.registered = true;
                    this.plugin.getLogger().info("[Quantum] registered "
                            + this.roots.stream()
                            .map(node -> "/" + node.getName())
                            .collect(java.util.stream.Collectors.joining(", "))
                            + " on the server command dispatcher (function-callable)");
                } else {
                    // Last resort: let Paper own the nodes — visible to humans, but functions will
                    // not be able to call them, so say so loudly.
                    try {
                        for (LiteralCommandNode<CommandSourceStack> node : this.roots) {
                            event.registrar().register(paper(node),
                                    "HeroBot verb (Quantum): /" + node.getName());
                        }
                        this.registered = true;
                        this.plugin.getLogger().warning("[Quantum] herobot verbs could only be "
                                + "registered as Paper commands; the map's functions will not be "
                                + "able to call them");
                    } catch (RuntimeException e) {
                        this.plugin.getLogger().warning(
                                "[Quantum] command registration failed: " + e.getMessage());
                    }
                }
            }
            afterRegister.run();
        });
    }

    /**
     * Fallback for the case where the lifecycle event has already fired without us (a plugin
     * loaded late, or the watchdog noticing that a reload took the roots away): add the nodes to
     * the live dispatcher directly. Idempotent, and it never fights the registrar — a name that is
     * already there is left alone.
     */
    public boolean ensureRoots() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        if (dispatcher == null) {
            return false;
        }
        this.buildRoots();
        boolean added = false;
        for (LiteralCommandNode<CommandSourceStack> node : this.roots) {
            if (dispatcher.getRoot().getChild(node.getName()) == null) {
                dispatcher.getRoot().addChild(node);
                added = true;
            }
        }
        if (added) {
            this.plugin.getLogger().info("[Quantum] re-added the herobot verbs to the live "
                    + "command dispatcher");
        }
        return added;
    }

    /** True once every root is reachable from the live dispatcher (i.e. also from functions). */
    public boolean areRootsRegistered() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        if (dispatcher == null || this.roots.isEmpty()) {
            return false;
        }
        for (LiteralCommandNode<CommandSourceStack> node : this.roots) {
            if (dispatcher.getRoot().getChild(node.getName()) == null) {
                return false;
            }
        }
        return true;
    }

    private void buildRoots() {
        if (!this.roots.isEmpty()) {
            return;
        }
        for (LiteralArgumentBuilder<CommandSourceStack> builder : HeroBotCommands.roots(this.bots)) {
            this.roots.add(builder.build());
        }
    }

    /** Hands a vanilla node to Paper's registrar; generic types differ, runtime objects do not. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static LiteralCommandNode<io.papermc.paper.command.brigadier.CommandSourceStack> paper(
            LiteralCommandNode<CommandSourceStack> node) {
        return (LiteralCommandNode) node;
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        if (!(Bukkit.getServer() instanceof CraftServer server)) {
            return null;
        }
        return server.getServer().getCommands().getDispatcher();
    }
}
