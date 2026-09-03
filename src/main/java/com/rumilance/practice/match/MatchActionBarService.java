package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Pinned action-bar line shown to every fighter and spectator during a match. What it shows is
 * configured by {@code match.action-bar-mode} in config.yml:
 *
 * <ul>
 *   <li>{@code score} (default) — the duel scoreline:
 *       {@code <red face><red score> - <blue score><blue face>} — names are intentionally
 *       omitted; the vanilla player sprite identifies each fighter. Team battles show the
 *       alive count per side instead ({@code ●n - ●n}).</li>
 *   <li>{@code time} — the elapsed match time in {@code min:sec} (e.g. {@code 04:09}),
 *       counted from the moment the fight went ACTIVE.</li>
 * </ul>
 *
 * <p>Colours follow team colours (red for {@link TeamColor#RED}, aqua/blue for
 * {@link TeamColor#BLUE}), rendered non-bold ("thin"). The score is the current game's kills.
 * Spectators see the same red-vs-blue line as the fighters (their own name never appears —
 * spectating is detected via {@link MatchSession#isParticipant(java.util.UUID)}, because
 * {@code teamColor()} defaults non-participants to RED).</p>
 */
public final class MatchActionBarService {

    private static final long PERIOD_TICKS = 10L;

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;
    private volatile com.rumilance.practice.spectator.SpectatorService spectatorService;
    private volatile com.rumilance.practice.headfont.HeadFontService headFontService;
    private volatile com.rumilance.practice.config.ConfigService configService;
    private BukkitTask task;

    public MatchActionBarService(Plugin plugin, MatchRegistry matchRegistry) {
        this.plugin = plugin;
        this.matchRegistry = matchRegistry;
    }

    public void setSpectatorService(com.rumilance.practice.spectator.SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    public void setHeadFontService(com.rumilance.practice.headfont.HeadFontService headFontService) {
        this.headFontService = headFontService;
    }

    public void setConfigService(com.rumilance.practice.config.ConfigService configService) {
        this.configService = configService;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        boolean timeMode = isTimeMode();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            MatchSession session = matchRegistry.byPlayer(viewer.getUniqueId()).orElse(null);
            if (session == null && spectatorService != null) {
                session = spectatorService.matchOf(viewer.getUniqueId())
                        .flatMap(matchRegistry::get)
                        .orElse(null);
            }
            if (session == null || session.state() != MatchState.ACTIVE) {
                continue;
            }
            viewer.sendActionBar(timeMode ? buildElapsed(viewer, session) : buildLine(viewer, session));
        }
    }

    /** {@code match.action-bar-mode: time} shows the elapsed fight time instead of the score. */
    private boolean isTimeMode() {
        com.rumilance.practice.config.ConfigService cfg = configService;
        if (cfg == null) {
            return false;
        }
        return "time".equalsIgnoreCase(cfg.config().getString("match.action-bar-mode", "score"));
    }

    /** Elapsed time since the fight went ACTIVE, rendered as {@code min:sec}. */
    private Component buildElapsed(Player viewer, MatchSession session) {
        java.time.Instant started = session.startedAt();
        long seconds = started == null
                ? 0L
                : Math.max(0L, java.time.Duration.between(started, java.time.Instant.now()).getSeconds());
        String mmss = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        return Component.text(mmss, NamedTextColor.WHITE, TextDecoration.BOLD);
    }

    private Component buildLine(Player viewer, MatchSession session) {
        // Spectator detection must use isParticipant(): teamColor() defaults non-participants
        // to RED (never null), which used to leak the spectator's own name into the bar.
        boolean fighter = session.isParticipant(viewer.getUniqueId());
        TeamColor selfColor = fighter ? session.teamColor(viewer.getUniqueId()) : TeamColor.RED;
        TeamColor oppColor = selfColor.opposite();

        Component left;
        Component right;
        if (session.isTeamMatch()) {
            left = sideLabel(session, selfColor);
            right = sideLabel(session, oppColor);
        } else {
            java.util.List<java.util.UUID> parts = session.participants();
            java.util.UUID me;
            java.util.UUID opp;
            if (fighter) {
                me = viewer.getUniqueId();
                opp = session.opponentOf(me);
            } else {
                // Pure spectator: show the two fighters, index 0 = RED, index 1 = BLUE.
                me = parts.isEmpty() ? null : parts.get(0);
                opp = parts.size() < 2 ? null : parts.get(1);
            }
            left = playerLabel(session, me, selfColor, true);
            right = playerLabel(session, opp, oppColor, false);
        }

        return Component.empty()
                .append(left)
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(right);
    }

    private Component sideLabel(MatchSession session, TeamColor color) {
        NamedTextColor textColor = color == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.AQUA;
        int alive = 0;
        for (java.util.UUID id : session.team(color)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                alive++;
            }
        }
        return Component.text((color == TeamColor.RED ? "● " : "● "), textColor, TextDecoration.BOLD)
                .append(Component.text(String.valueOf(alive), textColor, TextDecoration.BOLD));
    }

    /**
     * One side of the duel scoreline: {@code {face}{score}} when {@code faceFirst},
     * otherwise {@code {score}{face}}. The score is non-bold ("thin") in the fighter's team
     * colour; no name — the vanilla player sprite identifies the fighter.
     */
    private Component playerLabel(MatchSession session, java.util.UUID id, TeamColor color,
                                  boolean faceFirst) {
        NamedTextColor textColor = color == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.AQUA;
        int score = id == null ? 0 : session.killsOf(id);
        Component face = (id != null && headFontService != null)
                ? headFontService.head(id)
                : Component.empty();
        Component scoreText = Component.text(" " + score + " ", textColor);
        return faceFirst ? face.append(scoreText) : scoreText.append(face);
    }
}
