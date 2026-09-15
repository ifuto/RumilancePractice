# java-trigger — Java 実行環境 / Fabric 実測サーバー配送の発火マーカー

このファイルへの変更を含む push ごとに `.github/workflows/java-env.yml` が走ります。
ワークフロー本体は **薄いディスパッチャ**で、実処理はすべてリポジトリ内の
[`ci/java-env.sh`](ci/java-env.sh) にあります(サンドボックスのエージェントは
`.github/workflows/**` を push できないため、ロジックを ci/ に置いて
**再貼り付けなしで修正・再実行できる**ようにした)。

## 配送されるもの

| ブランチ | 中身 | 用途 |
|---|---|---|
| `java-env-delivery` | `jdk21.tar.gz.part-*`(Temurin 21 ポータブル) + `paper-1.21.11-<build>.jar` + `sha256s.txt` | サンドボックスに Java が無い / Paper 側の検証 |
| `mc-server-delivery` | `mcserver.tar.gz.part-*` + `sha256s.txt` + `README.txt` | **起動検証済み Fabric 実測サーバー**(fabric-server-launch + fabric-api + HeroBot MOD + Quantum マップ + qlog + eula/properties + 起動ログ) |

artifact `java-env`(`/tmp/bundle/*`)も保存されるが、サンドボックスは Azure blob を
読めない(EOF 遮断)ので、そちらへは git ブランチで渡す。

## オーナー操作(1回だけ)

`.github/workflows/java-env.yml` の中身をリポジトリ直下の `java-env.workflow.yml` と
同じにして Commit する(以後このファイルを触る必要はない)。

1. GitHub web UI で対象ブランチの `java-env.workflow.yml` を開く
2. 全文コピー → `.github/workflows/java-env.yml` を Edit して貼り付け → Commit

## サンドボックスでの取得手順

```bash
# JDK (一度やれば以後は /tmp/jdk21 を使う)
git clone --depth 1 --branch java-env-delivery \
  https://github.com/ifuto/RumilancePractice.git /tmp/ship
cat /tmp/ship/delivery/jdk21.tar.gz.part-* > /tmp/jdk21.tar.gz
(cd /tmp && sha256sum -c /tmp/ship/delivery/sha256s.txt)   # paper jar はここで検証
mkdir -p /tmp/jdk21 && tar -xzf /tmp/jdk21.tar.gz -C /tmp/jdk21 --strip-components=1

# Fabric 実測サーバー
git clone --depth 1 --branch mc-server-delivery \
  https://github.com/ifuto/RumilancePractice.git /tmp/mcship
cat /tmp/mcship/mcserver/mcserver.tar.gz.part-* > /tmp/mcserver.tar.gz
(cd /tmp && sha256sum -c /tmp/mcship/mcserver/sha256s.txt)
tar -xzf /tmp/mcserver.tar.gz -C /tmp
cd /tmp/mcserver && /tmp/jdk21/bin/java -Xmx2400M -jar fabric-server-launch.jar nogui
```

## 測定(コンソール)

```
/function quantum:options/crystal
/player quantumbot spawn at 11 34 10 facing 0 0 in survival
/scoreboard players set .start start 1
```

`latest.log` の `[q]` 行(= qlog の 0.1 秒サンプル)を回収して、当側の
`[N Arena][BotMatch]` trace / samples と `tools/compare_fights.py` で突き合わせる。
手順は [docs/bot-combat-parity.md](docs/bot-combat-parity.md) の「標準測定シナリオ」。

## 発火ログ

- 2026-09-13: ワークフロー導入(73b68ed)後の初回発火 → run 34760045775 は Paper 取得で失敗。
  原因: api.papermc.io v2 が sunset → fill v3 (fill.papermc.io/v3) へ移行。
- 2026-09-13: v3 版での再発火 → run 34760226363 成功。ただしサンドボックスから artifact DL は
  blob 遮断(EOF)で不能 → `java-env-delivery` ブランチ配送を追加。
- 2026-09-13: 配送ブランチ版の試行錯誤(run 34760427888 → push 403 / checkout の clean が
  bundle を消す)を経て、run 34761374376 で全緑。**Java 環境の調達経路が確立**。
