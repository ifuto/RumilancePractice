package com.rumilance.practice.rank;

import java.util.Locale;

/**
 * Social / donor ranks. Ordering: {@link #NORM} &lt; {@link #PRO} &lt; {@link #VIP} &lt;
 * {@link #VIP_PLUS} ≤ {@link #ADMIN}. PRO is the badge rank shipped in the resource pack
 * (glyph U+E004, {@code rumilance:font/pro.png}) — it grants no gameplay perks, it only
 * renders the PRO badge on names and the tab.
 */
public enum PlayerRank {
    NORM,
    PRO,
    VIP,
    VIP_PLUS,
    ADMIN;

    public boolean atLeast(PlayerRank other) {
        return ordinal() >= other.ordinal();
    }

    public boolean isVipOrAbove() {
        return atLeast(VIP);
    }

    public boolean isVipPlusOrAbove() {
        return atLeast(VIP_PLUS);
    }

    public static PlayerRank parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORM;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT)
                .replace('＋', '+')
                .replace(" ", "");
        return switch (key) {
            case "pro" -> PRO;
            case "vip" -> VIP;
            case "vip+", "vip_plus", "vipplus", "svip" -> VIP_PLUS;
            case "admin", "administrator", "owner" -> ADMIN;
            case "norm", "normal", "default", "member", "mem" -> NORM;
            default -> null;
        };
    }

    public String storageKey() {
        return switch (this) {
            case NORM -> "norm";
            case PRO -> "pro";
            case VIP -> "vip";
            case VIP_PLUS -> "vip+";
            case ADMIN -> "admin";
        };
    }

    public String displayLabel() {
        return switch (this) {
            case NORM -> "NORM";
            case PRO -> "PRO";
            case VIP -> "VIP";
            case VIP_PLUS -> "VIP+";
            case ADMIN -> "OWNER";
        };
    }
}
