# パリティ検証 作業ノート(2026-09-16 時点)

Fabric(herobot MOD)と Paper(この repо の HeroBot 移植)で **戦闘BOT vs 戦闘BOT** を
同じシナリオで走らせ、qlog を突き合わせるための現状・手順・未解決点。

## 1. 環境の立て直し(サンドボックス再起動で /tmp が消えても 1 コマンド)

```bash
tools/parity-runner/env_up.sh --start
```

- delivery ブランチから JDK / Fabric / Paper / 土台プラグインを取り出し、
  `/tmp/mcref/mcserver`(Fabric, 25565, RCON 25575)と `/tmp/testsrv`(Paper, 25566, RCON 25576)、
  パスワードはどちらも `parity`。
- プラグインをこの repо のソースからビルドして Paper に配置
  (`tools/parity-runner/build_plugin.sh`。Gradle ではなく「土台 jar + javac + jar uf」)。
- parity ハーネス データパックを両ワールドへ配布し、`instrument_world.py` で計測カウンタを仕込む。
- ワールドは Fabric 側の `QuantumMap` をコピーして使う(Paper は `level-name=QuantumMap`)。

### 走らせ方

```bash
# 記録つき(推奨): 両側で同じシナリオを回して .gz に落とす
python3 tools/parity_runner.py run --console /tmp/mcref/mcserver/console.in \
  --log /tmp/mcref/mcserver/console.log --side fabric \
  --scenario crystal_k10v11 --seconds 60 --warmup 25 --out parity-logs/fabric_crystal.log.gz
python3 tools/parity_runner.py run --console /tmp/testsrv/console.in \
  --log /tmp/testsrv/console.log --side paper \
  --invoke 'quantum run ' --scenario crystal_k10v11 --seconds 60 --warmup 25 \
  --out parity-logs/paper_crystal.log.gz

python3 tools/parity_compare.py parity-logs/fabric_crystal.log.gz parity-logs/paper_crystal.log.gz
```

`--invoke 'quantum run '` は Paper 用。プラグインは「関数から呼べる verb」を持たないので、
マップの関数を外から叩くときだけ RCON 経由で `quantum run <cmd>` に包む。

## 2. 今日わかった重要な落とし穴(全部実測で踏んだ)

| 事象 | 原因 | 対処 |
| --- | --- | --- |
| Paper の bot が棒立ち・カウンタが 0 のまま | ハーネスのラウンド開始がマップの `map/start3` を呼んでおらず、bot のスコア(`totem_timer` 等)が未設定 →`crystal/tick` が early return | `parity:start_round` が役割タグを貼ってから `quantum:map/start3` を呼ぶよう修正 |
| Fabric だけ正常に見えた | Fabric の bot は同一 UUID で再スポーンし、**前ラウンドのスコアが残っていた**(Paper は毎回まっさら) | 上と同じ修正で両側そろった |
| Paper が数十秒ごとにサーバークラッシュ | `CrystalSelfBlastListener.detonate()` が自分の爆発で再入し `StackOverflowError`(クリスタル連鎖が無限) | プラグイン側で「1 tick 1 個まで / 連鎖深さ上限 / tick 終わりにクリア」 |
| RCON で「0 を入れた直後に読むと 1 万」のような嘘の値 | RCON 応答を ID 照合せず 1 コマンド=1 パケットで読んでいた(1 つズレる) | `rcon.py` を応答 ID 照合+古い応答の破棄に修正 |
| 計測が全部 0 | ラウンド開始が setup 直後ではなく十数秒後になることがあり、0 の窓を測っていた | `compare.py` は `.c_mode` が伸びるのを確認してから計測窓に入る |

## 3. 現在の到達点(crystal_k10v11 / 60 秒 / r8)

両側とも本物のクリスタル戦になった。`parity_compare.py` の判定:

- **一致に近づいた**: charge(10%差)・explode(6%)・pearl(3%)・anchor/charge の tick 間隔(約 30%)・
  hp_low_share(32%)・move_share(30%)。以前は 100% 乖離だった項目。
