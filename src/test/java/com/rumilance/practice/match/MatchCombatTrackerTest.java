package com.rumilance.practice.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchCombatTrackerTest {

    private static final UUID MATCH = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID A = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID B = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID C = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test
    void meleeHitsOnSameTargetCountAsCombo() {
        MatchCombatTracker tracker = new MatchCombatTracker();
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordHit(MATCH, A, B, 1.0d, true);
        assertEquals(3, tracker.forParticipant(MATCH, A).currentCombo());
        assertEquals(3, tracker.forParticipant(MATCH, A).bestCombo());
        assertEquals(0, tracker.forParticipant(MATCH, B).currentCombo());
    }

    @Test
    void takingAHitBreaksYourCombo() {
        MatchCombatTracker tracker = new MatchCombatTracker();
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordHit(MATCH, B, A, 1.0d, false);
        assertEquals(0, tracker.forParticipant(MATCH, A).currentCombo());
        assertEquals(2, tracker.forParticipant(MATCH, A).bestCombo());
        assertEquals(1, tracker.forParticipant(MATCH, B).currentCombo());
    }

    @Test
    void switchingTargetsStartsANewCombo() {
        MatchCombatTracker tracker = new MatchCombatTracker();
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordHit(MATCH, A, C, 1.0d, false);
        assertEquals(1, tracker.forParticipant(MATCH, A).currentCombo());
        assertEquals(2, tracker.forParticipant(MATCH, A).bestCombo());
    }

    @Test
    void projectileHitsDoNotExtendMeleeCombo() {
        MatchCombatTracker tracker = new MatchCombatTracker();
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        tracker.recordProjectileHit(MATCH, A, B, 2.0d);
        tracker.recordHit(MATCH, A, B, 1.0d, false);
        assertEquals(2, tracker.forParticipant(MATCH, A).currentCombo());
        assertEquals(1, tracker.forParticipant(MATCH, A).projectileHits());
    }
}
