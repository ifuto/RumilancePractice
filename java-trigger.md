# java-trigger — Java 実行環境アーティファクトの発火マーカー

このファイルへの変更を含む push ごとに `.github/workflows/java-env.yml` が走り、
GitHub Actions のアーティファクト **`java-env`** に以下を保管する:

| ファイル | 中身 |
|---|---|
| `jdk21.tar.gz` | Eclipse Temurin 21(linux x64・ポータブルJDK・tar展開するだけ) |
| `paper-1.21.11-<build>.jar` | Paper 1.21.11 サーバー(最新ビルドをAPI解決) |
| `sha256s.txt` | チェックサム一覧 |

## 導入手順(オーナー操作・1回だけ)

サンドボックスの GitHub App トークンには `workflows` 権限がないため、
エージェントは `.github/workflows/` への追加ができない(refused)。
**リポジトリ直下の `java-env.workflow.yml` が本体**なので、それを配置する:

1. GitHub web UI でブランチ `arena/01a0948b-rumilancepractice` を開く
2. `Add file` → `Create new file` → パスに `.github/workflows/java-env.yml`
3. 中身にリポジトリ直下 `java-env.workflow.yml` をコピペして Commit
   (このコミット自体が java-trigger.md 初回作成を兼ねないなら、
    `java-trigger.md` も同画面で 1 行編集すると即発火)
4. 以降はエージェントが `java-trigger.md` を編集する push のたびに自動発火
   (エージェント側は pull --rebase 済みの force push なので消さない)

main に置いて `Run workflow` ボタン運用でも可(その場合は発火は手動クリック)。

## サンドボックスでの取得手順

サンドボックスの下りは github.com(git)と api.github.com のみで、Azure blob 系
(アーティファクト/リリース資産)は EOF 遮断 → **`gh run download` は使えない**。
そこで workflow が **`java-env-delivery` ブランチ**に分割コミットするので、git で受ける:

```bash
git clone --depth 1 --branch java-env-delivery \
  https://github.com/ifuto/RumilancePractice.git /tmp/ship
cat /tmp/ship/delivery/jdk21.tar.gz.part-* > /tmp/jdk21.tar.gz
(cd /tmp/ship/delivery && sha256sum -c sha256s.txt --ignore-missing)
mkdir -p /tmp/jdk21 && tar -xzf /tmp/jdk21.tar.gz -C /tmp/jdk21 --strip-components=1
/tmp/jdk21/bin/java -version
```

## 運用メモ

- サンドボックスは JDK を持たないため、Java 実行環境はこのアーティファクト経由で調達する。
- `/tmp` は非永続(サンドボックス再構築で消える)→ その都度上記コマンドで再取得。
- アーティファクト保持は 30 日。期限切れ・手動再実行はどちらでも:
  - `java-trigger.md` に何か 1 行追記して push(自動発火)
  - `gh workflow run java-env.yml`(workflow_dispatch)
- 用途: `bot/herobot-*.jar`(Fabric MOD)を `javap` で静的解析、Paper サーバー実行検証など。
- 既存ワークフロー(build / build-mod)は一切変更していない。判定は従来どおり name=build のみ。

## 発火ログ

- 2026-09-13: ワークフロー導入(73b68ed)後の初回発火 → run 34760045775 は Paper 取得で失敗。
  原因: api.papermc.io **v2 API が sunset**。fill v3 (fill.papermc.io/v3) へ移行した修正版を
  `java-env.workflow.yml` に反映済み → オーナーが `.github/workflows/java-env.yml` へ適用済み(156ad77)。
- 2026-09-13: v3 版での再発火(2回目)→ run 34760226363 は **成功**。
  ただしサンドボックスからアーティファクトDLが blob 遮断(EOF)で取得不能と判明。
  → **`java-env-delivery` ブランチ配送を追加した最終版**(3回目の貼り付けで確定)。
- 2026-09-13: delivery ブランチ版を適用(6392c93)→ 3回目の発火は run 34760427888。
  Steps 1-5 成功(JDK/Paper取得・アーティファクトOK)、**Step 6 のみ失敗**:
  clone/push URL に GITHUB_TOKEN を埋め込んでいなかった(public なので clone は通り
  push が 403)。トークン URL + `push -f`(孤儿ブランチ再作成)へ修正 → **4回目の貼り付け**。
- 2026-09-13: トークン認証fix版を適用(85bf75c)→ 4回目の発火も Step 6 が 128。
  ログ遮断のため失敗行は不明 → **actions/checkout@v4 の永続クレデンシャル方式へ全面切替**
  (手動 clone 廃止・チェックアウト作業ツリーをそのまま orphan 化して push)+
  ERR trap で失敗行を `::error::` annotation に出す。detached HEAD からの全路径を
  ローカルベアリモートで検証済み → **5回目の貼り付け**。
- 2026-09-13: checkout永続クレデンシャル版を適用(fe8f422)→ 5回目の発火も失敗。
  ただし **ERR trap が原因を特定**: `actions/checkout` のデフォルト `clean: true`
  (`git clean -ffdx`)が前ステップの `bundle/` を消していた → bundle先を
  ワークスペース外の `/tmp/bundle` へ変更 → **6回目の貼り付け**。
- 2026-09-13: /tmp/bundle 版を適用 → **6回目の発火で run 34761374376 成功**。
  `java-env-delivery` ブランチを clone → 結合 → sha256 両 OK →
  Temurin 21.0.12.1 で `java -version` 成功・herobot.jar へ `javap` 成功。
  **Java環境の調達経路が確立**(サンドボックス再構築のたびに「取得手順」を実行)。
- 2026-09-13(夜): QuantumBOT 実測フェーズ開始。起動検証済み Fabric サーバー一式(マップ+MOD+eula/properties 同梱)を mc-server-delivery ブランチへ配送するワークフローに拡張 → 貼り付け待ち。

- 2026-09-13(深夜): Crystalボットをマップg1gc実装に全面一致させた(v1.67.0)。詳細は docs/bot-combat-parity.md。
- 2026-09-13(深夜2): アンカー経路も全行照合 → g1gc anchor chain(place→charge→爆発)を全ラングの梯子で実装(v1.68.0)。crystal_cd の初回抽出誤り(6/4/3/2/2/3→正6/6/6/3/2/3)も訂正。
- 2026-09-13(深夜3): 全モジュール監査表を docs/bot-combat-parity.md に作成。金リンゴ80%実食・ボット採掘・可視ホットバー切替を実装(v1.69.0)。次フェーズ=マネキン→パケットプレイヤー(NMS ServerPlayer)。
- 2026-09-14: パケットプレイヤー移行Phase1(v1.71.0)。paperweight-userdev導入+BotBodyシーム+Carpet式フェイクプレイヤー実装。トグル bot.packet-bots は既定false(サーバー検証後に切替)。yml更新は不要。
- 2026-09-14(2): 「戦わないBOT」根本原因2件を修正 — slowcast梯子は戦闘未使用(視線はスナップ+delta)/近接ミス二重罰。メイスもpacket branch。AFK packet化は次工程(v1.72.0)。
