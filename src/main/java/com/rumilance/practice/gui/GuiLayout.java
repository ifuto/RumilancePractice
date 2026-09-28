package com.rumilance.practice.gui;

import com.rumilance.practice.util.GuiSlots;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 2026-09-28 全画面刷新のためのレイアウト語彙。<b>左右対称・余白・主役=中央</b>を
 * 1箇所で保証する配置プリミティブだけを集めたユーティリティ。位置の計算は全部ここ ——
 * 各家事画面に生のスロット番号を書かせず、ルール変更はこのファイルだけで済む形にする。
 *
 * <p>ルール:
 * <ul>
 *   <li><b>主役 (hero)</b> — 開始/送信など画面で一番大事なボタンは中央列 (col4)
 *   <li><b>ペア (pair)</b> — 二択は中央列の左右ミラー (cols 3 & 5)
 *   <li><b>行中央寄せ</b> — N 個のアイテムは均等な間隔で中央に吸着 (奇数= col4が中央、偶数= col3/4 が中央)
 *   <li><b>隅は弱い操作</b> — 閉じる/戻る外の消極ボタンだけが角に置ける
 * </ul>
 */
public final class GuiLayout {

    private GuiLayout() {
    }

    /** Central column index on every 9-wide row. */
    public static final int CENTRE_COL = 4;

    /**
     * Places two items mirrored around the central column on {@code row} (cols 3 and 5).
     * Either item may be null to keep only one side.
     */
    public static void pair(Inventory inventory, int row, ItemStack left, ItemStack right) {
        if (left != null) {
            inventory.setItem(GuiSlots.slot(row, CENTRE_COL - 1), left);
        }
        if (right != null) {
            inventory.setItem(GuiSlots.slot(row, CENTRE_COL + 1), right);
        }
    }

    /** Places the hero item on the central column of {@code row}. */
    public static void hero(Inventory inventory, int row, ItemStack item) {
        inventory.setItem(GuiSlots.slot(row, CENTRE_COL), item);
    }

    /**
     * Mirror pair with a wider, caller-controlled gap: left at {@code CENTRE_COL - gap},
     * right at {@code CENTRE_COL + gap}. Out-of-range sides are skipped.
     */
    public static void widePair(Inventory inventory, int row, int gap, ItemStack left, ItemStack right) {
        int l = CENTRE_COL - gap;
        int r = CENTRE_COL + gap;
        if (left != null && l >= 0) {
            inventory.setItem(GuiSlots.slot(row, l), left);
        }
        if (right != null && r < GuiSlots.ROW_SIZE) {
            inventory.setItem(GuiSlots.slot(row, r), right);
        }
    }

    /**
     * Lays {@code items} out on {@code row}, centred around the central column with
     * consecutive columns. For an odd count the middle item lands exactly on col4; for an
     * even count the two middle items land on cols 3/4. Guarantees a symmetric silhouette
     * no matter how few items the screen currently has.
     */
    public static void centredRow(Inventory inventory, int row, List<ItemStack> items) {
        centredRow(inventory, row, items.toArray(new ItemStack[0]));
    }

    /** varargs twin of {@link #centredRow(Inventory, int, List)}. */
    public static void centredRow(Inventory inventory, int row, ItemStack... items) {
        int n = items.length;
        if (n == 0) {
            return;
        }
        int start = CENTRE_COL - (n - 1) / 2;
        for (int i = 0; i < n; i++) {
            int col = start + i;
            if (col < 0 || col >= GuiSlots.ROW_SIZE) {
                continue;
            }
            if (items[i] != null) {
                inventory.setItem(GuiSlots.slot(row, col), items[i]);
            }
        }
    }

    /**
     * Row band of up to {@code capacity} consecutive items centered like
     * {@link #centredRow} but every screen sharing the convention keeps a FIXED band
     * window: the band always begins at col1 — paging slides items along the band while
     * the window itself never moves. Symmetric because the window spans cols 1-7.
     */
    public static int bandStart(int row) {
        return GuiSlots.slot(row, 1);
    }

    /** Left end of a band row (col0) — prev arrow anchor. */
    public static int bandPrev(int row) {
        return GuiSlots.slot(row, 0);
    }

    /** Right end of a band row (col8) — next arrow anchor. */
    public static int bandNext(int row) {
        return GuiSlots.slot(row, GuiSlots.ROW_SIZE - 1);
    }
}
