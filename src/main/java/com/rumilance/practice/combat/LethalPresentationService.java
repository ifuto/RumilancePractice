package com.rumilance.practice.combat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * How a kill LOOKS to the killer (user spec 2026-10-06) — built on PacketEvents.
 *
 * <p>A losing fighter never actually dies: their gear is cleared and they are made invulnerable,
 * because the round (or the end-of-match screen) has to carry on with the player still connected.
 * That is fine from the loser's side, but it made kills feel weightless for the winner — the
 * opponent just stood there holding nothing.</p>
 *
 * <p>So the kill is <em>staged for the killer only</em>:</p>
 * <ol>
 *   <li>a forged {@code Entity Status} packet (status 3) is sent to the killer, whose client then
 *       plays the full death animation and death sound for the victim;</li>
 *   <li>a moment later a {@code Destroy Entities} packet removes the victim from the killer's
 *       client outright. This is the part {@code Player#hideEntity} does not do on its own: a
 *       destroy packet is what actually drops the entity — including its F3+B hit box — instead
 *       of only blanking the model;</li>
 *   <li>from then on every {@code Spawn Player} packet for that entity id is cancelled for that
 *       viewer, so the server cannot re-add the victim on a chunk reload or a re-track. This is
 *       literally 「Aのパケットを送信するのをやめます」.</li>
 * </ol>
 *
 * <p>The victim's own view is deliberately untouched: cleared inventory, invulnerability and
 * everything else behave exactly as before. Net effect — from B's side nothing changed, from
 * A's side B simply died like on any survival server.</p>
 *
 * <p>PacketEvents is a soft dependency: without it only step 2 is lost, and a missing listener
 * simply means the victim can reappear later rather than the feature breaking.</p>
 */
public final class LethalPresentationService implements Listener, PacketListener {

    /** Entity Status value that makes the client play the death animation and sound. */
    public static final int DEATH_STATUS = 3;
    /** How long the killer gets to watch the fall before the victim is destroyed. */
    public static final long VANISH_DELAY_TICKS = 20L;

    private final Plugin plugin;
    /** True when PacketEvents is installed and this service is registered as a packet listener. */
    private final boolean packetEvents;
    /** killer → entity ids that must never be spawned to that viewer again. */
    private final Map<UUID, Set<Integer>> suppressed = new ConcurrentHashMap<>();
    /** killer → victim, kept so the Bukkit-side tracker can be released on top of the packets. */
    private final Map<UUID, Set<UUID>> hidden = new ConcurrentHashMap<>();

    public LethalPresentationService(Plugin plugin) {
        this.plugin = plugin;
        boolean pe = false;
        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            try {
                PacketEvents.getAPI().getEventManager()
                        .registerListener(this, PacketListenerPriority.NORMAL);
                pe = true;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "[LethalFx] PacketEvents found but the listener could not be registered - "
                                + "the death animation and the packet cut-off are off.", t);
            }
        } else {
            plugin.getLogger().info("[LethalFx] PacketEvents missing - a killed fighter is only "
                    + "hidden from their killer; no death animation and no packet cut-off.");
        }
        this.packetEvents = pe;
    }

    public boolean animationAvailable() {
        return packetEvents;
    }

    /**
     * A killed B: play B's death on A's screen, then stop A from receiving B at all.
     *
     * <p>Never throws and never touches B — this is pure presentation for A.</p>
     */
    public void stageKill(Player killer, Player victim) {
        if (killer == null || victim == null
                || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        sendDeathAnimation(killer, victim);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!killer.isOnline()) {
                return;
            }
            Player target = victim.isOnline() ? victim : Bukkit.getPlayer(victim.getUniqueId());
            if (target == null || !target.isOnline()) {
                return;
            }
            try {
                // 1. Destroy the entity on A's client — model AND hit box (the F3+B concern).
                if (packetEvents) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(killer,
                            new WrapperPlayServerDestroyEntities(target.getEntityId()));
                }
                // 2. Belt and braces: stop the server tracking the entity for A at all.
                killer.hideEntity(plugin, target);
                // 3. Keep anything from re-adding it (see onPacketSend).
                suppressed.computeIfAbsent(killer.getUniqueId(), id -> ConcurrentHashMap.newKeySet())
                        .add(target.getEntityId());
                hidden.computeIfAbsent(killer.getUniqueId(), id -> ConcurrentHashMap.newKeySet())
                        .add(target.getUniqueId());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.FINE, "[LethalFx] could not hide the victim", t);
            }
        }, VANISH_DELAY_TICKS);
    }

    /** Makes the victim visible to the killer again, if it was hidden. */
    public void restore(UUID killerId, UUID victimId) {
        if (killerId == null || victimId == null) {
            return;
        }
        Set<UUID> victims = hidden.get(killerId);
        if (victims != null && victims.remove(victimId) && victims.isEmpty()) {
            hidden.remove(killerId, victims);
        }
        release(killerId, victimId);
    }

    /**
     * End of a match (or any other "everyone is done here" moment): both directions are lifted
     * for every participant, so nobody is left invisible in the next round or in the lobby.
     */
    public void restoreAll(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return;
        }
        for (UUID id : participantIds) {
            Set<UUID> victims = hidden.remove(id);
            if (victims != null) {
                for (UUID victimId : victims) {
                    release(id, victimId);
                }
            }
            for (UUID killerId : new ArrayList<>(hidden.keySet())) {
                restore(killerId, id);
            }
        }
    }

    private void release(UUID killerId, UUID victimId) {
        Set<Integer> ids = suppressed.get(killerId);
        Player victim = Bukkit.getPlayer(victimId);
        if (ids != null && victim != null) {
            ids.remove(victim.getEntityId());
            if (ids.isEmpty()) {
                suppressed.remove(killerId, ids);
            }
        }
        show(killerId, victimId);
    }

    private void show(UUID killerId, UUID victimId) {
        Player killer = Bukkit.getPlayer(killerId);
        Player victim = Bukkit.getPlayer(victimId);
        if (killer == null || !killer.isOnline() || victim == null) {
            return;
        }
        try {
            killer.showEntity(plugin, victim);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[LethalFx] could not re-show the victim", t);
        }
    }

    private void sendDeathAnimation(Player killer, Player victim) {
        if (!packetEvents) {
            return;
        }
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(killer,
                    new WrapperPlayServerEntityStatus(victim.getEntityId(), DEATH_STATUS));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[LethalFx] could not send the death packet", t);
        }
    }

    /**
     * The packet cut-off: while a victim is staged as dead for a viewer, their spawn packet never
     * reaches that viewer, so no chunk reload or re-track can bring them back.
     */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (suppressed.isEmpty()
                || event.getPacketType() != PacketType.Play.Server.SPAWN_PLAYER) {
            return;
        }
        Object receiver = event.getPlayer();
        if (!(receiver instanceof Player viewer)) {
            return;
        }
        Set<Integer> ids = suppressed.get(viewer.getUniqueId());
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            if (ids.contains(new WrapperPlayServerSpawnPlayer(event).getEntityId())) {
                event.setCancelled(true);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[LethalFx] spawn filter failed", t);
        }
    }

    /**
     * A player coming back must not stay invisible to someone who killed them earlier. The
     * victim keeps the same entity id as far as this service is concerned, and the suppression
     * is keyed on the viewer, so it has to be lifted explicitly — here and on quit, while the
     * player object is still valid.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID joining = event.getPlayer().getUniqueId();
        for (UUID killerId : new ArrayList<>(hidden.keySet())) {
            Set<UUID> victims = hidden.get(killerId);
            if (victims != null && victims.remove(joining)) {
                if (victims.isEmpty()) {
                    hidden.remove(killerId, victims);
                }
                release(killerId, joining);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        UUID id = leaving.getUniqueId();
        // Their own view of other players dies with their session.
        hidden.remove(id);
        suppressed.remove(id);
        // …but they may have been suppressed FROM somebody still online, and that does not clear
        // itself. Lift it now, while the player object is still valid.
        for (UUID killerId : new ArrayList<>(hidden.keySet())) {
            Set<UUID> victims = hidden.get(killerId);
            if (victims == null || !victims.remove(id)) {
                continue;
            }
            if (victims.isEmpty()) {
                hidden.remove(killerId, victims);
            }
            Set<Integer> ids = suppressed.get(killerId);
            if (ids != null) {
                ids.remove(leaving.getEntityId());
                if (ids.isEmpty()) {
                    suppressed.remove(killerId, ids);
                }
            }
            Player killer = Bukkit.getPlayer(killerId);
            if (killer != null && killer.isOnline()) {
                try {
                    killer.showEntity(plugin, leaving);
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.FINE, "[LethalFx] quit-time re-show failed", t);
                }
            }
        }
    }

    /** Exposed for tests: the pairs currently staged (killer → victims). */
    public List<String> stagedPairs() {
        List<String> out = new ArrayList<>();
        hidden.forEach((killer, victims) -> {
            for (UUID victim : victims) {
                out.add(killer + "->" + victim);
            }
        });
        out.sort(null);
        return out;
    }
}
