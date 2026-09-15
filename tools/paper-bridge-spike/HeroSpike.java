package herospike;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

/** Spike: register herobot-compatible command roots through Paper's Brigadier API and check whether
 *  datapack functions (`player @s move forward` etc.) can call them. */
public class HeroSpike extends JavaPlugin {

    private static final String[] NAMES = {"player", "playerspawn", "herobot"};

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            for (String name : NAMES) {
                try {
                    registrar.register(Commands.literal(name)
                            .then(Commands.argument("args", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String rest = StringArgumentType.getString(ctx, "args");
                                        getLogger().info("[SPIKE] root=" + name + " args=" + rest
                                                + " src=" + ctx.getSource().getSender().getName());
                                        return 1;
                                    })).build(), "herobot bridge (spike): " + name);
                    getLogger().info("[SPIKE] registered brigadier root: " + name);
                } catch (Throwable t) {
                    getLogger().warning("[SPIKE] register " + name + " failed: " + t);
                }
            }
        });
        getServer().getScheduler().runTask(this, () -> {
            getLogger().info("[SPIKE] triggering reloadData()");
            try {
                getServer().reloadData();
                getLogger().info("[SPIKE] reloadData() returned");
            } catch (Throwable t) {
                getLogger().warning("[SPIKE] reloadData() failed: " + t);
            }
        });
    }
}
