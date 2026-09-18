
> ⚠️ **この文書の前半(過去セッションの「到達点」)には、壊れた比較ツールが出した
> 『一致』が混じっています。** 当時の `tools/parity_compare.py` は
> 「片側だけ 0 の指標を無条件一致」＋「122% までの差を注意帯」にしていたため、
> たとえば s2_sword の `swing 122.1 vs 0.0`・`x_span 10.13 vs 1.23` でも
> **VERDICT: 一致** と表示していました。判定は必ず
> `python3 tools/parity_verify.py <fabric.gz> <paper.gz>` で取り直してください。
> 現在、**過去に出た「一致」はすべて否定済み**(＝まだ一致したシナリオは無い)。

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

python3 tools/parity_verify.py parity-logs/fabric_crystal.log.gz parity-logs/paper_crystal.log.gz
```

`parity_verify.py` が「カナリア自己テスト → リグレッション試験 → 本番判定(who=a/b)」を
まとめて実行して合否(終了コード)を返す。個別に見たいときだけ `parity_compare.py` を直接叩く。

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


### 5) 「嘘の一致」の再発防止(追加)

- `tools/parity_verify.py` — 1コマンドの入口。⓪**監査** ①カナリア自己テスト ②リグレッション試験
  ③本番判定(who=a/b 両方) を通し、**すべて通れば 0 / 乖離があれば 1** を返す。
- **監査(`parity_compare.py --audit`)** — 「乖離があるのに一致と出る指標」が残っていないかを
  全数検査する。両側が同一の指標から出発して 1 指標ずつ 3 倍+10 に壊し、必ず不一致になることを
  確認する(情報表示のみ: `ticks`, `samples`)。この検査で **2 つの穴** が見つかって塞いだ:
  `y_max` が判定対象外だった／アイテム滞在率を **上位10件しか判定していなかった**。
  なお最初の実装は土台に「すでに乖離したペア」を使っていたため、何を壊しても不一致になり
  **抜け道を検出できない無力なテスト**だった(テスト自体の穴。土台を同一指標にして解決)。
  検出力の確認は、わざと穴を開けた版で監査を走らせて FAIL することを見る(`y_max`,
  `items.下位(11件目)の項目` を検出)。
- `tools/parity_runner/fixtures/regression_s2_metrics.json` — 実際に嘘をついた指標
  (s2_sword: swing 122.1 vs 0.0 / x_span 10.13 vs 1.23 / item_switch 104.1 vs 0.0)を
  固定した回帰試験の土台。`--regression` がこれを判定に通し、**不一致と出なければ FAIL**。
- レポート冒頭に **主要指標(スイング/移動/平均HP/アイテム切替/x の広がり/視点スナップ)** を
  `!` `~` `=` 付きで表示。要約だけ見て判断できないようにした。
- `tools/compare_fights.py` は判定を出さない(数値を並べるだけ)と明記。
  `parity_runner.py report` も「片側統計であり合否ではない」と案内するようにした。

現時点の `parity_verify.py` の出力:

    [PASS] カナリア自己テスト
    [PASS] リグレッション試験
    [FAIL] 本番判定 who=a (quantumbot)   → 不一致: totem_pop, item_switch, yaw_snap, yaw_rate, x_span, hp_avg, hp_min, hp_low_share
    [FAIL] 本番判定 who=b (qbot2)        → 不一致: totem, speed_med, x_span, hp_min
    GATE: 不一致 — 再現できていない項目あり

## 6) 進捗報酬の停止 — 最大の乖離の根因と修正 (2026-09-16)

### 症状
Paper 側だけ `real_hitcd` が 1〜2 から上がらない → それを gate にしている
アイテム切替・メイス・トーテム系のフローが **一度も走らない**。

### 根因: **自作プラグインの `AdvancementBlockListener` が全 criterion をキャンセルしていた**
`PlayerAdvancementCriterionGrantEvent` をキャンセルすると、Paper の
`PlayerAdvancements.award` は

```
if (!progress.grantProgress(criterion)) return false;
if (!PlayerAdvancementCriterionGrantEvent.callEvent()) {
    progress.revokeProgress(criterion);   // ← 打ち消し
    return false;                         // ← ここで帰るので…
}
if (wasDone || !progress.isDone()) return flag;
…PlayerAdvancementDoneEvent…
advancement.rewards().grant(player);      // ← 経験値・loot・**報酬関数**が丸ごと飛ぶ
```

つまりキャンセルは「トーストを消す」だけでなく **報酬関数(と経験値・loot)を消す**。
Quantum パックは `data/stats/advancement/hit.json` の報酬関数
`quantum:allstats/advancestats` でヒット時の内部状態を作っているので、Paper では
ヒットパイプラインが根本から止まっていた。Fabric にはこのイベント自体が無い。

### 実測（バニラ進捗で切り分け／両サーバ同一手順）
| 手順 | Fabric | Paper(修正前) |
|---|---|---|
| `XpTotal` → `advancement grant @s parity:xp1`(経験値99) → `XpTotal` | 0 → **99** | 0 → **0** |
| `advancement grant @s minecraft:nether/fast_travel`(経験値100) | +100 | **+0** |
| 報酬関数カウンタ `.c_advfired dbgc` | +1 | **+0** |
| `/function quantum:allstats/advancestats` を直接実行 | real_hitcd 11 | real_hitcd 11（**関数自体は健全**） |

関数も関数マネージャも正常で、**報酬 grant の手前で帰っている**ことが確定。

### 修正
- `AdvancementBlockListener` — **既定オフ**（`config.yml: advancements.block-vanilla: false`）。
  オンにしても (a) `minecraft:` 以外の名前空間（＝datapack の機構）には触れない、
  (b) `recipes/` は触れない、(c) **報酬(関数/経験値/loot/レシピ)を持つ進捗は触れない**。
- `FeatureBootstrap` は設定値を読んで登録する。
- `docs/parity/session-notes.md`(本書) と config.yml のコメントに事故の記録を残した。

### 修正後の実測（sword_k10v11 / 75s 同時ラウンド）
| 指標 | 修正前 Paper | 修正後 Paper | Fabric(参照) |
|---|---|---|---|
| `real_hitcd` 最大 | 1〜2 | **10** | 10 |
| スイング/分 (who=a) | 0（過去の s2 系） | **117.7** | 109.7 → **一致** |
| トーテム POP (who=a) | 0 | 0.8 | 0.8 → **一致** |
| x の広がり (who=a) | 1.23 | 2.89 | 3.89 → **一致** |
| 平均HP (who=a) | 6.5 | 11.5 | 5.7 → まだ不一致 |

`parity_verify.py` 判定: 監査 PASS / カナリア PASS / リグレッション PASS、
本番は who=a `item_switch, yaw_snap, yaw_rate, yaw_med, speed_med, hp_low_share` /
who=b `speed, speed_med, hp_min` のみ不一致（以前は swing から何もかも不一致だった）。

### ハーネスの落とし穴（今回踏んだ）
- **配備済み `parity` パックが古いと `who=` 付きサンプラ行が出ない**（`minecraft/tags/function/tick.json`
  が無い世代）。`parity_runner.py run` は「0 [q] lines」と出して無言で空ログを書くので、
  計測前に `gen_pack.py` → `parity_runner.py deploy <world>/datapacks` を必ず通すこと。

## 7) ノイズ床テスト — 「まだ直すべき乖離」と「カオス」を分ける (2026-09-16)

BOT vs BOT はカオスなので、**同じエンジン同士**でも勝敗が入れ替われば指標は振れる。
Paper の移植を疑う前に「エンジン内で再現するか」を測るのが正しい順序なので、
`parity_compare.py --noise` を追加した。

    python3 tools/parity_compare.py --noise \
        <fab1> <fab2> <pap1> <pap2> --who a

同一エンジン2ランで振れる指標 =**ノイズ**（判定に使えない）、
両エンジン内で再現するのに Fabric↔Paper で食い違う指標 =**本物の乖離**として分離する。

### 実測（fix2=75s×2エンジン, long1=180s×2エンジン）

| who | ノイズ（同一エンジンで振れる） | **本物の乖離（要修正）** |
|---|---|---|
| a (quantumbot) | totem_pop, yaw_snap, speed_med, move_share, z_span, hp_avg, hp_min, hp_low_share | **item_switch, speed, yaw_rate, yaw_med** |
| b (qbot2) | speed_med, x_span, z_span, hp_avg, hp_min | **speed, move_share** |

一致（両エンジンで再現）: swing, crystal/anchor/charge/explode/pearl/totem の各レート,
anchor_gap, charge_explode_gap, x_span(a), y_med/y_min/y_max, dist_med/dist_close/dist_far,
hp_low_share(b), item 配分(sword/cobweb/bucket/water/lava)。

つまり **残っているのは「移動（speed/move_share）」と「視点（yaw_rate/yaw_med）」、
そして who=a のアイテム切替(item_switch)** の3系統だけ。以前のように swing や
トーテムが丸ごと 0 という状態ではない。

### 副次的な発見（次の調査の入口）
- `BotActionPack.onUpdate()` は `HeroBotPlayer#doTick()` から毎tick呼ばれており、
  参照(mod)の「HEAD of tick」と同じ順序（autoJump → look補間 → 遅延アクション →
  アクション → 移動入力）。ロジックの写し間違いではない。
- `look upon … closest delta N` は **毎tick呼ばれる**ので、補間は毎tick
  「残りの 1/N だけ寄る」＝ 指数的な追従になる。実測の Paper の yaw は
  まさにその滑らかな追従（-38→-30→…→0）。**Fabric 側は同じコマンドで yaw が
  ほぼ動かず、90度単位で飛ぶ**（-180 のまま 981/3599 tick）。→ 次はここを詰める。
- `getTarget()` も参照と条件が違う（参照: 1本目が **MISS** なら即返す／こちらの移植:
  1本目が **BLOCK** なら即返す）。属性の対応（BLOCK/ENTITY interaction range）と
  あわせて要確認。
- item 切替の実体は `quantum:cobwebs/*`（cobweb/water/lava の各フロー）。計器化すると
  fluid_main と water_main は **両エンジンで走る**のに、内側の fill/empty_bucket と
  cobweb だけ Paper 0 回（Fabric 7/18/3/8/14 回）＝**decision 系スコアの差**。
  道具: `tools/parity-runner/instrument_flows.py`（両worldに計器を入れ/revertする）と
  `read_counters.py`、`probe_slot.py`（hotbar verb のライブ確認。Paper は読み取りを
  プレフィックス無しで送ること）、`probe_move.py`。

### ハーネス運用メモ（今回踏んだ地雷）
- `hotbar` verb は **両エンジンで正常**（Paper: lava_bucket/8 → `player quantumbot hotbar 5`
  → golden_apple/4 → 9 → lava_bucket/8）。もう item 切替の器を疑わない。
- Fabric は素の `playerspawn` 直後だと bot が**動かない**（`player @s move forward` を
  投げても位置が 1mm も動かず、`look north` もほぼ効かない）。ただし **entity 自体は tick
  している**（levitation を与えると 0.18/tick で上がる＝vanilla どおり）。つまり止まって
  いるのは mod の **action pack** で、これはラウンドが武装した後（harness の setup 実行後）
  にしか回らない。→ **verb 単体の性能比較は「試合を回している最中」にしか意味がない**。
  Paper は素の状態でも verb が効くので、**そのまま並べると Paper だけが動いて見える**（誤診のもと）。
- 一時的に Fabric が 1〜2 TPS まで落ちることがある（アリーナ充填直後など）。落ちている間は
  wall-clock の観測（0.5s スリープで位置を読む等）が全部嘘になる。TPS は
  `scoreboard players get pari_clock parity_t` を 5 秒あけて 2 回読めば測れる
  （両サーバ静穏時はどちらも 20.0 TPS）。比較に使う qlog は tick 基準なので影響を受けない。

## 8) 訂正: 「Paper は進捗報酬を一切適用しない」は誤診だった (2026-09-16)

§6 の切り分け表のうち、**経験値プローブの行（`parity:xp1` で 0 → 0）は信用できない**。
`/advancement grant` は**冪等**で、既に取得済みの進捗をもう一度 grant しても何も起きない。
当時のプローブは前の実験で既に grant 済みのものを使い回していたため、Paper 側だけ
「報酬が 0」に見えていた。

**新しい進捗で測り直した結果（今回）**

| 手順 | Fabric | Paper |
|---|---|---|
| `parity:auto1`(tick トリガ, 経験値99 + 報酬関数) を revoke → 再取得 | `XpTotal` +99 / `.c_advfired` +1 | `XpTotal` +99 / `.c_advfired` +1 |

つまり **Paper の報酬経路（経験値も報酬関数も）は正常に動く**。したがって §6 の根因の説明で
「報酬 grant が丸ごと飛ぶ」と書いた部分の*メカニズム*は正しい（リスナーが criterion を
キャンセルすると grant まで到達しない）が、**「Paper では報酬が死んでいる」という一般化は誤り**。
実際に効いていた証拠は「修正前はラウンド中の `real_hitcd` が 1〜2 で止まり、修正後は 10 に
届く」という**ラウンド内の挙動**であって、単発の grant プローブではない。

