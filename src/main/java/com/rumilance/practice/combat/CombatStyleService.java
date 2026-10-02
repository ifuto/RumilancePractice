package com.rumilance.practice.combat;

import com.rumilance.practice.platform.PlayerPlatform;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * マッチ単位で戦闘仕様（攻撃速度）を管理するサービス。
 *
 * <p>Duel Request GUI で選択された {@link CombatMode} を試合開始時に適用し、
 * 終了時にデフォルトに戻す。</p>
 *
 * <ul>
 *   <li>Java → 通常攻撃速度 4.0（クールダウンあり）</li>
 *   <li>Bedrock → 攻撃速度 16.0（事実上クールダウンなし）</li>
 * </ul>
 */
public final class CombatStyleService implements Listener {

    /** Paper default attack speed. */
    private static final double JAVA_ATTACK_SPEED = 4.0;

    /** Bedrock-style: effectively no cooldown. */
    private static final double BEDROCK_ATTACK_SPEED = 16.0;

    /** matchId → chosen CombatMode (null = auto / not set → default per-platform). */
    private final Map<String, CombatMode> matchModes = new ConcurrentHashMap<>();

    /** playerId → CombatMode applied at match start (for reset). */
    private final Map<UUID, CombatMode> appliedModes = new ConcurrentHashMap<>();

    private final Plugin plugin;

    public CombatStyleService(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---- match lifecycle ----

    /**
     * マッチに戦闘モードを設定（Duel Request GUI / Queue から呼ばれる）。
     * {@code null} なら各プレイヤーのプラットフォームデフォルト。
     */
    public void setMatchMode(String matchId, CombatMode mode) {
        if (mode != null) {
            matchModes.put(matchId, mode);
        }
    }

    /**
     * マッチ参加者全員に戦闘モードを適用。
     * {@code matchId} にモードが未設定の場合、各プレイヤーのプラットフォームに応じたデフォルト。
     */
    public void applyToMatch(String matchId, Iterable<UUID> participants) {
        CombatMode global = matchModes.get(matchId);
        for (UUID uid : participants) {
            Player p = Bukkit.getPlayer(uid);
            if (p == null || !p.isOnline()) continue;
            CombatMode mode = global != null ? global
                    : CombatMode.defaultFor(PlayerPlatform.of(p));
            applyToPlayer(p, mode);
        }
    }

    /**
     * マッチ終了: 全員をデフォルト攻撃速度に戻し、モード記録を消去。
     */
    public void resetMatch(String matchId) {
        matchModes.remove(matchId);
        // appliedModes からこの match に属していたプレイヤーをリセット
        for (Map.Entry<UUID, CombatMode> e : appliedModes.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                resetPlayer(p);
            }
        }
        appliedModes.clear();
    }

    /**
     * 指定プレイヤーの戦闘モードを適用。
     */
    public void applyToPlayer(Player player, CombatMode mode) {
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;
        double target = (mode == CombatMode.BEDROCK) ? BEDROCK_ATTACK_SPEED : JAVA_ATTACK_SPEED;
        // 既存の modifier を全除去してベース値を設定
        attr.getModifiers().forEach(attr::removeModifier);
        attr.setBaseValue(target);
        appliedModes.put(player.getUniqueId(), mode);
    }

    /**
     * プレイヤーをデフォルトの Java 戦闘に戻す。
     */
    public void resetPlayer(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;
        attr.getModifiers().forEach(attr::removeModifier);
        attr.setBaseValue(JAVA_ATTACK_SPEED);
        appliedModes.remove(player.getUniqueId());
    }

    /**
     * プレイヤーに現在適用中のモードを返す（null = 未適用 / デフォルト）。
     */
    public CombatMode getAppliedMode(UUID playerId) {
        return appliedModes.get(playerId);
    }

    // ---- cross-platform helpers ----

    /**
     * sender → target の組み合わせがクロスプラットフォームかどうか。
     */
    public static boolean isCrossPlatform(UUID sender, UUID target) {
        Player s = Bukkit.getPlayer(sender);
        Player t = Bukkit.getPlayer(target);
        if (s == null || t == null) return false;
        return PlayerPlatform.of(s) != PlayerPlatform.of(t);
    }

    /**
     * クロスプラットフォーム Duel のデフォルト推奨モード（送信者のプラットフォーム）。
     */
    public static CombatMode suggestedMode(UUID sender) {
        Player s = Bukkit.getPlayer(sender);
        if (s == null) return CombatMode.JAVA;
        return CombatMode.defaultFor(PlayerPlatform.of(s));
    }

    /**
     * matchId に紐づく CombatMode を取得（null = 自動判定）。
     */
    public CombatMode getMatchMode(String matchId) {
        return matchModes.get(matchId);
    }
}