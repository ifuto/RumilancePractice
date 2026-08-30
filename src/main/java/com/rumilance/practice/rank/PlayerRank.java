package com.rumilance.practice.rank;

import java.util.Locale;

/**
 * Social / donor ranks. Ordering: {@link #NORM} &lt; {@link #VIP} &lt; {@link #VIP_PLUS} ≤ {@link #ADMIN}.
 */
public enum PlayerRank {
    NORM,
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
            case VIP -> "vip";
            case VIP_PLUS -> "vip+";
            case ADMIN -> "admin";
        };
    }

    public String displayLabel() {
        return switch (this) {
            case NORM -> "NORM";
            case VIP -> "VIP";
            case VIP_PLUS -> "VIP+";
            case ADMIN -> "OWNER";
        };
    }
}