教訓: **進捗の報酬を試すときは毎回まっさらな進捗を作る**（revoke → grant、あるいは新規 ID）。

### ついでに直した計測の穴
- `parity_runner.py` のログ取り出しを**時刻ベース**にした。サイズのオフセットは
  サーバー再起動でログが差し替わると無意味になり、「0 [q] lines」で計測ごと消える
  （実際に一度やらかした）。
- サンプラ `parity:sample` のマクロに新しい `store` を足すと、スコア未設定のときに
  マクロ変数が欠けて **`$say` 行ごと落ちる**（＝以後 [q] が一切出ない）。判断スコアの観測は
  サンプラに相乗りせず、`parity:dec`（条件成立時だけ `say [d] ...=0/1+`）で独立に出す。

## 9) 落下距離(fall_distance)が Paper 側で 0 固定 — 水・クモの巣フローが全滅していた (2026-09-16)

### 9.1 症状(実測)
`dec8`/`dec9`/`dec10`(sword_k10v11, 45〜60 秒)で、**Fabric だけがアイテムを切り替える**:
- Fabric who=a の手持ち: `diamond_sword 98.4%` + `cobweb 0.7%` + `bucket 0.5%` + `water_bucket 0.4%`
- Paper  who=a の手持ち: `diamond_sword 100.0%`(3 ラウンド連続で再現)
- `item_switch` は Fabric 27.6〜28 回/分、Paper 0 回。

### 9.2 ゲートを1つずつ数えた(本命の特定)
Practicebot パックの該当行の条件部だけを複製して `.g_* dbgc` でカウント(1 ラウンド、両エンジン同時)。
| 条件 | Fabric | Paper |
|---|---|---|
| `quantum:vmotion_m0` / `fall_distance1`(`Δy≥0 かつ fall_distance≥0.1`) | **234 / 234** | **0 / 0** |
| `real_hitcd 1..` | 2355(45 秒) | 1007 |
| `in_range 0` | 5640 | 3591 |
| `hotbar.7 water_bucket` / `hotbar.8 lava_bucket` | 2656 / 2701 | 2600 / 2600 |
| `.water toggles=1` | 2704 | 2654 |
| マーカー(`in_player`/`lava`/`water`) | 0 | 0 |
| `tag util` が付いた tick | 1525 | **0** |

→ `fluid_main` の `tag util` 付与は
`real_hitcd 1..` **かつ** `@p[tag=xlib_target,predicate=quantum:vmotion_m0]` **かつ** `in_range=0`。
他が全部一致しているので、**犯人は `vmotion_m0`(= `fall_distance`)**。
`tag util` が無いと `water_main` / cobweb / lava の各フローが動かず、
`empty_bucket` / `fill_bucket` / `cobweb` の `player @s hotbar N` も走らない = アイテム切替 0 回。

### 9.3 根因
参照 MOD の bot は **偽クライアント接続**(`bot/connection`)を通るので、サーバーの通常の
プレイヤー移動処理が回り `Entity#fallDistance` が更新される。Paper 側の疑似プレイヤーには
その経路が無いため、`data get entity <bot> FallDistance` に値が出ない=常に 0。
(Paper の `ServerPlayer#checkFallDamage` は `Entity#checkFallDamage` を呼ばないことも確認:
カウンタ/バイトコード両面で `Entity.checkFallDamage` が一切現れない。)

### 9.4 修正
`HeroBotPlayer#doTick` の末尾で、バニラの蓄積規則をそのまま移植(`updateFallDistance`)。
- 空中かつ `Δy < 0` のとき `fallDistance -= Δy`(降下分を加算)
- `onGround()` なら 0 に戻す

### 9.5 結果(dec15/dec16, 修正後)
- Paper の `g_fd1`/`g_m0`: **0 → 222**、`tag util`: **0 → 29**、
  手持ち: `diamond_sword 99.4%` + `cobweb 0.2%` + `water_bucket 0.2%` + `bucket 0.2%`
- ノイズ床(dec15 vs dec16 の同一エンジン同士)で再判定した「本物の乖離」は
  **`yaw_rate` と `hp_avg` の 2 つだけ**に縮んだ。`item_switch` / `speed_med` / `yaw_snap` /
  `yaw_med` / `hp_low_share` / `totem_pop` は同一エンジンでも同程度に振れる=カオス。
- **一致に昇格**: `swing` / `speed` / `move_share` / `dist_med` / `items.*`(剣・クモの巣・水・バケツ・
  溶岩バケツ)/ スパン類 / `hp_min` / `dist_close` / `dist_far` / `pearl_gap` /
  crystal・anchor・charge・explode・pearl レート。

