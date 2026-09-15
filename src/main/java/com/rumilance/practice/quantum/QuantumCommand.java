package com.rumilance.practice.quantum;

import com.rumilance.practice.herobot.HeroBotPlayer;
import com.rumilance.practice.herobot.HeroBotRegistry;
import com.rumilance.practice.herobot.HeroBotSettings;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /quantum} — the operator side of the runtime: status, the map's own switches, and a way
 * to run a raw dispatcher line (which is also how the map's behaviour is driven in tests).
 *
 * <p>Everything the map itself does is reachable from functions; this command exists so an
 * operator can drive the same things without editing a function, and so the parity harness
 * ({@code /narena-harness}) has a single entry point.</p>
 */
public final class QuantumCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final QuantumRuntime quantum;
    private final HeroBotRegistry bots;

    public QuantumCommand(Plugin plugin, QuantumRuntime quantum, HeroBotRegistry bots) {
        this.plugin = plugin;
        this.quantum = quantum;
        this.bots = bots;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.usage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                for (String line : this.quantum.statusLines()) {
                    sender.sendMessage(ChatColor.AQUA + line);
                }
            }
            case "reload" -> {
                QuantumFunctionRegistry.Result result = this.quantum.reload();
                sender.sendMessage(ChatColor.AQUA + "Quantum reloaded: " + result.functions()
                        + " function(s), " + result.tags() + " tag(s), "
                        + result.failures().size() + " failure(s)");
            }
            case "failures" -> {
                List<String> failures = this.quantum.functions().failures();
                if (failures.isEmpty()) {
                    sender.sendMessage(ChatColor.GREEN + "Every function compiled.");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + "" + failures.size() + " function(s) failed:");
                for (String failure : failures) {
                    sender.sendMessage(ChatColor.GRAY + "  " + failure);
                }
            }
            case "seed" -> sender.sendMessage(ChatColor.AQUA + "seeded " + this.quantum.seed(sender)
                    + " score/toggle entr(ies)");
            case "start" -> this.report(sender, "start", this.quantum.start(sender));
            case "stop" -> this.report(sender, "stop", this.quantum.stop(sender));
            case "option" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "usage: /" + label + " option <name>");
                    return true;
                }
                this.report(sender, "option " + args[1], this.quantum.setOption(sender, args[1]));
            }
            case "toggle" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "usage: /" + label + " toggle <name> <on|off>");
                    return true;
                }
                boolean on = args[2].equalsIgnoreCase("on") || args[2].equals("1")
                        || args[2].equalsIgnoreCase("true");
                this.report(sender, "toggle " + args[1] + " " + on,
                        this.quantum.setToggle(sender, args[1], on));
            }
            case "difficulty" -> {
                if (args.length < 2 || !args[1].chars().allMatch(Character::isDigit)) {
                    sender.sendMessage(ChatColor.RED + "usage: /" + label + " difficulty <0-5>");
                    return true;
                }
                int difficulty = Integer.parseInt(args[1]);
                this.report(sender, "difficulty " + difficulty,
                        this.quantum.setDifficulty(sender, difficulty));
            }
            case "run" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "usage: /" + label + " run <command line>");
                    return true;
                }
                String line = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                try {
                    int result = this.quantum.run(sender, line);
                    sender.sendMessage(ChatColor.AQUA + "ran '" + line + "' → " + result);
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "failed: " + e.getMessage());
                }
            }
            case "spawnbot" -> {
                Player player = sender instanceof Player p ? p : null;
                HeroBotPlayer bot = this.quantum.spawnBot(
                        player == null ? null : player.getLocation(), player);
                sender.sendMessage(ChatColor.AQUA + "spawned " + bot.profileName() + " at "
                        + bot.blockPosition().toShortString() + " as "
                        + bot.gameMode.getGameModeForPlayer());
            }
            case "despawnbot" -> this.report(sender, "despawn bot", this.quantum.despawnBot());
            case "bots" -> sender.sendMessage(ChatColor.AQUA + "bots: " + this.bots.names());
            case "rules" -> {
                for (String rule : HeroBotSettings.ruleNames()) {
                    sender.sendMessage(ChatColor.GRAY + "  " + rule);
                }
            }
            case "importworld" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "usage: /" + label
                            + " importworld <zip> [worldName]");
                    return true;
                }
                String worldName = args.length > 2 ? args[2]
                        : this.quantum.config().getString("world.name", "quantum");
                sender.sendMessage(ChatColor.AQUA + this.quantum.importWorld(
                        Path.of(args[1]), worldName));
            }
            case "loadworld" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "usage: /" + label + " loadworld <name>");
                    return true;
                }
                sender.sendMessage(ChatColor.AQUA + this.quantum.loadWorld(args[1]));
            }
            default -> this.usage(sender, label);
        }
        return true;
    }

    private void report(CommandSender sender, String what, boolean ok) {
        sender.sendMessage((ok ? ChatColor.GREEN : ChatColor.RED) + what + (ok ? " ok" : " failed"));
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "Quantum (Practicebot datapack runtime) — /" + label);
        sender.sendMessage(ChatColor.GRAY
                + "  status | reload | failures | seed | start | stop | run <line>");
        sender.sendMessage(ChatColor.GRAY
                + "  option <name> | toggle <name> <on|off> | difficulty <0-5>");
        sender.sendMessage(ChatColor.GRAY
                + "  spawnbot | despawnbot | bots | rules | importworld <zip> [name] | loadworld <name>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                      String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "failures", "seed", "start", "stop", "run",
                    "option", "toggle", "difficulty", "spawnbot", "despawnbot", "bots", "rules",
                    "importworld", "loadworld").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            List<String> names = new ArrayList<>(List.of("stun", "crit", "sprint", "shield"));
            names.removeIf(s -> !s.startsWith(args[1].toLowerCase(Locale.ROOT)));
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            return List.of("on", "off");
        }
        return List.of();
    }

    /** The plugin this command belongs to (used by the harness for scheduling). */
    public Plugin plugin() {
        return this.plugin;
    }
}
