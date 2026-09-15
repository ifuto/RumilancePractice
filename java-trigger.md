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
