# Trigger v5: 全文実行判定 (CJK本文がbashに通れば即127 → failure が正)
# Trigger: kb-probe (0.5.0) の Fabric (Loom) ビルド検証 v4 (exit 3 = 抽出実証、緑=成功)

前回トリガは「wrapperがない kb-probe/ 内で ./gradlew を呼ぶ」失敗スクリプトでした。
kb-probe は root の gradlew を借りてビルドする standalone 単独プロジェクトです
（settings.gradle 冒頭コメントのとおり `cd kb-probe && ../gradlew build`）。
以下では **wrapper の存在検査 + 実ビルド** を明示して実行します。


