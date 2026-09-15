# プラグイン側 BOT 実測 #1 — 普通の試合でアンカーを勝手に使うか

参照(本場 QuantumBOT / Fabric)は `docs/parity/fabric_normal_anchor_run8_400s.log.gz` で
「普通に戦わせてもアンカーを勝手に使う」ことが確認済み。ここでは **同じことをプラグイン側
(NARENA の練習 BOT)でも実測**した。シナリオ固定もピン留めも無し、普通の試合のみ。

## 環境(実測)

| 項目 | 値 |
| --- | --- |
| サーバ | Paper 1.21.11-132 (`/tmp/paper-run`, port 25566) |
| プラグイン | NARENA **1.76.10** (`plugin-delivery` の jar, sha256 d96c51cc…) |
| 起動フラグ | `-Drumilance.harness=true`(ヘッドレス測定ハーネス `/narena-harness` を有効化) |
| 地面 | 石 **100 ブロック厚**(`narena-harness ground 40 100 world` → 81×81, y=-37..63, 上は空気24) |
| 部屋 | `narena-harness room r1 CRYSTAL 30 0 64 0`(通常の練習部屋を CRYSTAL の会場として有効化) |
| 相手 | `narena-harness dummy HarnessBot 4 64 4`(カーペット式の偽プレイヤー=放置。攻撃してこない) |
| 試合 | `narena-harness fight CRYSTAL 150 INTERMEDIATE 1` → 通常の join→カウントダウン→試合(シナリオ固定なし) |
| 難易度 | INTERMEDIATE(既定) |
| 取得 | 試合終了時に `[N Arena][BotMatch] trace (…)/samples (…@0.1s)` がコンソールへ |

ポイント: **BOT は本番の入場・ロードアウト・AI ティック経路をそのまま使う**(専用 AI 無し)。
相手が人間の代わりに偽プレイヤーなだけで、試合そのものは普通の BOT 戦。

## 結果 — アンカーを自分の判断で使った

trace(0.1 秒刻み・1.76.10 の生ログから抜粋):

```
7.0s anchor place          ← アンカー設置
7.2s anchor charge         ← グロウストーンでチャージ
7.6s anchor detonate       ← 起爆
8.5s crystal place         ← 以降はクリスタルコンボと剣
```

サンプル(0.1 秒)のアイテム遷移から測った間隔:

| 区間 | 実測(この試合) | 参照 run8 | 判定 |
| --- | --- | --- | --- |
| place → charge | **0.2 s = 4 tick** | 4 tick (mode, 111/116 回) | ✅ 一致 |
| charge → detonate | **0.4 s = 8 tick** | 8 tick (mode) | ✅ 一致 |
| クリスタルの起爆ヒューズ | 7 tick | 7 tick | ✅ 一致 |

つまり「普通に戦わせたら勝手にアンカーを使う」は **プラグイン側でも成立**している。
本場と同じ 4t / 8t のラダーで、シナリオ固定なしに出ている。

## 145 秒間の内訳(この試合と参照 run8 の比較)

| 指標 | 本試合(プラグイン, 145 s) | 参照 run8(Fabric, 403 s) | 倍率(毎分換算) |
| --- | --- | --- | --- |
| アンカー設置 | 1 (0.4/min) | 117 (17.4/min) | **0.02×** ⚠️ |
| アンカーチャージ | 1 | 116 | 0.02× |
| アンカー起爆 | 1 | (236 = アンカー+クリスタル) | — |
| クリスタル設置 | 16 (6.6/min) | 30–33 (4.6/min) | 1.4× ✅ |
| 剣スイング | 107+ (44/min, 上限で切り詰め) | 61 (9.1/min) | 4.8× ⚠️ |
| トーテム POP | 0 | 3 | — |
| HP 平均 | 20.00(無傷) | 16.66 | — |

## 分かった差分(次の作業)

1. **アンカー頻度が桁違い**(0.4/min vs 17.4/min)。理由は実装のゲート:
   `tickAnchorCycle` は `dist <= CRYSTAL_MELEE_REACH (3.0)` で `return` する
   (「近距離は剣で答える」=マップの can_hit 準拠)。ところが本試合の相手は放置なので、
   BOT は 2 ブロックまで詰めて**そのまま密着して剣を振り続ける**(サンプルの 97% が
   NETHERITE_SWORD)。参照 BOT は逆で、**パール主体(持ち手の 76.5%)で距離を保ち**、
   アンカーを回し続ける(設置 17.4/min)。→ 距離の取り方(パールでの離脱/再接近)が
   未実装なのが本質。近距離でもアンカーを回すか、離脱して回すかのどちらがマップ通りかは
   マップの `g1gc` 側の分岐で確定させる必要がある。
2. **トーテム POP 0**(参照 3)。自爆ダメージを受ける距離でアンカーを起爆していない
   (相手の真横で起爆する挙動が少ない)ことの裏返し。
3. 剣スイングが 4.8 倍。距離を詰めて密着する分、攻撃機会が多い。

## 再現手順(サンドボックス)

```bash
# Paper 起動 (harness 有効)
/tmp/jdk21/bin/java -Xmx2G -Drumilance.harness=true -jar server.jar nogui   # cwd=/tmp/paper-run
# RCON (25576)
export RCON_PORT=25576
python3 /tmp/rcon.py "narena-harness ground 40 100 world"
python3 /tmp/rcon.py "narena-harness room r1 CRYSTAL 30 0 64 0"
python3 /tmp/rcon.py "narena-harness dummy HarnessBot 4 64 4"
python3 /tmp/rcon.py "narena-harness fight CRYSTAL 150 INTERMEDIATE 1"
grep "BotMatch" /tmp/paper-run/logs/latest.log
```

注意: `room` の y は **地面の上端(石の1つ上)** を渡す。ここを浮かせると偽プレイヤーが
BOT の上に乗ってしまい、密着状態が固定されてアンカー経路が発火しなくなる(実測で確認)。

## 補足: プラグイン側の前提が揃った経緯(v1.76.8 → 1.76.10)

- 素の Paper ではソフト依存(FAWE / ProtocolLib / LuckPerms)の API 型をクラスロードした
  時点で有効化に失敗していた(`NoClassDefFoundError` @ `FeatureBootstrap:379` /
  `RumilancePractice.enableInternal:110`)。v1.76.8/1.76.9 で呼び出し側にガードを入れ、
  素の Paper でも `NARENA has been enabled` まで到達。
- 致命傷を受けた偽プレイヤーが 0 HP で取り残され(`isDead()` で BOT AI が恒久停止)、
  試合が ACTIVE のまま終わらない問題をハーネス側で回避(毎 tick 延命)。v1.76.10。
- 測定精度のため trace を 0.1 秒刻みにし、swing/hit と目的イベントを別枠上限に。v1.76.11。
