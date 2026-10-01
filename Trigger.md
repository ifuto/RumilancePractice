# Trigger: kb-probe (0.5.0) の Fabric (Loom) ビルド検証 v3 (wrong-code marker付き — Execute失敗が正解)

前回トリガは「wrapperがない kb-probe/ 内で ./gradlew を呼ぶ」失敗スクリプトでした。
kb-probe は root の gradlew を借りてビルドする standalone 単独プロジェクトです
（settings.gradle 冒頭コメントのとおり `cd kb-probe && ../gradlew build`）。
以下では **wrapper の存在検査 + 実ビルド** を明示して実行します。

```bash
set -e
echo "== preflight =="
which bash && bash --version | head -1
ls -la gradlew gradle/wrapper/ || true
test -f kb-probe/gradlew && echo "kb-probe has own wrapper" || echo "kb-probe has NO wrapper (expected)"
echo "== kb-probe build via repo-root wrapper =="
chmod +x gradlew
./gradlew -p kb-probe build --no-daemon --stacktrace
echo "== jars =="
ls -la kb-probe/build/libs/ || true
sha256sum kb-probe/build/libs/*.jar 2>/dev/null || true
```
