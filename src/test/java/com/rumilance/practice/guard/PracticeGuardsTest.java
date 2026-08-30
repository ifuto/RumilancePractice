package com.rumilance.practice.guard;

import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeGuardsTest {

    // --- Rank matrix ---

    @ParameterizedTest(name = "vipOrAbove rank={0} vip={1} vip+={2} admin={3} => {4}")
    @CsvSource({
            "NORM, false, false, false, false",
            "NORM, true,  false, false, true",
            "NORM, false, true,  false, true",
            "NORM, false, false, true,  true",
            "VIP,  false, false, false, true",
            "VIP_PLUS, false, false, false, true",
            "ADMIN, false, false, false, true",
    })
    void effectiveVipOrAboveMatrix(
            PlayerRank rank,
            boolean permVip,
            boolean permVipPlus,
            boolean permAdmin,
            boolean expected
    ) {
        assertEquals(expected, PracticeGuards.effectiveVipOrAbove(rank, permVip, permVipPlus, permAdmin));
    }

    @Test
    void effectiveVipOrAboveNullRankUsesNorm() {
        assertFalse(PracticeGuards.effectiveVipOrAbove(null, false, false, false));
        assertTrue(PracticeGuards.effectiveVipOrAbove(null, true, false, false));
    }

    @ParameterizedTest(name = "vipPlusOrAbove rank={0} vip+={1} admin={2} => {3}")
    @CsvSource({
            "NORM,     false, false, false",
            "NORM,     true,  false, true",
            "NORM,     false, true,  true",
            "VIP,      false, false, false",
            "VIP_PLUS, false, false, true",
            "ADMIN,    false, false, true",
    })
    void effectiveVipPlusOrAboveMatrix(
            PlayerRank rank,
            boolean permVipPlus,
            boolean permAdmin,
            boolean expected
    ) {
        assertEquals(expected, PracticeGuards.effectiveVipPlusOrAbove(rank, permVipPlus, permAdmin));
    }

    @ParameterizedTest
    @CsvSource({
            "NORM,  false, false",
            "NORM,  true,  true",
            "ADMIN, false, true",
            "VIP,   false, false",
    })
    void effectiveAdminMatrix(PlayerRank rank, boolean permAdmin, boolean expected) {
        assertEquals(expected, PracticeGuards.effectiveAdmin(rank, permAdmin));
    }

    // --- Trim editor state ---

    @ParameterizedTest
    @EnumSource(value = PlayerState.class, names = {"LOBBY", "OPENING_GUI", "IDLE", "EDITING_KIT"})
    void trimEditorAllowedInSafeStates(PlayerState state) {
        assertTrue(PracticeGuards.trimEditorAllowedInState(state));
    }

    @ParameterizedTest
    @EnumSource(
            value = PlayerState.class,
            names = {"LOBBY", "OPENING_GUI", "IDLE", "EDITING_KIT"},
            mode = EnumSource.Mode.EXCLUDE
    )
    void trimEditorBlockedInCombatStates(PlayerState state) {
        assertFalse(PracticeGuards.trimEditorAllowedInState(state));
    }

    @Test
    void trimEditorBlockedWhenStateNull() {
        assertFalse(PracticeGuards.trimEditorAllowedInState(null));
    }

    // --- Armor material ---

    @ParameterizedTest
    @CsvSource({
            "DIAMOND_HELMET, true",
            "NETHERITE_CHESTPLATE, true",
            "IRON_LEGGINGS, true",
            "GOLDEN_BOOTS, true",
            "DIAMOND_SWORD, false",
            "BOW, false",
            "SHIELD, false",
            "TURTLE_HELMET, true",
    })
    void isTrimmableArmorMaterialMatrix(String material, boolean expected) {
        assertEquals(expected, PracticeGuards.isTrimmableArmorMaterial(material));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void isTrimmableArmorMaterialRejectsBlank(String blank) {
        assertFalse(PracticeGuards.isTrimmableArmorMaterial(blank));
    }

    // --- Trim VIP+ restrictions ---

    @ParameterizedTest(name = "VIP material {0} allowed={1}")
    @CsvSource({
            "copper, true",
            "iron, true",
            "emerald, true",
            "lapis, true",
            "netherite, true",
            "redstone, true",
            "quartz, false",
            "gold, false",
            "diamond, false",
            "amethyst, false",
    })
    void trimMaterialVipMatrix(String key, boolean allowedForVip) {
        assertEquals(allowedForVip, PracticeGuards.trimMaterialAllowed(false, key));
        assertTrue(PracticeGuards.trimMaterialAllowed(true, key));
    }

    @ParameterizedTest(name = "VIP pattern {0} allowed={1}")
    @CsvSource({
            "sentry, true",
            "dune, true",
            "coast, true",
            "silence, false",
            "snout, false",
    })
    void trimPatternVipMatrix(String key, boolean allowedForVip) {
        assertEquals(allowedForVip, PracticeGuards.trimPatternAllowed(false, key));
        assertTrue(PracticeGuards.trimPatternAllowed(true, key));
    }

    @ParameterizedTest
    @CsvSource({
            "false, copper, sentry, true",
            "false, gold, sentry, false",
            "false, copper, silence, false",
            "false, gold, snout, false",
            "true,  gold, snout, true",
            "true,  amethyst, silence, true",
    })
    void trimSelectionCombinedMatrix(
            boolean vipPlus,
            String material,
            String pattern,
            boolean expected
    ) {
        assertEquals(expected, PracticeGuards.trimSelectionAllowed(vipPlus, material, pattern));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void trimKeysRejectBlank(String blank) {
        assertFalse(PracticeGuards.trimMaterialAllowed(true, blank));
        assertFalse(PracticeGuards.trimPatternAllowed(true, blank));
        assertFalse(PracticeGuards.trimSelectionAllowed(true, blank, "sentry"));
        assertFalse(PracticeGuards.trimSelectionAllowed(true, "copper", blank));
    }

    // --- Queue join ---

    @ParameterizedTest
    @CsvSource({
            "RANKED, false, true",
            "UNRANKED, false, true",
            "FFA, false, false",
            "RANKED, true, false",
            "UNRANKED, true, false",
    })
    void canEnterQueueMatrix(MatchMode mode, boolean alreadyQueued, boolean expected) {
        assertEquals(expected, PracticeGuards.canEnterQueue(mode, alreadyQueued));
    }

    @Test
    void canEnterQueueRejectsNullMode() {
        assertFalse(PracticeGuards.canEnterQueue(null, false));
    }

    // --- Queue pool / pairing ---

    static Stream<Arguments> queuePoolCases() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Instant now = Instant.now();
        QueueService.QueueEntry javaA = entry(a, "nodebuff", MatchMode.UNRANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry javaB = entry(b, "nodebuff", MatchMode.UNRANKED, 1000, "2.2.2.2", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry bedrockB = entry(b, "nodebuff", MatchMode.UNRANKED, 1000, "2.2.2.2", PlayerPlatform.BEDROCK, now);
        QueueService.QueueEntry otherKit = entry(b, "axe", MatchMode.UNRANKED, 1000, "2.2.2.2", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry ranked = entry(b, "nodebuff", MatchMode.RANKED, 1000, "2.2.2.2", PlayerPlatform.JAVA, now);
        return Stream.of(
                Arguments.of(javaA, javaB, true),
                Arguments.of(javaA, bedrockB, false),
                Arguments.of(javaA, otherKit, false),
                Arguments.of(javaA, ranked, false),
                Arguments.of(null, javaB, false),
                Arguments.of(javaA, null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("queuePoolCases")
    void queueEntriesSamePoolMatrix(
            QueueService.QueueEntry a,
            QueueService.QueueEntry b,
            boolean expected
    ) {
        assertEquals(expected, PracticeGuards.queueEntriesSamePool(a, b));
    }

    static Stream<Arguments> pairCases() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        Instant now = Instant.now();
        QueueService.QueueEntry a = entry(p1, "nodebuff", MatchMode.UNRANKED, 1000, "1.1.1.1", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry b = entry(p2, "nodebuff", MatchMode.UNRANKED, 1400, "2.2.2.2", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry sameIp = entry(p2, "nodebuff", MatchMode.UNRANKED, 1400, "1.1.1.1", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry rankedLow = entry(p1, "axe", MatchMode.RANKED, 800, "1.1.1.1", PlayerPlatform.JAVA, now);
        QueueService.QueueEntry rankedHigh = entry(p2, "axe", MatchMode.RANKED, 2000, "2.2.2.2", PlayerPlatform.JAVA, now);
        return Stream.of(
                Arguments.of(a, b, false, false, false, 75, null, null, true),
                Arguments.of(a, sameIp, true, false, false, 75, null, null, false),
                Arguments.of(a, b, false, true, false, 75, p2, null, false),
                Arguments.of(a, b, false, true, false, 75, null, p1, false),
                Arguments.of(rankedLow, rankedHigh, false, false, false, 75, null, null, false),
                Arguments.of(rankedLow, rankedHigh, false, false, true, 75, null, null, true),
                Arguments.of(rankedLow, rankedHigh, false, false, false, 1500, null, null, true),
                Arguments.of(a, a, false, false, false, 75, null, null, false),
                Arguments.of(rankedLow, rankedHigh, false, false, false, -1, null, null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("pairCases")
    void canPairInQueueMatrix(
            QueueService.QueueEntry a,
            QueueService.QueueEntry b,
            boolean blockSameIp,
            boolean avoidRecent,
            boolean ignoreElo,
            int eloRange,
            UUID recentA,
            UUID recentB,
            boolean expected
    ) {
        assertEquals(
                expected,
                PracticeGuards.canPairInQueue(a, b, blockSameIp, avoidRecent, ignoreElo, eloRange, recentA, recentB)
        );
    }

    // --- Platform ---

    @ParameterizedTest
    @CsvSource({
            ".Steve, true",
            ".BedrockPlayer, true",
            "JavaPlayer, false",
            "NotBedrock, false",
    })
    void isBedrockNameMatrix(String name, boolean expected) {
        assertEquals(expected, PracticeGuards.isBedrockName(name));
    }

    @Test
    void isBedrockNameNullIsFalse() {
        assertFalse(PracticeGuards.isBedrockName(null));
    }

    // --- Match / border / duel ---

    @ParameterizedTest
    @EnumSource(value = PlayerState.class, names = {
            "LOBBY", "OPENING_GUI", "QUEUED_RANKED", "QUEUED_UNRANKED", "REQUESTING_DUEL", "IDLE", "ENDING"
    })
    void canSendOrAcceptDuelInLobbyStates(PlayerState state) {
        assertTrue(PracticeGuards.canSendOrAcceptDuel(state));
    }

    @ParameterizedTest
    @EnumSource(value = PlayerState.class, names = {
            "FIGHTING", "COUNTDOWN", "PREPARING_MATCH", "SPECTATING", "FFA",
            "PRACTICE_WAIT", "PRACTICE_ACTIVE", "EDITING_KIT"
    })
    void canSendOrAcceptDuelBlockedInMatchStates(PlayerState state) {
        assertFalse(PracticeGuards.canSendOrAcceptDuel(state));
    }

    @Test
    void canSendOrAcceptDuelNullIsFalse() {
        assertFalse(PracticeGuards.canSendOrAcceptDuel(null));
    }

    @ParameterizedTest
    @EnumSource(value = MatchState.class, names = {"ACTIVE", "COUNTDOWN", "ENDING"})
    void arenaBoundsActiveDuringPlayPhases(MatchState state) {
        assertTrue(PracticeGuards.arenaBoundsActive(state));
    }

    @ParameterizedTest
    @EnumSource(
            value = MatchState.class,
            names = {"ACTIVE", "COUNTDOWN", "ENDING"},
            mode = EnumSource.Mode.EXCLUDE
    )
    void arenaBoundsInactiveOutsidePlayPhases(MatchState state) {
        assertFalse(PracticeGuards.arenaBoundsActive(state));
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, false, false",
            "true,  true,  false, true",
            "true,  true,  true,  false",
            "true,  false, false, false",
            "false, true,  false, false",
    })
    void shouldBlockTeammateDamageMatrix(
            boolean teamMatch,
            boolean teammates,
            boolean friendlyFire,
            boolean blocked
    ) {
        assertEquals(blocked, PracticeGuards.shouldBlockTeammateDamage(teamMatch, teammates, friendlyFire));
    }

    @Test
    void matchBorderSizeUsesLargerHorizontalDimensionPlusMargin() {
        assertEquals(52.0d, PracticeGuards.matchBorderSize(0, 49, 0, 10), 0.001d);
        assertEquals(22.0d, PracticeGuards.matchBorderSize(0, 9, 0, 19), 0.001d);
    }

    @Test
    void matchBorderCenterUsesBlockCenters() {
        assertEquals(25.0d, PracticeGuards.matchBorderCenterX(0, 49), 0.001d);
        assertEquals(5.5d, PracticeGuards.matchBorderCenterZ(0, 10), 0.001d);
    }

    // --- Kit layout ---

    @Test
    void hasValidEditorLayoutRequires41Slots() {
        assertFalse(PracticeGuards.hasValidEditorLayout(null));
        assertFalse(PracticeGuards.hasValidEditorLayout(new ItemStack[40]));
        assertTrue(PracticeGuards.hasValidEditorLayout(new ItemStack[41]));
    }

    @Test
    void kitLayoutUnchangedRejectsNullOrShort() {
        ItemStack[] baseline = new ItemStack[41];
        assertFalse(PracticeGuards.kitLayoutUnchanged(null, baseline));
        assertFalse(PracticeGuards.kitLayoutUnchanged(baseline, null));
        assertFalse(PracticeGuards.kitLayoutUnchanged(baseline, new ItemStack[10]));
    }

    @Test
    void kitLayoutUnchangedAcceptsEmptyMatchingLayouts() {
        ItemStack[] baseline = new ItemStack[41];
        ItemStack[] edited = new ItemStack[41];
        assertTrue(PracticeGuards.kitLayoutUnchanged(baseline, edited));
    }

    private static QueueService.QueueEntry entry(
            UUID id,
            String kit,
            MatchMode mode,
            int elo,
            String ip,
            PlayerPlatform platform,
            Instant joined
    ) {
        return new QueueService.QueueEntry(id, kit, mode, elo, joined, ip, platform);
    }
}