### 9.6 訂正(このセッションで消した疑い)
- **「Paper は進捗報酬を一切適用しない」は誤診**(既出)/**既得進捗の罠**だった。
  `advancement revoke` してから `grant` すれば Paper も経験値 99 を適用する(198→297)。
- `BotActionPack.getTarget` の BLOCK/MISS 差は**存在しない**。参照の `getTarget` も
  「ブロックに当たれば BLOCK、そうでなければエンティティ再レイ」で完全一致(バイトコード確認)。
- `stop`(→`stopAll`)/`look`(`% 360.0f`)/`hotbar`/`setSlot`/移動入力/`onUpdate` は参照と一致。

### 9.7 計測用の一時配線(撤去済み)
`quantum:gates`(ゲート単体カウンタ)と `parity:tick` の呼び出しは撤去済み。
同種の計測をするときは、**条件部だけを複製した行**を足す(実行部を差し替えない)と
「どの条件が落ちているか」を切り分けられる。Paper はワールド保存前に落ちると
スコアが巻き戻るので、カウンタは**ラウンド直後に読む**こと。

## 10) 環境リセット後の再測定 (2026-09-16 後半) — Sword は一致圏、Crystal に残り

### 10.1 何が起きたか
サンドボックス再起動で `/tmp`(JDK・Fabric・Paper・ワールド)とセッションのコミットが消えたため、
`tools/parity-runner/env_up.sh --start` で**ゼロから再構築**した(ワールドは Fabric 側を Paper に複製)。
再構築時に `build_plugin.sh` のソース一覧へ `AdvancementBlockListener.java` を足した
(`FeatureBootstrap` が `CONFIG_PATH` を参照するため、抜けているとコンパイルが通らない)。

再構築後も**落下距離の修正は入っている**(`HeroBotPlayer#doTick` 末尾の `updateFallDistance()`)。

### 10.2 新しい道具(コミット済み)
- `tools/parity-runner/pair.sh <tag> [scenario] [seconds] [warmup]`
  — Fabric と Paper で**同時に**1ラウンド走らせて `parity-logs/<tag>_{fabric,paper}.log.gz` を作る。
- `tools/parity-runner/count_round.py <tag> [scenario] [seconds] [warmup]`
  — カウンタを 0 に戻す → 1ラウンド → **ラウンド直後**に左右のカウンタ/スコアを並べる。
  注意: 0 に戻すのは**1コマンドずつ**(まとめて 1 行にすると構文エラーで無効)。
  Paper の `scoreboard players get` に `quantum run ` を付けない(付けると返事が化ける)。
- `tools/parity-runner/watch_round.py [scenario] [seconds] [interval]`
  — ラウンド中を数秒おきに生で観測(`.start` / `pari_round` / `.c_rounds` / 各BOTの hp・hitcd・state・tempcrit)。

### 10.3 Sword: 実測結果(60 秒・warmup 25)
| シナリオ | who=a (quantumbot) | who=b (qbot2) |
|---|---|---|
| `sword_k10v11` (dec18/dec18b) | **本物の乖離なし**(z_span, hp_avg, hp_min, hp_low_share は同一エンジンでも振れる=ノイズ) | **本物の乖離なし**(z_span のみノイズ) |
| `sword_k10v10` (dec19/dec19b) | `hp_low_share` **のみ本物** | **VERDICT: 一致** |

- dec19 の who=a だけが残った理由を HP の毎tick差分から分解すると:
  **被弾回数 19 回(Fabric) vs 16 回(Paper)**、1発あたりのダメージは両者 1.7〜1.8 で同一
  (クリティカル無し)。=「当たる回数」が違う。回復回数も 24 回 vs 20 回で、Paper の方が長く生き残る。
- つまり Sword は「攻撃の当たり数」だけが残差。決定フロー(`c_bmlogic_qa/q2`、`c_look`)の回数は
  0.96〜1.18 倍でほぼ同じ、`c_look` は 900 vs 860(45 秒)とほぼ一致。

### 10.4 クモの巣の件(質問への回答)
- 剣モードのキットの中身は**ワールドのチェスト**が決めている:
  `quantum:bin/3` が `.mode=1`(剣) のとき `kits/kit10|11|12` を `positioned -657 31 89|90|91`
  から呼び、その関数は `~1 ~ ~` のチェスト(実体は **-656 31 89|90|91**)から
  防具・オフハンド・ホットバーを移す。
- 実測(両エンジン一致): kit10 のチェスト = 20個(ダイヤ防具一式 + トーテム + リンゴ + **クモの巣**)、
  kit11 = 22個(+盾・弓・矢・水バケツ)、kit12 = 27個(ネザライト + 釣り竿 + スプラッシュポーション)。
- ラウンド中の実際の手持ち(両エンジン完全一致):
  quantumbot(kit10) = 剣・リンゴ・**クモの巣** / qbot2(kit11) = トーテム・風玉・クモの巣・剣・リンゴ・斧・パール・水・溶岩。
- したがって「剣モードなのにクモの巣」は **BOTの不具合ではなくキットのチェストの中身**。
  BOTは「インベントリにある物しか使わない」仕様どおりに、持っているから使っているだけ。
  剣と防具だけにしたいなら、そのチェストの中身(キット定義)を差し替えるのが正しい直し方。

### 10.5 Crystal: 実測結果(60 秒・warmup 25)
- ラウンドは成立(両BOTが結晶・アンカー・グロウストーンを実際に使う。hp_min 0.0 まで落ちる)。
- ノイズ床(cry1 vs cry1b)で残った**本物の乖離**:
  - who=a: `crystal`(設置レート), `swing`, `yaw_snap`, `yaw_rate`, `hp_min`, `dist_med`, `items.diamond_sword`
  - who=b: `crystal`, `swing`, `hp_avg`, `dist_med`, `items.diamond_sword`
- いちばん大きいのは **剣スロット滞在率**: Fabric の a は結晶 24〜28%・グロウストーン 13〜15% なのに対し、
  Paper の a は **剣 25〜27%**・結晶 14%。Paper の方が剣を持ったままの時間が長い。
- 世界側カウンタ(45 秒・2回)? ばらつきが大きいが、繰り返し同じ向きに出たのは:
  `c_qa_bin27` 0.85 / 0.84、`c_qa_ctick` 0.80 / 0.84(= Paper の A の脳が結晶tickを回す回数が 16〜20% 少ない)、
  `c_obbycheck` 1.85 / 2.37(= Paper の方が黒曜石チェックを 2 倍回る)。
  次はこの2つ(剣スロット滞在と `bin/27` 到達数)を追う。

### 10.6 副次的に確定したこと
- `data get entity <bot> Inventory[{Slot:103b}]` は両エンジンで "Found no elements" = **プレイヤーNBTの
  防具スロットはこの書き方では取れない**。防具を見たいときは別手段が要る。
- `.mode` は剣=1 / クリスタル=2。`.tempaim` はラウンド終了時に 1〜3 のいずれか(乱数)なので判定に使わない。
- ラウンド間で BOT は消える(`list` が 0 人)。スコア(`kit` 等)は残るので RCON で読める。

### 10.7 視覚系(視線判定)の実測 — レイ/セル判定は両エンジン一致
結晶モードで Paper が剣で殴りに行く原因を `g1gc/can_hit` の条件別カウンタで分解した(45 秒 ×2 ラウンド):

| 条件 | run1 f/p | run2 f/p | 向き |
|---|---|---|---|
| `can_see_target==1`(`c_cansee`) | 1403/2261 | 1223/1859 | **Paper 1.5〜1.6倍** |
| 相手が3block以内・hurtTime=0(`c_hurt0`) | 357/504 | 300/539 | Paper 1.4〜1.8倍 |
| `hit_decision_without_cd==1`(`c_hitdec`) | 79/164 | 62/150 | **Paper 2.1〜2.4倍** |
| 実際の `g1gc/hit`(`c_hit`) | 77/162 | 60/147 | **Paper 2.1〜2.4倍** |

= Paper は「相手が近い」状態が多く、剣ブランチに入る回数が約2倍。クリスタルの設置(`c_spawncry` 0.78倍)が
そのぶん減っている。

その `can_see_target` を作っている `allstats/newstats.mcfunction:13` は
`@a[tag=xlib_target,distance=..3.2]` かつ `xaniclelib:check/raycast4` が真のとき 1 を立てる。
そこで**距離スイープの直接実験**(世界に検証用関数を置き、`quantumbot` と `qbot2` を固定座標に tp して
`raycast4`/セル判定を距離ごとに評価。1 関数呼び出し内なので map の介入は入らない):

| 距離(block) | 0.0 | 0.5 | 1.0 | 1.5 | 2.0 | 2.5 | 3.0 |
|---|---|---|---|---|---|---|---|
| Fabric `raycast4` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| Paper `raycast4` | 1 | 1 | 1 | 0 | 0 | 0 | 0 |

→ **レイキャストと `dx=0` セル判定は両エンジンで完全一致**(回答: 視覚系プリミティブは一致。
食い違うのは「間合い」= 距離の取り方で、その結果として `can_see_target` の頻度が変わる)。

実験時の注意: `playerspawn <新しい名前>` で作った Bot は map 側の処理で別の場所へ飛ばされる/死ぬ
(Fabric は world spawn 落ち、Paper は死亡)。**制御実験は `quantumbot`/`qbot2` を使い、
1 つの function 呼び出しの中で tp → 計測 → 元に戻す**のが正しい(間に RCON を挟むと tick をまたいで map が動かす)。

## 10.8 tick 位相の修正 — 剣の残差(hp_low_share)を潰した本物のバグ (2026-09-16)

### 症状と原因
剣ミラー (`sword_k10v10`) の残差は `hp_low_share` だけで、dagger の与 hit 数が
Fabric 19 / Paper 16 (命中平均は同じ 1.77) と食い違っていた。原因は**ボットを tick の
どこで動かしていたか**:

- 参照 (Fabric mod): bot は player list の `ServerPlayer` なので `PlayerList.tick()`
  (= tick の内側) で `doTick` が走る → サーバーの `#minecraft:tick` 関数は
  **同じ tick の状態**を見る。
- 以前のプラグイン: `BukkitScheduler#runTaskTimer(1, 1)` = scheduler の heart は
  tick の *終わり*。つまり関数は常に**1 tick 前の状態**を見ていた。
  → 攻撃/クールダウンの判定が参照より 1 tick ずれ、同じ 45 秒でも与 hit が減る。

### 修正
- `HeroBotSettings.tickPhase` (quantum.yml `herobot.tick-phase`, 既定 `tick-start`)
- `HeroBotRegistry.ensureTicker()`: `tick-start` のとき `ServerTickStartEvent`
  (tick の先頭 = 関数タグより前) で `doTick` を回す。`scheduler` を指定すれば旧挙動。
- 起動ログに `tickPhase=` を出す。

### 検証 (tools/parity_compare.py --noise, 同一位相の 2 本をノイズ床に)
| ラウンド | who | 本物(Fabric↔Paper で食い違う指標) |
|---|---|---|
| dec19/dec19b (旧: scheduler) | a | `hp_low_share` |
| swfix1 (新: tick-start) | a / b | **なし** (a は z_span/hp_avg/hp_min/hp_low_share がノイズ判定) |
| swfix2 (新: tick-start) | a / b | **なし** (a は totem_pop/hp_min がノイズ、b はノイズなし・全 30 指標一致) |

カウンタも同位相に揃った: `c_look` 1301/1303, `c_bmlogic_qa` 1301/1300 (比 1.00)。
→ **剣は残差なし**。以降の計測は `tick-start` が前提。

## 10.9 「未実装コマンド 3 個」の正体と /reload 復旧の修正

### 正体 = 未実装ではなく *vanilla のプラグイン以前コンパイル*
Paper の起動ログ 12:34:27 に出る `Failed to load function ...` は約 20 件あり、その中に
`quantum:crystal/hardcode/totem` / `quantum:sword/passive/bow/load` / `mech_train:escape/main`
が含まれる。順序は 12:34:27 (vanilla の関数コンパイル) → 12:34:29 (プラグイン読込) →
12:34:33 (`re-added the herobot verbs` + `loaded 905 function(s) and 5 tag(s)`, failures 0)。
つまり **Paper ではプラグインが読まれる前に datapack の関数がコンパイルされる**ので、
`player …` を呼ぶ行はこのときだけ落ちる。プラグインの自前ローダが直後に 905 個
(failures 0) を入れ直すため、実行時は 3 つとも存在する (RCON `function` が
"Running function" を返し、カウンタも両エンジンで同じ値を示す)。

機能の直接確認 (脳の無い検体 bot を作り、同 tick で書き→読み):
| verb | Fabric | Paper |
|---|---|---|
| `player <bot> hotbar 1..9` → `SelectedItemSlot` | 0,1,2,3,4,5,6,7,8 | 0,1,2,3,4,5,6,7,8 |
| `player <bot> move forward` (0.5s の Δxz) | 2.66 | 2.86 |
| `player <bot> jump` (静止時) | 変化なし | 変化なし (両者同一挙動) |

結晶シナリオでの呼び出し地点のカウンタ (`c_esc_main*`, `c_esc_ledge`, `c_bowload`, `c_hctotem`) は
**両エンジンとも 0** = そのシナリオでは到達しないので、これらの関数が差の原因にはならない。

### 本当にあったバグ = `/reload` で関数が壊れる (Paper のみ)
サーバーの `/reload` は「新しい dispatcher を作る → その dispatcher で関数をコンパイルする」
順で進むため、`player …` を含む行は必ず失敗し、5 秒おきの watchdog が拾うまでの間
(=非同期の読み直しと競合した回) は動詞なしの状態で計測されていた (cry6 の Paper 側ゼロ)。
修正: `ServerResourcesReloadedEvent` (読み直し完了後に発火) で動詞を戻し、
**自前ローダでパックを入れ直す** (`QuantumRuntime#reinstallAfterResourceReload`)。
ここで `reloadResources` を投げないのが重要 (自分自身を無限に呼ぶ)。

## 10.10 BOT の装備をサーバーキット (`/botadmin`) から与える (PvP サーバーとしての本線)

**指摘**: このプラグインは PvP サーバー用なので、BOT の装備は参照パックのキットチェストでは
なく **サーバーのキット** (`/botadmin` の紐づけ) から与えるのが正しい。これまで Quantum BOT は
マップの `kits/kitN` (チェスト) だけで装備が決まっており、`/botadmin` の紐づけは
practice 側 (`PracticeService`) にしか効いていなかった = **BOT にサーバーキットが届いていない**。

**修正**:
- `PracticeService#applyServerKitToBot(Player, kitName)` — サーバーキットをそのまま着せる
  (`kitService.apply` = 装備/最大体力/満腹度/ゲームモードまで本番と同じ経路)。
- `QuantumRuntime`:
  - `setPracticeService(...)` で practice/kit サービスを繋ぐ (`FeatureBootstrap`)。
  - `bot.mode: SWORD|MACE|CRYSTAL|NETHERITE_POT|CART` を書くと
    **`/botadmin <モード> <キット>` の装備紐づけ**を、`bot.kit: <キット名>` を書くと
    **キット直指定** (`/botadmin <botkit> <arenakit>` の botkit と同じ発想) を使う。
  - 適用点は `/quantum spawnbot` と **モード切替の直後** (`setOption` =
    `quantum:options/<name>`)。後者が要るのは、モード切替がマップのキットチェストから
    装備を読み直すため (サーバーキットがチェストで上書きされるのを防ぐ)。
  - 両方空なら何もしない = キットチェスト (パリティ計測はこのまま)。
- `quantum.yml` に `bot.mode` / `bot.kit` を追加 (既定は空)。

**実機確認 (Paper, 25576)**:
```
/botadmin SWORD sword_only      → 紐づけました: SWORD BOT → キット 'sword_only'（装備）
/quantum spawnbot               → [Quantum] bot quantumbot wears the server kit 'sword_only' (mode SWORD, /botadmin)
hotbar: slot0=diamond_sword slot2=golden_apple x8 / armor: diamond 4点 / offhand: 空
クモの巣: 無し   (キットに無いものは BOT も持たない)
```
キットは `kits.yml` の `kits.sword_only` (剣+防具+リンゴのみ) で定義。**「剣モードは剣と装備だけ」は
チェストを触らずサーバーキットの紐づけで表現できる** = 以降の PvP サーバー運用はこれで行く。

## 10.11 クリスタル: 差の実体と「初期状態が揃っていない」問題 (2026-09-16)

### 用語
以後「結晶」ではなく **クリスタル** と書く(ユーザー指定)。順序は クリスタル完成 → メイス。

### クリスタルで実際に起きている差 (cry9/cry10/cry11, tick-start 位相)
| 指標 | Fabric | Paper | 比 |
|---|---|---|---|
| `c_hit` (殴り) | 86 | 209 | **2.4×** |
| `c_hitdec` (`hit_decision_without_cd=1` の tick 数) | 88 | 211 | 2.4× |
| `c_cansee` (`can_see_target=1` の利用) | 1401 | 2104 | 1.5× |
| `c_hurt0`/`c_block` | 300 | 1395/1317 | **4.4-4.7×** |
| `c_placeobby` | 42 | 13 | **0.31×** |
| `c_spawncry` | 174 | 63 | 0.36× |
| `c_g1move_qa` | 796 | 2066 | 2.6× |

トレース率(1tick サンプルの `1` の割合, cry1 60秒):
- `can_see_target=1`: a 33.3% → **74.2%** / b 36.6% → **75.8%**
- `hit_decision_without_cd=1`: a 4.1% → **12.2%** / b 2.4% → **11.1%**
- `OnGround=1`: a 74.3% → **91.5%** / b 77.9% → **93.3%** (Fabric は 4分の1 空中)
- 手持ちアイテム: Fabric a = end_crystal 28.7% / sword **0%** に対し Paper a = **diamond_sword 25.0%** / crystal 14.0%

→ **Paper のクリスタルBOTは「地上にいて剣を振る」側に寄っている**。視覚プリミティブ(raycast4/dx=0 セル判定)は
一致済みなので、差分は「空中にいるかどうか」= ジャンプ/crit 系と、クリスタル設置系の分岐にある。
`player <bot> jump` 単体は **両エンジンとも BOT を浮かせない**(同一挙動・検体 bot で確認)ので、
flow 内でジャンプが効いているかどうかを詰めるのが次の一手。

### 計測基盤の問題: ラウンド開始の初期状態が左右で揃っていない
ラウンド最初のサンプルですでに左右が違う(前ラウンドの持ち越し):
cry1 t=491 で `fabric a hp=2.3 / totem_timer=0 / 敵HP 11.0` に対し `paper a hp=2.0 / totem_timer=13 / 敵HP 20.0`。
setup に `effect clear` + `scoreboard players reset` + 全回復を入れて試したが、
(a) `start_round` が既に `regeneration 255` + `absorption` + `parity:hpreset` で HP を正規化しており、
(b) 追加した `resistance 5` が 5 秒ぶんダメージを消して**逆に攪乱**したため **撤去**(world 側も元に戻した)。
今後この部分を触るなら `start_round` 内のリセットを1箇所にまとめ、ダメージに影響する効果は入れないこと。

## 10.12 クリスタルの一次原因 = Paper の「ノックバック戻し」を修正 (2026-09-16)

### 原因 (Paper の bytecode で確定)
Paper 1.21.11 の `Player#causeExtraKnockback` は、被弾側 ServerPlayer の delta を
**被弾前の値に戻す**:

    // Player.causeExtraKnockback, offset 270 (source line 1232)
    target.setDeltaMovement(currentMovement);   // currentMovement = Player#attack が被弾前に控えた delta

これは「実クライアントが `ClientboundSetEntityMotionPacket` を受けて動く」前提の Paper 実装で、
**クライアントの居ない BOT ではノックバックが完全に消える**。参照 (Fabric = 偽クライアント) は
この戻しを踏まないので、そちらだけ吹き飛ぶ → 間合いが詰まり (dist_med 1.62 vs 3.14)、
剣を振り続ける (swing 60 vs 19) → クリスタルを置かない、という連鎖の一次原因。

| プローブ (1.75 ブロック / ダイヤ剣 / `player <bot> attack once`) | 修正前 Δxz(0.8s) | 修正後 Δxz(0.8s) |
|---|---|---|
| Fabric (参照) | 1.984 | 1.525 |
| Paper (移植)  | **0.000** | **1.989** |

TNT 爆発 (1.75 ブロック, 耐性 IV): fabric 3.140 / paper 3.110 → 爆発系ノックバックは元から一致
(残差は近接ノックバックだけだった)。

### 修正 (`herobot/HeroBotPlayer.java`)
- `attack(Entity)` を override: `super.attack(target)` の直後に、被弾 BOT のノックバックが
  「戻し」で消えていれば書き戻す (`restoreKnockbackIfStolen`)。
- 適用側 `applyKnockbackWithScale` で「適用した delta」と「被弾前の delta」を控える。
- `doTick()` の先頭でも同じ判定を保険として実行 (攻撃側が BOT でない場合もカバー)。
- 書き戻し条件: 同一 tick かつ 現在値 == 被弾前の値 のときだけ (二重適用しない)。

### 修正後の実測 (cry14/cry15, クリスタル k10v11, 60s/25w)
- アイテム滞在率: `diamond_sword` **fabric 4.6% / paper 26.0%** (!), `end_crystal` 24.6/14.2,
  `glowstone` 14.0/7.5, `respawn_anchor` 13.6/6.9, `totem_of_undying` 36.9/41.2
- `swing` 18.7/60.1 (n=19/61), `speed_med` 0.51/0.22, `dist_med` 3.14/1.62, `explode` 95.6/55.2
- `g1gc/can_hit` 内訳カウンタ: `c_cansee` 1128/2664 (= fabric 33% / paper 80%),
  `c_hurt0` 425/700, `c_noloc` 2466/2593, `c_canhit` 3399/3318 (=同数)
- `--noise` 判定 (同一エンジン 2 本で振れる指標を除外): 本物 = `charge_explode_gap`, `swing`,
  `speed_med`, `hp_min`, `diamond_sword` (軽微: `anchor_gap`, `explode`, `dist_med`, crystal 系)

### 次の狙い
`quantum:g1gc/can_hit` の第 1 節 `can_see_target` (`allstats/newstats.mcfunction:13` →
`xaniclelib:check/raycast4`) が Paper で 80% / Fabric 33%。ここが hit_decision 3.4 倍 →
swing 3.2 倍 → 剣保持 5.7 倍の入口。`raycast4` は純 vanilla execute (`dx=0` 箱判定 +
`positioned ^ ^ ^.71` レイマーチ) なので、次は「同じ座標・同じ向きでの cansee 直接比較」で
距離閾値 (`distance=..3.2`) の食い違いを潰す。

## 10.13 クリスタルを「サーバーキット」で動かす (`/botadmin`, PvP サーバー本線) (2026-09-16)

パリティ計測で使うキットチェストは *参照パックの都合* であって製品の供給元ではない。
PvP サーバーでは **BOT の装備 = サーバーキット** (`/botadmin`) が本線:

    /botadmin <SWORD|MACE|CRYSTAL|NETHERITE_POT|CART> <kit|off>   # 装備 (ロードアウト) の紐づけ
    /botadmin <botkit> <arenakit|off>                             # 開催アリーナ (会場) の紐づけ

### クリスタルの正規スロット (パックの脳が切り替える位置)
`quantum:g1gc/*` と `quantum:crystal/*` の `player @s hotbar N` から逆算した契約
(1-based hotbar → inventory slot index):

| hotbar | slot | 中身 | 根拠 |
|---|---|---|---|
| 1 | 0 | トーテム | `crystal/maintot` `hotbar.0`, `crystal/totmain` |
| 2 | 1 | 黒曜石 | `g1gc/placeobsidian` `hotbar 2` |
| 3 | 2 | エンドクリスタル | `g1gc/spawncrystal` `hotbar 3` |
| 4 | 3 | 殴り (剣) | `g1gc/hit`, `crystal/hardcode/mech/hit` `hotbar 4` |
| 5 | 4 | 金リンゴ (盾) | `crystal/passive/gap` / `passive/shield/main` |
| 6 | 5 | クロスボウ / 低速落下 | `crystal/passive/crossbow/load` / `slowfall` |
| 7 | 6 | エンダーパール | `g1gc/pearl` `hotbar 7` |
| 8 | 7 | リスポーンアンカー | `g1gc/place_anchor` `hotbar 8` |
| 9 | 8 | グロウストーン | `g1gc/charge_anchor` `hotbar 9` |

### 実装・検証 (Paper ライブ)
- サーバー側キット `crystal` を `plugins/n-arena/kits.yml` に追加 (上表 + ダイヤ防具)、
  `quantum.yml` は `bot.mode: CRYSTAL`。
- `/botadmin CRYSTAL crystal` →「紐づけました: CRYSTAL BOT → キット 'crystal'」。
- `/quantum spawnbot` の起動ログ:
  `[Quantum] bot quantumbot wears the server kit 'crystal' (mode CRYSTAL, /botadmin)`
- 実インベントリ照合: slot 0=トーテム / 1=黒曜石x8 / 2=クリスタルx64 / 3=ダイヤ剣 /
  4=金リンゴx8 / 6=パールx16 / 7=アンカーx8 / 8=グロウストーンx64 +
  `equipment.head/chest/legs/feet` = ダイヤ一式 → **サーバーキットが BOT に載っている**。
- 未達: `slot: 40` (offhand トーテム) は適用されず (`KitLoadout.give` は `setItemInOffHand` を
  呼ぶので、ロード/正規化側で落ちている疑い) → 追跡項目。パック側は
  `item replace entity @s weapon.offhand with totem_of_undying` で自前補充するため、
  パリティ計測はこれ無しでも成立する。

### パリティ計測への含意
両エンジン比較では **同じロードアウトを両側に配る**必要がある (Fabric 側にサーバーキットは無い)。
実ラウンド中のインベントリはパックが動的に供給する (q 行の `i=` は
`data modify storage parity:in item set from entity <bot> SelectedItem.id` の値で、
失敗時は前回値が残る = スティッキー)。したがって `i=` の滞在率は「最後に成功した読み」であり、
クリスタル残差 (`diamond_sword` 26.0% vs 4.6%) は **剣保持そのもの**ではなく
「スロット選択の時間配分」の差として読むのが安全。

## 10.14 クリスタル完了 — 残差ゼロ (2026-09-16 深夜 / 環境リセット後)

### 結論 (cryF/cryG, crystal_k10v11, 60s/25w, ノイズ床)
```
[本物] エンジン内では再現し、Fabric↔Paper で食い違う: なし       (who=a)
[本物] エンジン内では再現し、Fabric↔Paper で食い違う: なし       (who=b)
[ノイズ] swing, speed, speed_med, anchor_gap, charge_explode_gap, x_span, hp_min, ... 
```
残差はすべて「同一エンジン内でも振れる」= 移植のせいではない指標に落ちた。
`cryF` の 1 本だけを見ても `c_hit` 110/106、`diamond_sword` 14.6%/15.5%、`dist_med` 一致。

### 原因は 2 つ (両方潰した)
1. **Paper の近接ノックバック消失** (§10.12) — `Player#causeExtraKnockback` が
   被弾前 delta に戻すため、クライアントの居ない BOT ではノックバックが消えていた。
2. **ラウンド中のロードアウトが両エンジンで違っていた** — Fabric はマップの
   `quantum:botgear/dia` (mode 2) の装備、Paper はプラグインが着せる **サーバーキット**
   (エンチャント無しの素の剣) で戦っていた。剣の `knockback:1`/`sharpness:5` と防具の
   `blast_protection` が落ちるため、間合い (`dist_med` 1.6 vs 3.1) → `cansee` (2.4 倍) →
   `hit_decision` → `swing` (2.5 倍) → クリスタル設置数、と連鎖していた。

### 実装
- **kits.yml に `enchantments:` を追加** (`KitItemEntry.enchantments` 新設 / `KitService`
  の読み書き / `KitLoadout.applyEnchantments`)。これまでエンチャントは
  `data:` の Base64 でしか持てず、手書きキットでは表現できなかった。
- **防具はアイテム定義 (slot 36-39) で書ける運用に** — `armor:` マップはエンチャントを
  表現できないため、キットはアイテムとして防具を持たせる。
- `tools/parity-runner/server_kit.py` — サーバーキットの upsert + `bot.mode` 切替 +
  `/botadmin <MODE> <kit>` の紐づけを 1 コマンドで (環境リセット後も再現可能)。
- **`build_plugin.sh` の盲点を修正** — コンパイル対象が手書きのファイル列挙で、
  `kit/` `model/` の変更が *黙って無視* されていた (delivery jar の古いクラスが使われる)。
  キット系 3 ファイルを追加。

### 検証 (Paper ライブ)
- `bot.mode: CRYSTAL` + `/botadmin CRYSTAL crystal` → ログ
  `[Quantum] bot quantumbot wears the server kit 'crystal' (mode CRYSTAL, /botadmin)`
- 実際のアイテム: 剣 `sharpness 5 + knockback 1` / 黒曜石 `knockback 1` /
  兜 `protection 4 + unbreaking 3` / 脚 `blast_protection 4 + unbreaking 3` — 参照と同一。

### 環境リセットの教訓 (次に踏まないための記録)
- サンドボックス再起動で `/tmp` も **作業ツリーの未コミット分も** 消えることがある。
  今回 `parity-logs/` と作業ツリーが初期化され、`git log` が `bcf8a0b` に戻っていた
  (プッシュ済みコミットはリモートに残っていたので `git reset --hard origin/<branch>` で復旧)。
- 復旧は `tools/parity-runner/env_up.sh --start` 1 発 (JDK/Fabric/Paper/ワールド/プラグイン)。
- **`start.sh` を二重に起動してはいけない**: スクリプトが `console.in` FIFO を作り直すため、
  先に動いていたサーバーは古い FIFO を掴んだままになり、コンソール経由のシナリオ投入が
  届かなくなる (RCON は生きているので気づきにくい)。症状: `count_round.py` が
  paper 側だけ無出力でハングする。
- `bot:` が quantum.yml に 2 つある (spawn 用と mode/kit 用)。YAML は後勝ちなので、
  `bot.mode` を書くツールは «最後の» `bot:` ブロックを狙うこと。

## 10.15 メイス計測で踏んだ罠と、その修正 (2026-09-17 未明)

### 1. 深夜のラウンドが「前夜のラウンド」と混ざっていた (計測バグ)
`tools/parity_runner.py: read_since()` はログの行頭時刻を **文字列比較** で切り出していた。
同じ日の中では正しいが、日付が変わると `'22:43:33' >= '00:20:05'` が **真** になり、
前夜 22:43 以降の行が丸ごと混入する。症状は「サンプル数が 10 倍」「座標が別ラウンドの値」
「y=-302(虚空)」など。→ ファイル末尾から見て時刻が単調増加している区間(= 最後の日付境界
より後ろ)だけを対象にするよう修正。偽ログ(23:59→00:01)での回帰確認つき。

### 2. アリーナに壁が足りず、メイス戦で場外へ飛んでいた
メイスの垂直機動 (ウィンドチャージ / エリトラ / 突進 / wind_pearl) は壁 y=44 を越える。
越えた個体は虚空へ落ちて帰ってこない(= 放置 BOT と化す)。`gen_pack.py` の
`wall=44 → wall=119`(天井 48 より上まで)。**生成パックは gen_pack.py が唯一の正** で、
`datapack/` 配下を直接編集しても `gen_pack.py` を走らせた瞬間に戻る(実際に踏んだ)。

### 3. シナリオが gear 系トグルを設定しておらず、片側だけ盾を持っていた
`parity:setup/*` は loadout を変えるトグルを一部しか設定していなかったため、前ラウンドの
`.shield` / `.elytra` / `.healing` / `.old_kb` が残り、Fabric と Paper で
「slot4 が golden_apple / shield」のような食い違いが出た。→ 4 つとも明示 (0) に。

### 4. Paper の RCON 応答は ~150 文字で `...` に切られる
`data get entity <bot> Inventory[{Slot:Nb}]` を丸ごと比較すると、後半の component が
**無いように見える**(実際に「ブーツの unbreaking が消えた」と誤判定し、原因追跡に時間を
溶かした)。→ `tools/parity-runner/kit_snapshot.py` は葉だけを個別に引き、エンチャントは
**名前ごとに 1 回**問い合わせる(応答が数字だけになるので絶対に切られない)。

### 5. 参照ロードアウトのキット化は「両エンジン一致を確認してから」
Paper の BOT をそのまま `/kit create` すると、その瞬間 Paper が参照と違えば**間違った
状態を正として保存**する(実際にやらかした: sword_only にメイスの装備が入った)。
`kit_snapshot.py` は (1) 両エンジンで setup (2) 全スロットの material/count/enchant/
unbreakable を突き合わせ (3) **一致したときだけ** キット化 + `/botadmin` 紐づけ、を行う。
さらにラウンド中の死亡/respawn で一瞬だけ元に戻る個体があるため 2 回サンプリングし、
両方で食い違うものだけを差とする(揺れは respawn/チェスト再読込の途中経過)。

### 6. kits.yml に `unbreakable:` を追加
参照パックの装備はほぼ全て `unbreakable` で作られているが、読み書きできるスキーマでは
表現できなかった(Base64 のみ)。`KitItemEntry.unbreakable` / `KitService` の読み書き /
`KitLoadout.applyUnbreakable` を追加。

### 7. 現状 (2026-09-17 03:xx)
- **クリスタル**: `kit_snapshot.py crystal_k10v11 CRYSTAL crystal` → 13 スロット一致で
  キット化 + 紐づけ済み。ノイズ床でも 本物ゼロ (cryF/cryG)。
- **ソード**: `kit_snapshot.py sword_k10v11 SWORD sword_only` → 13 スロット一致で紐づけ済み。
- **メイス**: サーバーキット(参照一致)は入ったが、まだ **本物の差** が残る:
  `pearl`(パール投げ), `y_max`(最大到達高度), `items.diamond_spear`(槍 = lunge),
  who=b では `hp_avg` も。カウンタでは **`c_bmcombo_qa` が Fabric 28 に対し Paper 741
  (26 倍)**、`c_bmlogic_qa` 1672 / 978。メイス固有の combo/lunge フローの移植が次の標的。

## 10.16 環境リセット耐性 + メイスの場外問題 (2026-09-17 早朝)

### 1. サンドボックス再起動で /tmp と .git の状態が巻き戻る
再起動後、`/tmp` が空になり、**リポジトリの .git も古いコミットを指していた**
(作業ツリーのファイルは最新のまま)。`git fetch` して
`git diff --stat origin/<branch>` で「作業ツリー == プッシュ済み」を確認してから
`git reset --hard origin/<branch>` で復旧する(前回と同じ手順)。
→ 復旧後は `tools/parity-runner/env_up.sh --start` 1 発で JDK/Fabric/Paper/ワールド/
   プラグインまで戻る。**約 3 分**。

### 2. サーバーキットと紐づけの永続化 (`server_preset.py`)
`kits.yml` / `practices.yml` の `bot-mode-kits` / `quantum.yml` の `bot.mode` はすべて
/tmp 側にあり、再起動のたびに消える。消えたまま計測すると **キット未適用の状態を
「本物の差」と誤認する**(実際に踏んだ: combo 発火が 28 対 741 に見えたが、正しい
キットを当てた後は 23 対 79 / 304 対 141 と**同エンジン内でも振れるノイズ**だった)。
- `server_preset.py save`  … いまの Paper 環境 → `tools/parity-runner/fixtures/parity-*`
- `server_preset.py apply` … fixtures → 実行ディレクトリ (サーバー起動前に適用)
- `env_up.sh` は unpack 直後と起動後に自動適用する。

### 3. メイス: アリーナから出た後の復帰挙動が左右で違っていた
ハーネスのアリーナは 24x24。メイス装備(真珠・ウィンドチャージ・突進)だと簡単に外へ出る。
- Fabric: 奈落へ落ちて死に、リスポーンで戦場に戻る。
- Paper : **壁に張り付いたまま完全停止**(実測: x=-712, y=32, z=95 で 30 秒以上動かない)。
  → `parity:rescue` を追加: アリーナ箱の外/落下中の BOT を毎 tick 検出して戦場中央へ戻す。
  これで両エンジンとも y_min=31 / z_span 23 に揃い、サンプル欠けも消えた。

### 4. メイスの判定 (maceR/maceS, 60s/25w, ノイズ床)
```
who=b: [本物] なし                    ← 一致
who=a: [本物] pearl, swing, hp_min    ← 残り 3 指標
       [一致] y_med, y_min, z_span, x_span, speed, move_share, items.*, crystal, explode …
```
- `swing` = q-sample の `hit` スコアの増分 (>=7) /分。Paper の BOT a が 2〜3 倍
  (36.7→120.0, 50.2→90.6)。`pearl` も約 2 倍 (7.9→17.9)。`hp_min` は Paper が低い。
- **`hit` / `pc` スコアを書いている関数がパック側に存在しない**(grep 済み)。つまり
  これらは BOT フレームワーク(Carpet/QuantumBOT 側)が更新するスコアで、移植側の
  更新規則が参照と違う疑いが強い。次はここ(攻撃/真珠のカウンタ更新)を潰す。
- BOT b 側(ハーネスの `vs_brain` が回す方)は一致しているので、差は「マップ自身が
  回す BOT a」の経路に固有。

### 5. メイスの残差の正体 (2026-09-17 未明の続き)
qlog の `hit=` フィールドは **`hitcd`(攻撃クールダウン)そのもの**だった
(`qlog/function/sample.mcfunction:13` が `scoreboard players get @s hitcd` を
`hit` として保存している)。したがって `swing` 指標 =「`hitcd` が 7 以上に跳ねた回数」=
**着弾イベント数**。
```
着弾数 (maceR / maceS)   fabric → paper
  who=a (マップが回す)     35/39 → 97/72     ← 2〜2.8 倍 (本物)
  who=b (ハーネスが回す)   69/68 → 84/76     ← 1.2 倍 (ノイズ床内)
```
- `hitcd` は `quantum:cooldowns` が毎tick `@a[scores={hitcd=1..}]` から 1 減らし、
  着弾時に `sword/combo/hit` が **11** に、ラウンド開始時は `difficulty/2` が
  **`@a[tag=xlib_bot]` に 15** をセットする(`map/start3` 経由)。
- BOT b は `xlib_target` なので難易度ラダーの影響を受けない。**a だけが 15t の上限に
  縛られる**。実測: Paper の a は 72〜97/分 ≒ 上限(20t/s ÷ 15t = 80/分)に張り付き、
  Fabric の a は 35〜39/分 = 上限の半分。つまり **Fabric 側には着弾を遅らせる何かが
  更にある**(難易度の aim/combo cd か、`hit_decision` が成立するまでの間合い取り)のに、
  Paper 側はクールダウンが空いた瞬間に殴れている。
- したがって次の標的は「`hitcd` が空いた瞬間に殴ってよいか」を決めている移植側の判定
  (`hit_decision` の成立条件 / 難易度の aim 相当) — 攻撃回数そのものではなく**殴る前の
  間合い・照準の作り方**。

## 10.17 剣退化の原因解決 + 剣一致再確認 + crystal 残差の再浮上 (2026-09-17 午後)

### 剣が degenerate していた原因 = ラウンド開始リセットブロック (22dcd02 で導入)
- 症状: swV1/swV2/swV3 (sword_k10v11 45s/5w) で両エンジン HP 凍結 (hp=20.0 固定)、
  開始 7s で両 BOT 東壁 (x≈-688) に張り付き壁ダンス、被弾 A=1〜2/45s、B=0。
  HPS 行の吸収減で被弾を数えると fabric A=2 / paper A=1 と左右同型 (→ 「一致」だが
  実質無接触で判定の実がなかった)。
- 機構: `parity:start_round` は `quantum:map/start3` の**後**に走る。start3 は
  `.crit toggles=1` のため `tempcrit 1` を配布し、init/mode は
  `.uppercut=0` の下で **tempcrit=1 → init/crit / tempcrit=0 → init/combo** を選ぶ。
  リセットブロックが直後に `tempcrit 0` を上書き → **開始脳が crit→combo に反転**し
  戦闘の型が壊れていた。`Pos1_difference` も未設定→0 設定で `unless matches 0..`
  の距離ゲートを反転させうる (スコアボードの「未設定」は `matches 0` にマッチしない)。
- 修正 (gen_pack.py): リセットを**タイマー類のみ**に縮小
  (pops/hitcd/real_hitcd/crystal/obby/pearl/anchor/charge/totem/explosion の 10 種×2体)。
  tempcrit/state/state_time/hit_decision_without_cd/can_see_target/Pos1_difference は触らない。
- 検証: swV4/4b (リセット無し)・swV5/5b (timers-only) のいずれでも HP が動き
  (A が hp_min 0.6〜6.9 まで削れる) healthy な戦闘に復帰。
  **剣 2v2 --noise は who=a/b とも本物なし = 一致** (speed_med を含む; swV3 時代の
  speed_med 本物は退化状態の副産物だった)。

### crystal は本日一貫して残差あり (cryF/cryG の一致は再現せず)
- 本物 (再現): who=a: swing / dist_med / items.diamond_sword (+anchor_gap/totem など),
  who=b: swing / dist_med (+pearl/crystal 数など)。可視サインとして **paper の A は
  diamond_sword 滞在 17〜27%、fabric の A は 0〜8%**。
- 同一残差が ①HEAD パック 45/5・60/25 ②22dcd02 パック (cryF/cryG 当時と同一生成物)
  60/25 ③timers-only + botadmin 紐づけ修復後、の全部で出る → **gen_pack・計測窓・
  紐づけは原因ではない**。fabric 側は 9/15 配信物とビット同一 (delivery 未更新)、
  装備 NBT (botgear/dia 由来) も左右一致済み。残る差分候補は paper 側の
  プラグイン (6c96cfd+ffc1863 の KitLoadout/KitService/KitItemEntry 変更) か、
  cryF/cryG のノイズ床 (2 本) が薄かった可能性。
- 混乱の元を 1 つ潰した: paper BOT の `data get entity Inventory` は実体と
  同期しない (トーテム 1 枠だけに見える; §10.6 の防具スロット盲点と同型)。
  **実状態は `SelectedItem` / `equipment.*` で読む** (held は glowstone/anchor/crystal を
  正しく巡回する)。`Inventory` NBT で装備差を論じないこと。

### 環境トラップ (朝の起動手順の落とし穴)
- **/botadmin の紐づけは practices.yml の bot-mode-kits だが、プラグインは起動時に
  読む。`server_preset.py apply` を起動後にやっただけでは反映されない** → 今日は
  CRYSTAL/SWORD/MACE の紐づけが全て無効のまま計測していた (16 時過ぎに
  `botadmin CRYSTAL crystal` / `MACE mace` / `SWORD sword_only` で再作成済み)。
  正順は **apply → (rumireload または botadmin 再実行) → 確認**。
  env_up.sh のコメント「紐づけが見えない→再適用して rumireload」がこれ。
