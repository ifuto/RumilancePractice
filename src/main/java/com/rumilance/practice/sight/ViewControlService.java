package com.rumilance.practice.sight;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.util.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

/**
 * Per-player sight control: a real (vanilla-behaving) per-player {@link WorldBorder} fitted
 * around the player's current play area, plus a per-player send-view-distance cap so non-admin
 * players are never sent chunks beyond that area.
 *
 * <ul>
 *   <li><b>Border</b> — {@code Bukkit.createWorldBorder()} virtual borders behave exactly like
 *       the world border on the client (solid wall, red vignette outside warning range). They
 *       apply to every match/FFA participant regardless of OP, because they are gameplay.
 *       {@code ArenaBoundsListener} remains as the server-side backstop for modified clients.</li>
 *   <li><b>View distance</b> — non-admin players get {@code setSendViewDistance} clamped to the
 *       area size, so surrounding arenas/land are simply never sent ("packets not sent").
 *       Admins ({@code rumilance.admin}) keep the server default view distance.</li>
 * </ul>
 */
public final class ViewControlService {

    private final ArenaService arenaService;
    private final boolean enabled;

    public ViewControlService(ArenaService arenaService, boolean enabled) {
        this.arenaService = arenaService;
        this.enabled = enabled;
    }

    public ViewControlService(ArenaService arenaService, SightSettings sightSettings) {
        this(arenaService, sightSettings == null || sightSettings.enabled());
    }

    /**
     * Lobby mode: non-admin players get a border + view distance fitted to the lobby region
     * itself (the cuboid configured via {@code /slobby}), so nothing beyond the lobby is ever
     * visible. Admins get no border and the server-default view distance. When no lobby
     * region is configured, everything resets to defaults.
     */
    public void applyLobby(Player player, Cuboid lobbyRegion) {
        if (!enabled) {
            return;
        }
        if (isExempt(player) || lobbyRegion == null) {
            player.setWorldBorder(null);
            resetViewDistance(player);
            return;
        }
        applyBorder(player, lobbyRegion);
        setViewChunks(player, chunksFor(lobbyRegion));
    }

    /** Fits a per-player border + view distance to an arbitrary cuboid region (FFA etc.). */
    public void applyRegion(Player player, Cuboid region) {
        if (!enabled || region == null) {
            return;
        }
        applyBorder(player, region);
        if (!isExempt(player)) {
            setViewChunks(player, chunksFor(region));
        }
    }

    /** Resolves the arena of {@code session} and applies border + view distance. */
    public void applyForMatch(Player player, MatchSession session) {
        if (!enabled || session == null || session.arenaInstanceId() == null) {
            return;
        }
        ArenaInstance instance = arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            return;
        }
        // Instance bounds (origin-shifted for disposable copies).
        applyRegion(player, Cuboid.of(instance.template().world(),
                instance.minX(), instance.minY(), instance.minZ(),
                instance.maxX(), instance.maxY(), instance.maxZ()));
    }

    /** Removes the per-player border and restores the server-default view distance. */
    public void clear(Player player) {
        if (!enabled) {
            return;
        }
        player.setWorldBorder(null);
        resetViewDistance(player);
    }

    // ------------------------------------------------------------------ internals

    private void applyBorder(Player player, Cuboid region) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(
                PracticeGuards.matchBorderCenterX(region.minX(), region.maxX()),
                PracticeGuards.matchBorderCenterZ(region.minZ(), region.maxZ()));
        border.setSize(PracticeGuards.matchBorderSize(
                region.minX(), region.maxX(), region.minZ(), region.maxZ()));
        border.setWarningDistance(0);
        border.setWarningTime(0);
        player.setWorldBorder(border);
    }

    private boolean isExempt(Player player) {
        return player.hasPermission("rumilance.admin");
    }

    private int chunksFor(Cuboid region) {
        int maxDim = Math.max(region.maxX() - region.minX(), region.maxZ() - region.minZ());
        // Enough to see the whole area from anywhere inside it, plus one chunk of slack.
        return clampChunks(maxDim / 16 + 2);
    }

    private void setViewChunks(Player player, int chunks) {
        try {
            player.setSendViewDistance(clampChunks(chunks));
        } catch (Throwable ignored) {
            // Older/forked servers without per-player view distance: silently skip.
        }
    }

    private void resetViewDistance(Player player) {
        try {
            player.setSendViewDistance(Bukkit.getViewDistance());
        } catch (Throwable ignored) {
        }
    }

    private static int clampChunks(int chunks) {
        return Math.max(2, Math.min(32, chunks));
    }
}
