# 参照側(Fabric QuantumBOT)実測フィクスチャ

`docs/bot-combat-parity.md` の「数値完全一致ループ」で使う**参照側の生データ**と、その取得条件。

## フィクスチャ

| ファイル | 内容 |
|---|---|
| `fabric_normal_crystal_anchor_310s.log.gz` | **通常戦闘(仕込みなし)310秒 / 6195行**。石の地面の上で BOT が自分からクリスタルとアンカーの両方を使用 |
| `fabric_crystal_int2_60s.log.gz` | 相手を柱上 y=34 に固定した「台クリスタル窓」60秒(1201行) |
| `fabric_crystal_int2_melee_60s.log.gz` | 相手を隣接(2.5blk, 同一y)に置いた「近接窓」60秒(1201行) |
| 解析 | `python3 tools/parity_report.py docs/parity/<file>.log.gz` |

## 会場の作り方(重要 — これが無いと試合が成立しない)

1. **地面を石で深さ約100ブロックに**: アリーナ帯(x −740..−660, z 64..122)の y=−64..30 を
   `fill ... minecraft:stone replace minecraft:air` で充填。素のマップは y=30 の床に
   奈落の穴があり、BOT が落ちて即ハブ送還される。
2. **`herobot explosionNoBlockDamage true perm world`(必須)**:
   MOD 既定では**クリスタル/アンカーの爆発が地形を破壊**し、BOT は自分で掘った穴に落ちる。
   実測では 5分で y=34〜37 に穴が開き、BOT が穴の底(y=27)で停止して戦闘不能になった
   (この症状は「BOTが戦わない」の一因)。爆破解体を切ると地面が保たれ、ラウンドが継続する。
   併せて `herobot explosionNoFire true` も設定。
3. **相手(target)には何もしない**(ピン留めも耐性も無し)。ラウンドは
   `.start=1` + `map/start2`(クリンナップ)で通常開始する。

## 実測値 — 通常戦闘(仕込みなし・5分)

難易度 = difficulty 2 (INTERMEDIATE) / モード = crystal (mode 2)。ラダー値は
`crystal_cd 6 / obby_cd 4 / anchor_cd 4 / charge_cd 4 / explosion_cd 4 / totem_cd 31`。

| 硬い数値 | 実測 | ラダー予測 |
|---|---|---|
| **アンカー設置 54 回 / チャージ 54 回** | 混在戦闘で両方使用 | — |
| アンカー 設置→チャージ | **4t**(45/54) | `anchor_cd` = 4 ✅ |
| アンカー チャージ→爆発 | **4t**(最頻) | `charge_cd` = 4 ✅ |
| アンカー 設置サイクル(設置→次設置) | **最小 12t** | 4+4+4 = 12 ✅ |
| クリスタル設置 | 36 回(間隔は 6t/7t/10t の短い群と、アンカーと交互になる長い群) | `crystal_cd` = 6 |
| クリスタルの寿命(ec 0→1→0) | 1t ×36 | 即 `damage`(自クリスタル) |
| 近接スイング | 41 回 / 最頻 10t | `g1gc/hit` は hitcd 7 を設定 |
| アイテム時間配分 | ender_pearl 80.2% / glowstone 4.7% / end_crystal 4.3% / totem 4.3% / respawn_anchor 3.5% / diamond_sword 2.8% / obsidian 0.2% | パール主体+クリスタル/アンカー混在 |
| 相手(target)HP | 20.0 → 最小 **2.0**(実ダメージ 18) | 実戦で機能 |
| BOT HP | 平均 19.0 / 最小 11.9 | |
| 平面移動 | 平均 18.1 b/s(パール瞬間移動を含む)/ 移動サンプル 74% | |
| 視点 | yaw 平均 3.75°/tick、>15°スナップ 273 回 | |

### 判明した「BOTが戦わない」根本原因(参照側)

1. **`Pos1_difference ≤ 0` が前提**: `xaniclelib:mark` の台クリスタル経路は
   `@s[tag=!airplace] Pos1_difference=..0` でゲートされ、`Pos1` は **Y座標**を入れるスコア
   (`data get entity @s Pos[1]`)。更新主体は `mech_train:tick`(mode 100+ 専用)なので、
   mode 2 で通常進行すると**マーカーが1つも生成されず、パールを撃つだけ**になる。
2. **爆発が掘った穴に落ちる**(上記 herobot 設定で解消)。
3. `main_tick` のハブ判定 + `load.mcfunction:315` の `schedule map/reset 30t` により
   放置するとハブへ戻る(`.start=0` → `map/passive`)。
4. BOT が奈落・自爆で死ぬと respawn がハブ。`death=0` 維持が要る。

→ 対策は `tools/qlog-datapack` の `qlog:keepalive` / `qlog:start_round`
(毎tick: 相手の延命のみ・`.start=0` のときだけ通常開始を実行)。

## 参照側の不整合(当側で真似してはいけない点)

- `herobot shieldStunning true perm world` が関数ロード時に失敗
  (`npc:settings/on/stun` / `quantum:options/toggles/stun_on/off` が未ロード)。
  コンソールからは成功するので原因は**コマンド権限(function からの parse 失敗)**と推定。
- `quantum:predicate/vmotion_3.json` は zip 内で **0バイト**(上流不備)。
