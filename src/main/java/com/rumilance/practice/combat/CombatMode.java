package com.rumilance.practice.combat;

/**
 * Duel で適用する戦闘仕様。
 *
 * <ul>
 *   <li>{@link #JAVA} — 通常の Java Edition 戦闘 (クールダウンあり、攻撃速度 4.0)</li>
 *   <li>{@link #BEDROCK} — Bedrock Edition 風 (クールダウンなし、攻撃速度 16.0)</li>
 * </ul>
 */
public enum CombatMode {

    /** Java 仕様 — クールダウン付き、スイープあり。 */
    JAVA,

    /** Bedrock 仕様 — スパムクリック可、クールダウンなし。 */
    BEDROCK;

    /**
     * プレイヤーのデフォルト戦闘モード。
     * Bedrock (Geyser/Floodgate) プレイヤーは自動で BEDROCK 扱い。
     */
    public static CombatMode defaultFor(com.rumilance.practice.platform.PlayerPlatform platform) {
        return platform == com.rumilance.practice.platform.PlayerPlatform.BEDROCK ? BEDROCK : JAVA;
    }

    /** 識別子からモードを解決。null / 不明は {@link #JAVA}。 */
    public static CombatMode fromString(String value) {
        if (value == null) return JAVA;
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "bedrock", "be" -> BEDROCK;
            default -> JAVA;
        };
    }

    /** DuelRequest 保存用の短い識別子。 */
    public String toKey() {
        return switch (this) {
            case JAVA -> "java";
            case BEDROCK -> "bedrock";
        };
    }
}