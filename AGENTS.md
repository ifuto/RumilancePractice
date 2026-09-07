# 作業・検証ルール

## 毎回 CI を確認して配布用 JAR を生成する

Java / Gradle / YAML / リソースを変更したら、完成扱いにする前に次を行う。

1. Java 21 で `bash ./gradlew test shadowJar --no-daemon` を実行する。
   バージョンの正は `gradle.properties`。`build.gradle.kts` に直書きしない。
2. 既存の `.github/workflows/build.yml` を使い、**今回の最終コミット**で GitHub Actions を実行する。
   作業ブランチへの push で起動する。再実行が必要な場合は `gh workflow run build.yml --ref <作業ブランチ>` を使う。
   別ブランチや古いコミットの成功で代用しない。
3. `gh run view` / `gh run watch --exit-status` で終了結果を確認する。
   失敗したら `gh run view --log-failed` と `build-log` Artifact を調べ、修正して CI を再実行する。
   テストを無効化して通さない。
4. 成功した run の `jars` Artifact に `RumilancePractice-<version>.jar` があることを確認する。
   配布するのは依存ライブラリ入りの JAR で、`-thin.jar` ではない。生成物は Git に追加せず、
   Actions の Artifact または無視対象の `build/libs/` を使う。
5. 完了報告に CI の結果・run のリンク・配布用 JAR の入手先を記載する。

Workflow の `build` タスクにはテスト・`shadowJar`・リソースパック生成が含まれる。
ローカルに Java がない等で実行できない場合はその制約を明記し、CI で必ず検証する。
CI や Artifact の取得が環境・権限で阻まれた場合は、未検証のまま成功と報告しない。
