package com.rumilance.practice.rank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRankTest {

    @ParameterizedTest
    @CsvSource({
            "NORM, NORM, true",
            "VIP, NORM, true",
            "VIP, VIP, true",
            "VIP, VIP_PLUS, false",
            "VIP_PLUS, VIP, true",
            "ADMIN, VIP_PLUS, true",
            "NORM, ADMIN, false",
    })
    void atLeastOrdering(PlayerRank rank, PlayerRank threshold, boolean expected) {
        assertEquals(expected, rank.atLeast(threshold));
    }

    @ParameterizedTest
    @CsvSource({
            "NORM, false",
            "VIP, true",
            "VIP_PLUS, true",
            "ADMIN, true",
    })
    void isVipOrAboveMatrix(PlayerRank rank, boolean expected) {
        assertEquals(expected, rank.isVipOrAbove());
    }

    @ParameterizedTest
    @CsvSource({
            "NORM, false",
            "VIP, false",
            "VIP_PLUS, true",
            "ADMIN, true",
    })
    void isVipPlusOrAboveMatrix(PlayerRank rank, boolean expected) {
        assertEquals(expected, rank.isVipPlusOrAbove());
    }

    @ParameterizedTest
    @CsvSource({
            "vip, VIP",
            "VIP, VIP",
            "vip+, VIP_PLUS",
            "vip_plus, VIP_PLUS",
            "vipplus, VIP_PLUS",
            "svip, VIP_PLUS",
            "admin, ADMIN",
            "owner, ADMIN",
            "norm, NORM",
            "default, NORM",
            "mem, NORM",
    })
    void parseAcceptsCommonAliases(String raw, PlayerRank expected) {
        assertEquals(expected, PlayerRank.parse(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown", "moderator"})
    void parseReturnsNullOrNormForInvalid(String raw) {
        if (raw == null || raw.isBlank()) {
            assertEquals(PlayerRank.NORM, PlayerRank.parse(raw));
        } else {
            assertNull(PlayerRank.parse(raw));
        }
    }

    @Test
    void parseNormalizesFullWidthPlus() {
        assertEquals(PlayerRank.VIP_PLUS, PlayerRank.parse("vip＋"));
    }

    @ParameterizedTest
    @CsvSource({
            "NORM, norm",
            "VIP, vip",
            "VIP_PLUS, vip+",
            "ADMIN, admin",
    })
    void storageKeyRoundTrip(PlayerRank rank, String key) {
        assertEquals(key, rank.storageKey());
    }

    @ParameterizedTest
    @CsvSource({
            "NORM, NORM",
            "VIP, VIP",
            "VIP_PLUS, VIP+",
            "ADMIN, OWNER",
    })
    void displayLabelMatrix(PlayerRank rank, String label) {
        assertEquals(label, rank.displayLabel());
    }
}
