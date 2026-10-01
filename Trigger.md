# Trigger.md — 現在は不使用（2026-10-01 の検証で customize.yml に致命的欠陥を発見）。
#
# customize.yml の「Execute Trigger.md」ステップは、本ファイルから抽出したスクリプトが
# 終了コード 3 / 存在しないコマンド / bash構文エラー相当(CJK生テキスト) でも
# 常に success になることを実証済みです(v3/v4/v5 の実証コミット)。原因は未添付の
# 既知経路 (run ブロックの `| tee` 終了コード伝播/シェル既定) だと思われますが、
# ワークフローファイル自体は GH App の workflows 権限欠如でサンドボックスから
# 修正できません。修正版 customize.yml 案:
#
#   - name: Execute Trigger.md
#     run: |
#       set -Eeuo pipefail
#       bash -euo pipefail /tmp/trigger.sh 2>&1 | tee /tmp/trigger.log
#
# kb-probe (mod) の CI ビルドは `ci/java-env.sh` (java-env.yml 起動) 内に統合済み
# です — java-trigger.md の bump push、または Actions → java-env → Run workflow で
# 実ビルドかつ失敗伝播は証明済み。