- bin/3 はモード別に別チェストを引く: mode1 剣 = 防具 -656 / 中身 -657,
  mode2 結晶 = 防具 -688 (壁内, resurface で生き残る) / 中身 -689 (アリーナ内 =
  **resurface の空気充填で破壊済み, 両エンジンで引き失敗=昔から対称**)。
  mode1 チェストは **paper 側だけ消耗** (-656=ヘルメット1, -657=剣1/ツルハシ1/
  トーテム99 vs fabric はフル) — 消耗メカニズム未解明。装備は botgear/dia が
  上書きするため今日の計測には影響しなかったが、チェスト引きに依存する
  将来の検証では偽差を出しうる。

## 10.18 爆発 KB 三重根因の特定と参照パイプライン移植 (cryR9〜R18)

### 根因 (3 つ重なっていた — すべて実測で確定)
1. **Paper は ServerPlayer の爆発 KB 耐性を modifier で 1.0 にする** (実クライアントは
   ClientboundExplodePacket で自前適用する前提)。実測: BOT の
   `explosion_knockback_resistance` は `base=0.0 mods=2 val=1.0`。一方 Fabric 参照
   (フルネザライト装備) は **0.0** (`attribute ... get`)。KB 計算式の (1-res) 因子が
   BOT だけ常に 0 → **EntityKnockbackEvent の kb が全て 0** (224 連続観測)。
