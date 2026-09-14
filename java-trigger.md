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