- 2026-09-13(夜): 起動検証済み Fabric サーバーを `mc-server-delivery` へ配送する拡張を書いたが、
  サンドボックスは `.github/workflows/**` を push できないため**オーナーの貼り付け待ち**のまま。
- 2026-09-14(夜): 配送を **薄いワークフロー + `ci/java-env.sh`** に作り替え(以後の修正に
  再貼り付けが不要)。旧拡張にあった不具合も修正: `mcserver/` ディレクトリを作業ツリー内に作り
  配送コミットがサーバー丸ごと(未圧縮)を巻き込む問題 → 作業ツリー外 `/tmp/mcserver` で組み立て、
  stop 用 FIFO もツリー外へ。qlog データパックを配送サーバーに同梱。
- 2026-09-14(深夜2): オーナーが **arena/01a09fda-rumilancepractice** に薄いディスパッチャを適用
  (workflow blob = リポジトリ直下 `java-env.workflow.yml` と一致)。本書き換えで java-env を発火し、
  Fabric 実測サーバーの `mc-server-delivery` 配送を初回実行する。
- 2026-09-14(深夜3): 初回の Fabric 配送は line 69 (meta.fabricmc.net の loader/1.21.11 解決) で失敗。
  ログ本体が読めないため、ci/java-env.sh に HTTP ステータス / ボディ先頭 / 既知 game versions を
  ::error:: アノテーションで吐く診断と、全ローダー一覧・maven metadata へのフォールバックを追加。
- 2026-09-14(深夜4): 2件目の失敗を修正 — (1) loader/1.21.11 は `[{"loader":{"version":…}}]` の
  ネスト構造でパース対象が `loader.version` だった(旧コードは `version` を見て空 → 偽の「空応答」判定)。
  (2) 起動検証の FIFO を読み取り専用で開いていたため open がブロックし、Java が一度も起動していなかった
  (`< fifo` は `> log` より先に処理されるのでログファイルすら作られない) → `exec 8<>` の読み書き両開きへ。
  失敗時は起動ログの head/tail/マーカーを ::error:: アノテーションで出す。

- 2026-09-14: 石の地面+通常戦闘でのクリスタル&アンカー実測(v1.75.7)を反映 — CI 再検証。

- 2026-09-14: アンカー突き合わせ(構造/ラダー/難易度ゲート一致)を doc に追記 — CI 再検証。

- 2026-09-14: 通常戦闘のアンカー実測 2本目(4t×49/49・12tサイクル)を追記 — CI 再検証。

- 2026-09-14: ci/java-env.sh に当プラグインの shadowJar ビルド + plugin-delivery 配送を追加(当側を Paper で走らせて参照と比較するため)。

- 2026-09-14: plugin-delivery のビルド順修正(orphan 前にビルド)を反映。

- 2026-09-14: java-env のプラグインビルド失敗を診断可能に(gradle エラー行を ::error:: へ)。

- 2026-09-14: java-env のプラグインビルドに Temurin21(JAVA_HOME)+GRADLE_USER_HOME を明示、失敗時は tail を notice で annotation へ。

- 2026-09-14: サンドボックスで Paper を起動できるよう paper-server-delivery(Mojang取得済みパッチ済みjar+libraries)を追加。

- 2026-09-14: ラダー値を参照実測に錠する BotLadderParityTest を追加。

- 2026-09-15: run8(通常戦闘403秒・アンカー117/116の独立再現)を docs/parity に追加。

- 2026-09-15: ヘッドレスBOT戦ハーネス(/narena-harness, -Drumilance.harness=true)を追加。

- 2026-09-15: ソフト依存(ProtocolLib/WorldEdit)未導入でも有効化できるようガード追加。

- 2026-09-15: v1.76.9 の jar を plugin-delivery に配信(java-env 再実行)。

- 2026-09-15: v1.76.10 の jar を配信(ハーネス測定のため)。

- 2026-09-15: v1.76.11 の jar を配信(BOT戦トレースの精度上げ)。

- 2026-09-15: v1.76.12 の jar を配信(クリスタルのパール圧モジュール)。

- 2026-09-15: v1.76.13 の jar を配信(パール圧のスタンドオフ修正)。

## v1.76.14 (2026-09-15)

実測(run3, 1.76.13, 石100ブロック床・通常戦)で、パールとアンカーが近接時に止まるのを修正:

