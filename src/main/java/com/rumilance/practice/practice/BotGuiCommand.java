package com.rumilance.practice.practice;

import com.rumilance.practice.herobot.HeroBotPlayer;
import com.rumilance.practice.quantum.QuantumRuntime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /bot} — spawns the real Quantum map bot.
 *
     * <p>This command intentionally does not open the old Java-side practice-bot selector. The
     * Quantum runtime owns one tagged instance per invocation, using a unique profile name for every invocation; its combat behavior is driven by
     * the bundled Quantum functions and HeroBot command implementation. The player's location is
     * used only as a fallback when the Quantum config has no explicit spawn location, and the
     * player's profile is used as the bot skin template.</p>
 */
public final class BotGuiCommand implements CommandExecutor, TabCompleter {

    private final QuantumRuntime quantum;

    public BotGuiCommand(QuantumRuntime quantum) {
        this.quantum = quantum;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can spawn the QuantumBOT.", NamedTextColor.RED));
            return true;
        }
        if (!quantum.enabled()) {
            player.sendMessage(Component.text("QuantumBOT is disabled in quantum.yml.", NamedTextColor.RED));
            return true;
        }
        try {
            HeroBotPlayer bot = quantum.spawnBot(player.getLocation(), player);
            boolean started = quantum.startBot(bot);
            player.sendMessage(Component.text(
                    "QuantumBOT spawned: " + bot.profileName()
                            + (started ? "" : " — could not start the match"),
                    started ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        } catch (RuntimeException error) {
            String detail = error.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = error.getClass().getSimpleName();
            }
            player.sendMessage(Component.text(
                    "QuantumBOT could not be spawned: " + detail, NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
