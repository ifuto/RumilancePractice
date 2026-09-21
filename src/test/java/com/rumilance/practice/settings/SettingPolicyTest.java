package com.rumilance.practice.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 無料プランでロックされる設定のルール。GUI の表示とバックエンドの適用判定が
 * 同じこのクラスを見るので、どちらかだけが緩むことはない。
 */
public class SettingPolicyTest {

    /** 味方グロウとマッチレポートは毎 tick / 毎試合の余分な処理を伴うので有料プラン限定。 */
    @Test
    void heavySettingsArePremiumOnly() {
        assertTrue(SettingPolicy.isPremiumOnly("team_glow"));
        assertTrue(SettingPolicy.isPremiumOnly("match_report"));
        assertEquals(2, SettingPolicy.premiumOnlyKeys().size());
    }

    /** 軽い設定は誰でも使える。 */
    @Test
    void everydaySettingsStayFree() {
        assertFalse(SettingPolicy.isPremiumOnly("sounds"));
        assertFalse(SettingPolicy.isPremiumOnly("scoreboard"));
        assertFalse(SettingPolicy.isPremiumOnly("deny_duels"));
        assertFalse(SettingPolicy.isPremiumOnly("auto_requeue"));
        assertFalse(SettingPolicy.isPremiumOnly("team_armor"));
        assertFalse(SettingPolicy.isPremiumOnly(null));
        assertFalse(SettingPolicy.isPremiumOnly(""));
    }

    /** ロックされるのは無料プランだけ。VIP / VIP+ は従来通り。 */
    @Test
    void lockAppliesOnlyToTheFreePlan() {
        assertTrue(SettingPolicy.isLocked("team_glow", false));
        assertTrue(SettingPolicy.isLocked("match_report", false));
        assertFalse(SettingPolicy.isLocked("team_glow", true));
        assertFalse(SettingPolicy.isLocked("sounds", false));
    }

    /** 保存値が ON でも、プランが落ちたら効果は無効になる。 */
    @Test
    void storedValueDoesNotOverrideThePlan() {
        assertTrue(SettingPolicy.allows("team_glow", true, true));
        assertFalse(SettingPolicy.allows("team_glow", true, false));
        assertFalse(SettingPolicy.allows("team_glow", false, true));
        assertTrue(SettingPolicy.allows("sounds", true, false));
        assertFalse(SettingPolicy.allows("sounds", false, true));
    }
}
