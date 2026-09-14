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

---

## 2026-09: 難易度梯子の完全解剖(quantum:difficulty/0..6 全行抽出)

`Practicebot` datapack の `data/quantum/function/difficulty/*.mcfunction` を全行読んだ実数値。
`BotDifficulty` 梯子はこれに一致させた(v1.64.0):

| rung | map名 | hitcd | reach(コンボ開始距離) | aim | totem_cd | crystal/obby/anchor cd | max_rotation |
|------|-------|-------|------------------------|-----|----------|-------------------------|--------------|
| 0 | NPC | 攻撃なし | n/a | (binomial 1) | n/a | 0 | (既定) |
| 1 | Easy | 23t (1.15s) | 1.3 | 5 | 40t | 6/5/5 | 1°/t |
| 2 | Intermediate | 15t (0.75s) | 1.6 | 4 | 31t | 6/4/4 | 4°/t |
| 3 | Hard | 10t (0.50s) | 2.0 | 3 | 21t | 6/3/4 | 10°/t |
| 4 | CRAZY | 5t (0.25s) | 2.3 | 2 | 10t | 3/2/3 | 14°/t |
| 5 | MASTER | 0 (バニラ連打) | 2.9 | 2 | 0t | 2/1/1 | 20°/t |
| 6 | SURVIVAL MASTER | 0 | 3.0 | (5を引継) | 1t | 3/0/1 | (5を引継) |

抽出で判明した本質:

- マップBOTは **Carpet式フェイクプレイヤー** = 攻撃は本物のバニラスイング
  (ネザライト剣=8ダメージ、本物の防具/無敵時間/攻撃クールダウンが乗る)。
  **難易度が変えるのは間隔・エイム・回転速度・トーテム/クリスタルのクールダウンだけで、
  1発の威力は全ラング同一**。
- 体力は常に20(mapリセット関数のコメント "MAKE IT CHANGE ITS GENERIC HEALTH TO 20")。
- 移動は常時バニラ走行速度(≈0.28 b/t)。`bot_speed` は任意トグルで梯子には出ない。
- hitcd 5t 以下のラングもダメージはバニラ無敵時間(500ms)で頭打ち → 実効DPSは一緒。

当プラグイン側の写像(v1.64.0):

- `attackDamage = 8`(全攻撃ラング共通=バニラ武器相当) / `attackIntervalMs = max(hitcd×50, 500)`
- `moveSpeed = 0.28`(全ラング=走行同等) / `turnRatePerTick` = 上表 max_rotation 梯子
- `botMaxHp = 20` 固定(プレイヤー同等) / `aimSpreadDegrees` = map aim(Easy5/Int4/Hard3/Crazy2/Master2/Surv2)
- reach はプレイヤーと同じ 3.0 固定(プロダクト決定)。マップの reach は「コンボ開始距離」で、
  スイング自体は常にバニラ3.0 — 当プラグインも swing 判定を 3.0 に固定済み
- regen は当プラグイン独自拡張(マップはモード別トグル: crystal=off / sword=on)→ 将来整列候補

---

## 2026-09: Crystalボット g1gc 実測照合(quantum:crystal/* 全行読了)

クリスタルBOTの本体は `crystal/tick` → state判定 → **bin/27(戦闘) = `g1gc/*`**。
`crystal/hardcode/*` はトグル切り替えの代替実装(既定は g1gc)。実測値:

| 事象 | マップ実数値 | 出典 |
|---|---|---|
| 近接剣撃 | ≤3blk + 対象hurtTime=0 + 視線通過 → **hitcd 7t(350ms)固定**(全ラング) | g1gc/hit, g1gc/can_hit |
| クリスタル設置 | crystal_timer≤0 で設置 → **crystal_timer=crystal_cd(6/4/3/2/2/3t)** | g1gc/spawncrystal + cooldowns |
| 台(オブシディアン) | obby_timer≤0 で setblock → **obby_cd(5/4/3/2/1/1t)** | g1gc/placeobsidian |
| 爆発 | 自クリスタルへ `damage 1 player_attack` = **バニラ爆発** | g1gc/breakcrystal |
| トーテム | pop後 **totem_timer=totem_cd(40/31/21/10/0/1t)** の休止・offhand再装填4t/9t | crystal/totmain |
| パール | pearlcd 20t・後方15blkへブリンク(crystal/passive/escape/pearl) | escape/pearl |
| 金リンゴ | HP≤16(80%)で gap(gap_timer 35t) | passive/aggression0 |
| クロスボウ | HP17+ かつ距離20〜40+ で 0.65s間隔射撃 | passive/crossbow/load |
| 移動 | 毎tick `move`(停止)が基本・対象>2blkで前進・壁でjump・後退しない | g1gc/botlogic+movement |
| NPC(rung 0) | 攻撃完全無し(クリスタルも設置しない・その場に立つ) | crystal/difficulty0 |
| 回転 | max_rotation梯子 1/4/10/14/20/20°/t(sword共通) | difficulty/0..6 |

旧実装からの修正(v1.67.0):

- 周回オービット+3mで後退 → **足止め+前進型**へ全面改修(マップは後退しない。
  ピンチはパールで答える)。壁詰まり時はホップで登る。
- **近接剣撃を新設**: 3blk・対象の無敵時間expired・視線必須・350ms固定。
  ダメージは他モードと同じく剣8(バニラ経路)。
- クリスタルコンボ間隔を comboCooldownMs(秒オーダー) → **crystal_cd梯子(0.1〜0.3s)へ**。
  ランダム振幅は撤去(マップは正確に rung 値を再装填)。
- **トーテム休止を新設**: BOT自身のトーテムポップ(EntityResurrectEvent)で totem_cd梯子分
  停戦。Easy 2秒 / Master は同tick復帰。
- **クリスタルモードのリジェネを廃止**(マップは sword=on / crystal=off。回復は金リンゴのみ)。
- NPCのクリスタル攻撃を停止(マップ rung 0 は設置すらしない)。アンカー交ぜ込みは
  HARD以上に整理(map .anchors に対応)。

既知の意図的差分: 爆発は Paper#11167 対策の源泉付き createExplosion(6f) 変換のまま
(プロダクト決定)。クロスボウは40blk級の狩場がないアリーナ前提で中距離pokeのまま。
