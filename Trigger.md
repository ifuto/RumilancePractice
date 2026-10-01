# Trigger: kb-probe (0.5.0) の Fabric (Loom) ビルド検証

0.5.0 で追加したクライアントGUI（ホーム画面/Kボタン/サーバー一覧/コピージャソン）が
yarn/fabric-api 前でコンパイルできるよう、CI 上で実ビルドを実行します。

```bash
set -e
echo "== kb-probe build =="
cd kb-probe
chmod +x gradlew
./gradlew build --no-daemon --stacktrace
echo "== jars =="
ls -la build/libs/ || true
sha256sum build/libs/*.jar 2>/dev/null || true
```
