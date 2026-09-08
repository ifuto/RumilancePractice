package com.rumilance.practice.ffa;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.SafeTeleport;
import com.rumilance.practice.util.SpawnFooting;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * In-FFA teleport requests ({@code /tpa}, {@code /tpahere}, {@code /tpaccept}, {@code /tpadeny}).
 * Only usable inside an FFA arena whose per-arena {@code settings.tpa} toggle is ON, and only
 * between two players in the <b>same</b> arena. Requests expire after 60 seconds; a player may
 * have at most one outbound and one inbound request at a time.
 *
 * <p>Teleports are corrected onto standable ground ({@link SpawnFooting#standClear}) and clamped
 * inside the arena region, honoring the same safety rules as FFA spawns.</p>
 */
public final class FfaTpaService {

    /** How long a request stays valid, in milliseconds. */
    private static final long EXPIRE_MILLIS = 60_000L;

    public enum SendResult {
        OK, SELF, TARGET_OFFLINE, NOT_IN_FFA, TARGET_OTHER_ARENA,
        ARENA_TPA_DISABLED, ALREADY_OUTBOUND, TARGET_ALREADY_HAS_REQUEST
    }

    public enum AcceptResult {
        OK, NO_REQUEST, EXPIRED, REQUESTER_OFFLINE, MOVED_OUT_OF_ARENA
    }

    /** One pending request. */
    public record Request(UUID requester, UUID target, boolean here, long expiresAtMillis) {
        boolean expired(long now) {
            return now >= expiresAtMillis;
        }
    }

    private final FfaService ffaService;
    private final MessageService messages;
    /** Inbound requests keyed by target id. */
    private final Map<UUID, Request> pending = new HashMap<>();

    public FfaTpaService(FfaService ffaService, MessageService messages) {
        this.ffaService = ffaService;
        this.messages = messages;
    }

    public SendResult request(Player requester, Player target, boolean here) {
        if (requester == null || target == null) {
            return SendResult.TARGET_OFFLINE;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return SendResult.SELF;
        }
        long now = System.currentTimeMillis();
        sweepExpired(now);
        String reqArena = arenaIdOf(requester.getUniqueId());
        String tgtArena = arenaIdOf(target.getUniqueId());
        if (reqArena == null || tgtArena == null) {
            return SendResult.NOT_IN_FFA;
        }
        if (!reqArena.equals(tgtArena)) {
            return SendResult.TARGET_OTHER_ARENA;
        }
        FfaService.FfaArena arena = ffaService.find(reqArena).orElse(null);
        if (arena == null || !arena.tpaEnabled()) {
            return SendResult.ARENA_TPA_DISABLED;
        }
        if (hasOutbound(requester.getUniqueId())) {
            return SendResult.ALREADY_OUTBOUND;
        }
        Request inbound = pending.get(target.getUniqueId());
        if (inbound != null && !inbound.expired(now)) {
            return SendResult.TARGET_ALREADY_HAS_REQUEST;
        }
        pending.put(target.getUniqueId(),
                new Request(requester.getUniqueId(), target.getUniqueId(), here, now + EXPIRE_MILLIS));
        return SendResult.OK;
    }

    public AcceptResult accept(Player target) {
        Request request = pending.remove(target.getUniqueId());
        if (request == null) {
            return AcceptResult.NO_REQUEST;
        }
        long now = System.currentTimeMillis();
        if (request.expired(now)) {
            return AcceptResult.EXPIRED;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            return AcceptResult.REQUESTER_OFFLINE;
        }
        String arenaId = arenaIdOf(target.getUniqueId());
        if (arenaId == null || !arenaId.equals(arenaIdOf(requester.getUniqueId()))) {
            return AcceptResult.MOVED_OUT_OF_ARENA;
        }
        FfaService.FfaArena arena = ffaService.find(arenaId).orElse(null);
        if (arena == null) {
            return AcceptResult.MOVED_OUT_OF_ARENA;
        }
        // /tpa: requester goes to target. /tpahere: target goes to requester.
        Player mover = request.here() ? target : requester;
        Player anchor = request.here() ? requester : target;
        teleportGrounded(mover, anchor, arena);
        messages.send(target, "tpa.accepted");
        return AcceptResult.OK;
    }

    public void deny(Player target) {
        Request request = pending.remove(target.getUniqueId());
        if (request == null) {
            messages.send(target, "tpa.none");
            return;
        }
        messages.send(target, "tpa.denied");
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) {
            messages.send(requester, "tpa.denied-notice",
                    Placeholder.unparsed("player", display(target)));
        }
    }

    /** Command-side wrapper: runs {@link #request} and tells both sides the outcome. */
    public void sendRequest(Player requester, Player target, boolean here) {
        switch (request(requester, target, here)) {
            case OK -> {
                messages.send(requester, here ? "tpa.here-sent" : "tpa.sent",
                        Placeholder.unparsed("player", display(target)));
                messages.send(target, here ? "tpa.here-received" : "tpa.received",
                        Placeholder.unparsed("player", display(requester)));
            }
            case SELF -> messages.send(requester, "tpa.self");
            case TARGET_OFFLINE -> messages.send(requester, "tpa.offline");
            case NOT_IN_FFA -> messages.send(requester, "tpa.not-in-ffa");
            case TARGET_OTHER_ARENA -> messages.send(requester, "tpa.other-arena");
            case ARENA_TPA_DISABLED -> messages.send(requester, "tpa.disabled");
            case ALREADY_OUTBOUND -> messages.send(requester, "tpa.already-outbound");
            default -> messages.send(requester, "tpa.target-busy");
        }
    }

    /** Command-side wrapper for direct error dispatch (e.g. target offline before lookup). */
    public void sendResult(Player requester, Player target, boolean here, SendResult result) {
        switch (result) {
            case OK -> { }
            case SELF -> messages.send(requester, "tpa.self");
            case TARGET_OFFLINE -> messages.send(requester, "tpa.offline");
            case NOT_IN_FFA -> messages.send(requester, "tpa.not-in-ffa");
            case TARGET_OTHER_ARENA -> messages.send(requester, "tpa.other-arena");
            case ARENA_TPA_DISABLED -> messages.send(requester, "tpa.disabled");
            case ALREADY_OUTBOUND -> messages.send(requester, "tpa.already-outbound");
            default -> messages.send(requester, "tpa.target-busy");
        }
    }

    /** Command-side wrapper: runs {@link #accept} and reports failures only. */
    public void sendAccept(Player target) {
        switch (accept(target)) {
            case OK -> { }
            case NO_REQUEST -> messages.send(target, "tpa.none");
            case EXPIRED -> messages.send(target, "tpa.expired");
            case REQUESTER_OFFLINE -> messages.send(target, "tpa.offline");
            default -> messages.send(target, "tpa.moved-out");
        }
    }

    /** Cancels every request the player is part of (quit, left FFA arena, forced out). */
    public void cancelAll(UUID playerId) {
        if (playerId == null) {
            return;
        }
        pending.entrySet().removeIf(e ->
                e.getKey().equals(playerId) || e.getValue().requester().equals(playerId));
    }

    /** Queue-depth query (tests). */
    public int pendingCount() {
        return pending.size();
    }

    private void teleportGrounded(Player mover, Player anchor, FfaService.FfaArena arena) {
        Location destination = anchor.getLocation();
        Location grounded = SpawnFooting.standClear(destination);
        if (grounded == null && arena.region() != null && arena.region().world() != null) {
            int minY = Math.max(arena.region().world().getMinHeight(), arena.region().minY());
            grounded = SpawnFooting.standClearDeep(destination, minY);
        }
        Location base = grounded != null ? grounded : destination;
        if (arena.region() != null) {
            base = LocationUtil.safeTeleportLocation(base, arena.region());
        }
        if (base.getWorld() == null && destination.getWorld() != null) {
            base = destination;
        }
        // Face the partner on arrival so the fight reads instantly.
        Location aimed = base.clone();
        aimed.setDirection(anchor.getLocation().toVector().subtract(base.toVector()));
        SafeTeleport.teleport(mover, aimed);
    }

    public static String display(Player player) {
        return player == null ? "?" : player.getName();
    }

    private String arenaIdOf(UUID id) {
        return ffaService.arenaOf(id).map(a -> a.toLowerCase(Locale.ROOT)).orElse(null);
    }

    private boolean hasOutbound(UUID requesterId) {
        for (Request r : pending.values()) {
            if (r.requester().equals(requesterId)) {
                return true;
            }
        }
        return false;
    }

    private void sweepExpired(long now) {
        pending.entrySet().removeIf(e -> e.getValue().expired(now));
    }
}
