package com.rumilance.practice.practice;

import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.packetbot.PacketBotBody;
import com.rumilance.practice.packetbot.PacketBotFactory;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless bot-fight harness — <b>development only</b>, gated behind the JVM flag
 * {@code -Drumilance.harness=true} (or {@code RUMILANCE_HARNESS=1}).
 *
 * <p>Why it exists: the practice BOT is the counterpart of the reference QuantumBOT, and
 * proving numeric parity needs the BOT to fight a <em>normal</em> match with nobody at the
 * keyboard. A real player is required by the whole practice flow (state machine, kit clone,
 * HUD), so this command spawns a carpet-style fake player (see
 * {@link PacketBotFactory#spawnDummy}) as the idle opponent and then drives the ordinary
 * join → countdown → match path. No scenario pinning, no frozen targets, no bespoke AI: the
 * BOT fights exactly like it does for a player, and the existing fight trace/samplers
 * ({@code [N Arena][BotMatch]}) land in {@code logs/latest.log} for
 * {@code tools/compare_fights.py}.</p>
 *
 * <p>Usage (console/RCON, all coordinates explicit because console has no position):</p>
 * <pre>
 * narena-harness ground &lt;radius&gt; &lt;depth&gt; [world]
 *     — arena floor: stone from y-depth..y-1, air y..y+24 (default 100 deep, like the
 *       reference map: a thin floor lets explosions dig holes the BOT falls into)
 * narena-harness room &lt;roomId&gt; &lt;TYPE&gt; &lt;radius&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *     — create + enable a practice room for that mode and bind it as the mode's venue
 * narena-harness dummy &lt;name&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;
 *     — spawn the idle fake player (the "human" side of the match)
 * narena-harness fight &lt;TYPE&gt; &lt;seconds&gt; [difficulty] [rounds]
 *     — run N normal bot matches back to back (default difficulty INTERMEDIATE)
 * narena-harness status | stop
 * </pre>
 */
public final class BotFightHarness implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final PracticeService practice;
    private final PlayerStateManager stateManager;
    private final Map<String, PacketBotBody> dummies = new HashMap<>();

    /** Ground/anchor position the earlier commands were run at (console has no location). */
    private Location anchor;
    private BukkitTask fillTask;
    private BukkitTask driveTask;

    public BotFightHarness(Plugin plugin, PracticeService practice, PlayerStateManager stateManager) {
        this.plugin = plugin;
        this.practice = practice;
        this.stateManager = stateManager;
    }

    /** Dev flag: {@code -Drumilance.harness=true} or {@code RUMILANCE_HARNESS=1}. */
    public static boolean enabled() {
        return Boolean.getBoolean("rumilance.harness")
                || "1".equals(System.getenv("RUMILANCE_HARNESS"))
                || "true".equalsIgnoreCase(System.getenv("RUMILANCE_HARNESS"));
    }

    private void log(String message) {
        plugin.getLogger().info("[N Arena][Harness] " + message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!enabled()) {
            sender.sendMessage("harness disabled (-Drumilance.harness=true)");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("usage: " + usage());
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "ground" -> ground(sender, args);
                case "room" -> room(sender, args);
                case "dummy" -> dummy(sender, args);
                case "fight" -> fight(sender, args);
                case "status" -> status(sender);
                case "stop" -> stop(sender);
                default -> sender.sendMessage("usage: " + usage());
            }
        } catch (RuntimeException e) {
            log("FAILED " + sub + ": " + e);
            sender.sendMessage("harness error: " + e);
        }
        return true;
    }

    private static String usage() {
        return "narena-harness ground <r> <depth> [world] | room <id> <TYPE> <r> <x> <y> <z> | "
                + "dummy <name> <x> <y> <z> | fight <TYPE> <secs> [difficulty] [rounds] | status | stop";
    }

    private World world(String name) {
        return name == null ? Bukkit.getWorlds().getFirst() : Bukkit.getWorld(name);
    }

    // ------------------------------------------------------------------ ground
    /**
     * Stone floor of {@code depth} blocks under (anchor x/z) plus cleared air above. Filled one
     * x-slice per tick so the server keeps ticking — 100 blocks deep is ~4k block updates a tick.
     */
    private void ground(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("usage: " + usage());
            return;
        }
        int radius = Integer.parseInt(args[1]);
        int depth = Integer.parseInt(args[2]);
        World target = args.length > 3 ? world(args[3])
                : (anchor != null && anchor.getWorld() != null ? anchor.getWorld() : world(null));
        if (target == null) {
            sender.sendMessage("world not loaded");
            return;
        }
        Location base = anchor != null ? anchor : new Location(target, 0, 64, 0);
        int cx = base.getBlockX();
        int cz = base.getBlockZ();
        int top = base.getBlockY() - 1;
        int bottom = Math.max(target.getMinHeight(), top - depth);
        if (fillTask != null) {
            fillTask.cancel();
        }
        final int[] x = {cx - radius};
        final int maxX = cx + radius;
        fillTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (x[0] > maxX) {
                fillTask.cancel();
                fillTask = null;
                log("ground done: " + (2 * radius + 1) + "x" + (2 * radius + 1)
                        + " stone y=" + bottom + ".." + top + " in " + target.getName());
                return;
            }
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = bottom; y <= top; y++) {
                    target.getBlockAt(x[0], y, z).setType(Material.STONE, false);
                }
                for (int y = top + 1; y <= top + 24; y++) {
                    if (y <= target.getMaxHeight()) {
                        target.getBlockAt(x[0], y, z).setType(Material.AIR, false);
                    }
                }
            }
            x[0]++;
        }, 1L, 1L);
        anchor = new Location(target, cx + 0.5, top + 1, cz + 0.5, 0f, 0f);
        sender.sendMessage("filling stone y=" + bottom + ".." + top + " around "
                + cx + "," + cz + " (r=" + radius + ") …");
        log("ground start x=" + cx + " z=" + cz + " top=" + top + " depth=" + depth
                + " world=" + target.getName());
    }

    // -------------------------------------------------------------------- room
    private void room(CommandSender sender, String[] args) {
        if (args.length < 7) {
            sender.sendMessage("usage: " + usage());
            return;
        }
        String roomId = args[1];
        PracticeType type = PracticeType.valueOf(args[2].toUpperCase(Locale.ROOT));
        int radius = Integer.parseInt(args[3]);
        int x = Integer.parseInt(args[4]);
        int y = Integer.parseInt(args[5]);
        int z = Integer.parseInt(args[6]);
        World w = anchor != null && anchor.getWorld() != null ? anchor.getWorld() : world(null);
        practice.createDraft(roomId, type);
        practice.applySelection(roomId, Cuboid.of(w.getName(),
                x - radius, y - 4, z - radius, x + radius, y + 8, z + radius));
        practice.setP1(roomId, new Location(w, x + 0.5, y, z + 0.5, 0f, 0f));
        practice.saveDraft(roomId).ifPresent(err -> log("room save note: " + err));
        practice.setEnabled(roomId, true);
        practice.bindBotRoom(type, roomId);
        anchor = new Location(w, x + 0.5, y, z + 0.5, 0f, 0f);
        log("room " + roomId + " type=" + type + " center=" + x + "," + y + "," + z
                + " r=" + radius + " bound=" + practice.botRoomFor(type));
        sender.sendMessage("room " + roomId + " ready (type=" + type + ", bound to bot mode)");
    }

    // ------------------------------------------------------------------- dummy
    private void dummy(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("usage: " + usage());
            return;
        }
        String name = args[1];
        World w = anchor != null && anchor.getWorld() != null ? anchor.getWorld() : world(null);
        double x = Double.parseDouble(args[2]);
        double y = Double.parseDouble(args[3]);
        double z = Double.parseDouble(args[4]);
        PacketBotBody old = dummies.remove(name);
        if (old != null) {
            PacketBotFactory.despawn(old);
        }
        PacketBotBody body = PacketBotFactory.spawnDummy(new Location(w, x, y, z), name, 20.0d);
        dummies.put(name, body);
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            stateManager.resetToLobby(player.getUniqueId());
        }
        anchor = new Location(w, x, y, z, 0f, 0f);
        log("dummy " + name + " spawned at " + x + "," + y + "," + z + " world=" + w.getName()
                + " online=" + (player != null));
        sender.sendMessage("dummy " + name + " online=" + (player != null));
    }

    // ------------------------------------------------------------------- fight
    /**
     * Runs ordinary bot matches for the dummy: join → start → (match end) → join again.
     * Everything else is the production path, so the trace/sampler output is comparable with
     * the reference qlog.
     */
    private void fight(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("usage: " + usage());
            return;
        }
        PracticeType type = PracticeType.valueOf(args[1].toUpperCase(Locale.ROOT));
        int seconds = Integer.parseInt(args[2]);
        BotDifficulty difficulty = args.length > 3
                ? BotDifficulty.deserialize(args[3]) : BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE);
        int rounds = args.length > 4 ? Integer.parseInt(args[4]) : 1;

        String roomId = practice.botRoomFor(type);
        if (roomId == null) {
            sender.sendMessage("no room bound for " + type + " — run 'room' first");
            return;
        }
        if (dummies.isEmpty()) {
            sender.sendMessage("no dummy — run 'dummy' first");
            return;
        }
        if (driveTask != null) {
            driveTask.cancel();
        }
        final int[] left = {rounds};
        final String dummyName = dummies.keySet().iterator().next();
        log("fight start type=" + type + " seconds=" + seconds + " difficulty="
                + difficulty.preset() + " rounds=" + rounds + " room=" + roomId
                + " dummy=" + dummyName);
        startRound(type, difficulty, seconds, roomId, dummyName, left, sender);
    }

    private void startRound(PracticeType type, BotDifficulty difficulty, int seconds, String roomId,
                            String dummyName, int[] left, CommandSender sender) {
        Player player = Bukkit.getPlayerExact(dummyName);
        if (player == null || !player.isOnline()) {
            sender.sendMessage("dummy offline: " + dummyName);
            return;
        }
        practice.session(player.getUniqueId()).ifPresent(session -> practice.leave(player, false));
        stateManager.resetToLobby(player.getUniqueId());
        PracticeRoom room = practice.get(roomId).orElse(null);
        if (room == null) {
            sender.sendMessage("room vanished: " + roomId);
            return;
        }
        practice.joinBotMode(player, type, room);
        // The join teleport is async; start the countdown once the session exists.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var session = practice.session(player.getUniqueId()).orElse(null);
            if (session == null) {
                log("round aborted: no session after join (dummy=" + dummyName + ")");
                return;
            }
            session.setDurationSeconds(seconds);
            session.setDifficulty(difficulty);
            practice.handleWaitInteract(player, session, PracticeItems.ACTION_START);
            log("round started dummy=" + dummyName + " seconds=" + seconds
                    + " difficulty=" + difficulty.preset() + " phase=" + session.phase());
            if (driveTask != null) {
                driveTask.cancel();
            }
            driveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (practice.session(player.getUniqueId()).isPresent()) {
                    return;
                }
                left[0]--;
                log("round finished, remaining=" + left[0]);
                if (left[0] <= 0) {
                    driveTask.cancel();
                    driveTask = null;
                    log("fight all rounds done (" + dummyName + ")");
                    return;
                }
                driveTask.cancel();
                driveTask = null;
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> startRound(type, difficulty, seconds, roomId, dummyName, left, sender),
                        40L);
            }, 60L, 20L);
        }, 40L);
    }

    // ------------------------------------------------------------ status / stop
    private void status(CommandSender sender) {
        StringBuilder out = new StringBuilder("harness: ");
        out.append("dummies=").append(dummies.size());
        for (String name : dummies.keySet()) {
            Player player = Bukkit.getPlayerExact(name);
            out.append(" [").append(name).append(player == null ? " offline" : " online hp="
                    + String.format(Locale.ROOT, "%.1f", player.getHealth()));
            if (player != null) {
                var session = practice.session(player.getUniqueId()).orElse(null);
                out.append(session == null ? " no-session"
                        : " phase=" + session.phase() + " type=" + session.type()
                        + " diff=" + session.difficulty().preset());
            }
            out.append("]");
        }
        for (PracticeType type : List.of(PracticeType.CRYSTAL, PracticeType.SWORD,
                PracticeType.NETHERITE_POT, PracticeType.MACE, PracticeType.CART)) {
            String room = practice.botRoomFor(type);
            if (room != null) {
                out.append(" ").append(type).append("->").append(room);
            }
        }
        if (anchor != null) {
            out.append(" anchor=").append(anchor.getBlockX()).append(",")
                    .append(anchor.getBlockY()).append(",").append(anchor.getBlockZ());
        }
        log(out.toString());
        sender.sendMessage(out.toString());
    }

    private void stop(CommandSender sender) {
        if (driveTask != null) {
            driveTask.cancel();
            driveTask = null;
        }
        if (fillTask != null) {
            fillTask.cancel();
            fillTask = null;
        }
        List<String> names = new ArrayList<>(dummies.keySet());
        for (String name : names) {
            Player player = Bukkit.getPlayerExact(name);
            if (player != null) {
                practice.session(player.getUniqueId()).ifPresent(session -> practice.leave(player, false));
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String name : names) {
                PacketBotBody body = dummies.remove(name);
                if (body != null) {
                    PacketBotFactory.despawn(body);
                }
            }
            log("stopped; despawned " + names.size() + " dummy(ies)");
        }, 20L);
        sender.sendMessage("harness stopping");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!enabled()) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("ground", "room", "dummy", "fight", "status", "stop");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("fight")) {
            return List.of("CRYSTAL", "SWORD", "NETHERITE_POT", "MACE", "CART");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("fight")) {
            return List.of("NPC", "EASY", "INTERMEDIATE", "HARD", "CRAZY", "MASTER");
        }
        return List.of();
    }
}
