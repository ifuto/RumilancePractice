# ボット戦闘関数 対応表（bot/ フォルダ vs 本プラグイン）

`bot/` フォルダの2つの参照物について、戦闘関数（combat behaviour）が本プラグインに
実装済みかを棚卸ししたもの。最終確認: **v1.10.0 — 戦闘関数は全実装完了**。

- `Quantum's PvP Practice v1.18.zip` — ワールド + `Practicebot` データパック
  （`quantum` / `g1gc` / `pot` / `cart` / `cobwebs` / `sword` / `crystal` / `mace_new` … の mcfunction 群、実体856関数）
- `herobot-1.21.11-*.jar` — **Fabric MOD**。Paper からは直接使えない参考実装。
  本プラグインは Mannequin 自前 AI（`PracticeService`）で全関数を代替実装している。

## Sword ボット（`quantum:sword/*`）— 本家 `PracticeType.SWORD`

| Quantum 関数 | 内容 | 実装 |
|---|---|---|
| `combo/hit` + reach/aim 分布 | 射程・エイムぶれ付き攻撃 | ✅ `reachBlocks` ジッター + `aimSpreadDegrees` |
| `bot_mech/strafe` / `distance` | ストレイフ・距離管理 | ✅ 1.5〜3秒ごとのストレイフ反転 |
| `bot_mech/jump` | 障害物ジャンプ | ✅ 移動停滞検知でホップ |
| `crit` | 跳びクリティカル | ✅ `swordJumpCrit`（ホップ→落下中に1.5倍ヒット＋CRIT粒子/音） |
| `scrit` | クリ後バックペダル | ✅ クリ着弾後に後退ベロシティ |
| `pcrit` | クリ判定ゲート（糸中等を除外） | ✅ 相手がクモの巣内なら通常スイング |
| `combo/jumpreset` | コンボ中のスプリントジャンプ | ✅ HARD以上、クリ後35%で再突入ホップ |
| `passive/escape/pearl` | 瀕死・近接時のパール逃走 | ✅ `tickEscapePearl`（HP35%以下・距離6以内、後方最大12ブロックの安全地へテレポート。紫色パーティクル付き） |
| `passive/gap` | 金リンゴ | ✅ `tickBotGap`（HP50%以下で40%回復、2回/ライフ制限。金粒子+食事音） |
| `passive/bow/*` | 弓チャージ・射撃 | ✅ 8〜18ブロックで弓ポーク |
| `cobwebs/cobweb` | 相手の位置にクモの巣 | ✅ `tickSwordDisruption`（距離4以内、8秒で自動撤去） |
| `cobwebs/water_main` | 水バケツ消火・糸洗い流し | ✅ `tickBotWaterSave`（炎上時に消化、糸内なら除去、一時的な水置き） |
| `cobwebs/empty_lava` / `fill_lava` | 空中の相手の落着地に溶岩 | ✅ 着地予測スキャン→溶岩設置（1.8秒で撤去、耐火ポーション時は対象外） |
| `shield/disable`（斧） | 盾構え中に斧で盾無効化 | ✅ 斧に持ち替えスイング→`Player#setCooldown(SHIELD)` 4秒＋破壊音 |
| オフハンド・トーテム | 盾なし時トーテム保持 | ✅ `equipCombatBot` |

## Mace ボット（`quantum:mace_new/*`）— 本家 `PracticeType.MACE`

| Quantum 関数 | 内容 | 実装 |
|---|---|---|
| `tick` / `combo` | 接近＆スマッシュ近接 | ✅ `tickMaceBots` |
| `lunge` | スプリントジャンプ突進 | ✅ |
| `wind` | ウィンドチャージ自己打ち上げ | ✅（HARD以上） |
| 落下比例スマッシュ | 落下距離スケール | ✅ `maceSmashScale` |
| `far_pearl` + `pearl` | 遠距離へパール急接近 | ✅ 8〜24ブロックで前方2.5ブロック手前へテレポート（7秒CD） |
| `wind_pearl` | ウィンド＋前進バースト | ✅ 4.5〜9ブロック、ウィンド波＋前ベロシティ（HARD以上） |
| `elytra` | エリトラ式ロケット突進 | ✅ 7〜20ブロック、前上0.95ベロシティ＋花火音/雲粒子（HARD以上） |
| 盾 | 盾装備 | ✅（構えトグル付き） |

