package com.rumilance.practice.replay;

import com.rumilance.practice.match.MatchActionRecorder.Frame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Playhead bounds of {@link ReplaySession}. The session must never report an empty playback
 * window for a recording that has frames — a zero-length window used to end the replay on the
 * very first tick (the "replay ends the moment it starts" bug).
 */
class ReplaySessionTest {

    private static Frame frame(long tick, double x) {
        return new Frame(tick, UUID.randomUUID(), x, 64.0, 0.5, 0f, 0f, 0, 0, 0, 20.0, false, true);
    }

    @Test
    void normalRecordingKeepsItsFullSpan() {
        ReplaySession.Avatar avatar = new ReplaySession.Avatar(UUID.randomUUID(), "Steve",
                List.of(frame(1000, 0), frame(1005, 1), frame(2200, 2)));
        ReplaySession session = new ReplaySession(UUID.randomUUID(), UUID.randomUUID(), "world",
                List.of(avatar));
        assertEquals(1000.0, session.startTick);
        assertEquals(2200.0, session.endTick);
        assertEquals(0.0, session.progress(), 1e-9);
    }

    @Test
    void singleFrameRecordingDoesNotEndImmediately() {
        ReplaySession.Avatar avatar = new ReplaySession.Avatar(UUID.randomUUID(), "Steve",
                List.of(frame(5000, 0)));
        ReplaySession session = new ReplaySession(UUID.randomUUID(), UUID.randomUUID(), "world",
                List.of(avatar));
        // At least one second of playback window, even for a one-frame forfeit recording.
        assertTrue(session.endTick - session.startTick >= 20.0,
                "degenerate recordings must not end on the first tick");
    }

    @Test
    void avatarsWithEmptyFramesAreIgnoredForBounds() {
        ReplaySession.Avatar empty = new ReplaySession.Avatar(UUID.randomUUID(), "Ghost", List.of());
        ReplaySession.Avatar real = new ReplaySession.Avatar(UUID.randomUUID(), "Steve",
                List.of(frame(100, 0), frame(700, 3)));
        ReplaySession session = new ReplaySession(UUID.randomUUID(), UUID.randomUUID(), "world",
                List.of(empty, real));
        assertEquals(100.0, session.startTick);
        assertEquals(700.0, session.endTick);
    }

    @Test
    void seekAndRestartStayInsideBounds() {
        ReplaySession.Avatar avatar = new ReplaySession.Avatar(UUID.randomUUID(), "Steve",
                List.of(frame(100, 0), frame(300, 3)));
        ReplaySession session = new ReplaySession(UUID.randomUUID(), UUID.randomUUID(), "world",
                List.of(avatar));
        session.seek(+1_000_000);
        assertEquals(session.endTick, session.playheadTick, 1e-9);
        session.restart();
        assertEquals(session.startTick, session.playheadTick, 1e-9);
    }
}
