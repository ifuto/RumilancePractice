package com.rumilance.practice.ffa;

import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LFF（Looking For Fight）: FFA のアリーナ設定 {@code settings.lff} が ON のとき、キットの9番目
 * （ホットバー最後のスロット）に置かれるトグルアイテムと、頭上に出る表示を管理する。
 *
 * <ul>
 *   <li>待機中は<b>火薬</b>。名前は灰色で {@code ⚔️ Looking for fight (/lff) ⚔️}。</li>
 *   <li>クリックすると<b>グロウストーンダスト</b>に変わり、名前はオレンジ色で
 *       {@code ⚔️ Now looking for fight ... (/lff)}。頭上に {@code ⚠ Looking For Fight ⚠} が出る。</li>
 *   <li>もう一度クリックすると火薬に戻り、頭上の表示も消える。</li>
 * </ul>
 *
 * <p>頭上表示はプレイヤーを vehicle にした {@link TextDisplay}。ネームタグそのものを書き換えると
 * 名前が消えてしまう（ランクアイコンのチーム設定とも競合する）ため、別の1行として上に足す。
 * 死亡・リスポーンで passenger は外れるので、{@link #refresh(Player)}（キット配布の後）で
 * 付け直す。
 */
public final class FfaLookingForFight {

    /** キットの9番目 = ホットバーの最後のスロット（レイアウト index 8）。 */
    public static final int SLOT = 8;

    private static final TextColor IDLE_COLOR = NamedTextColor.GRAY;
    private static final TextColor ACTIVE_COLOR = NamedTextColor.GOLD;
    /** 「ちょっと濃い黄色」。YELLOW(#FFFF55) より一段深い GOLD(#FFAA00)。 */
    private static final TextColor TAG_COLOR = NamedTextColor.GOLD;

    private static final String IDLE_LABEL = "⚔️ Looking for fight (/lff) ⚔️";
    private static final String ACTIVE_LABEL = "⚔️ Now looking for fight ... (/lff)";
    private static final String TAG_LABEL = "⚠ Looking For Fight ⚠";

    /** ネームタグの上に出す高さ（プレイヤーの足元からのオフセット）。 */
    private static final float TAG_HEIGHT = 2.35f;

    private final Map<UUID, Boolean> looking = new ConcurrentHashMap<>();
    private final Map<UUID, TextDisplay> tags = new ConcurrentHashMap<>();

    public boolean isLooking(UUID playerId) {
        return Boolean.TRUE.equals(looking.get(playerId));
    }

    /** 状態を反転し、反転後の状態を返す。 */
    public boolean toggle(Player player) {
        boolean next = !isLooking(player.getUniqueId());
        setLooking(player, next);
        return next;
    }

    public void setLooking(Player player, boolean value) {
        if (value) {
            looking.put(player.getUniqueId(), Boolean.TRUE);
        } else {
            looking.remove(player.getUniqueId());
        }
        player.getInventory().setItem(SLOT, item(value));
        updateTag(player, value);
    }

    /**
     * キット配布の直後に呼ぶ: 9番目を LFF アイテムで上書きし、募集中なら頭上表示を付け直す。
     * リスポーンで passenger が外れるため、状態の復元もここが担う。
     */
    public void refresh(Player player) {
        boolean on = isLooking(player.getUniqueId());
        player.getInventory().setItem(SLOT, item(on));
        updateTag(player, on);
    }

    /** FFA を抜けた / 切断したとき: 状態も頭上表示も消す。 */
    public void clear(Player player) {
        looking.remove(player.getUniqueId());
        removeTag(player);
    }

    /** 現在の状態に対応するトグルアイテム。 */
    public ItemStack item(boolean active) {
        ItemStack stack = new ItemStack(active ? Material.GLOWSTONE_DUST : Material.GUNPOWDER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(active ? ACTIVE_LABEL : IDLE_LABEL,
                        active ? ACTIVE_COLOR : IDLE_COLOR)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.ffaLff(), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    /** LFF トグルアイテムかどうか。 */
    public static boolean isLffItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(ItemKeys.ffaLff(), PersistentDataType.BYTE);
    }

    private void updateTag(Player player, boolean on) {
        removeTag(player);
        if (!on || !player.isOnline() || player.isDead()) {
            return;
        }
        TextDisplay tag = player.getWorld().spawn(player.getLocation(), TextDisplay.class, display -> {
            display.text(Component.text(TAG_LABEL, TAG_COLOR)
                    .decoration(TextDecoration.ITALIC, false));
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(true);
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, TAG_HEIGHT, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(1f, 1f, 1f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
        });
        player.addPassenger(tag);
        tags.put(player.getUniqueId(), tag);
    }

    private void removeTag(Player player) {
        TextDisplay old = tags.remove(player.getUniqueId());
        if (old != null && old.isValid()) {
            old.remove();
        }
    }
}
