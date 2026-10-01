package com.rumilance.practice.kb;

import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.session.MatchSession;

import java.util.UUID;
import java.util.function.Function;

/**
 * Part of the {@code KnockbackTuningListener} wiring: answers the VICTIM's current-match
 * KB profile as a concrete (h, v) factor, or {@code null} for "no profile layer" (fall
 * back to the classic kit &gt; cause &gt; global precedence untouched).
 * The session stores the profile NAME resolved once at match start ({@code null} = the
 * fight runs the plain knockback.json rules), so this hot path is one map lookup.
 */
public final class KbProfileRuntime {

    private KbProfileRuntime() {
    }

    public static Function<UUID, double[]> resolver(MatchRegistry matchRegistry,
                                                    KbProfileService profiles) {
        return playerId -> {
            if (playerId == null || matchRegistry == null || profiles == null) {
                return null;
            }
            MatchSession session = matchRegistry.byPlayer(playerId).orElse(null);
            if (session == null) {
                return null;
            }
            String name = session.kbProfile();
            if (name == null) {
                return null;
            }
            return profiles.find(name).orElse(null);
        };
    }
}
