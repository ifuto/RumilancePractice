package com.rumilance.practice.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerPlatformTest {

    @ParameterizedTest
    @CsvSource({
            ".Steve, BEDROCK",
            ".Player123, BEDROCK",
            "JavaPlayer, JAVA",
            "NotDot, JAVA",
            "Steve, JAVA",
    })
    void ofNameMatrix(String name, PlayerPlatform expected) {
        assertEquals(expected, PlayerPlatform.ofName(name));
    }

    @Test
    void ofNameNullIsJava() {
        assertEquals(PlayerPlatform.JAVA, PlayerPlatform.ofName(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {".bedrock", ".x"})
    void bedrockQueueToken(String ignored) {
        assertEquals("bedrock", PlayerPlatform.BEDROCK.queueToken());
    }

    @Test
    void javaQueueToken() {
        assertEquals("java", PlayerPlatform.JAVA.queueToken());
    }

    @Test
    void ofRequiresNonNullPlayer() {
        assertThrows(NullPointerException.class, () -> PlayerPlatform.of(null));
    }
}
