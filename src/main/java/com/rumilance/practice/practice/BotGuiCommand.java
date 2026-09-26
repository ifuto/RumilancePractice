package com.rumilance.practice.practice;

import com.rumilance.practice.ffa.FfaMannequinService;
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
 * {@code /bot} — the practice dummy in FFA, the real Quantum map bot everywhere else.
 *
 * <p>In FFA the command belongs to {@link FfaMannequinService}: it toggles a mannequin dummy
 * (unbreakable netherite, Protection 4 / Blast Protection 4 leggings, a totem in each hand, bold
 * aqua {@code NARENA BOT} nametag) that takes every hit exactly as vanilla deals it and disappears
 * again on the second {@code /bot}, on leaving FFA or on disconnect. The dummy is an opt-in arena
 * feature that is OFF by default — {@code /practiceadmin} → FFA Config → the arena's {@code FFA Bot}
 * toggle — and while it is OFF {@code /bot} in that arena spawns nothing at all and says why.
 * Spawning the Quantum combat bot into a shared FFA arena instead was the reported
 * "FFAで/botすると別のやつになる" — a full fighting bot is not what someone mid-FFA wants, and it
 * fought the arena's own combat rules.</p>
 *
 * <p>Outside FFA this command intentionally does not open the old Java-side practice-bot selector.
 * The Quantum runtime owns one tagged instance per invocation, using a unique profile name for
 * every invocation; its combat behavior is driven by the bundled Quantum functions and HeroBot
 * command implementation. The player's location is used only as a fallback when the Quantum config
 * has no explicit spawn location, and the player's profile is used as the bot skin template.</p>
 */
public final class BotGuiCommand implements CommandExecutor, TabCompleter {

    private final QuantumRuntime quantum;
    /** FFA dummy; null until the bootstrap wires it (then /bot in FFA never reaches Quantum). */
    private FfaMannequinService ffaMannequins;

    public BotGuiCommand(QuantumRuntime quantum) {
        this.quantum = quantum;
    }

    /** Routes {@code /bot} to the FFA mannequin while the player is inside an FFA arena. */
    public void setFfaMannequins(FfaMannequinService ffaMannequins) {
        this.ffaMannequins = ffaMannequins;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can spawn the QuantumBOT.", NamedTextColor.RED));
            return true;
        }
        if (ffaMannequins != null && ffaMannequins.handleBotCommand(player)) {
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
