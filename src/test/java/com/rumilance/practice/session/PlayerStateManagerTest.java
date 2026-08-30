package com.rumilance.practice.session;

import com.rumilance.practice.state.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateManagerTest {

    private PlayerStateManager manager;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        manager = new PlayerStateManager();
        playerId = UUID.randomUUID();
    }

    @Test
    void unknownPlayerDefaultsToIdle() {
        assertEquals(PlayerState.IDLE, manager.getState(UUID.randomUUID()));
    }

    @Test
    void initializePlacesPlayerDirectlyInLobby() {
        assertEquals(PlayerState.LOBBY, manager.initialize(playerId));
        assertEquals(PlayerState.LOBBY, manager.getState(playerId));
    }

    @Test
    void legalTransitionSucceeds() {
        manager.initialize(playerId);
        assertEquals(PlayerState.QUEUED_RANKED, manager.transition(playerId, PlayerState.QUEUED_RANKED));
        assertEquals(PlayerState.QUEUED_RANKED, manager.getState(playerId));
    }

    @Test
    void fullRankedMatchLifecycleSucceeds() {
        manager.initialize(playerId);
        manager.transition(playerId, PlayerState.OPENING_GUI);
        manager.transition(playerId, PlayerState.QUEUED_RANKED);
        manager.transition(playerId, PlayerState.PREPARING_MATCH);
        manager.transition(playerId, PlayerState.COUNTDOWN);
        manager.transition(playerId, PlayerState.FIGHTING);
        manager.transition(playerId, PlayerState.ENDING);
        manager.transition(playerId, PlayerState.LOBBY);
        assertEquals(PlayerState.LOBBY, manager.getState(playerId));
    }

    @Test
    void rematchLifecycleReenterPreparingMatchFromEnding() {
        manager.initialize(playerId);
        manager.transition(playerId, PlayerState.QUEUED_UNRANKED);
        manager.transition(playerId, PlayerState.PREPARING_MATCH);
        manager.transition(playerId, PlayerState.COUNTDOWN);
        manager.transition(playerId, PlayerState.FIGHTING);
        manager.transition(playerId, PlayerState.ENDING);
        assertEquals(PlayerState.PREPARING_MATCH, manager.transition(playerId, PlayerState.PREPARING_MATCH));
    }

    @Test
    void illegalTransitionFromIdleDirectlyToFightingThrows() {
        manager.initialize(playerId);

        IllegalStateTransitionException exception = assertThrows(
                IllegalStateTransitionException.class,
                () -> manager.transition(playerId, PlayerState.FIGHTING)
        );

        assertEquals(playerId, exception.playerId());
        assertEquals(PlayerState.LOBBY, exception.from());
        assertEquals(PlayerState.FIGHTING, exception.to());
        // State must remain unchanged after a rejected transition.
        assertEquals(PlayerState.LOBBY, manager.getState(playerId));
    }

    @Test
    void illegalTransitionFromQueueDirectlyToSpectatingThrows() {
        manager.initialize(playerId);
        manager.transition(playerId, PlayerState.QUEUED_RANKED);

        assertThrows(IllegalStateTransitionException.class,
                () -> manager.transition(playerId, PlayerState.SPECTATING));
        assertEquals(PlayerState.QUEUED_RANKED, manager.getState(playerId));
    }

    @Test
    void acceptingDuelWhileFightingIsImpossibleBecauseFightingOnlyLeadsToEnding() {
        manager.initialize(playerId);
        manager.transition(playerId, PlayerState.REQUESTING_DUEL);
        manager.transition(playerId, PlayerState.PREPARING_MATCH);
        manager.transition(playerId, PlayerState.COUNTDOWN);
        manager.transition(playerId, PlayerState.FIGHTING);

        // A second duel request/queue join cannot be represented while FIGHTING.
        assertFalse(PlayerStateManager.canTransition(PlayerState.FIGHTING, PlayerState.REQUESTING_DUEL));
        assertFalse(PlayerStateManager.canTransition(PlayerState.FIGHTING, PlayerState.QUEUED_RANKED));
        assertEquals(PlayerState.ENDING, manager.transition(playerId, PlayerState.ENDING));
    }

    @Test
    void transitionToSameStateIsIllegal() {
        manager.initialize(playerId);
        assertThrows(IllegalStateTransitionException.class,
                () -> manager.transition(playerId, PlayerState.LOBBY));
    }

    @Test
    void canTransitionReflectsGraphWithoutMutatingState() {
        assertTrue(PlayerStateManager.canTransition(PlayerState.IDLE, PlayerState.LOBBY));
        assertFalse(PlayerStateManager.canTransition(PlayerState.IDLE, PlayerState.FIGHTING));
        assertFalse(PlayerStateManager.canTransition(PlayerState.LOBBY, PlayerState.LOBBY));
    }

    @Test
    void removeClearsTrackedState() {
        manager.initialize(playerId);
        manager.remove(playerId);
        assertEquals(PlayerState.IDLE, manager.getState(playerId));
    }

    @Test
    void resetToLobbyForcesStateRegardlessOfGraph() {
        manager.initialize(playerId);
        manager.transition(playerId, PlayerState.QUEUED_RANKED);
        manager.transition(playerId, PlayerState.PREPARING_MATCH);
        manager.transition(playerId, PlayerState.COUNTDOWN);
        manager.transition(playerId, PlayerState.FIGHTING);

        // Simulates a crash-recovery/admin cleanup forcing a stuck FIGHTING player back to LOBBY,
        // which would otherwise be an illegal transition.
        assertFalse(PlayerStateManager.canTransition(PlayerState.FIGHTING, PlayerState.LOBBY));
        assertEquals(PlayerState.LOBBY, manager.resetToLobby(playerId));
        assertEquals(PlayerState.LOBBY, manager.getState(playerId));
    }

    @Test
    void practiceWaitActiveRoundTripFromLobby() {
        manager.initialize(playerId);
        assertEquals(PlayerState.PRACTICE_WAIT, manager.transition(playerId, PlayerState.PRACTICE_WAIT));
        assertEquals(PlayerState.PRACTICE_ACTIVE, manager.transition(playerId, PlayerState.PRACTICE_ACTIVE));
        assertEquals(PlayerState.PRACTICE_WAIT, manager.transition(playerId, PlayerState.PRACTICE_WAIT));
        assertEquals(PlayerState.LOBBY, manager.transition(playerId, PlayerState.LOBBY));
    }

    @Test
    void practiceActiveDirectFromLobbyForMace() {
        manager.initialize(playerId);
        assertTrue(PlayerStateManager.canTransition(PlayerState.LOBBY, PlayerState.PRACTICE_ACTIVE));
        assertEquals(PlayerState.PRACTICE_ACTIVE, manager.transition(playerId, PlayerState.PRACTICE_ACTIVE));
        assertEquals(PlayerState.LOBBY, manager.transition(playerId, PlayerState.LOBBY));
    }

    @Test
    void openingGuiCanEnterPractice() {
        manager.initialize(playerId);
        manager.transition(playerId, PlayerState.OPENING_GUI);
        assertEquals(PlayerState.PRACTICE_WAIT, manager.transition(playerId, PlayerState.PRACTICE_WAIT));
    }
}
