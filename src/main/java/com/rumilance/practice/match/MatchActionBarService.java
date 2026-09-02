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
 * Pinned action-bar scoreline shown to every fighter and spectator during a match:
 *
 * <pre>{@code <self name> <self score> - <opponent score> <opponent name>}</pre>
 *
 * <p>Names are coloured by the fighter's team colour (red for {@link TeamColor#RED}, aqua/blue
 * for {@link TeamColor#BLUE}). The score is the current game's kills (deaths) — the value that
 * moves during the fight — prefixed by the running series/round wins so the Best-of count is
 * visible too. A player head cannot be rendered inside an action bar, so the coloured name with
 * a small marker stands in for the requested "face".</p>
 */
public final class MatchActionBarService {

    private static final long PERIOD_TICKS = 10L;

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;
    private volatile com.rumilance.practice.spectator.SpectatorService spectatorService;
    private volatile com.rumilance.practice.headfont.HeadFontService headFontService;
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
            viewer.sendActionBar(buildLine(viewer, session));
        }
    }

    private Component buildLine(Player viewer, MatchSession session) {
        TeamColor viewerColor = session.teamColor(viewer.getUniqueId());
        TeamColor selfColor = viewerColor != null ? viewerColor : TeamColor.RED;
        TeamColor oppColor = selfColor.opposite();

        Component left;
        Component right;
        if (session.isTeamMatch()) {
            left = sideLabel(session, selfColor);
            right = sideLabel(session, oppColor);
        } else {
            java.util.List<java.util.UUID> parts = session.participants();
            java.util.UUID me = viewerColor != null ? viewer.getUniqueId()
                    : (parts.isEmpty() ? null : parts.get(0));
            java.util.UUID opp = me == null ? null : session.opponentOf(me);
            // For a pure spectator (no colour) show the two fighters as red/blue.
            if (viewerColor == null) {
                me = parts.isEmpty() ? null : parts.get(0);
                opp = parts.size() < 2 ? null : parts.get(1);
            }
            left = playerLabel(session, me, selfColor);
            right = playerLabel(session, opp, oppColor);
        }

        return Component.empty()
                .append(left)
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(Component.text("-", NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
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

    private Component playerLabel(MatchSession session, java.util.UUID id, TeamColor color) {
        NamedTextColor textColor = color == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.AQUA;
        String name = id == null ? "-" : com.rumilance.practice.stats.StatsService.nameOf(id);
        int score = id == null ? 0 : session.killsOf(id);
        int series = id == null ? 0 : session.seriesWinsOf(id);

        Component label = Component.empty();
        // Render the fighter's real face via the vanilla player-sprite (<head:uuid>, no resource pack).
        if (id != null && headFontService != null) {
            label = label.append(headFontService.head(id));
        }
        label = label.append(Component.text(" " + name + " ", textColor))
                .append(Component.text(score, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(series > 0
                        ? Component.text(" (" + series + ")", NamedTextColor.GRAY)
                        : Component.empty());
        return label;
    }
}
