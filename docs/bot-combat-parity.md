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
| クリスタル設置 | crystal_timer≤0 で設置 → **crystal_timer=crystal_cd(6/6/6/3/2/3t)** | g1gc/spawncrystal + cooldowns |
| 台(オブシディアン) | obby_timer≤0 で setblock → **obby_cd(5/4/3/2/1/1t)** | g1gc/placeobsidian |
| 爆発 | 自クリスタルへ `damage 1 player_attack` = **バニラ爆発** | g1gc/breakcrystal |
| トーテム | pop後 **totem_timer=totem_cd(40/31/21/10/0/1t)** の休止・offhand再装填4t/9t | crystal/totmain |
| パール | pearlcd 20t・後方15blkへブリンク(crystal/passive/escape/pearl) | escape/pearl |
| 金リンゴ | HP≤16(80%)で gap(gap_timer 35t) | passive/aggression0 |
| クロスボウ | HP17+ かつ距離20〜40+ で 0.65s間隔射撃 | passive/crossbow/load |
| 移動 | 毎tick `move`(停止)が基本・対象>2blkで前進・壁でjump・後退しない | g1gc/botlogic+movement |
| アンカー | **全戦闘ラングで使用**(近接圏外のみ)。place(anchor charges0)→anchor_cd(5/4/4/3/1/1t)→charge(charges1+音)→charge_cd(5/4/3/2/2/2t)→爆発→explosion_cd(同)→次サイクル。爆発はバニラ相当(威力5+着火)。アンカー進行中はクリスタル休止(crystal_timerが各段で再装填)・途中破壊でサイクル中止 | bin/13→anchor_tick→bin/14/15, g1gc/place_anchor+charge_anchor+defenceplace, xaniclelib:anchor/* |
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

既知の意図的差分: 爆発は Paper#11167 対策の源泉付き createExplosion 変換のまま
(クリスタル6f/アンカー5f+着火・プロダクト決定)。クロスボウは40blk級の狩場がない
アリーナ前提で中距離pokeのまま。マップの防衛グロウストーン(defenceplace)は当 bot が
自爆ダメージ免除済みのため省略。アンカーの浮き設置(airplace)は 5 方位スキャンで再現。

### 訂正(2026-09 深夜の全行再抽出)
- crystal_cd の正確値は **6/6/6/3/2/3**(初回抽出の 6/4/3/2/2/3 は誤り)→ v1.68.0 で修正。
- アンカーは HARD+ のトグルではなく **g1gc の通常経路(全ラング)**。

---

## 2026-09: 全モジュール監査(Anchor漏れの再発防止・モジュール単位の対応表)

`data/quantum/function/` の**全サブモジュール**を棚卸しし、当プラグインの対応を宣言する:

| マップモジュール | 内容 | 当プラグイン | 判定 |
|---|---|---|---|
| `difficulty/0..6` | 難易度梯子(hitcd/aim/回転/_cd類) | `BotDifficulty` 梯子+各梯子switch | ✅ |
| `cooldowns` | タイマ減算体・非シャープhitcd13 | msタイマ体系(`BotAbilityState`) | ✅ |
| `crystal/tick` → `g1gc/*` | クリスタルBOT本体 | `tickCrystalBot`+`tickAnchorCycle` | ✅ |
| `crystal/hardcode/*` | 代替実装(トグル・非既定) | 対象外(g1gcが既定のため) | ➖ |
| `crystal/passive/gap` | HP≤16で金リンゴ・35t咀嚼・2個/命 | `tickBotGap`(80%閾値・実食・HP20想定) | ✅ |
| `crystal/passive/escape/pearl` | 瀕死パール(後方15blk) | `tickEscapePearl` | ✅ |
| `crystal/passive/block` | 防衛黒曜石 | `placeDefenseWall` | ✅ |
| `crystal/passive/crossbow` | 20〜40+m狙撃(0.65s) | 中距離poke(アリーナ前提で意図的差分) | △ |
| `crystal/passive/shield` | 盾構え(トグル) | ボット設定GUIの盾トグル | ✅ |
| `g1gc/hit`+`can_hit` | 剣撃(hitcd7t・hurtTime0ゲート) | 350ms固定剣撃+無敵時間ゲート | ✅ |
| `g1gc/spawncrystal`→`breakcrystal` | クリスタル設置→剣で爆発 | `launchCrystalAttack` | ✅ |
| `g1gc/anchor_tick`→bin/14/15 | アンカー3段(全ラング) | `tickAnchorCycle`(place→charge→爆発) | ✅ |
| `g1gc/defenceplace` | 爆発前グロウストーンの盾 | 省略(自爆免疫済み・意図的差分) | ➖ |
| `g1gc/movement`+`botlogic` | 毎tick足止め+前進+壁ジャンプ | 足止め+前進+ホップ | ✅ |
| `g1gc/pearl` | 敵近接時の攻めパール(pearlcd20) | ドリルCRYSTAL_HIT_ANCHOR+escape | △ |
| `sword/*`(crit/jump/scrit/combo) | 剣BOT本体 | `tickCombatBot`剣経路 | ✅ |
| `sword/bot_mech/{strafe,jump}` | ストレイフ・障害物ジャンプ | ストレイフ反転+停滞ホップ | ✅ |
| `sword/passive/bow/*` | 弓チャージ射撃 | 削除(サーバー裁定: 剣BOTは近接専用) | ➖ |
| `mace_new/*` | メイスBOT一式 | `tickMaceBots` | ✅ |
| `cobwebs/*` | クモの巣/水/溶岩 | disruption+waterSave+lava | ✅ |
| `pot/*` | ネザポットBOT | NETHERITE_POT | ✅ |
| `cart/*` | カートBOT(レール/TNT) | CART | ✅ |
| `binomial_dist/{reach,aim}` | 着弾分布の二項ランダム | ミス率+リーチジッター | △(分布近似) |
| `holeoffense/*` | 穴攻めダッシュ | 穴コンテキスト無しの為部分 | △ |
| `adaptivedifficulty` | 死亡連続数でラング自動シフト(2/3/6/8/10) | **未実装** | ❌次候補 |
| `treats` | ご褒美エンチャント金リンゴ | 未実装(戦闘外) | ❌ |
| `eval`/`allstats`/`prac_stats` | 統計 | `PracticeAnkerStats`(一部) | △ |
| `kits`/`botgear` | BOTキット(ホットバー1-9) | 在庫+**可視スロット選択**(今回) | ✅ |
| `options`/`toggles` | 設定トグル群 | /botadmin+ボット設定GUI | ✅(部分) |
| `rtp`/`hub`/`map`/`init`/`reset` | 空間管理 | 部屋/GUI/コマンドで同等 | ✅ |
| `mark`/`xaniclelib`/`ray` | マーカー/レイキャスト内部lib | Bukkit直操作で代替 | ✅ |
| `npc/function`(群) | BOT生成・テレポート本体 | `spawnCombatBot`/`botPathDirection` | ✅ |

**今回の監査で修正した追加差分**: 金リンゴ閾値50%→**80%(Health..16)**+実食(手に持って1.75s咀嚼→+8HP/5s・吸収2)・**ボットのブロック破壊**(ヘッド上/足元の床/間の壁を1.5sで採掘)・全アクションに**可視スロット切替+スイング**(マップ`player @s hotbar N`+`swing once`の再現)。

**次フェーズ(宣言済み)**: マネキン→**Carpet式パケットプレイヤー**(NMS `ServerPlayer`+擬似接続。paperweight-userdevで実NMS型を使用。本物のインベントリ・スキン・当たり判定・音声を得る。paper 1.21.11 mojang-mappedランタイム前提)。

---

## 2026-09: パケットプレイヤー移行(Phase 1・v1.71.0)

マネキン→**Carpet式パケットフェイクプレイヤー**(本物の `ServerPlayer`)への移行基盤:

- **ビルド**: `io.papermc.paperweight.userdev` 2.0.0-beta.23 + dev-bundle 1.21.11
  (1.20.5以降はランタイムがMojangマップなのでreobf不要)。
- **生成パターン**(fabric-carpet `EntityPlayerMPFake.createFake` 準拠・ソース照合済み):
  `placeNewPlayer(FakePlayerConnection, PacketBot, CommonListenerCookie)` →
  `teleportTo(...)` → `setHealth` → `unsetRemoved` → GameType.SURVIVAL。
  `FakePlayerConnection` は Carpet の `FakeClientConnection` 同型
  (send廃棄+EmbeddedChannel+ハンドシェイクno-op)。
- **`BotBody` シーム**: `PracticeSession.combatBot/maceBot` を介した全AI(51参照)を
  `BotBody` インターフェース越しに駆動。`MannequinBody`(従来・既定)と
  `PacketBotBody`(新)が実装。ダメージ帰属は `instanceof Mannequin` から
  `body.owns(entity)`(uuid照合)へ全面移行 — パケットボットの攻撃は
  本物のプレイヤー damager としてイベントに乗るため。
- **移行の本質**: パケットボットの近接は**バニラの近接パイプライン**(ダメージ・
  ノックバック・クリティカル・無敵時間・見た目の方向)がそのまま走るので、
  `botSwing/botMeleeHit` は `isPacket()` で早期リターン(二重ダメージ防止)。
- **トグル**: `bot.packet-bots`(既定 **false**)= 従来のマネキンで動作。
  有効化はサーバー側検証後。
- Phase 1 対象: 戦闘ボット(sword/crystal/nethpot/cart)。メイス・AFKボットは
  Phase 2。死亡は `PacketBot.die()` オーバーライドでバニラ落下/ドロップを迂回し
  コールバック経由(プレイヤー死亡画面も出ない)。

---

## 2026-09: 「戦わないBOT」の根本原因修正(v1.72.0)

ユーザー報告「昔のクリスタルBOTは死ぬほど変で、まともに戦わなかった」→ 全行照合の結果、
**戦闘フローの根幹を2つ誤実装していた**ことが確定:

1. **回転速度梯子は実在しなかった**: `slowcast.step.max_rotation_per_tick` は
   difficulty関数が値を設定するだけで、**戦闘関数はどこも読んでいない**(全mcfunction照合)。
   マップの視線は `quantum:look` = **`player @s look upon <target> closest [delta N]`**
   の**即時スナップ**(delta = aimラング-1 度の歪み)。旧実装は Easy 1°/tick = 20°/秒で
   回すためストレイフを追跡できず、**剣を振る前に視線が外れたまま**だった。
   → `turnToward` をスナップ式に全面修正 + `lookDeltaDegrees` 梯子(4/3/2/1/1/1°)。
2. **近接ミス率は二重罰だった**: マップは視線の歪みがそのままミス機構(近距離では
   0.6mヒットボックスに当たるので実質ミス0)。旧実装はさらに aim×2.5% の乱数ミスを
   上乗せ(Easy 12.5%)→ プリセットは0%、CUSTOMのみスライダー反映に修正。

「Easyが弱い」の正体も確定: マップのEasyは**追いつけないから当たらない**
(ゆっくり前進・クリスタル6t間隔)のであって、当てる技術が劣るわけではない。

メイスBOTも packet branch 対応(Phase 2の一部)。AFKボットのPacket移行は次工程
(AfkCrystalManager/AfkPracticeManager の BotBody 化 + setGlowing/ポーズ API の
BotBody 追加が必要)。

### v1.72.2 追記(正常な戦いの最後のピース)

- **Packetボットの攻撃は `gameMode.attack`**: バニラで swing はアニメーションのみ。
  以前の packet branch は swing して早期リターン=**空を殴るだけ**(「戦わないBOT」の
  再現になっていた)。`packetMeleeAttack` を新設: 視線スナップ →
  `ServerPlayer.gameMode.attack(bot, target)` → swing。クローンした武器の実ダメージ・
  ノックバック・クリティカル・スイープ・無敵時間・被弾演出が全部本物どおり走る。
  メイススマッシュもバニラが落下距離スケールを攻撃内で計算するので同経路。
- **クロスボウは回復フェーズ専具に**(12〜24blk): マップは HP17+ かつ退避後の遠距離でしか
  構えない。旧実装の5.5blkからの戦闘中pokeは「変な戦い方」の一部だった。

### v1.73.0 追記(正常な戦い=入力駆動の移動)

「正常な戦いをしない」の体の側の解決: マップのフェイクプレイヤーは
`player @s move / sprint / jump`(=バニラの移動**入力**)で動く。当実装は
`setVelocity` で毎tick押し出していた=挙動がプレイヤーの戦い方と別物だった。

- `PacketBotBody.setVelocity` を**入力変換層**に作り替え: AIの平面wishを
  `xxa`(左ストレイフ正)/`zza`(前進正)+`setSprinting`+`setJumping` に変換。
  バニラの加速・摩擦・スプリント倍率で歩く=マップBOTと同一の移動品質。
- 前進wish(≥0.22)=前進入力+スプリント(マップは毎tick `sprint`)/ 後退=後ろ入力/
  横=ストレイフ(歩速)/ Y上向き=ジャンプ入力 / ゼロ=全入力解放(マップの `stop`)。
- マネキン経路は従来の速度押し出しのまま(既定・無効化なし)。

---

## 2026-09: 数値完全一致ループ(完了条件の正式合意)

完了条件=**QuantumBOT実測との数値完全一致**(一歩一歩の移動・設置/破壊タイミング・
使用アイテム・視点まで)。道具は全部用意済み:

1. **qlog**(tools/qlog-datapack): Fabric側観測器。2tick毎に pos/vel/yaw/pitch/hp/
   ground/手持ち + マップのタイマ(hitcd/totem/crystal/obby/pearl)+近傍クリスタルを
   `[q]` 行でコンソールへ。開始はコンソール3行(options/crystal → /player spawn →
   .start=1・タグ自動)。BOT vs BOT も2体目を別名スポーンするだけ。
2. **当側 0.1s サンプラ**: fight trace の `s` 行(同軸: pos/yaw/pitch/hp/ground/hand)。
   試合終了時にコンソールへ全件ダンプ。
3. **tools/compare_fights.py**: 両ログを同軸化し、平均/最大速度・ストライド・視線
   ジャンプ数・アイテム遷移列・クリスタル設置サイクル・トーテムPOP・side-by-side表を
   出力。差分行=修正対象。

サーバー受領後の実行順: Fabric起動→qlog導入→コンソールで試合→[q]回収→
当側で同条件→samples回収→compare→差分修正→(数値一致まで繰り返し)。

### 標準測定シナリオ(数値完全一致の判定手順)

両側とも同じ条件で測る。揺らぎを消すため手順を固定する:

1. **Fabric側**: `qlog`導入 → `/function quantum:options/crystal` → 難易度ラング2
   (INTERMEDIATE相当) → `/player quantumbot spawn ...` → `.start=1` → 30〜60秒戦闘
   (プレイヤーが戦う。または2体目別名スポーンでBOT vs BOT) → `latest.log`の`[q]`行
2. **当側**: crystal部屋 + INTERMEDIATE → 30〜60秒戦闘 → 終了時コンソールの
   `s`行(0.1sサンプル)とtrace行
3. **比較**(tools/compare_fights.py):
   - **硬い数値(一致必須)**: クリスタル設置間隔(crystal_timerラング=6t)、
     設置→爆発(7tヒューズ)、トーテムPOP後の休止(totem_cdラング)、近接 swing間隔(7t)、
     アイテム遷移の順序(剣→クリスタル→剣/テレポート…)、HP80%での金リンゴ開始
   - **統計的一致(±10%目安)**: 平均/最大平面速度、ストライド分布、視線スナップ頻度、
     エイム歪み幅(±3°@INT)
   - 一致しない行が修正対象 → 修正 → 再測定(完全一致までループ)


---

## 2026-09-14: 参照BOT 実測キャプチャ成功 → 数値ループ開始

Fabric 参照サーバー上で **QuantumBOT を実戦させ、`[q]` 生データを取得できた**
(60秒 / 1201行 = 毎tick)。フィクスチャは `docs/parity/fabric_crystal_int2_60s.log.gz`、
抽出は `python3 tools/parity_report.py <log>`。

### なぜ今まで「戦わないBOT」だったか(確定原因)

1. `Pos1` スコア(=**Y座標**)が未更新だった。`xaniclelib:mark` の台クリスタル経路は
   `@s[tag=!airplace] Pos1_difference=..0` でゲートされ、`Pos1_difference` は
   `bot.y - target.y`。未更新(=既定0のまま、または差>0)だと **マーカーが1つも作られず**、
   BOTは `g1gc/pearl` を無限に撃つだけになる。更新主体は `mech_train:tick`
   (mode 100+ 専用)なので、mode 2 では毎tick回す必要がある。
2. `main_tick` のハブ判定 + `load.mcfunction:315` の `schedule map/reset 30t` により
   放置すると必ずハブへ戻る(.start=0 → map/passive)。
3. BOT が奈落・自爆で死ぬと respawn がハブ → `death=0` 維持と位置の戻しが必要。

→ 対策は `tools/qlog-datapack` の **keepalive + ハーネス**(毎tick: `.start=1` /
`Pos1` 更新 / `eval:stats/pos1` / `init/mode` 駆動 / 相手のピン留めと延命)。
マップ側は `quantum:tick` の `main_tick` と `load` の `schedule map/reset` を停止して計測した。

### 実測値(参照側 / mode 2 = crystal・difficulty 2 = INTERMEDIATE)

| 完了条件の硬い数値 | 参照実測 | 判定材料 |
|---|---|---|
| クリスタル設置間隔 | **6t** / 柱上窓 196/197(例外は19t)、近接窓 **199/199・平均6.00t** | 設置198〜200回/60s |
| 設置→爆発 | **1t** (198/198) | マップは `g1gc/crystal_tick` で `crystal_timer≥1` の瞬間に自クリスタルを `damage`(即爆発)。**doc 旧記載の「7tヒューズ」は要再定義**(7tは剣スイング側の値で、この窓では剣経路が未発火) |
| トーテムPOP休止 | 1回 / 16t まで観測(窓切れ) | `totem_cd` rung2=31t の途中。再測定で完走させる |
| 近接スイング | 未発火(`hitcd`=0 のまま) | 剣経路は相手が近接圏に来ないと出ない。別シナリオで取得 |
| アイテム遷移 | `end_crystal`(364t) → `totem_of_undying`(17t) → `end_crystal`(820t) | クリスタル→トーテム→クリスタル |
| HP80%金リンゴ | 未使用 | この窓の HP 低下はトーテムで受けていた |

| 完了条件の統計 | 参照実測(±10%判定の分母) |
|---|---|
| 平面速度 | 移動サンプル平均 **2.74 b/s** / 最大 **6.81 b/s** / 移動割合 1.8%(相手固定のため) |
| 視点 | yaw 平均 1.80°/sample、>15°スナップ 3 回、最大 29.6° |
| BOT HP | 平均 10.32 / 最小 0.0(自爆消耗→トーテム復帰) |
| クリスタル近接率 | 16.5%(寿命1tのため) |

### 参照側の不整合(当側で真似してはいけない点)

- `herobot shieldStunning true perm world` が関数ロード時に失敗
  (`npc:settings/on/stun` / `quantum:options/toggles/stun_on/off` が未ロード)。
  コンソールからは成功するので原因は**コマンド権限(function からの parse 失敗)**と推定。
- `quantum:predicate/vmotion_3.json` は zip 内で **0バイト**(上流不備)。

### 次の手順

1. 当側(Paper + RumilancePractice)で同条件の crystal INTERMEDIATE を 60 秒戦わせ、
   fight trace の `s` 行を回収する。
2. `python3 tools/compare_fights.py <fabric.log> <ours.log>` で差分表を出す。
3. 差分(特に設置間隔 6t・トーテム休止ラダー・アイテム遷移順)をコードで詰める → 再測定。
4. 剣スイング窓と金リンゴ窓は専用シナリオ(相手を近接圏に置く)で追加取得する。


---

## 2026-09-14(続き): 石の地面 + 通常戦闘で クリスタル&アンカー 同時実測

ユーザー指示「地面を深さ100ブロックの石にして、クリスタルだけでなくアンカーも。追加シナリオではなく普通に戦わせる」に
対して、会場を整えて**通常のラウンド**を戦わせ、BOT が自分から両方を使うことを実測した。

### 会場の前提(これが無いと試合が成立しない)

| 要件 | 内容 | 理由 |
|---|---|---|
| 地面 | アリーナ帯 y=−64..30 を **石で充填(深さ約100ブロック)** | 素のマップは y=30 の床に奈落穴があり BOT が落ちて即ハブ送還 |
| 爆破解体 | `herobot explosionNoBlockDamage true perm world` + `explosionNoFire true` | MOD 既定では**クリスタル/アンカーの爆発が床を破壊**し、BOT が自分で掘った穴(y=27)に落ちて戦闘不能になる(5分で実測) |
| 開始 | `qlog:start_round` = `map/start2` + `.start=1`(通常の start 相当) | GUI/プロンプトが無いため |

相手(target)へのピン留め・耐性付与・座標固定は**行っていない**(＝普通の戦闘)。

### 実測(5分 / 6195サンプル / difficulty 2 = INTERMEDIATE, mode 2 = crystal)

| 硬い数値 | 実測 | ラダー |
|---|---|---|
| **アンカー設置 / チャージ** | **54 回 / 54 回**(混在戦闘で両方使用) | — |
| アンカー 設置→チャージ | **4t**(45/54) | `anchor_cd`=4 ✅ |
| アンカー チャージ→爆発 | **4t**(最頻) | `charge_cd`=4 ✅ |
| アンカー 設置サイクル | **最小 12t** | 4+4+4=12 ✅ |
| クリスタル設置 | 36 回(短い群 6t/7t/10t + アンカーと交互の長い群) | `crystal_cd`=6 |
| クリスタル寿命 | 1t(自クリスタル即爆) | ✅ |
| 近接スイング | 41 回 / 最頻 10t | hitcd 7 起点 |
| アイテム配分 | pearl 80.2% / glowstone 4.7% / crystal 4.3% / totem 4.3% / anchor 3.5% / sword 2.8% / obsidian 0.2% | パール主体+クリスタル/アンカー混在 |
| 相手 HP | 20.0 → 最小 2.0(実ダメージ18) | 実戦で機能 |
| BOT HP | 平均 19.0 / 最小 11.9 | |

フィクスチャ: `docs/parity/fabric_normal_crystal_anchor_310s.log.gz`。

### 当プラグイン側への含意(重要)

- **アンカーは「全ラングで使う」が実測で裏付けられた**(difficulty 2 = INTERMEDIATE でも使用)。
  当側の `tickAnchorCycle` は HARD 以上の整理になっていないか要確認 → INTERMEDIATE でも発動が正。
- **アンカーは crystal_timer も再装填する**(`g1gc/place_anchor` が
  `crystal_timer = charge_cd` を実行)。パリティ判定でクリスタル設置間隔を測る時は
  アンカー由来の再装填を除外する必要がある(`tools/parity_report.py` が除外済み)。
- **アリーナは耐爆である必要がある**:MOD 既定でボットの自爆が床を壊し、
  自分で掘った穴に落ちて停止する。当プラグインの crystal/anchor 実装でも
  「自分の爆発で会場が壊れて詰む」経路が無いか確認対象。
- パールが行動時間の 80% を占める(プレッシャー移動の主体)。当側のパール挙動の
  頻度・間隔も次の比較対象。