2. **この Paper ビルドは爆発 KB で EntityKnockbackEvent を発火しない**。res=0 にしても
   イベントは 1 回も来ず、`Entity.push(Vec3)` が直接呼ばれる (TNT/クリスタル両方で実測、
   `push(Vec3) called vec=(0.0,0.232,-0.685)`)。`CraftEventFactory.callEntityKnockbackEvent`
   の逆アセンでは攻撃者付き=Bukkit 系 ByEntity / 無し=Paper 系という 2 経路があるが、
   爆発はそもそもイベント経路に乗らない。
3. **参照の脳は tick 毎に水平速度を入力ベースで再構築する** (クライアント権限シム)。
   実測: Fabric BOT へ TNT 2blk → 爆発直後 `Motion=(0.0, +0.129, 0.0)` — vy は
   kb(0.232)−重力 1tick 分、**水平は完全ゼロ**、変位 0.2blk。Paper の入力積分物理は
   KB を何 tick も保持するため吹き飛びが累積していた (R15/16: vy p99 1.17-1.31 vs
   fabric 0.31-0.40)。

### 実装 (HeroBotPlayer、HEAD から 1 本のパイプライン)
- `hurtServer` (IS_EXPLOSION): **base/modifier を剥がして res=0** (KB 計算の直前・同一
  イテレーション)。初回 doTick でも 1 回リセットを試みる (`resetExplosionKnockbackResistance`)。
- `push(Vec3)` override: **爆発 hurt と同 tick の push を横取り**し、即時適用をやめて
  `pingDelayTicks(2)` 後の tick 終端に `setDeltaMovement(当時の delta + KB)` で SET
  (参照 `BotPlayer.delayedExplosionKB` と同じ式・同じ遅延・同じ「後の SET が勝つ」冪等)。
  delay=0 はバニラ通り即時加算 (= 参照の即時 SET と等価)。
- **KB 適用の翌 tick の doTick 冒頭で水平 delta をゼロ化** (vy は重力減衰に任せる) —
  参照の「水平は 1 tick で消える」を再現。
