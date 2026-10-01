package com.rumilance.practice.alt;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds the alt-detection pipeline: login IP + cadence, and arm-swing intervals while the
 * player is in real combat (a FIGHTING state or an FFA arena). Swing timing is the
 * server-side analogue of the click-cadence feature used in mouse-dynamics research —
 * measured only in combat so menu clicks and lobby noise never pollute the distribution.
 */
public final class AltSignalListener implements Listener {

    private final AltDetectionService altDetection;
    private final PlayerStateManager stateManager;
    private final FfaService ffaService;

    private final Map<UUID, Long> lastSwingMs = new ConcurrentHashMap<>();

    public AltSignalListener(AltDetectionService altDetection, PlayerStateManager stateManager,
                             FfaService ffaService) {
        this.altDetection = altDetection;
        this.stateManager = stateManager;
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        altDetection.onLogin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        altDetection.onQuit(id);
        lastSwingMs.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        PlayerState state = stateManager.getState(id);
        boolean inCombat = state == PlayerState.FIGHTING
                || (ffaService != null && ffaService.isInFfa(id));
        if (!inCombat) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSwingMs.put(id, now);
        altDetection.onSwing(id, now, last == null ? -1 : last);
    }
}
