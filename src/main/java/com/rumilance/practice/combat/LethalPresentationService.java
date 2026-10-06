package com.rumilance.practice.combat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
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
 * How a kill LOOKS to the killer (user spec 2026-10-06).
 *
 * <p>A losing fighter in this plugin never actually dies: their gear is cleared and they are
 * made invulnerable, because the round (or the end-of-match screen) has to carry on with the
 * player still connected. That is fine from the loser's side, but it made kills feel weightless
 * for the winner — the opponent just stood there holding nothing.</p>
 *
 * <p>So the kill is now <em>staged for the killer only</em>:</p>
 * <ol>
 *   <li>a forged {@code ClientboundEntityEventPacket} carrying the death status is sent to the
 *       killer, whose client plays the full death animation and sound for the victim;</li>
 *   <li>a moment later the victim is hidden from the killer, which stops every remaining packet
 *       about that entity from reaching them.</li>
 * </ol>
 *
 * <p>The victim's own view is deliberately untouched: cleared inventory, invulnerability and
 * everything else behave exactly as before. Net effect — from B's side nothing changed, from
 * A's side B simply died like on any survival server.</p>
 *
 * <p>Without ProtocolLib only step 2 runs (after the same delay the victim is just gone), so
 * the feature degrades instead of failing.</p>
 */
public final class LethalPresentationService implements Listener {

    /** Entity event status that makes the client play the death animation and sound. */
    public static final byte DEATH_STATUS = 3;
    /** How long the killer gets to watch the fall before the victim stops being sent. */
    public static final long VANISH_DELAY_TICKS = 20L;

    private final Plugin plugin;
    /** Null when ProtocolLib is absent or unusable — then only the hiding half runs. */
    private final ProtocolManager protocol;
    /** killer → victims currently hidden from that killer. */
    private final Map<UUID, Set<UUID>> hidden = new ConcurrentHashMap<>();

    public LethalPresentationService(Plugin plugin) {
        this.plugin = plugin;
        ProtocolManager manager = null;
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                manager = ProtocolLibrary.getProtocolManager();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "[LethalFx] ProtocolLib found but unusable - the death animation is off.", t);
            }
        } else {
            plugin.getLogger().info("[LethalFx] ProtocolLib missing - the forged death animation is "
                    + "off; a killed fighter is only hidden from their killer.");
        }
        this.protocol = manager;
    }

    public boolean animationAvailable() {
        return protocol != null;
    }

    /**
     * A killed B: play B's death on A's screen, then stop sending B to A altogether.
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
                killer.hideEntity(plugin, target);
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
        if (victims == null || !victims.remove(victimId)) {
            return;
        }
        if (victims.isEmpty()) {
            hidden.remove(killerId, victims);
        }
        show(killerId, victimId);
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
                    show(id, victimId);
                }
            }
            for (UUID killerId : new ArrayList<>(hidden.keySet())) {
                restore(killerId, id);
            }
        }
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
        if (protocol == null) {
            return;
        }
        try {
            PacketContainer packet = protocol.createPacket(PacketType.Play.Server.ENTITY_STATUS);
            packet.getIntegers().write(0, victim.getEntityId());
            packet.getBytes().write(0, DEATH_STATUS);
            protocol.sendServerPacket(killer, packet);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "[LethalFx] could not send the death packet", t);
        }
    }

    /**
     * A player coming back must not stay invisible to someone who killed them earlier. The
     * victim's side of the pair survives a reconnect (visibility is tracked per viewer), so it is
     * lifted here — and on quit, while the player object is still valid.
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
                show(killerId, joining);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        UUID id = leaving.getUniqueId();
        // Their own hidden set dies with their viewer session.
        hidden.remove(id);
        // …but they may have been hidden FROM somebody who is still online, and that state does
        // not go away on its own. Lift it now, while the player object is still valid.
        for (UUID killerId : new ArrayList<>(hidden.keySet())) {
            Set<UUID> victims = hidden.get(killerId);
            if (victims == null || !victims.remove(id)) {
                continue;
            }
            if (victims.isEmpty()) {
                hidden.remove(killerId, victims);
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