- イベントリスナー方式 (ExplosionKBPingListener) は不発が実証されたため撤去。

### ping=100 の恒久設定
サンドボックス再構築で playerdata が消えるたび BOT ping が 0 に戻る (参照環境は
.ping=100 トグル)。`gen_pack.py` の `parity:start_round` に `player <botA/B> ping 100`
を追加 — 両エンジン対称でラウンド毎に復元 (遅延 = 100/25 = 4tick、MOD と同一式)。

### 判定の推移 (--noise, 36 指標)
- cryR9/R10 (属性修正前): 不一致 — launches ほぼ 0、dist_med P1.5 vs F3.6。
- cryR13/R14 (res=0 + push 直通 SET): launches 数は一致 (21-33 vs 17-29) だが強度 5 倍
  (peak 2.1-2.6 vs 0.40)。
- cryR15/R16 (遅延 SET + ping=100): swing/explode/items 大半が一致域へ。残差 = 強度
  (vy p99 1.3 vs 0.4) と dist_med (4-5.2 vs 3.2-3.5)。
- **cryR17/R18 (1-tick 水平 cleanup): 36 指標中 24 一致** (両 who)。一致域に crystal /
  anchor / swing(a) / totem / totem_pop / item_switch / speed / dist_close / hp_avg /
  hp_low_share / **items 全種 (end_crystal・ender_pearl 含む)** が入った。
  残差 (本物): who=a: charge, explode, pearl, speed_med, yaw_rate, y_max, dist_med /
  who=b: crystal, charge, explode, swing, yaw_rate, y_max, dist_med, items.ender_pearl。

### 残差の定量 (R17/R18)
- dist_med **P1.36-1.44 vs F3.46-3.48** — KB 修正前は P が遠すぎたが今度は近接に張り付く。
- ct/anc P109-131 vs F166-183、chg P31-48 vs F65-75、exp 同傾向 — 起爆回数は距離レジーム
  の従属変数として連動して少ない。
- B swing P32-33 vs F57-59。
- 主疑: **脳の移動制御レイヤー (escape/後退/視線) の速度特性差** — 参照は入力=速度
  (即応)、paper は入力=加速度 (慣性)。KB パイプラインはもう正しいので、次はここ。

### 環境トラップ (サンドボックス再生成)
- リサイクルで /tmp 全滅・parity-logs 消滅・git が base に戻る。**worktree-backup から
  未コミット分を復元**した (バックアップ習慣が効いた)。
- env_up.sh → server_preset.py apply (起動後) → botadmin 再作成 (CRYSTAL/MACE/SWORD) →
  build_plugin.sh → RCON stop で新 jar 読み込み、の再順序を確認済み。
- RCON ラッパの `quantum run data get ...` 応答は値を返さない (→ 1 のみ)。値は
  fabric のように直接読めない → 計測は [q] ログか fabric 側で。

## 10.19 cryR17/R18 後の残差分析 (移動レジーム層)

### 残差の構造 (--noise 36指標中 24一致 / 両who)
残差: charge, explode, pearl, speed_med, yaw_rate, y_max, dist_med (who=a) /
crystal, charge, explode, swing, yaw_rate, y_max, dist_med, items.ender_pearl (who=b)。

### 定量 (R17/R18)
- **dist_med P1.36-1.44 vs F3.46-3.48** — R13/R14 (4.6-5.2) から今度は近すぎる。均衡点が逆転。
- 距離変化レート (1s窓): close(<3.5) で F **-1.1〜-1.2/s** (後退する) vs P **-0.1/s**
  (後退しない, p75=+0.5〜0.7 でまだ詰めている)。mid(3.5-6) で F +1.0-1.2/s vs P +2.2-2.5/s
  (接近が2倍速い)。
- **パール投擲数 P42-44 vs F22-31 (2倍)**、投擲後変位 **P1.5-2.1 vs F2.9-3.6 (半分)**。
- **投擲時ピッチ P +55〜77° (急俯角=地面に撃ち込む) vs F +11〜21° (ほぼ水平)** — MCの
  pitchは+が下方。escape/pearl は close(<=8blk) の逃げ手段で、投擲が弱いと距離が保てず
  再投擲が増える (投擲数2倍・pearl滞在率高・dist_med低下の連鎖)。
- yaw_rate p90 **P78-90°/tick vs F33-43°/tick**。

### escape/pearl の機械 (マップ精読)
- crystal/passive/main: `distance=..8` で escape/pearl → marker を背後15blkに
  spreadplayers → `quantum:ray/ray2/cast` (目線から0.5stepで前進レイキャスト、
  遮蔽で lookhigh (上 回し)、marker到達で looklow (lowpos高さ別に look at ~+4/1.4/0.8/-1.5))
  → stop → hotbar 7 → use once。**投擲方向はこのレイキャストのlook次第**。
- look.mcfunction: `.mode 2` なら即時look、それ以外は `.tempaim aim` ラダー
  (aim4 → `look upon ... closest delta 3` = 3tick補間)。実測: crystalラウンドの
  aim スコアは **両エンジンとも 4** ✓。
- 両エンジンで `spreadplayers` / `g1gc/block` (nonsolid2判定) / marker summon は
  管理実験で同一動作を確認済み。逆に管理状態での `ray2/cast` は**両エンジンとも
  lookを変えなかった** (実戦文脈との差: 走行中の呼び出し文脈/ワールド状態)。

### 未解決の疑い (次回の接続点)
1. 実戦での cast → looklow/lookhigh が paper だけ「低pos・至近」枝に落ちる経路
   (lowpos の高さ走査 marklow は `unless g1gc/block` を y-1 ずつ下げる再帰 —
   床/ob 残置状態への依存)。
2. yaw_rate 2× の発生源 (closest点は実装同一を確認済み — mod/plugin とも
   closestPointToBox(botEye, target.getBoundingBox())。interpolation (lookInterpolated,
   delta=wrap/ticks) も一致。差は実戦の呼び出しミックスに残る)。
3. 接近速度 2× は sprint/strafe の入力実装差 (travel 積分 vs 速度指定) か、
   yaw 即応性の従属効果か — 未分離。

### 運用メモ
- paper の RCON 応答 (`§bran ... → 1`) は値を返さない。数値観測は
  **scorestore → 閾値 matches → say → console.log grep** (両エンジン共通で使える)。
- kbtest/kbtgt 等のプローブBOTは使い捨て → disconnect → `list` で 0 確認まで。

## 10.20 プロープ計測バグの全件撤回 + 空中レジームの根因FIX

### 計測側バグ(過去プローブ無効の完全リスト)
- 前セッションのプローブ#1(as無し)/#2(×1000閾値)に加え、§10.19の3本
  (spreadplayers/KBTurn×2)も **store×10 に対し bins ×100/×1000 を書いた恒真ビン**で無効。
  §10.19の「管理実験で同一動作確認済み(spreadplayers/g1gc)」のうち spreadplayers は無効だった。
  教訓: `data get Pos[i] 10` → 実座標×10 (y31→310)。ビンは必ず検算してから書く。

### 正しく再計測した結果(交差再生)
- 固定ジオメトリ cast 再プローブ: 両エンジン同一(レイが南壁基部hit→lookhigh→pitch −82.7°)。
- 実ログ投擲状態の交差再生(fabric状態→paperで再生): マーカー位置/look方向/パール方向とも一致。
- escape照準チェーン・spreadplayers・パール発射方向・発射体物理はエンジン等価。
- 実戦差の正体: **paperだけBOTが空中に打ち上げられる**(R17/18: y>32が19-81%、最大y71;
  fabricは0/136回、maxY 33)。急俯角パール投擲・pearl 2倍・dist_med低下は全てその下流。

### 根因1: 遅延爆発KBの意味論誤り(javap確定→修正)
- 参照 `BotPlayer$DelayedExplosionKB` は **(tick, 生KB vec) のみ**を保存。
  適用は `lambda$processPendingKBs$7` → `invokespecial class_3222.method_60491(vec)` =
  **Entity.push(生vec) = 適用時点の現在deltaへの加算** (yarn: method_60491=Entity.h=push)。
  tick予定/判定は **MinecraftServer#getTickCount**(tickServer冒頭で++)。
- 適用位置は `BotPlayer#method_5773 (tick) HEAD → processPendingKBs` = **移動物理の前**。
  (旧メモ「tick終端SET」は誤り)
- 旧実装の不具合: ①キャプチャ時delta+vec を保存→SET=数tick前の速度を復活させる
  (パールテレポートtickに着火すると vy=1.08(過去)+0.75(KB)=1.84 で打ち上げ — R18実測)
  ②SET後勝ちで同一tick複数爆発の加算が落ちる ③適用が移動後(doTick末)で1tick位相遅れ。
- 新実装: 生vec保存 / サーバtickで予定 / doTick HEAD で super.push(生vec) / 翌HEADで後処理。

### 根因2: 爆発vyの保持(cryR19/20で判明→修正)
- raw vec+加算FIX後も P maxY 84-91 / |vy|max 3.87 vs F maxY 32.2 / |vy|max 0.665。
  参照脳は爆発vyも**1tick分だけ統合して消す**(=1ブロックホップ、水平も同様)。
- cleanup を水平のみ→**全成分ゼロ**(翌doTick HEAD)に拡張。jar sha=75e84855。

### 運用事故: start.sh ルーラーの複製
- start.sh は無限ループ(停止後5sで再起動)。nohup重複起動で**2ループが session.lock 競合**し、
  BOT不在インスタンスがラウンドを担当 → cryR21/22 初回は pos 0,0,0 / hp=0 の無効ログ。
  対処: 余分な paper ルーラーを kill(1616/1624) → RCON stop → 単一ループで起動。
  **fabric側ルーラー(1615/1627)は触っていない。今後 paper 再起動は「ルーラー数確認→stop→
  単一ループ起動」の順で行うこと。**

## 10.21 cryR21/22 判定 (--noise, 2ペア)
- **who=a: 29/36 一致** — 残差[本物]: speed のみ(他7はノイズ床)。
- **who=b: 28/36 一致** — 残差[本物]: speed, hp_avg。
- 値: speed a F1.34/1.81 vs P1.16/1.77、b F1.37/2.21 vs P1.35/1.85。
  hp_avg b F1.09/1.69 vs P1.07/1.73 (フロア近傍)。
- 空中レジームはほぼ消滅 (P maxY 33.8-34.6/57.7外れ値1回, vyMax 2.2-2.4は適用tick内の
  サンプルで翌tickに消える)。F側も R22a で maxY 39.6 と跳ねる等、元々両エンジンに変動あり。
- 次の候補: ①speed/hp_avg の残差は閾値 boundary の可能性→ペア増やして確認
  ②R21a の maxY 57.7 外れ値の追跡(単発) ③anchor起爆paper不発(独立残課題)。

## 10.22 全キット検証スイープ (環境再構築後, jar=1e714a40, 2スイープ×2ペア)

### 環境復旧
- sandboxリサイクル: HEAD→base(ddb9cc6)・/tmp消失・worktree-backup消失。ただしworktreeは
  最終状態を保持していたため fetch+reset --hard FETCH_HEAD で a27a584 に復元(内容一致確認)。
  env_up.sh --start で /tmp 環境を再構築(jar再build=1e714a40, 両鯖起動, botadmin紐づけOK,
  全10 setup関数+ping=100込パック展開済み確認)。

### 判定 (--noise, 各シナリオ2ペア×2スイープ, 計20ペア)
| キット | who=a 残差 | who=b 残差 | 評価 |
|---|---|---|---|
| pot (R1/2) | なし | なし | **完全一致** |
| nethpot (R1/2) | yaw_med (1回, 非再現) | なし | **実質一致** |
| crystal (R23-26) | **なし (2スイープとも)** | move_share→anchor/speed_med/hp_avg と**フラッター** | a完全一致, b=安定残差なし(境界ノイズ) |
| sword (R1-4) | hp_avg(1回)→なし | **speed_med (2スイープ連続)** | a一致, b要確認 |
| mace (R1-4) | pearl,speed,speed_med→totem_pop | **pearl,yaw_snap,speed_med,yaw_rate (連続)** | **要修正** |

### 定量
- sword-b speed_med: F=1.54/1.50/0.32/0.46 vs P=0.96/0.96/0.86/1.18 b/s。
  F側の丸内分散が巨大(同エンジンで4.8倍)→境界ノイズの可能性大。ただしPはFの高状態(1.5)に一度も到達せず。
- **mace-b**: speed_med P=3.14/1.98/3.11/3.02 vs F=1.42/1.16/2.26/2.56 (P高め)。
  **パール投擲数 b: P19-21 vs F9-10 (約2倍)** — 旧mace残差(pearl≈2倍)の残存。
  yaw_rate/yaw_snap b も連続残差 = パールescape多発に伴う高速回転の下流と推定。
  swing/hp系は一致済み(旧残差のswing2-2.8倍は解消)。

