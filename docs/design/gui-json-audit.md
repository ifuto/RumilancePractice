# gui.json 再現監査レポート

> `docs/design/gui.json`（saves v2・全10画面）と実装の突き合わせ結果。
> 作成: 2026-10-04 / v1.92.37 時点

## 凡例

| 略号 | material | 略号 | material |
|---|---|---|---|
| GREEN | `green_stained_glass_pane` | LGRY | `light_gray_stained_glass_pane` |
| GRAY | `gray_stained_glass_pane` | WHT | `white_stained_glass_pane` |
| BLK | `black_stained_glass_pane` | RED | `red_stained_glass_pane` |
| BLUE | `blue_stained_glass_pane` | LBLU | `light_blue_stained_glass_pane` |
| YEL | `yellow_stained_glass_pane` | LIME | `lime_stained_glass_pane` |
| ORNG | `orange_stained_glass_pane` | GLAS | `glass_pane`（透明） |
| CHN | `*_copper_chain` | HEAD | `player_head` |
| WILD / BOLT | 鍛冶型（MAIN / SUB） | BARR | `barrier` |

`·` は空気（gui.json 上 null）。

---

## サマリ: どの画面が合っているか

| # | gui.json の画面 | 実装クラス | 状態 | 主な差分 |
|---|---|---|---|---|
| 1 | MAIN KIT SELECTER | `EkitSelectGui#renderChooser` | ✅ 一致 | なし |
| 2 | KIT SELECT GUI | `EkitSelectGui#renderKitSelect` | ✅ 一致（v1.92.37 で修正） | bottom 13/31 が灰色→**ライムに修正済み** |
| 3 | KIT EDIT GUI | `EditKitGui` | ❌ 不一致 | r1 の緑バー9枠なし / r2-5 の透明 `glass_pane` 36枠なし |
| 4 | Duel Request GUI | `DuelRequestGui` | ❌ 不一致 | 枠なし。橙17・LGRY12・黒9・CHN6 の構成が未実装 |
| 5 | Battle Mode GUI | `PartyBattleModeGui` | ✅ 一致（v1.92.39 で修正） | 内側 col1-7 まで水色で埋めていた→**両端のみで中は空気** |
| 6 | Party Start Battle GUI | `PartyStartBattleGui` | ✅ 一致（v1.92.39 で修正） | col1/7 の余分な水色を除去、**開始ボタンを (3,3)→(3,4)** へ |
| 7 | Danger Settings GUI | `SettingsGui`（危険設定） | ❌ 不一致 | 赤24 + 黒9 の枠が未実装 |
| 8 | Party Config GUI | `TeamConfigGui` | ❌ 不一致 | 白24 + LGRY20 + 赤3 の市松が未実装 |
| 9 | Party MAIN GUI | `TeamHubGui` | ❌ 不一致 | 赤/青の陣営カラム・灰10・LGRY4 が未実装 |
| 10 | Party setfunc-item-main GUI | （未特定 / 黄+LGRY+白） | ❌ 未特定 | 対応クラスが特定できていない |

## なぜ大半が「枠なし」なのか（重要な方針転換）

`GuiFrame` のコメントに明記されているとおり、**2026-09-28 の「板ガラスの枠廃止」決定**で
`GuiFrame#frame()` は「枠を描かずクリアするだけ」になりました。

```java
// GuiFrame.java
// PERIMETER FRAMES WERE ABOLISHED on 2026-09-28 (user decision: "板ガラスの枠廃止")
public static Inventory frame(Inventory inventory, Theme theme) {
    inventory.clear();
    return inventory;
}
```

一方 `docs/design/gui.json` は **2026-10-02/03 作成**で、**枠ありのモックアップ**になっています。
そして v1.92.31「KIT GUI mockups 1:1 from docs/design/gui.json」で **/ekit の2画面だけが
gui.json 準拠に戻りました**（残り8画面は枠なしのまま）。

→ **つまり「gui.json が正」で全画面を統一するなら、残り8画面の枠を復活させる必要があります。**
これは見た目の大きな変更なので、1画面ずつ順に進める前提で下記に差分を固定します。

---

## 各画面の期待グリッド（gui.json 実体）

### KIT EDIT GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | HLM | CST | LEG | BOOT | GRAY | SHLD | GRAY | LWOL | YWOL |
| r1 | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN |
| r2 | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS |
| r3 | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS |
| r4 | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS |
| r5 | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS | GLAS |

### KIT SELECT GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | GREEN | GREEN | GREEN | GREEN | WILD | GREEN | GREEN | GREEN | GREEN |
| r1 | CHN | DAXE | DPICK | GRAY | GRAY | GRAY | GRAY | GRAY | CHN |
| r2 | CHN | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | CHN |
| r3 | CHN | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | CHN |
| r4 | CHN | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | CHN |
| r5 | GREEN | GREEN | GREEN | GREEN | BARR | GREEN | GREEN | GREEN | GREEN |

<details><summary>bottom (player inventory)</summary>

| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| i 0 | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY | GRAY |
| i 9 | GRAY | GRAY | GRAY | GREEN | LIME | GREEN | GRAY | GRAY | GRAY |
| i18 | GRAY | BOOK | PAPR | LIME | · | LIME | PAPR | PAPR | GRAY |
| i27 | GRAY | GRAY | GRAY | GREEN | LIME | GREEN | GRAY | GRAY | GRAY |

</details>

### MAIN KIT SELECTER
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN |
| r1 | CHN | · | · | · | · | · | · | · | CHN |
| r2 | CHN | · | WILD | · | · | · | BOLT | · | CHN |
| r3 | CHN | · | · | · | DSWD | · | · | · | CHN |
| r4 | CHN | · | · | · | · | · | · | · | CHN |
| r5 | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN | GREEN |

### Party setfunc-item-main GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | WHT | YEL | YEL | YEL | YEL | YEL | YEL | YEL | WHT |
| r1 | YEL | HEAD | HEAD | LGRY | LGRY | LGRY | LGRY | LGRY | YEL |
| r2 | YEL | LGRY | LGRY | LGRY | LGRY | LGRY | LGRY | LGRY | YEL |
| r3 | YEL | LGRY | LGRY | LGRY | LGRY | LGRY | LGRY | LGRY | YEL |
| r4 | YEL | LGRY | LGRY | LGRY | LGRY | LGRY | LGRY | LGRY | YEL |
| r5 | CLOC | YEL | AROW | YEL | YEL | YEL | AROW | YEL | BOOK |

<details><summary>bottom (player inventory)</summary>

| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| i 0 | · | · | · | · | · | · | · | YEL | WHT |
| i 9 | · | · | · | · | · | · | · | · | · |
| i18 | · | · | · | · | · | · | · | · | · |
| i27 | · | · | · | · | · | · | · | · | LGRY |

</details>

### Duel Request GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK |
| r1 | ORNG | ORNG | ORNG | ORNG | HEAD | ORNG | ORNG | ORNG | ORNG |
| r2 | CHN | LGRY | BARL | LGRY | WHT | LGRY | MAP | LGRY | CHN |
| r3 | CHN | LGRY | LGRY | WHT | DSWD | WHT | LGRY | LGRY | CHN |
| r4 | CHN | LGRY | GAPP | LGRY | WHT | LGRY | SLIM | LGRY | CHN |
| r5 | ORNG | ORNG | ORNG | ORNG | ORNG | ORNG | ORNG | ORNG | ORNG |

<details><summary>bottom (player inventory)</summary>

| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| i 0 | · | · | · | · | · | · | · | · | · |
| i 9 | · | · | · | · | · | · | · | · | · |
| i18 | · | · | · | · | · | · | · | · | WHT |
| i27 | · | · | · | · | · | · | · | · | · |

</details>

### Battle Mode GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU |
| r1 | LBLU | · | · | · | · | · | · | · | LBLU |
| r2 | LBLU | · | · | DSWD | · | CRYS | · | · | LBLU |
| r3 | LBLU | · | · | · | · | · | · | · | LBLU |
| r4 | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU |
| r5 | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK |

<details><summary>bottom (player inventory)</summary>

| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| i 0 | NETH | LEAD | LEAT | · | · | · | · | · | · |
| i 9 | · | · | · | · | · | · | · | · | · |
| i18 | · | · | · | · | · | · | · | · | · |
| i27 | · | · | · | · | · | · | · | · | · |

</details>

### Party Start Battle GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU |
| r1 | LBLU | · | BARL | · | IAXE | · | MAP | · | LBLU |
| r2 | LBLU | · | · | · | · | · | · | · | LBLU |
| r3 | LBLU | · | · | · | DSWD | · | · | · | LBLU |
| r4 | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU | LBLU |
| r5 | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK |

<details><summary>bottom (player inventory)</summary>

| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| i 0 | NETH | LEAD | LEAT | · | · | · | · | · | · |
| i 9 | · | · | · | · | · | · | · | · | · |
| i18 | · | · | · | · | · | · | · | · | · |
| i27 | · | · | · | · | · | · | · | · | · |

</details>

### Danger Settings GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | RED | RED | RED | RED | RED | RED | RED | RED | RED |
| r1 | RED | · | · | · | · | · | · | · | RED |
| r2 | RED | · | ROD | · | BARR | · | SIGN | · | RED |
| r3 | RED | · | · | · | · | · | · | · | RED |
| r4 | RED | RED | RED | RED | RED | RED | RED | RED | RED |
| r5 | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK | BLK |

### Party Config GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | LGRY | LGRY | LGRY | WHT | WHT | WHT | LGRY | LGRY | LGRY |
| r1 | LGRY | TNT | LGRY | WHT | CMP | WHT | LGRY | LDYE | LGRY |
| r2 | LGRY | LGRY | LGRY | WHT | WHT | WHT | LGRY | LGRY | LGRY |
| r3 | WHT | WHT | WHT | RED | RSB | RED | WHT | WHT | WHT |
| r4 | WHT | TAG | WHT | LGRY | RED | LGRY | WHT | HEAD | WHT |
| r5 | WHT | WHT | WHT | LGRY | AROW | LGRY | WHT | WHT | WHT |

