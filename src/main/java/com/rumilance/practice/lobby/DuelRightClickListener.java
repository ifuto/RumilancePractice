package com.rumilance.practice.lobby;

import com.rumilance.practice.gui.menus.DuelRequestGui;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking another player in the lobby opens the duel request screen for them.
 *
 * <p>Only fires for the lobby/idle state (the clicker must be able to send a duel and must not
 * be in a fight / queue / GUI / FFA / practice room / spectator). Clicking yourself or an
 * unreachable player does nothing. The off-hand interaction is ignored so one physical click
 * does not open the GUI twice.</p>
 */
public final class DuelRightClickListener implements Listener {

    private final PlayerStateManager stateManager;
    private final DuelRequestGui duelRequestGui;
    private final SoundService soundService;
    private final com.rumilance.practice.locale.MessageService messageService;

    public DuelRightClickListener(PlayerStateManager stateManager,
                                  DuelRequestGui duelRequestGui,
                                  SoundService soundService,
                                  com.rumilance.practice.locale.MessageService messageService) {
        this.stateManager = stateManager;
        this.duelRequestGui = duelRequestGui;
        this.soundService = soundService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickPlayer(PlayerInteractEntityEvent event) {
        // Only the main hand fires the GUI; off-hand would double-open on one click.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        if (!target.isOnline()) {
            return;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        // Only in a "hub" state: lobby, idle, menus, queue GUI browsing — never during a fight,
        // queue pairing, FFA, practice room or spectating.
        if (!PracticeGuards.lobbyProtectedStates(state)) {
            return;
        }
        // Both sides must be able to start a solo duel (party members and active fighters are
        // handled with a message; everything else just opens the GUI).
        if (!PracticeGuards.canSendOrAcceptDuel(state)) {
            return;
        }
        event.setCancelled(true);
        try {
            // Unranked request screen by default; the sender can flip to ranked inside the GUI.
            duelRequestGui.openFor(player, target, false);
        } catch (RuntimeException e) {
            if (soundService != null) {
                soundService.play(player, "error");
            }
            if (messageService != null) {
                messageService.send(player, "general.unknown-error");
            }
        }
    }
}