### 結論と次
- 爆発KB修正が全キットに波及: 5キット中3キット(pot/nethpot/crystal-a)が残差ゼロ、
  swordはspeed_med(b)のみ、mace-bのみが構造的残差。
- 次: ①mace-bのpearl 2倍のトリガ解析(gap_timer/eval/pearlcd 条件が mace mode でどう読まれるか)
  ②sword-b speed_med は+2ペアで境界確認(優先低)
  ③crystal-bフラッターも必要なら+2ペア。
- 注意: pair.shのサマリ([a]/[b]行)はハブ段のhpを表示することがある(hp=20固定に見える)。
  実データは[q]行にあり swordR3/4 は minHp 0.5-0.8 の実戦。tailサマリだけでは判断しない。

## 10.23 mace-b pearl の構造追跡 (fp_watch 計装) + crystal 4スイープ確定

### mace のパール経路 (gear=2 確定済み)
- `.gear=2` → sword-escape (`if .gear matches 1`) は**無効**。maceのパールは
  **far_pearl** (pearlcd0 + 敵5.. + 敵が安定垂直 + 自分が敵の下 + random 40% + airborne2)
  と **wind_pearl** (p1d≤-5 + wind charge リポジ) のみ。
- far_pearl の照準 = crystal/passive/crossbow/aim (敵方向)。airborne2 = 足元3ブロック空気。
- wind charge 自爆ジャンプは**両エンジン同等**を直接確認 (launch vy≈0.9-1.4, 1-2t以内,
  遅延なし。参照MODも wind charge は遅延しない。paper console に [KB] deferred ゼロ = 一致)。

### fp_watch 計装 (gen_pack.py に実装済み, 常時ON)
- parity:tick が各tick、A は自然文脈 (A=xlib_bot)、B は vs_brain スワップ内 (B=xlib_bot)
  で far_pearl の各条件の成立を dbgc に積算し、100tickごとに `[fp] ck=...` を say。
  start_round でリセット。**注意: `parity_runner.py deploy` の引数はワールドの
  datapacks ディレクトリ (/tmp/.../QuantumMap/datapacks)。パックの staging dir を渡すと
  自分自身を rmtree する。**

### maceR10 の fp 計測 (1600t窓)
| 条件 | A_F | A_P | B_F | B_P |
|---|---|---|---|---|
| pos (自分が敵の下) | 36% | 37% | **0.3%** | **54%** |
| stab (敵が垂直安定) | 17% | 52%+ | 39% | 52%+ |
| d5 (距離5..) | 40% | 63% | 25% | 64% |
| air (airborne2) | 50% | 66% | 40% | 53% |
| **all (far窓)** | **9.9%** | **10%** | **0.6%** | **9.5%** |
- **Bのfar窓が16倍差** → 投擲数差 (F 3-9 vs P 17-24) の直接説明。
- 最大寄与は pos: **fabric は両BOTが64%のtickで同一yブロック、paper は9%**
  (y差 med F1.62 vs P5.05)。paper の mace 空中バrawlは2体の高さが分離しやすく、
  fabric は同期して滞空する。両エンジンとも滞空率 ~70% (bothG 5-6%)。
- stab (敵の垂直安定 = vmotion 述語) も 3-5x 差。fallDistance エミュレーションは
  vanilla と等価なことをバイトコードで確認済み (checkFallDamage: dy<0 加算 /
  onGround リセット / 上昇据え置き)。
- 結論: mace-b pearl は「空中バrawlの垂直同期」の差という**movementレジーム下流**。
  単発修正対象ではなく、wind起爆の同期 (hitcd/windcd/OnGround ゲート) から
  軌道同期まで踏み込む必要。優先度は crystal 完後に再評価。

### crystal 4スイープ集計 (R21-28, 8ペア, --noise)
- who=a: totem 1/4, dist_med 1/4 以外ゼロ → **持続残差なし → 合格**
- who=b: z_span 1/4, dist_med 1/4 以外ゼロ → **持続残差なし → 合格**
- (基準: 「Fabric 2回近似=運ではなく Paper 一致必須」= 複数スイープで持続すること)

### 全キット現状
| キット | 状態 |
|---|---|
| pot | 残差ゼロ (1スイープ) |
| nethpot | 残差ゼロ (1スイープ) |
| **crystal** | **4スイープで持続残差ゼロ → 一致** |
| sword | b: speed_med 2/2 (境界の可能性、要+ペア) |
| mace | b: pearl 3/3 構造残差 (垂直同期、上記) |

## 10.24 6キット目 CART (mode 6) の検証

