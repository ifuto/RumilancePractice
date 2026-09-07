# ボット戦闘関数 対応表（bot/ フォルダ vs 本プラグイン）

`bot/` フォルダの2つの参照物について、戦闘関数（combat behaviour）が本プラグインに
実装済みかを棚卸ししたもの。最終確認: v1.9.7。

- `Quantum's PvP Practice v1.18.zip` — ワールド + `Practicebot` データパック
  （`quantum` / `g1gc` / `pot` / `cart` / `cobwebs` / `sword` / `crystal` / `mace_new` … の mcfunction 群）
- `herobot-1.21.11-*.jar` — **Fabric MOD**（`BotPlayer` / `BotPathing` / `BotPlayerActionPack`）。
  Bukkit 系では直接使えない参考実装。本プラグインは Mannequin 自前 AI（`PracticeService`）で代替しているため
  「関数の移植」という扱いではない。

## Sword ボット（`quantum:sword/*`）— 本家 `PracticeType.SWORD`

| Quantum 関数 | 内容 | 実装状況 |
|---|---|---|
| `combo/hit` + `binomial_dist/reach・aim` | 射程・エイムぶれ付きの攻撃 | ✅ `reachBlocks` ジッター + `aimSpreadDegrees` ミス率（`BotDifficulty` 7段ラダー） |
| `bot_mech/strafe`, `bot_mech/distance` | ストレイフ・距離管理 | ✅ 1.5〜3秒ごとのストレイフ反転 |
| `bot_mech/jump`, `combo/tick` | スプリント接近・ジャンプ | ✅（常時スプリント接近。ジャンプ攻撃は mace 側のみ） |
| `crit` / `scrit` / `pcrit` | クリティカル（跳び・スプリント解除クリ） | ❌ 未実装 |
| `combo/jumpreset` | ジャンプリセット | ❌ 未実装 |
| `passive/escape/pearl` | 瀕死・近接時に後方15ブロックへパール逃走 | ❌ 未実装 |
| `passive/gap` | 被弾時に金リンゴを食べる | ❌ 未実装 |
| `passive/bow/*` | 弓チャージ・射撃 | ❌ 未実装（sword ボットは近接のみ。弓は CART ボットが使用中） |
| `cobwebs/cobweb` | 相手の位置にクモの巣を設置 | ❌ 未実装 |
| `cobwebs/water_main` / `empty_lava` / `fill_lava` | 水バケツ消火・MLG水・溶岩設置/回収 | ❌ 未実装 |
| `shield/disable`（斧） | 斧スイッチで相手の盾無効化 | ❌ 未実装 |
| `passive/main` のトーテム持ち | オフハンドにトーテム | ❌ 未実装 |
| 盾ガード | 盾を構えて被弾を減衰 | △ 構え ON/OFF（`botShieldRaised`）と盾スタンは実装済み。能動的な構え直しはなし |

## Mace ボット（`quantum:mace_new/*`）— 本家 `PracticeType.MACE`

| Quantum 関数 | 内容 | 実装状況 |
|---|---|---|
| `tick` / `combo/tick` | 接近してスマッシュ主体の近接 | ✅ `tickMaceBots` |
| `lunge` | スプリントジャンプ突進 → 落下スマッシュ | ✅ `MACE_LUNGE_*` |
| `wind` | ウィンドチャージ自己打ち上げ → 大スマッシュ | ✅ `MACE_WIND_*`（HARD以上） |
| 落下距離比例ダメージ | スマッシュ補正 | ✅ `maceSmashScale` |
| `far_pearl` + `pearl` | 遠距離の相手にパールで急接近 | ❌ 未実装 |
| `wind_pearl` / `wind_pearl_main` | ウィンド + パール複合 | ❌ 未実装 |
| `elytra` / `elytra1` | エリトラ展開ジャンプ | ❌ 未実装 |
| 盾 | 盾装備 | ✅（構えトグル付き） |

## Crystal ボット（`quantum:crystal/*`, `quantum:g1gc/*`）— 本家 `PracticeType.CRYSTAL`

| Quantum 関数 | 内容 | 実装状況 |
|---|---|---|
| `hardcode/*`（黒曜石→クリスタル→起爆） | 台座クリスタルコンボ | ✅ `launchCrystalAttack`（台座は自動撤去） |
| 撤退・オービット移動 | 被弾後に距離を取る | ✅ `botRetreatUntilMs` 後退 + 3〜6ブロック周回 |
| 難易度 = コンボ間隔 | `crystal_cd` ラダー | ✅ `comboCooldownMs` ラダー |
| `maintot` / `offtot` / `totem` | トーテム保持・POPやり直し | △ POP→位置リセット・全快は実装済み（オフハンド保持の演出なし） |
| `refill` | クリスタル補充 | ✅ `refillCrystals` |
| `passive/crossbow/*` | クロスボウ狙撃 | ❌ 未実装 |
| `passive/escape/pearl` | パール逃走 | ❌ 未実装 |
| `passive/gap` | 金リンゴ | ❌ 未実装 |
| `passive/block/*` | 防御ブロック設置 | ❌ 未実装 |
| `g1gc` アンカー系（`place_anchor` / `charge_anchor` / `spawncrystal`） | アンカー攻撃 | ❌ 未実装（ANKER モードはタイミング計測のみ。アンカーで戦うボットはいない） |

## Pot ボット（`quantum:pot/*`）— 本家 `PracticeType.NETHERITE_POT`

| Quantum 関数 | 内容 | 実装状況 |
|---|---|---|
| `pot`（ハーミングを足元に投げる） | 近接時にハーミングスプラッシュ | ✅ `tickNethPotPotions` |
| 被弾時の自己回復 | HP50%以下で回復 | ✅ ヒール演出（即時回復 + パーティクル） |
| `pot/gap` | 金リンゴを食べる | ❌ 未実装 |
| `pot_count` 上限（2個まで） | スプラッシュ使用制限 | ❌ 未実装（クールダウンのみ） |
| ソードコンボ（`sword/tick` 併用） | 近接は剣AIと共通 | ✅ |

## Cart ボット（`quantum:cart/*`）— 本家 `PracticeType.CART`

| Quantum 関数 | 内容 | 実装状況 |
|---|---|---|
| TNT カートを転がす | 起爆カート | ✅ `TNTPrimed` で代替（fuse 26t） |
| `charge`（弓チャージ） | 弓で攻撃 | ✅ 弓斉射（aim ぶれ付き） |
| 距離キープ | 弓レンジ維持のカイト | ✅ 4〜9ブロック帯 |
| `powered_rail` / `rail` マーカー展開 | レール設置演出 | ❌ 未実装 |
| `defenseplace`（`oak_log`） | 防御ブロック設置 | ❌ 未実装 |

## 環境認識（`quantum:decisions/*`）

`in_cobweb` / `in_player_water` / `in_player_fire` / `airborne` / `lavacmd` など、
相手の状態（糸・水・炎・空中）を見て行動を変える分岐群。
→ ❌ 未実装（本プラグインのボットは距離 / 接地判定 / HP のみ参照）。

## 参考: herobot（Fabric MOD）

`hero.bane.herobot` — 内部に高度な戦闘 AI（パシング、アクションパック、遅延NBK等）。
Paper プラグインから直接呼ぶ API は持たないため対応表の対象外。
ボット挙動の設計参考として `bot/` に保管。

---

凡例: ✅ 実装済み △ 一部実装 ❌ 未実装