## Crystal ボット（`quantum:crystal/*`, `quantum:g1gc/*`）— 本家 `PracticeType.CRYSTAL`

| Quantum 関数 | 内容 | 実装 |
|---|---|---|
| `hardcode/*` | 台座クリスタルコンボ | ✅ `launchCrystalAttack` |
| 撤退・オービット | 距離管理 | ✅ |
| 難易度=コンボ間隔 | `crystal_cd` ラダー | ✅ `comboCooldownMs` |
| `maintot`/`offtot`/`totem` | トーテム保持・POP | ✅ オフハンド保持＋POP→リセット |
| `refill` | クリスタル補充 | ✅ `refillCrystals` |
| `passive/crossbow/*` | クロスボウ狙撃 | ✅ 5.5〜15ブロックで狙撃（クロスボウ音） |
| `passive/escape/pearl` | パール逃走 | ✅ 共通 `tickEscapePearl` |
| `passive/gap` | 金リンゴ | ✅ 共通 `tickBotGap` |
| `passive/block/*` | 防御ブロック壁 | ✅ `placeDefenseWall`（黒曜石2段＋後退ホップ、7秒で撤去） |
| `g1gc` アンカー系（`place_anchor`/`charge_anchor`） | アンカー攻撃 | ✅ `launchAnchorStrike`（満充填アンカー設置→8tick後に起爆、HARD以上でコンボの半数・6ブロック以内） |

## Pot ボット（`quantum:pot/*`）— 本家 `PracticeType.NETHERITE_POT`

| Quantum 関数 | 内容 | 実装 |
|---|---|---|
| `pot`（ハーミング投げ） | 近接でハーミング | ✅ |
| 瀕死の自己回復 | ヒール | ✅ ヒール演出（回復＝補充で pot カウントリセット） |
| `pot/gap` | 金リンゴ | ✅ 共通 `tickBotGap` |
| `pot_count` 上限2 | スプラッシュ使用制限 | ✅ `ab.potUses`（2投げたら補充まで投げない） |

## Cart ボット（`quantum:cart/*`）— 本家 `PracticeType.CART`

| Quantum 関数 | 内容 | 実装 |
|---|---|---|
| TNT カート | 起爆カート | ✅ `TNTPrimed` |
| `charge`（弓） | 弓斉射 | ✅ |
| 距離キープ | カイト | ✅ |
| `powered_rail` 設置 | レール演出 | ✅ TNT の下にレール設置（8秒で撤去） |
| `defenseplace`（`oak_log`） | 防御木材 | ✅ 近接＆被弾時に oak_log 壁＋後退（7秒で撤去） |
| sword/tick 共有パッシブ | 金リンゴ・パール逃走 | ✅ 共通ハンドラで実施 |

## 環境認識（`quantum:decisions/*`）

相手/自分の状態検知（`airborne` / `in_cobweb` / `fire` / 糸内等）で行動分岐する層。
→ ✅ `isAirborne` / `inCobweb` / `getFireTicks` プローブとして実装し、
溶岩バケツ（空中のみ）、PCrit ゲート（糸内は通常打ち）、水バケツ（炎上/糸）に反映済み。

## 残り（戦闘関数ではない／対象外のもの）

- `mech_train` の escape / treats / generic などトレーニングモジュール → 未実装（戦闘関数ではなく部屋モジュール。必要なら別部屋として追加可能）
- `stats/*` ボット戦の詳細統計（ヒット精度など） → 一部のみ
- `xaniclelib` / `eval` / `benchmark` / `bin` → データパック専用ユーティリティ・実験コード。移植対象外
- options / npc / kits / map などシステム系 → 本プラグインの GUI/設定/部屋管理で同等実装済み

---

凡例: ✅ 実装済み △ 一部実装 ❌ 未実装
