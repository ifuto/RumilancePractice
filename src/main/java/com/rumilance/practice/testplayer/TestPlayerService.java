package com.rumilance.practice.testplayer;

import com.rumilance.practice.command.DuelCommand;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.herobot.HeroBotPlayer;
import com.rumilance.practice.herobot.HeroBotRegistry;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.RealPlayers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Operator smoke-test doubles: {@code /testplayer} spawns a stand-in opponent so one person can
 * exercise the whole duel flow (Battle Menu → player list → Duel Request → accept → fight)
 * without a second human on the server.
 *
 * <p>The double is a {@link HeroBotPlayer} — a carpet-style fake {@code ServerPlayer} (real
 * hitbox, real damage/combat pipeline, real inventory) — that is then:</p>
 * <ul>
 *   <li>given a {@link com.rumilance.practice.session.PlayerSession} and forced into
 *       {@link PlayerState#LOBBY}, so every state guard and duel picker accepts it;</li>
 *   <li>registered in {@link RealPlayers#include} so it shows up in the online count, the
 *       scoreboard and (critically) the Battle Menu's player list — bots are otherwise hidden
 *       from every player-facing list on purpose;</li>
 *   <li>driven by {@link #tickAutoAccept}, which accepts any incoming duel request after a short
 *       delay, through the very same {@link DuelCommand#handleAccept} a human would run.</li>
 * </ul>
 */
public final class TestPlayerService {

    /** Delay before a pending duel request is auto-accepted, in server ticks (0.75 s). */
    private static final long ACCEPT_DELAY_TICKS = 15L;
    /** Polling period of the auto-accept task, in server ticks. */
    private static final long ACCEPT_PERIOD_TICKS = 10L;

    private final Plugin plugin;
    private final HeroBotRegistry bots;
    private final SessionManager sessions;
    private final PlayerStateManager states;
    private final DuelRequestService duels;
    private final DuelCommand duelCommand;

    private final Set<UUID> testPlayers = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, Long> seenAt = new ConcurrentHashMap<>();
    private BukkitTask autoAcceptTask;
    private int nameCounter;

    public TestPlayerService(Plugin plugin, HeroBotRegistry bots, SessionManager sessions,
                             PlayerStateManager states, DuelRequestService duels,
                             DuelCommand duelCommand) {
        this.plugin = plugin;
        this.bots = bots;
        this.sessions = sessions;
        this.states = states;
        this.duels = duels;
        this.duelCommand = duelCommand;
    }

    public boolean isTestPlayer(UUID playerId) {
        return playerId != null && testPlayers.contains(playerId);
    }

    public Set<UUID> testPlayers() {
        return Set.copyOf(testPlayers);
    }

    /** True when {@code /testplayer} can work at all (the fake-player registry is available). */
    public boolean available() {
        return bots != null;
    }

    /**
     * Spawns one test player. Names default to {@code Test<n>} and must be unique.
     *
     * @return the spawned player, or {@code null} when the name is taken or the registry is down
     */
    public Player spawn(Location location, String requestedName) {
        if (bots == null || location == null || location.getWorld() == null) {
            return null;
        }
        String name = requestedName == null || requestedName.isBlank()
                ? nextName()
                : requestedName.trim();
        if (bots.byName(name) != null) {
            return null;
        }
        if (Bukkit.getPlayerExact(name) != null) {
            return null;
        }
        HeroBotPlayer bot;
        try {
            bot = bots.spawn(name, location, location.getYaw(), location.getPitch(),
                    net.minecraft.world.level.GameType.SURVIVAL, null, false);
        } catch (Throwable t) {
            plugin.getLogger().warning("[testplayer] spawn failed for " + name + ": " + t);
            return null;
        }
        if (bot == null) {
            return null;
        }
        Player bukkit = bot.getBukkitEntity();
        if (bukkit == null) {
            bots.despawn(name);
            return null;
        }
        UUID id = bukkit.getUniqueId();
        // Human-readable nametag: the registry's default is "NARENA BOT".
        bukkit.setCustomName(name);
        bukkit.setCustomNameVisible(true);
        // Make it a first-class citizen: session, lobby state, and player-facing lists.
        try {
            sessions.createSession(id, "en_us");
        } catch (Throwable ignored) {
            // A session may already exist (re-spawn of the same UUID); keeping it is fine.
        }
        states.initialize(id);
        RealPlayers.include(id);
        testPlayers.add(id);
        startAutoAccept();
        return bukkit;
    }

    /** Removes one test player by name; {@code false} when no such test player exists. */
    public boolean despawn(String name) {
        if (bots == null || name == null || name.isBlank()) {
            return false;
        }
        HeroBotPlayer bot = bots.byName(name.trim());
        if (bot == null) {
            return false;
        }
        Player bukkit = bot.getBukkitEntity();
        UUID id = bukkit == null ? null : bukkit.getUniqueId();
        forget(id);
        return bots.despawn(name.trim());
    }

    /** Removes every test player. Returns how many were removed. */
    public int despawnAll() {
        if (bots == null) {
            return 0;
        }
        List<String> names = new ArrayList<>();
        for (UUID id : testPlayers) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                names.add(online.getName());
            }
        }
        int removed = 0;
        for (String name : names) {
            if (despawn(name)) {
                removed++;
            }
        }
        return removed;
    }

    /** Displays names of the live test players (best effort; a vanished double is skipped). */
    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (UUID id : testPlayers) {
            Player online = Bukkit.getPlayer(id);
            out.add(online != null ? online.getName() : id.toString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Plugin shutdown: drop every double and stop the task. */
    public void shutdown() {
        stopAutoAccept();
        despawnAll();
        for (UUID id : Set.copyOf(testPlayers)) {
            forget(id);
        }
    }

    private void forget(UUID id) {
        if (id == null) {
            return;
        }
        testPlayers.remove(id);
        seenAt.remove(id);
        RealPlayers.exclude(id);
        states.remove(id);
        try {
            sessions.removeSession(id);
        } catch (Throwable ignored) {
            // Nothing registered — nothing to clean up.
        }
        try {
            duels.invalidateForPlayer(id);
        } catch (Throwable ignored) {
            // Ignore: the request service may already be torn down during shutdown.
        }
    }

    private String nextName() {
        String candidate;
        do {
            candidate = "Test" + (++nameCounter);
        } while (bots != null && bots.byName(candidate) != null
                && nameCounter < 1000);
        return candidate;
    }

    private void startAutoAccept() {
        if (autoAcceptTask != null || plugin == null || duelCommand == null) {
            return;
        }
        autoAcceptTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAutoAccept,
                ACCEPT_PERIOD_TICKS, ACCEPT_PERIOD_TICKS);
    }

    private void stopAutoAccept() {
        if (autoAcceptTask != null) {
            autoAcceptTask.cancel();
            autoAcceptTask = null;
        }
    }

    /**
     * Accepts duel requests aimed at a test player, but only after the request has been pending
     * for {@link #ACCEPT_DELAY_TICKS} — accepting on the same tick the sender's click lands would
     * tear the sender out of the Duel Request GUI before it can close cleanly.
     */
    private void tickAutoAccept() {
        if (testPlayers.isEmpty()) {
            stopAutoAccept();
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID id : testPlayers) {
            Player self = Bukkit.getPlayer(id);
            if (self == null || !self.isOnline()) {
                forget(id);
                continue;
            }
            if (states.getState(id) != PlayerState.LOBBY) {
                // In a fight / countdown / queue: leave it to the normal flow.
                seenAt.remove(id);
                continue;
            }
            var request = duels.latestForTarget(id);
            if (request.isEmpty()) {
                seenAt.remove(id);
                continue;
            }
            long firstSeen = seenAt.computeIfAbsent(request.get().id(), key -> now);
            if (now - firstSeen < ACCEPT_DELAY_TICKS * 50L) {
                continue;
            }
            seenAt.remove(request.get().id());
            try {
                duelCommand.handleAccept(self, null);
            } catch (Throwable t) {
                plugin.getLogger().warning("[testplayer] auto-accept failed for "
                        + self.getName() + ": " + t);
            }
        }
    }
}