- パール: 距離ゲートを撤去。map `g1gc/pearl` は距離を見ず「メカ機構が動いていない & pearlcd/hitcd が空き」なら
  常に投げる(1秒毎)。着地は相手ヒットボックスで止まる = 約1ブロック手前。
- アンカー: 「近接距離では撃たない」ゲートを撤去し、map `bin/27` と同じ条件に。つまり
  「今は殴れない(相手がhurt frame / 射程外 / 視線なし)」ときにのみ `anchor_tick` が動く。
  設置先は相手の足元隣なので block reach が唯一の距離制限。
- 計測: chatter(swing/hit)バジェット 300 → 3000。145秒の試合で60秒以降の剣ログが落ちていた。

## v1.76.15 (2026-09-15)

run4(1.76.14)の実測で残った2点を修正:

- パール: 近接時に「着地点が相手に近すぎる」で全部弾いていた(着地は相手ヒットボックスで止まるのが正常)。
  短いホップで再レイし、それでも近ければそのまま着地として採用。投擲レートが1秒毎に戻る。
- アンカー: 相手のhurt frame を待つ実装だと、こちらの偽プレイヤーはプラグイン独自のダメージ処理で
  i-frameが付かず永久に窓が開かない。map bin/27 の `cannot_anchor`(殴った直後の hitcd 6..1 の間は
  anchor_tick が return)を自分のスイング間隔で再現する形に変更。

## v1.76.16 (2026-09-15)

run5(1.76.15)でアンカーは動き出した(16回, 中央ギャップ0.70s = map rung 4t/4t/4t 相当)が、
33秒で止まりパールは1回のままだった。原因は2つとも「弾切れ/射程」:

- キットの弾切れ: アンカー16個 + グロウストーン64個(=チャージ4個消費×16)を33秒で使い切り、
  以降チェーンが起動しなくなっていた。map のキットは事実上無限なので、パールと同じく補充する。
- パール着地探索が `dist < 2.0` の候補を全部捨てていたため、近接(1.2-2.0ブロック)では
  常に着地失敗 → 投げられず。近接ホップ(<2ブロック)も許可する。

## v1.76.17 (2026-09-15)

map `crystal/passive/escape/pearl` を実装(受動パール)。距離8ブロック以内で `pearlcd`(20 tick)が
空いていれば、相手から約15ブロック後方へパールで離脱する(自分の足元3ブロック以内のエンドクリスタルは
先に破壊)。参照BOTが試合中76%の時間パールを持っているのはこの層のため。
チェイスパール(`g1gc/pearl`)とは同じ `pearlcd` を共有するので、合計は参照と同じ約1秒に1投。

加えて、部屋にクリスタルドリルを割り当てても「素の戦闘」が動いていた配線ミス(CRYSTAL 早期リターンの
後ろにドリル呼び出しがあった)を修正 → アサインされたドリルが通常戦の上に乗る。

## v1.76.18 (2026-09-15)

run6(1.76.16)でアンカーが 200回/145秒 = 82.8/min と過剰(参照は 18.1/min、6ブロック以内の
滞在時間あたりなら約40/min)。map の rung は 4t/4t/4t だが、実際は着地点を毎回 look/raycast で
取り直す(mark/mark_main → check/raycast3)ため、連鎖の再開は約1.4秒間隔。
これを定数 ANCHOR_RECYCLE_MS = 1000 として明示し、爆発後の待ちを rung と最大値を取る形にした。
ラダー(place→charge 4t、charge→explode 4t)はそのまま。

## v1.76.19 (2026-09-15)

参照(実 QuantumBOT)の qlog 405 秒分から**アイテム遷移の順序と保持率**を実測して合わせた:
pearl 76.5% / totem 7.2% / glowstone 5.7% / respawn_anchor 5.5% / end_crystal 2.7% /
diamond_sword 2.3%。遷移は respawn_anchor→glowstone 104 回、glowstone→totem 96 回
(アンカー連鎖のたびにトーテムへ戻る)、totem→respawn_anchor 69 回など。

- `BotAbilityState.hold(item, untilMs)` を追加。各モジュールは自分のタイマ分だけスロットを
  維持する(近接 150ms / アンカー設置・装填 200ms / 爆発後のトーテム 300ms / クリスタル 350ms)。
