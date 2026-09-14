# qlog — QuantumBOT 完全比較用サンプラ(毎tick)

Practicebot と同じ world の `datapacks/` に置くだけで、QuantumBOT(マップ側フェイクプレイヤー)の
全行動が **毎tick(0.05秒)** サーバーコンソールに流れる:

```
[q] -698.700,31.000,91.922 v=0.000,-0.079,0.000 y=161.7 p=-11.7 hp=1.2 g=1 i=minecraft:end_crystal hit=0 tot=0 ct=2 ob=2 pc=0 cry=0 hpT=2000 pop=16 ec=0 t=24831
```

| 項目 | 意味 |
|---|---|
| `x,y,z` | 位置(0.001 blk分解能)= 一歩一歩の移動軌跡 |
| `v= vx,vy,vz` | 速度ベクトル(加速・摩擦・ジャンプ放物線がそのまま出る) |
| `y= / p=` | 視点 yaw/pitch(スナップ+エイム歪みの実測) |
| `hp / g` | 体力 / 接地 |
| `i=` | 手持ちアイテム(使うアイテムの遷移) |
| `hit / tot / ct / ob / pc` | マップ自身のタイマ(hitcd/トーテム/クリスタル/オブシディアン/パール) |
| `cry` | 近傍(9blk)にエンドクリスタルが存在 |
| `hpT` | 相手(xlib_target)の体力×10(`distance=..30` のときのみ更新) |
| `pop` | BOT自身のトーテムPOP累計(`pops`) |
| `ec` | 近傍16blkのエンドクリスタル数(設置→爆発の実測用) |
| `t` | 連番tick(tick差=そのまま時間差) |

> 旧版は2tick毎(0.1秒)だった。tick精度の硬い数値(6t間隔・7tヒューズ)を測るため
> **毎tickに変更**(2026-09-14)。`tools/compare_fights.py` は両方の粒度を吸収する。

## 試合開始の自動化(実データ確認済み)

```
/function quantum:options/crystal                          # モード選択(mode=2)
/player quantumbot spawn at 11 34 10 facing 0 0 in survival
/scoreboard players set .start start 1                     # (promptトグル既定なら自動でも開始)
```

タグは自動(quantumbot等=xlib_bot、他=xlib_target)。**BOT vs BOT** は2体目を別名で
スポーンするだけ。`latest.log` の `[q]` 行を回収 = 参照データ完成。

## 会場の前提(実測で必須と判明)

- アリーナ帯の地面を**石で深さ100ブロック**(y=−64..30)に充填する。素のマップは
  y=30 の床に奈落穴があり、BOT が落ちて即ハブ送還される。
- `herobot explosionNoBlockDamage true perm world`(+`explosionNoFire true`)を設定する。
  MOD 既定ではクリスタル/アンカーの爆発が床を破壊し、BOT が自分で掘った穴に落ちて停止する。

## 計測ハーネス(重要・これが無いとBOTは動かない)

2026-09-14 の実測で判明: このマップの BOT は**前提スコアが揃わないと一切攻撃しない**。
`qlog:keepalive` + `qlog:tick` の後半がその前提を毎tick維持する:

| ハーネス行 | 理由 |
|---|---|
| `scoreboard players set .start start 1` | ハブ扱い(map/passive)に落ちるのを防ぐ |
| `execute as @a store result score @s Pos1 run data get entity @s Pos[1]`<br>`execute as quantumbot run function eval:stats/pos1` | `Pos1_difference = bot.y - target.y`。`xaniclelib:mark` の台クリスタル経路は `Pos1_difference=..0` が前提。未更新だと **マーカーが1つも生成されず、BOTはパールを投げ続けるだけ**になる |
| `execute if score .mode mode matches 2 run function quantum:init/mode` | `main_tick` を止めている間の AI 駆動(通常は main_tick が担当) |
| `qlog:start_round` | GUI/プロンプトが無いので `map/start2` + `.start=1` を実行して通常ラウンドを開始(`.start=0` のときだけ) |
| `effect give @a[tag=xlib_target] resistance/regeneration` (keepalive) | 相手を延命して長く観測するため(**相手のピン留めはしない**) |

> 通常戦闘の実測(相手を固定しない)では、BOT は pearl 80% / glowstone 4.7% /
> end_crystal 4.3% / respawn_anchor 3.5% の時間配分で**クリスタルとアンカーを混在使用**する。

## マップ側に必要なパッチ(計測の安定化・リポジトリ外)

サーバーの `Practicebot` パックに対して次を入れて計測した(いずれも1行):

- `quantum/function/tick.mcfunction`: `function quantum:main_tick` → コメントアウト
  (ハブ/リセット/難易度再適用のループを止める。AI駆動は上表のハーネスが代替)
- `quantum/function/load.mcfunction`: `schedule function quantum:map/reset 30t` → コメントアウト
  (30t毎の自動リセットで試合が切れるのを防ぐ)

## 比較手順(数値完全一致ループ)

1. Fabric側: 上記 + ハーネスで30〜60秒戦う → `[q]` 行を回収
2. 参照値の抽出: `python3 tools/parity_report.py <fabric.log>`
3. 当側: RumilancePractice で同条件(難易度/モード)を戦う → fight trace の `s` 行を回収
4. `python3 tools/compare_fights.py fabric.log ours.log` → 軸を揃えた差分レポート
5. 差分がある箇所をコードで直す → 4へ(完全一致まで)