### Party MAIN GUI
| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| r0 | RED | RED | RWOL | RED | HEAD | BLUE | BWOL | BLUE | PEARL |
| r1 | RED | HEAD | LIME | GRAY | LGRY | HEAD | HEAD | HEAD | BLUE |
| r2 | RED | GRAY | GRAY | GRAY | LGRY | HEAD | HEAD | HEAD | BLUE |
| r3 | RED | GRAY | GRAY | GRAY | LGRY | HEAD | HEAD | HEAD | BLUE |
| r4 | RED | GRAY | GRAY | GRAY | LGRY | HEAD | HEAD | HEAD | BLUE |
| r5 | RED | AROW | SIGN | BARR | SPYG | BARR | SIGN | AROW | BLUE |

<details><summary>bottom (player inventory)</summary>

| row | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| i 0 | · | · | · | · | · | · | · | · | · |
| i 9 | · | · | BLUE | WHT | LGRY | WHT | RED | · | · |
| i18 | · | · | LIME | LGRY | HEAD | LGRY | YEL | · | · |
| i27 | · | · | · | WHT | LGRY | WHT | · | · | · |

</details>

---

## 今回の修正

- **KIT SELECT GUI bottom 13 / 31**: `gray_stained_glass_pane` → `lime_stained_glass_pane`
  （`EkitSelectGui#paintChipPanel`）。アサインセル(22) を囲むライムの十字が完成。

## 次の一手（提案）

1. `KIT EDIT GUI` — /ekit 導線のど真ん中。r1 緑バー + r2-5 透明ガラス36枠を追加（影響範囲が閉じている）
2. `Danger Settings GUI` — 赤24 + 黒9
3. `Party Config GUI` / `Party MAIN GUI` — パーティ系は TeamService の状態表示と絡むので2画面まとめて
4. `Duel Request GUI`
5. `Party setfunc-item-main GUI` — 対応クラスの特定から

> 方針（2026-10-04 確定）: **gui.json が唯一の正**。2026-09-28 の「枠廃止」を含む過去の判断は
> すべて無視して、全画面を gui.json のグリッドへ合わせる。

---

# プログラムによる差分判別（gui.json ↔ 実装）

**結論: 静的解析では無理、実行時スナップショットなら完全に判別できます。**

`render()` はループ・ヘルパメソッド（`pane()` / `paintFrame` / `MenuScaffold.gridSlot`）・
条件分岐・実行時データ（キット一覧、パーティ所属、キュー数）で格子を組み立てるため、
ソースを読んでも正確なグリッドは復元できません。そこで **実際に描画させて JSON に落とし、
gui.json と機械比較**します。

## 仕組み

```
  サーバー(ヘッドレス可)                     手元
  ─────────────────────                    ────────────────────────
  /guisnapshot all
        │  GuiSnapshotService が
        │  renderPublic() で
        │  ─ 画面は開かない(パケット0)
        │  ─ インベントリは退避して復元
        │  ─ ホットバー退避は一時停止
        ▼
  plugins/n-arena/gui-snapshots/*.json  ──copy──▶  ./gui-snapshots/
                                                      │
                             tools/gui_diff.py ◀──────┘
                                    │  tools/gui_screen_map.json
                                    │  (gui.json セーブ名 ↔ スナップショット名)
                                    ▼
                             1セル単位の差分表
```

## 使い方

```bash
# 1) サーバー側（権限 rumilance.admin、ヘッドレスで可）
/guisnapshot all                 # 全画面
/guisnapshot EKIT_SELECT MAIN 0  # 1画面だけ（カテゴリ・ページ指定可）

# 2) 手元にコピーして比較
cp -r <server>/plugins/n-arena/gui-snapshots ./gui-snapshots
python3 tools/gui_diff.py                        # 全部
python3 tools/gui_diff.py --screen "KIT SELECT GUI"
python3 tools/gui_diff.py --json report.json     # 機械可読
python3 tools/gui_diff.py --strict               # コンテンツ差異も失敗扱い
```

- 装飾cell（`*_stained_glass_pane` / `glass_pane` / `*_chain`）の不一致は **FAIL**（直すべき）
- コンテンツcell（キットアイコン等、実行時に変わるもの）の不一致は **注記**
- 装飾の不一致が1件でもあれば exit code 1（`--no-fail` で無効化）。将来 CI のゲートに使えます
- 下段36マス（KIT SELECT のチップパネル等）も、gui.json 側に定義がある画面は比較対象

## ファイル

| ファイル | 役割 |
|---|---|
| `src/main/java/com/rumilance/practice/gui/GuiSnapshotService.java` | オフスクリーン描画＋JSON 書き出し |
| `src/main/java/com/rumilance/practice/command/GuiSnapshotCommand.java` | `/guisnapshot` |
| `tools/gui_diff.py` | 比較ツール |
| `tools/gui_screen_map.json` | gui.json セーブ名 ↔ スナップショット名の対応表 |

未マッピングの画面は `gui_screen_map.json` の値を `null` にしておくと
「no snapshot mapping」として報告されます（現状 `Party setfunc-item-main GUI` が該当）。