- ユーザー指摘で判明: キットは6種 (SWORD/MACE/CRYSTAL/NETHERITE_POT/POT/**CART**)。
  harness に cart_k10v11 を追加 (mode_fn='cart', kit 10/11, gear=2)。
  `.cart_speed=3` (insta, 参照環境と同一) を REF_TOGGLES に追加 — carttest/tick だけが読む。
  dispatch は init/crit|combo → quantum:carttest/tick|combo/tick (mode 6) で、
  harness の vs_dispatch は上位ブランチをコピーしているので B の脳も自動で cart になる。
  遅延爆発KB経路は TNT カート爆発でも機能 ([KB] deferred 確認)。

### cart 判定 (2スイープ×2ペア, --noise)
- R1/R2: a 本物=x_span,z_span,hp_avg,hp_low_share / b 本物=totem,totem_pop,x_span,z_span
- R3/R4: a 本物=totem_pop,yaw_med,hp_avg,hp_low_share / b 本物=yaw_snap,yaw_rate,yaw_med
- **2/2 持続: a = hp_avg, hp_low_share**。他はスイープ間で入れ替わり (境界寄与混在)。
- 定量: **hp_avg a  F 3.2-3.7 vs P 10.8-19.7**。hp<5 の時間 a: F 78-85% vs P 0%
  (paper の A は R3/R4 で min 13.8-17.2 = ほぼ無傷)。可動域は paper が常にアリーナ全幅
  (x_span 22.4 固定, F は 10-22 で変動)。
- **解釈**: 相互破壊 (fabric 両者 hp<5 100%近く) vs 一方的展開 (paper B だけ削られる)
  の**レジーム分岐**。B→A の矢/melee が機能しない定常バグではない (R1/R2 の paper A は
  大ダメージを受けている)。mace の垂直同期と同系の「cart バrawl の優劣が初期乱数で
  固まる」構造。bow 使用率は両エンジン同率 (9-14%)。
- PracticeSideListener.onArrow は追跡のみ/cancel なし。MatchCountdownLock は
  parity ラウンドでは非アクティブ — プラグインによる矢キャンセルは可能性から除外。

### 6キット検証の最終表
| キット | 判定 |
|---|---|
| sword | a 一致 / b speed_med (境界の可能性, +ペアで確定可能) |
| mace  | a ほぼ一致 / **b pearl 3/3 構造残差 (垂直同期)** |
| crystal | **a/b 4スイープ持続残差ゼロ → 一致** |
| netherite_pot | a/b 残差ゼロ |
| pot | a/b 残差ゼロ |
| cart | **a hp系 2/2 構造残差 (一方的展開レジーム)** / b スイープ間変動 |

残作業: mace 垂直同期, cart レジーム, sword-b speed_med 確認, anchor起爆(独立)。

## 10.25 getKnownMovement FIX — movement述語がpaperのBOTで全滅していた根本原因

### 発見経路
- mace fp_watch の stab ゲート (F 9-18% vs P 62-100%) が |vy| 直接計測 (F 18-23% vs P 23-35%)
  と矛盾 → 述語側が壊れている仮説 → 生きたBOTで predicate を直接比較。
- **実測**: 落下中 (vy −0.45, fall_distance≥1.0) で paper は vmotion_m1 不発 / fabric は成立。
  fall_distance 自体は NBT に正常蓄積 (初期プローブの FD_0 は誤キー `fallDistance` を読んだため。
  1.21.11 の正キーは `fall_distance`)。
- **根因 (バイトコード確定)**: `entity_properties` の movement 述語は
  `EntityPredicate → entity.getKnownMovement() (×20 = per-second) + entity.fallDistance` を読む。
  **Paper の `ServerPlayer#getKnownMovement` は `lastKnownClientMovement`**
  (実クライアントの move packet で更新) を返す。参照 BOT は fake client 接続で餌付けされるが、
  この BOT にクライアントは無い → **常に Vec3.ZERO** → 全 vmotion*/speed 述語が死ぬ。

### 修正
- HeroBotPlayer に `getKnownMovement()` override (delta をそのまま返す)。jar=e33970bf。
- 再プローブ: 落下中 vmotion_m1 **成立** ✓。

### 影響 (fix前→fix後)
- **mace-b pearl 3/3 構造残差 → 消滅**。投擲数 R13/14: F 7/8,7/5 vs P 4/8,4/2 (以前 P17-24)。
  ender_pearl 所持率 2.0% → 0.2-0.7% (fabric 同等)。
- mace-b 残差は yaw_med のみへ縮小。cart-b も yaw_med のみ (x/z_span, totem_pop 消滅)。
- sword-b speed_med 3/3 → R7/R8 で speed+speed_med+yaw系+dist_med (構図変化、下記参照)。

### fix後に残る共通残差 (sword/mace/cart の a 側)
- **A の被弾量が paper で少ない** (hp_avg: F 2.9-3.3 vs P 6.3-14.5、hp<5: F 65-86% vs P 0-45%)。
  分解: B の攻撃数は同率 (maceR15: 50 vs 50) だが A への**接続が少ない**
  (drops F21/54.7dmg vs P8/29.5、小ダメージの連打が欠ける。p90 は逆に P 大)。
  dist_med は P が半分 (むしろ近い) なので距離ではない。
- swordR7 は両エンジンで「B カイト & A は手出し無効」の一方向戦 (B 被弾 0)。
  カウンター頻度 F8 vs P2 の差が A 被弾差になる構図。
- 近接マイクロプローブ (attack once ×6, 1.3blk) は**両エンジン同一** (0 ダメージ = プローブ
  不発、パリティ信号なし)。attack 動詞のレイキャスト条件は要精査 (次回の接続点)。

### crystal (最優先) — fix後もクリーン維持
- R29/30: anchor, yaw_rate / R31/32: anchor_gap+pearl (a), charge (b)。
  **2スイープで持続残差ゼロ** (各指標 1/2 回のみ = 従来のフラッター範囲)。
  getKnownMovement fix によるリグレッションなし。fix前 4 スイープ + fix後 2 スイープ = 計 6 スイープ一致。

### 6キット現状 (fix後)
| キット | a | b |
|---|---|---|
| crystal | ✅ (2スイープ追加確認済み) | ✅ |
| pot / nethpot | ✅ (fix前計測。vmotion非依存とみられるが未再計測) | ✅ |
| sword | ⚠️ totem_pop/hp_avg/hp_min/dist_med (2/2) | ⚠️ yaw系+speed+dist_med |
| mace | ⚠️ totem_pop/hp系 2/2, pearl 1/2 | 🟡 yaw_med 2/2 (他1/2) |
| cart | ⚠️ hp_avg/yaw_med/z_span/totem_pop | 🟡 yaw_med のみ |

## 10.26 mace a側hp残差の正体 = 落下maceスマッシュの接続率 (melee/爆発/i-frameは否定)

### maceR17/18 (fix後, 新環境) の被弾ソース分解
- Bのmelee接続率: **両エンジンとも 1-4%** (B swing 190/107回中 hit 1-2)。バrawlの被弾はmelee連打ではない。
- wind charge爆発ダメージ: 固定ジオメトリプローブ (BOT横1.0blk起爆) で **F 0.0 / P ≤0.3** — ほぼ無害。頻度も同率 (wc占有率 F2-5% vs P4-5%)。
- 被弾サイズ med 1.6-2.5 = **落下中maceスマッシュ** (Bがwind self-launch→落下mace攻撃) のダメージ。
- A drops: F 12/16 vs P 7/10 — サイズは同等 (med 2.3 vs 2.5)、**接続回数が半分**。
- i-frame仮説否定: B swingのhit率は Aの無傷窓/i-frame窓で変わらず (1-4% flat)。
- deaths 0、pops F1/6 vs P1/1 (非体系)。攻撃判定回数 (hitcd遷移) は同オーダー (F190/54 vs P107/67、ラウンド差大=レジーム)。

### 結論
- a側hp系残差 = **空中バrawlにおける落下スマッシュの接続率差**。被弾サイズ・攻撃回数・i-frame・爆発ダメージは全て同等。
- swing時の補間look (aimラダー) と落下軌道の位相が絡む movement レジーム下流。個別のダメージ/動詞バグではない
  (crystal が 6スイープ完全一致であること、被弾サイズ一致、近接1発のダメージ式は crash 前提で同一から整合)。

### プローブ手法メモ ( Dead ends / 契約 )
- **RCON コンソール文脈からの手動 `attack once` プローブは両エンジンで不発** (照準を直しても HP 不変)。
  実戦の attack once は脳関数内 (tick 開始, actionPack.onUpdate 前に start) で機能する。
  コンソール文脈の近接プローブは**再試行禁止**。検証は必ず実ラウンドログ (hitcd遷移×hp drop) で。
- `player <bot> look <yaw> <pitch>` は**文法エラー** (絶対look動詞は無い。絶対向きは playerspawn facing のみ)。
- par_dt/par_dd 統計は累積値 (ラウンド差分なら使えるが paper 側は数値読みが面倒)。hp時系列分析で十分。
- [q] パース時の who は group(8) (regex 構造で変動するので毎回数えること)。

### 6キット現状 (§10.25 から変化なし / mace a側の原因がスマッシュ接続率と特定されただけ)
| キット | a | b |
|---|---|---|
| crystal | ✅ 6スイープ一致 | ✅ |
| pot / nethpot | ✅ | ✅ |
| mace | ⚠️ スマッシュ接続率 (regime下流) | 🟡 yaw_med 僅差 |
| sword | ⚠️ hp系+dist_med | ⚠️ yaw系+speed |
| cart | ⚠️ 一方向的展開レジーム | 🟡 yaw_med 僅差 |

## 10.27 crystal恒久 (計装除去+最終3スイープ) / pot・nethpot fix後再計測 / keepalive再武装バグ

### pot/nethpot fix後再計測 (jar=fix後, potR3/R4, nethR3/R4)
- `--noise` who=a/b とも **[本物] 残差なし** (両キット)。[ノイズ] は x_span/z_span/yaw_med/speed_med 程度。
- §10.25 の「vmotion非依存とみられるが未再計測」注記を解消。**6キット表の pot/nethpot が fix後計測に更新。**

### 計装除去 (plugin, jar 2a88f240 → 8b334374)
- HeroBotPlayer から `[KB] expKBres reset` / `[KB] explosion deferred` の2 print と
  デッドメソッド `resetExplosionKnockbackResistance()` (+field expKnockbackResReset) を削除。
  呼び出しsite無し (hurtServer 内のインライン版が現役)。ロジック系 (push override /
  getKnownMovement / hurtServer expKBres / cleanup) は一切触らず。`getKnownMovement` は
  jar 内バイトコードで存在確認済み。

### 運用インシデント2件 (原因特定済み・対処済み)
1. **keepalive 再武装レース**: `parity:stop` の closing と BOT despawn の tick 際で keepalive が
   同一/翌 tick に `.start=1` を踏み直す。fabric は BOT無しの幽霊ラウンド機構が継続
   (t=2499 逆算 = nethR4 closing 08:42:21 と一致)、paper は `.start=1` がワールド保存され
   **再起動後も再開** (起動時に quantumbot が自動復帰→rescue が SPOT_A へ tp、who=b 空の
   半死ラウンドが tick)。→ **修正**: gen_pack.py `stop()` 末尾に `pari_round=200` (10s ガード)、
   `load()` に `.start=0` (ブート/リロード復帰クラスの根絶)。両エンジンへ再展開。
   post-stop 検証: 両エンジン `.start=0`・[fp] 凍結 ✓。
2. **fabric /reload は破壊的**: パック再展開後の `/reload` 直後の最初のラウンドでサーバーが
   凍結 (watchdog: 1 tick 60s → クラッシュ。スタックは関数実行ループ内)。クリーンブートなら
   同じパックで正常。→ **fabric の再展開後は reload でなくサーバー再起動**をルール化。
   なお paper の reload は問題なし。
- 副次所見: parity_runner は取りこぼし検知で**自動リトライする** (nethR4 paper は 08:41:16 と
  08:42:21 の2回 setup → 書かれたログはリトライ分)。リトライ有無の境界は未調査。
- 両エンジンとも**起動時に quantumbot が自動復帰する** (fabric も同様)。次の setup の
  playerspawn と衝突して脳死ラウンドになるので、起動後は `player quantumbot disconnect` し、
  2 回目で「No player was found」を確認する (paper は自動復帰元の特定までに至らず)。

### crystal 最終3スイープ (cryR33-38, 2ラウンド×3, jar 8b334374 + 新ハーネス)
- 公式判定 (`--noise`): R33/34 a=[pearl, speed_med] b=[speed_med] / R35/36 a=[anchor_gap,
  anchor, pearl, z_span, dist_med] b=[speed, hp_avg, dist_med] / R37/38 a=[anchor] b=[swing,
  speed_med]。**3/3 持続の構造残差なし**。フラッター族 = R29-32 と同系 (anchor/yaw/pearl/charge)。
- **ただし a側 pearl 投擲数は 6/6 ラウンド全て P>F** (F 15,18,14,20,19,14 vs P 28,25,25,23,25,21、
  順位和 p≈0.001、倍率 1.2-1.9x)。35% 閾値前後でフラップする**方向固定の弱バイアス**。
- 切り分け (既存ログ分析のみ):
  - **use 動詞は無罪**: gate 呼出数 (i→ender_pearl 遷移) ≈ 実投擲 (pc 遷移) = 100% 両エンジン。
  - トリガは `crystal/passive/main` line 7 (敵≤8blk)。`tag=close` は**どこでも付与されない**
    (セレクタ常に空 → pearlcd チェック実質無効)。実レート制限はバニラ item cooldown 20t。
  - far_pearl は水晶では不発 (fp_watch all=0 両エンジン) → escape pearl が本体。
  - 差は**ゲート発火数そのもの** (a: F 14-20 vs P 21-28)。上流入力差 = 戦闘テクスチャ
    (≤4blk 滞在 F 63% vs P 44%、fabric がより密着 grind)。line 4 の mace-defense preemption
    (敵が前進中は escape 判定を飛ばす) が抑制側の候だが未検証。
  - 投擲時距離分布・レンジ構造は同一 (≤8blk 支配) → 幾何/動詞/ダメージ系バグではなく、
    **mace-a スマッシュ接続率と同族の regime 下流**。
- 結論: crystal のコア一致 (HARD タイミング/アイテム/hp/デス/swing/移動分布) は本日 6 ラウンド
  でも維持。**既知の弱バイアスとして a側 escape-pearl 増 (~+40%, 方向固定・閾値未満) を記録**。
  修正対象は無し (脳は共用マップ関数、エンジン差は出力の emergent 差)。

### 6キット現状 (§10.27 時点)
| キット | a | b |
|---|---|---|
| crystal | ✅ コア一致 / 📝 a-pearl 弱バイアス (6/6 方向固定, 2/3 テストで閾値超え) | ✅ |
| pot | ✅ (fix後再計測) | ✅ (fix後再計測) |
| nethpot | ✅ (fix後再計測) | ✅ (fix後再計測) |
| mace | ⚠️ スマッシュ接続率 (regime下流) | 🟡 yaw_med 僅差 |
| sword | ⚠️ hp系+dist_med | ⚠️ yaw系+speed |
| cart | ⚠️ 一方向的展開レジーム | 🟡 yaw_med 僅差 |

## 10.28 sword-b speed_med 決着 (swordR9/R10)
- `--noise` who=b: **[本物] 残差なし** (speed_med/x_span は[ノイズ])。
- 値: F 1.80/0.76 vs P 1.16/1.20 — fabric の丸内分散が大きく (旧 R1-4 の 4.8 倍分散と同じ挙動)、
  paper はその帯の中間。R1-4 の「P が F 高状態 (1.5) 未到達」も本スイープは解消 (F 1.8 / P 1.2)。
- 通算 2連続[本物]→構図変化→[ノイズ] で 3連続ならず。**境界ノイズとして決着、修正対象なし**。

## 10.29 use release FIX — a側残差族(mace/sword/cart)の一掃 + texture.py

### 発見経路 (cart-a 一方向的レジームの正体)
- cartR7/8: **P-A は tnt_minecart/powered_rail に一度も持ち替えない** (F は 20-25 回/ラウンド)。
  チェーンを下から検証: command block auto実行は両エンジン健在 (プローブマーカー出現)、
  placecmd の条件チェーン通過率も両エンジン同率 (g1gc 25/26, bot距離 14/12, ray類同水準)、
  rail_1 マーカーは P でも湧く (6/25)。**P だけ tnt_minecart エンティティが 0**。
- 設置 (util/rail|cart) は insta の「**arrows_in_air>=1 (射撃中) 窓」でしか走らない**構造。
  矢の存在プローブ (1s×50): **F 11/50 vs P 0/50 — P は矢が一度も飛ばない**。
  弓の release が P で起きていなかった。

### 根因 (バイトコード確定)
- `LivingEntity.stopUsingItem()` = abort (useItem=EMPTY, 発火なし) /
  `releaseUsingItem()` = 発火 (弓なら矢)。
- 参照 MOD: 新しい use 動詞で旧 USE action を置き換える時と inactiveTick で
  **method_6075 (= releaseUsingItem, 発火側)** を呼ぶ (BotPlayerActionPack bytebode)。
  ポートは同位置で **stopUsingItem (abort)** を呼んでいた → hold 中の弓が永遠に発火しない。
  cart/charge (draw) → bowcharge==1 で `player @s use` → 参照はここで発火、P は不発。
- 注: 手動 verb プローブ (draw→stop / draw→use once) は**両エンジン不発** (既知の
  console 文脈問題と同型)。検証は必ず実ラウンドプローブで。

### 修正 (BotActionPack 2 箇所, jar 7cf0597f)
- `stop(USE)` と `USE.inactiveTick` の `stopUsingItem()` → `releaseUsingItem()`。
  盾/ポーションは releaseUsing 側でも副作用なし (設計上 completeUsing と別)。

### 結果
- 矢プローブ: P 0/50 → **11/50 (F と同一値)**。
- **cart: a/b [本物]残差ゼロ** (R9/10, R13/14 = 2スイープ。R11/12 a yaw_snap 1回のみ)。
  a hp_avg F 3.0-4.1 vs P 3.5-4.7 (旧 F4.5 vs P14-19 の一方向的レジーム消滅)。
  P-A も tnt/rail を使用するようになった (2.2-3.6%)。
- **sword: a/b [本物]ゼロ 2スイープ** (R13-16)。旧 a hp系 (F2.8-3.4 vs P11-12) 消滅
  (B カイト一方向戦が両エンジン同型になった。A hp_avg 3.0/3.3, 3.4/3.1)。
- **mace: a [本物]ゼロ (R21/22)**, R23/24 pearl 1回 (フラッター)。b R23/24 ゼロ。
  旧「落下スマッシュ接続率差」も消滅 (被弾 hp_avg a F3.0-3.9 vs P3.4-4.5)。
- crystal: R39/40 a totem+speed 1回 / b hp_avg。R41/42 は fabric 側ハブ 0,0,0 サンプル
  混入で無効 (x_span 705 = 既知アーティファクト増幅)。R43/44 取り直し:
  a pearl+y_max 1回 / **b hp_avg+speed_med+y_max が 2スイープ連続** (4/4 ラウンド非重畳:
  hp_avg b F0.98-1.18 vs P1.76-1.95, speed_med b F1.28-1.97 vs P0.38-0.62, y_max P 38-57 vs F 32-34)。

### 残作業
- **crystal-b**: P-B がやや低速 & 被弾少なめ (弱い構造差の可能性)。movement レジーム系の
  最後の1本。次の切り分け候補: b の jump/launch 頻度 (y_max P 高め = P-B が上に飛ぶ)。
- 6キット表 (fix後): crystal ✅(a) + 🟡(b 弱残差) / sword ✅✅ / cart ✅ / mace ✅(a) + ✅(b,
  hp_min 1回) / pot ✅ / nethpot ✅。

### §10.29 追記: crystal-b 弱残差のテクスチャ分解 (texture.py + alive フィルタ)
- R39/40/43/44 の b 側: P-B は **alive 時の hp_avg が 1.91-2.00 vs F 1.03-1.22 (4/4 非重畳)**。
  死亡/ハブサンプル (hp<=0.5 or pos 0,0,0) を除外しても変わらない = 真の差。
  一方で被弾イベント総量は P-B の方が多い (dmg_ev F 1-5 vs P 8-13) → 「被弾の早期性」の差
  (F-B は序盤に削れてそのまま低 hp で推移、P-B は序盤の被弾が少ない)。
- alive 速度 median は両エンジン ~0.01-0.06 (両者とも立ち止まり支配)。speed_med の
  F1.3-2.0 vs P0.4-0.6 は fingerprint の計算方法 (移動サンプル寄り) による増幅。
- swings b: F/P 同帯。connects b: 同帯。→ B の「攻撃」は一致、B の「被弾タイミング」だけズレる。
- 切り分け候補 (次回): ①A の序盤クリスタル設置→起爆の到達時間差 ②totem pop 時点の差
  ③regen/absorption 付与タイミング (N-Arena DeathBridge 系)。レジーム差の可能性も残る
  (hp_avg 両者とも ≈1-2 = どちらも「破壊されている」帯の中の差)。