- **まだ乖離**: `swing` 93/分(Paper) vs 18/分(Fabric)、`speed` 1.60 vs 6.73、
  `x_span` 9.2 vs 49.3、`dist_med` 0.98 vs 3.46、手持ち `totem_of_undying` 24% vs 47% /
  `diamond_sword` 33% vs 4%。
  → **Paper は相手に張り付いて剣を振り続け、Fabric は距離を取って動き回る**。

## 4. 次の一手(優先順)

1. `can_see_target` が Paper で 1 に張り付く件。`quantum:g1gc/can_hit` が参照している
   マーカー / `xaniclelib` の ray 系関数の入力を左右で並べる(計測カウンタは
   `instrument_world.py` にある)。
2. `dist_med`/`speed` の乖離。`player @s move forward` 等の入力が AI 経由で
   どれだけ維持されているか(直接叩くと Paper は 4.4 b/s で正しく歩く。Fabric の手動
   テストは mod の仕様上 `as @s` でないと効かないので比較不能)。
3. キット別(sword / mace / nethpot / pot / crystal の各 10-11-12 組み合わせ)に
   同じ run + compare を回して一覧化する(`gen_pack.py` の `SCENARIOS` に全部ある)。

## 5. カウンタの左右比較(2026-09-16, crystal_k10v11 / 25 秒 / ハーネス修正後)

ハーネス修正後に取り直した値。**分岐の回数はほぼ揃った**(以前は 8 倍差)。

| カウンタ | Fabric | Paper | 比 |
| --- | --- | --- | --- |
| bin27(状態機械) | 905 | 1043 | 1.15 |
| botlogic | 907 | 1061 | 1.17 |
| movement | 663 | 877 | 1.32 |
| look | 240 | 344 | 1.43 |
| crystaltick | 1005 | 1156 | 1.15 |
| canhit | 1007 | 1188 | 1.18 |
| **hit(攻撃実行)** | **15** | **89** | **5.9** |
| mode | 503 | 611 | 1.21 |
| anchortick | 788 | 950 | 1.21 |
| spawncry(クリスタル生成) | 55 | 66 | 1.20 |
| placeobby(黒曜石) | 16 | 10 | 0.63 |
| chargeanchor | 65 | 49 | 0.75 |
| placeanchor | 67 | 48 | 0.72 |
| rounds(ラウンド数) | 1 | 1 | = |

読み方: `.c_hit` は `g1gc/hit`(攻撃を振る)到達回数。`can_hit` は「見えていて
3 ブロック以内・相手が無敵でない」で `hit_decision_without_cd=1` を立てるだけで、
hit 側はそのフラグだけを見る(クールダウンは見ない)。したがって Paper が 6 倍叩くのは
**Paper の bot が相手に張り付いたまま(距離 ~1.0)でいること**の帰結であり、
黒曜石・アンカー設置が少ないのも同じ理由(距離を取って設置する動きが少ない)。

つまり次の本命は「**なぜ Paper の bot が距離を取らない/動かないのか**」。
movement 関数自体は Paper の方が多く呼ばれている(877 vs 663)のに、
変位が小さい(speed 1.60 vs 6.73 / x_span 9 vs 49)。
`player @s move forward` を直接叩くと Paper は 4.4 b/s で正常に歩くので、
入力そのものではなく「毎tickの 停止→入力 の順序・持続」が疑わしい。

## セッション追記 — 自動比較ツールの「嘘の一致」修正と、見つかった乖離

### 1) 比較ツールが嘘をついていた（修正済み）

`tools/parity_compare.py` は **「片側だけ 0 の指標を無条件で一致扱い」**、さらに
**35%×3.5 = 122% までの差を『注意』帯にして VERDICT を「一致」と表示**していた。
このため、たとえば s6_sword の突き合わせで

    item_switch 42.0 → 0.0   (100%差)
    hp_low_share 0.0 → 20.8  (100%差)
    yaw_snap    65.1 → 208.2 (69%差)

でも「VERDICT: 一致（…すべて許容内）※注意: …」と出ていた。修正内容:

