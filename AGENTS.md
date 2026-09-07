# 作業・検証ルール

## 毎回 CI を回してバグ取りし、配布用 JAR を生成する

Java / Gradle / YAML / リソースを変更したら、完成扱いにする前に必ず次を行う。

1. Java 21 で `bash ./gradlew test shadowJar --no-daemon` を実行する（ローカルに Java がある場合）。
   バージョンの正は `gradle.properties`。`build.gradle.kts` に直書きしない。
2. 既存の `.github/workflows/build.yml` を使い、**今回の最終コミット**で GitHub Actions を実行する。
   作業ブランチへの push で自動起動する。再実行は `gh workflow run build.yml --ref <作業ブランチ>`。
   別ブランチや古いコミットの成功で代用しない。
3. `gh run watch <run-id> --exit-status` / `gh run view <run-id>` で結果を確認する。
   失敗したら `gh run view <run-id> --log-failed` と `build-log` Artifact を読み、修正して再度 push → CI。
   テストを削除・無効化して通さない。
4. 成功した run の `jars` Artifact に `RumilancePractice-<version>.jar` があることを確認する
   （`gh run download <run-id> -n jars -D /tmp/jars`）。配布するのは依存ライブラリ入りの JAR で、
   `-thin.jar` ではない。生成物は Git に追加しない（`build/` は ignore 済み）。
5. 完了報告に CI の結果・run のリンク・配布用 JAR の入手先（Artifact 名）を記載する。

Workflow の `build` タスクにはテスト・`shadowJar`・リソースパック zip 生成が含まれる。
ローカルに Java / Maven リポジトリへのアクセスがない等で実行できない場合はその制約を明記し、CI で必ず検証する。
CI や Artifact の取得が環境・権限で阻まれた場合は、未検証のまま成功と報告しない。
