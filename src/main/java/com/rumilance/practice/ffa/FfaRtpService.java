package com.rumilance.practice.ffa;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.util.SafeTeleport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * In-FFA random teleport ({@code /rtp}) — teleports the caller to a random safe standing spot
 * inside their current arena. Only usable inside an FFA arena whose per-arena
 * {@code settings.rtp} toggle is ON (default OFF), and only out of combat.
 *
 * <p>Reuses the same safe-spawn pipeline as normal FFA spawning: indexed spots first, then the
 * live spawn locator, then the arena's configured spawn — always corrected onto standable ground
 * and clamped inside the arena region.</p>
 */
public final class FfaRtpService {

    public enum RtpResult {
        OK, NOT_IN_FFA, DISABLED, IN_COMBAT, NO_SPOT
    }

    private final FfaService ffaService;
    private final FfaSpawnIndex spawnIndex;
    private final MessageService messages;

    /**
     * @param spawnIndex may be {@code null}; then the live locator picks the destination
     */
    public FfaRtpService(FfaService ffaService, FfaSpawnIndex spawnIndex, MessageService messages) {
        this.ffaService = ffaService;
        this.spawnIndex = spawnIndex;
        this.messages = messages;
    }

    /** Runs {@link #teleport(Player)} and reports the outcome through the message service. */
    public void rtp(Player player) {
        switch (teleport(player)) {
            case OK -> { /* success message sent before the move */ }
            case NOT_IN_FFA -> messages.send(player, "rtp.not-in-ffa");
            case DISABLED -> messages.send(player, "rtp.disabled");
            case IN_COMBAT -> messages.send(player, "rtp.in-combat");
            case NO_SPOT -> messages.send(player, "rtp.no-spot");
            default -> { }
        }
    }

    public RtpResult teleport(Player player) {
        UUID id = player.getUniqueId();
        String arenaId = arenaIdOf(id);
        if (arenaId == null) {
            return RtpResult.NOT_IN_FFA;
        }
        FfaService.FfaArena arena = ffaService.find(arenaId).orElse(null);
        if (arena == null || !arena.enabled() || !arena.rtpEnabled()) {
            return RtpResult.DISABLED;
        }
        if (ffaService.inCombat(id)) {
            return RtpResult.IN_COMBAT;
        }
        List<Location> occupied = new ArrayList<>();
        for (UUID occupantId : ffaService.occupantIds()) {
            if (occupantId.equals(id) || !arenaId.equals(arenaIdOf(occupantId))) {
                continue;
            }
            Player other = Bukkit.getPlayer(occupantId);
            if (other != null) {
                occupied.add(other.getLocation());
            }
        }
        Location anchor = spawnIndex != null ? spawnIndex.pick(arena, occupied) : null;
        if (anchor == null || anchor.getWorld() == null) {
            anchor = FfaSpawnLocator.find(arena, occupied);
        }
        if (anchor == null || anchor.getWorld() == null) {
            Location configured = arena.spawn();
            if (configured != null) {
                anchor = com.rumilance.practice.util.SpawnFooting.standClear(configured);
                if (anchor == null) {
                    int minY = arena.region() != null && arena.region().world() != null
                            ? Math.max(arena.region().world().getMinHeight(), arena.region().minY())
                            : configured.getWorld() != null ? configured.getWorld().getMinHeight() : 0;
                    anchor = com.rumilance.practice.util.SpawnFooting.standClearDeep(configured, minY);
                }
            }
        }
        if (anchor == null || anchor.getWorld() == null) {
            return RtpResult.NO_SPOT;
        }
        if (arena.region() != null) {
            anchor = com.rumilance.practice.util.LocationUtil.safeTeleportLocation(anchor, arena.region());
        }
        messages.send(player, "rtp.teleported");
        SafeTeleport.teleport(player, anchor);
        return RtpResult.OK;
    }

    private String arenaIdOf(UUID id) {
        return ffaService.arenaOf(id).map(a -> a.toLowerCase(Locale.ROOT)).orElse(null);
    }
}
