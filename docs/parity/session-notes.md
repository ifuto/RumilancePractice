
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
- Fabric は素の `playerspawn` 直後だと bot が動かない（重力すら積まない）。verb 単体の
  性能比較は「ハーネスで試合を回す」文脈でしかできない。Paper は同じ状況でも動くので、
  **そのまま比較すると Paper だけが動いて見える**（誤診のもと）。