- 片側だけ 0 は **乖離**（＝その行動が再現できていない）として扱う
- 注意帯を 1.5 倍に縮小し、**「注意」が 1 つでもあれば VERDICT は「要確認」**にする
  （「一致」は全指標が許容内のときだけ）
- 各行に **生の発生回数 n=** を併記（率だけでは小サンプルを誤読するため）
- サンプル数が 200 tick 未満なら一致判定を出さない（暫定表示）
- `--selftest` を追加: 実ログを土台に「同一→一致」「攻撃を消す→不一致」
  「座標を凍結→不一致」「アイテム切替を消す→不一致」を機械的に検査する

再判定の結果、**過去に「一致」と出ていたすべてのペア（k_sword, r9, s2〜s8）は
不一致**だった（＝再現できていなかった）。ツールが嘘をつかなくなった状態が現在の基準。

### 2) 見つけて直した本物の乖離

| # | 症状 | 原因 | 修正 |
|---|---|---|---|
| 1 | Paper の BOT だけ満腹度が 20 のまま＝自然回復が止まらず、参照では起きる「満腹度切れ→回復停止→死亡」が起きない | `PacketBot.doTick()` が毎tick `getFoodData().eat(1, 0.2f)` で餌を注入していた（参照 herobot にそんな処理は無い） | eat ループを削除。修正後は両側とも満腹度が 20→0 まで減り、HP が 2 前後で押し合う挙動に一致 |
| 2 | Paper だけ `quantum:sword/jump`, `sword/passive/bow/load`, `map/reset`, `map/start`, `g1gc/placeobsidian`, `allstats/advancestats`, `crystal/*` などが**ロードされない** | Paper の `/reload` は dispatcher を作り直す → `player` 動詞が消えた状態で .mcfunction がコンパイルされ失敗。プラグインは数秒後に動詞を戻すが関数は再コンパイルされない | COMMANDS ライフサイクル（＝関数コンパイル前）で必ず動詞を入れ直す。遅れて復旧した場合のみデータパックを読み直す。→ `/reload` 後の失敗 0 件 |
| 3 | アリーナに壁が無く、BOT が見失うと 300 ブロック先（NPC キット台 x≈-625）まで散らばって戦闘が成立しない | ハーネスのアリーナ設計 | `gen_pack.py` のアリーナを壁付き 24×24（x -712..-688 / z 76..100, 外周 y31..44 岩盤）に変更 |

### 3) 現在の正直な判定（s8_sword 60s, 壁あり・満腹度修正後）

- **一致**: swing 115/113, speed 2.63/2.83, move_share 58/75, z_span 17.7/12.3, y_med/y_min,
  dist_close 99.2/100, 硬い数値（アンカー連鎖）は両側 0
- **不一致（次に直す対象）**:
  - `item_switch` 45 → 0（Paper はクモの巣/水/溶岩への持ち替えが一度も起きない）
  - `real_hitcd` が Paper では 1 までしか上がらない（Fabric は 10〜11）→
    `fluid_main` の `real_hitcd matches 1..` ゲートが開かず、アイテム使用系フローが全部止まる
  - `yaw_rate` 4.6 → 11.2 / `yaw_snap` 83 → 222（Paper の方がよく回る・pitch が真上を向く瞬間がある）
  - `hp_avg` 17.5 → 6.5（Paper の A が一方的に削られる）、`totem_pop` 0 → 2

### 4) 使った測り方（再現用）

- `python3 tools/parity_compare.py <fabric.gz> <paper.gz> [--who a|b]` — 正直版の判定
- `python3 tools/parity_compare.py --selftest` — ツール自体のカナリア
- `python3 tools/parity_runner.py cleanup --side both` — 迷い込み BOT の掃除（Paper は `list` を
  素のコマンドで読む必要があった。修正済み）
- 位置を固定した知覚テスト: `function parity:geo_a|geo_b|geo_c` → `GEO` 行
  （両側完全一致を確認: d2t/horiz/vert/can_see/tags）
