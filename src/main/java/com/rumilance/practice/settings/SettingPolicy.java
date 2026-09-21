package com.rumilance.practice.settings;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which personal settings the free plan may use.
 *
 * <p>A few toggles cost the server real work per player: ally glow keeps re-evaluating line of
 * sight and pushing glowing state to every viewer, and the match report builds and sends an
 * extra item plus its packets after every match. Those are paid-plan features — the free plan
 * simply keeps them off, and the toggle is shown locked instead of clickable so the reason is
 * visible rather than silent.</p>
 *
 * <p>Pure on purpose: no Bukkit, no plugin state, so the rule set is unit-testable and the GUI
 * and the backend gates cannot drift apart.</p>
 */
public final class SettingPolicy {

    /** Setting keys that require VIP or above. Matches the {@code toggle:<key>} action keys. */
    private static final Set<String> PREMIUM_ONLY;

    static {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("team_glow");
        keys.add("match_report");
        PREMIUM_ONLY = Collections.unmodifiableSet(keys);
    }

    private SettingPolicy() {
    }

    /** Keys that are locked on the free plan, in display order. */
    public static Set<String> premiumOnlyKeys() {
        return PREMIUM_ONLY;
    }

    public static boolean isPremiumOnly(String key) {
        return key != null && PREMIUM_ONLY.contains(key);
    }

    /**
     * True when this player may not flip the toggle.
     *
     * @param premium true for VIP / VIP+ (see {@code RankService#isVipOrAbove})
     */
    public static boolean isLocked(String key, boolean premium) {
        return isPremiumOnly(key) && !premium;
    }

    /**
     * Whether the stored value may actually take effect. Backend gates call this instead of
     * trusting the database row, so a value saved while a player had VIP stops applying the
     * moment the rank does.
     */
    public static boolean allows(String key, boolean storedValue, boolean premium) {
        return storedValue && !isLocked(key, premium);
    }
}