- `tickCrystalBot` の末尾で既定の持ち物(エンダーパール)へ戻す。gap食い中は金リンゴを維持。
- アンカー連鎖の再開間隔を実測にあわせて 1000 → 1700 ms(参照の実測 17.4/min へ寄せる)。
- tools/fight_profile.py のサンプル時刻バグを修正: サンプルは**デシ秒**(10 = 1 秒)で、
  イベントは秒。混在していたため距離バンドの対応付けが全滅していた。

## v1.76.21 (2026-09-15)

参照マップ(bot/Quantum's PvP Practice v1.18.zip)の**実際のモジュール解禁状態**を
`data/scoreboard.dat` から直接読んで突き合わせた。

参照(基準となる実 QuantumBOT 環境)で有効なもの:
`.anchors=1`, `.crystals=1`, `.crystal_playstyle=2`(**ANCHOR SPAMMER**), `.axe=1`,
`.cobweb=1`, `.strafe=1`, `.crit/.pcrit/.scrit=1`, `.jumpreset=1`, `.stun=1`,
`.triple_tap=1`, `.breach=1`, `.spear=1`, `.lava=1`, `.water=1`, `.far_pearl=1`,
`.wind_pearl=1`, `.dbp=1`, `.refill=1`, `.blocks_drop=1`, `.inf_tot=1`,
`.random=1`, `.random_mech=1`, `.crystal_hardcode=0`(通常の crystal/tick)。

無効(未解禁):`.shield`, `.pearl_spam`, `.slowfall`, `.elytra`, `.healing`, `.uppercut`,
`.no_pearl_land`, `.flat_terrain`, `.breakable`, `.fast`, `.small`, `.res`, `.old_kb`,
`.inf`, `.music`, `.shieldcd`, `.holding`, `.no_fall`, `.jreset`, `.jresett`,
`.prompt_activation`(すべて未設定=0)。`.gear=2`, `.ping=100`, `.cart_speed=3`。

→ つまり「クリスタルのシールド/パールスパム/スローフォール」は参照でも**切ってある**。
逆にこちらの実装で抜けていたのは以下の2つなので、それを足した。

1. **穴に詰まったらパールで脱出**(map `quantum:holeoffense/tick` → `holeoffense/dash` →
   `quantum:pearl`)。頭の上と四方が塞がっているとき、自分の足元を見て上方向へパールを投げる
   (`hotbar 7` + `use once` + `pearlcd=20`)。`tickHoleEscape` として追加。
2. **詰まったときは蓋を掘る**(map と同じ発想のもう一つの脱出路): BOT自身の頭上のブロックを
   1.5秒で破壊する(`tickBotMining` の先頭に追加)。従来は「相手の箱」だけを掘っていた。

さらに、アンカー連鎖の再開間隔を**実測値に戻した**: 参照 qlog
(`docs/parity/fabric_normal_anchor_run8_400s.log.gz`, 403秒, difficulty 2)で
`place -> charge = 0.20s`、`place -> 次の place = 中央値 0.65s / 最頻 0.60s(117件中57件)= 12 tick`。
つまり `anchor_cd 4t + charge_cd 4t + explosion_cd 4t` がそのまま刻みで、
v1.76.18/.19 で入れた 1000/1700 ms は誤り(全体 17.3/min は「近距離に8%しか居ない」ことの
帰結であって、連鎖自体を遅くする理由にはならない)。`ANCHOR_RECYCLE_MS = 200` に修正。

## v1.76.22 (2026-09-15)

map `bin/27` のアンカー開始条件を**そのまま**にした。`hit_decision_without_cd` は
`g1gc/can_hit` = 「can_see_target && distance..3 && **hurtTime=0**」の否定なので、
剣とアンカーは**排他**: 相手が今殴れる間は剣、相手の被弾硬直(10 tick)が明けた瞬間に剣の
判定が落ちてアンカー連鎖が始まる(place 4t → charge 4t → explode 4t)。

これまでの「自分のスイング間隔の最後 50ms だけアンカー可」は便宜的な近似で、実測でも
連鎖が 2.0 秒間隔に間延びしていた(参照は 0.60 秒)。1.76.20 でダミーが実際にダメージを
受けるようになり被弾硬直が回るようになったので、map どおりの条件に置き換えた。
