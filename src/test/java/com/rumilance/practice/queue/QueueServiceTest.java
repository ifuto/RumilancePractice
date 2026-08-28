package com.rumilance.practice.queue;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.state.MatchMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueServiceTest {

    @Test
    void twoUnrankedPlayersMatchImmediately() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(queue.join(a, "nodebuff", MatchMode.UNRANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA));
        assertTrue(queue.join(b, "nodebuff", MatchMode.UNRANKED, 1400, "2.2.2.2", PlayerPlatform.JAVA));
        List<QueueService.MatchPair> pairs = queue.pollMatches(false, true, Instant.now());
        assertEquals(1, pairs.size());
        assertEquals(0, queue.totalWaiting());
    }

    @Test
    void lonelyRankedPairIgnoresEloAndRecentOpponent() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        queue.join(a, "axe", MatchMode.RANKED, 800, "1.1.1.1", PlayerPlatform.JAVA);
        queue.join(b, "axe", MatchMode.RANKED, 2000, "2.2.2.2", PlayerPlatform.JAVA);
        List<QueueService.MatchPair> first = queue.pollMatches(false, true, Instant.now());
        assertEquals(1, first.size());

        queue.join(a, "axe", MatchMode.RANKED, 800, "1.1.1.1", PlayerPlatform.JAVA);
        queue.join(b, "axe", MatchMode.RANKED, 2000, "2.2.2.2", PlayerPlatform.JAVA);
        List<QueueService.MatchPair> rematch = queue.pollMatches(false, true, Instant.now());
        assertEquals(1, rematch.size(), "two-person queue must rematch even as recent opponents");
    }

    @Test
    void threeRankedPlayersRespectEloUntilRangeGrows() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        queue.join(a, "nethop", MatchMode.RANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA);
        queue.join(b, "nethop", MatchMode.RANKED, 1000, "2.2.2.2", PlayerPlatform.JAVA);
        queue.join(c, "nethop", MatchMode.RANKED, 1800, "3.3.3.3", PlayerPlatform.JAVA);
        List<QueueService.MatchPair> pairs = queue.pollMatches(false, false, Instant.now());
        assertEquals(1, pairs.size());
        assertEquals(1, queue.totalWaiting());
    }

    @Test
    void bedrockAndJavaQueuesStaySeparate() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        UUID java = UUID.randomUUID();
        UUID bedrock = UUID.randomUUID();
        assertTrue(queue.join(java, "nodebuff", MatchMode.UNRANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA));
        assertTrue(queue.join(bedrock, "nodebuff", MatchMode.UNRANKED, 1000, "2.2.2.2", PlayerPlatform.BEDROCK));
        List<QueueService.MatchPair> pairs = queue.pollMatches(false, true, Instant.now());
        assertEquals(0, pairs.size());
        assertEquals(2, queue.totalWaiting());
    }

    @Test
    void ffaModeCannotJoinQueue() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        assertFalse(queue.join(UUID.randomUUID(), "nodebuff", MatchMode.FFA, 1000, "1.1.1.1", PlayerPlatform.JAVA));
    }

    @Test
    void doubleJoinRejected() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        UUID player = UUID.randomUUID();
        assertTrue(queue.join(player, "nodebuff", MatchMode.UNRANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA));
        assertFalse(queue.join(player, "nodebuff", MatchMode.UNRANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA));
    }

    @Test
    void sameIpBlockedWhenConfigured() {
        QueueService queue = new QueueService(settings(75, 25, 15));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        queue.join(a, "nodebuff", MatchMode.UNRANKED, 1000, "9.9.9.9", PlayerPlatform.JAVA);
        queue.join(b, "nodebuff", MatchMode.UNRANKED, 1000, "9.9.9.9", PlayerPlatform.JAVA);
        List<QueueService.MatchPair> pairs = queue.pollMatches(true, false, Instant.now());
        assertEquals(0, pairs.size());
        assertEquals(2, queue.totalWaiting());
    }

    @ParameterizedTest
    @CsvSource({
            "RANKED, nodebuff, java",
            "UNRANKED, axe, bedrock",
    })
    void queueKeyIncludesModeKitAndPlatform(String modeName, String kit, String platformToken) {
        MatchMode mode = MatchMode.valueOf(modeName);
        PlayerPlatform platform = platformToken.equals("java") ? PlayerPlatform.JAVA : PlayerPlatform.BEDROCK;
        String key = QueueService.queueKey(mode, kit, platform);
        assertTrue(key.contains(mode.name()));
        assertTrue(key.contains(kit.toLowerCase()));
        assertTrue(key.endsWith(platform.queueToken()));
    }

    @Test
    void nullPlatformDefaultsToJavaInQueueKey() {
        String key = QueueService.queueKey(MatchMode.UNRANKED, "NodeBuff", null);
        assertTrue(key.endsWith("java"));
    }

    private static PluginSettings settings(int initial, int growth, int interval) {
        return new PluginSettings(
                "en_us", false, 1, 4, 2, 60L,
                5, 0, 30, true, 5,
                60, initial, growth, interval, 0,
                1000, 20, 64, 32, 26, 0.10d,
                true, true, "world", false,
                true, 30, true, true, 20, true,
                "N.", "play.example.com", 40, false);
    }
}
