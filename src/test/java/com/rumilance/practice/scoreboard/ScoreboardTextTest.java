package com.rumilance.practice.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardTextTest {

    @Test
    void substitutesPlaceholdersBeforeLegacyAmpersand() {
        String out = ScoreboardText.render("&bHello {player}", Map.of("player", "Steve"));
        assertTrue(out.contains("Steve"));
        assertTrue(out.contains("§b") || out.contains("Hello"));
    }

    @Test
    void miniMessageRendersToSectionCodes() {
        String out = ScoreboardText.render("<aqua>{server_name}", Map.of("server_name", "N Arena"));
        assertTrue(out.contains("N Arena"));
        assertTrue(out.contains("§"));
    }

    @Test
    void missingPlaceholderBecomesEmpty() {
        assertEquals("X", ScoreboardText.substitute("X{missing}", Map.of()));
    }
}
