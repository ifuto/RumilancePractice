package com.rumilance.practice.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 参照 QuantumBOT の実測値に当プラグインのラダーを錠する(数値完全一致ループの回帰防止)。
 *
 * <p>参照値の出典は 2 つ:</p>
 * <ol>
 *   <li>データパック {@code quantum:difficulty/1..6} の生値
 *       (crystal_cd 6/6/6/3/2/3t, anchor_cd 5/4/4/3/1/1t, charge_cd 5/4/3/2/2/2t,
 *        explosion_cd 同, totem_cd 40/31/21/10/0/1t)</li>
 *   <li>Fabric 実サーバーでの <b>実測キャプチャ</b>({@code docs/parity/*.log.gz},
 *       {@code tools/parity_report.py} で抽出)。通常戦闘・difficulty 2 で
 *       アンカー設置→チャージが <b>4t</b>(=200ms)、サイクル最小 <b>12t</b>(=600ms)、
 *       クリスタル 1 個の寿命 1t を確認済み。</li>
 * </ol>
 *
 * <p>このテストが落ちる = 参照BOTとの数値一致が壊れた、という意味。値は必ず
 * {@code docs/bot-combat-parity.md} と突き合わせて更新する。</p>
 */
class BotLadderParityTest {

    /** 参照 difficulty 2 (INTERMEDIATE) の anchor_cd = 4t。実測も 4t×45/54, 4t×49/49。 */
    private static final long INTERMEDIATE_ANCHOR_CD_MS = 200L;

    @Test
    @DisplayName("クリスタル設置間隔 = crystal_cd rung (6/6/6/3/2/3t)")
    void crystalPlaceCadenceMatchesReference() {
        assertEquals(300L, PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(300L, PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(300L, PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(150L, PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(100L, PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(150L, PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
    }

    @Test
    @DisplayName("アンカー 設置→チャージ = anchor_cd rung (5/4/4/3/1/1t)")
    void anchorPlaceCdMatchesReference() {
        assertEquals(250L, PracticeService.anchorPlaceCdMs(BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(INTERMEDIATE_ANCHOR_CD_MS,
                PracticeService.anchorPlaceCdMs(BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(200L, PracticeService.anchorPlaceCdMs(BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(150L, PracticeService.anchorPlaceCdMs(BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(50L, PracticeService.anchorPlaceCdMs(BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(50L, PracticeService.anchorPlaceCdMs(BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
    }

    @Test
    @DisplayName("アンカー チャージ→爆発 = charge_cd rung (5/4/3/2/2/2t)")
    void anchorChargeCdMatchesReference() {
        assertEquals(250L, PracticeService.anchorChargeCdMs(BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(200L, PracticeService.anchorChargeCdMs(BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(150L, PracticeService.anchorChargeCdMs(BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(100L, PracticeService.anchorChargeCdMs(BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(100L, PracticeService.anchorChargeCdMs(BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(100L, PracticeService.anchorChargeCdMs(BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
    }

    @Test
    @DisplayName("アンカー 爆発後クールダウン = explosion_cd rung (5/4/3/2/2/2t)")
    void anchorExplodeCdMatchesReference() {
        assertEquals(250L, PracticeService.anchorExplodeCdMs(BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(200L, PracticeService.anchorExplodeCdMs(BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(150L, PracticeService.anchorExplodeCdMs(BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(100L, PracticeService.anchorExplodeCdMs(BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(100L, PracticeService.anchorExplodeCdMs(BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(100L, PracticeService.anchorExplodeCdMs(BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
    }

    @Test
    @DisplayName("INTERMEDIATE のアンカー1サイクル = 12t (参照実測の最小サイクルと一致)")
    void intermediateAnchorCycleMatchesMeasuredCycle() {
        BotDifficulty d = BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE);
        long cycle = PracticeService.anchorPlaceCdMs(d)
                + PracticeService.anchorChargeCdMs(d)
                + PracticeService.anchorExplodeCdMs(d);
        assertEquals(600L, cycle, "実測の最小サイクル 12t (=600ms) と一致すること");
    }

    @Test
    @DisplayName("トーテムPOP休止 = totem_cd rung (40/31/21/10/0/1t)")
    void totemPauseMatchesReference() {
        assertEquals(2000L, PracticeService.crystalTotemPauseMs(BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(1550L, PracticeService.crystalTotemPauseMs(BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(1050L, PracticeService.crystalTotemPauseMs(BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(500L, PracticeService.crystalTotemPauseMs(BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(0L, PracticeService.crystalTotemPauseMs(BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(50L, PracticeService.crystalTotemPauseMs(BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
    }

    @Test
    @DisplayName("剣スイング間隔 = hitcd rung、ただしバニラ無敵時間 500ms が床")
    void swordIntervalMatchesReference() {
        // map hitcd = 23/15/10/5/0/0t。CRAZY 以降は無敵時間 (500ms) が律速になる。
        assertEquals(1150L, BotDifficulty.of(BotDifficulty.Preset.EASY).attackIntervalMs());
        assertEquals(750L, BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE).attackIntervalMs());
        assertEquals(500L, BotDifficulty.of(BotDifficulty.Preset.HARD).attackIntervalMs());
        for (BotDifficulty.Preset p : new BotDifficulty.Preset[] {
                BotDifficulty.Preset.CRAZY, BotDifficulty.Preset.MASTER}) {
            assertTrue(BotDifficulty.of(p).attackIntervalMs() >= 500L,
                    p + " はバニラ無敵時間 (500ms) を下回らない");
        }
    }

    @Test
    @DisplayName("移動は全ラング バニラ走行速度 (0.28 b/t)")
    void moveSpeedMatchesReference() {
        for (BotDifficulty.Preset p : BotDifficulty.Preset.values()) {
            if (p == BotDifficulty.Preset.NPC) {
                continue; // NPC はその場に立つ(0.10)
            }
            assertEquals(0.28d, BotDifficulty.of(p).moveSpeed(), 1e-9, p + " の移動速度");
        }
    }
}
