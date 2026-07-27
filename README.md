# RumilancePractice

Paper 1.21.11 向け Practice PvP プラグインです。ランク戦 / アンランクド戦 / ロビー / Kit / キュー / FAWE アリーナを独自実装しています。

## 必要環境

- Paper 1.21.11
- Java 21+
- （推奨）FastAsyncWorldEdit / WorldEdit — 未導入時はアリーナ再生成を無効化し、警告を出して起動します

## ビルド

```bash
cd RumilancePractice
./gradlew.bat build
```

成果物: `build/libs/RumilancePractice-1.0.2.jar`

## 導入

1. JAR を `plugins/` に配置
2. （推奨）FAWE を導入
3. サーバーを起動して設定ファイルを生成
4. 以下の管理者セットアップを実施

## 管理者セットアップ

1. `/practiceadmin tool` で Region Selector と Setup Menu を取得
2. `/slobby pos1` `/slobby pos2` でロビー範囲、`/slobby spawn` でスポーン
3. `/setlobbyitem` でロビー用インベントリ保存
4. `/setfunc ranked` などで機能アイテムを作成し、ロビーインベントリへ配置
5. `arenas.yml` にアリーナテンプレート（spawn-a / spawn-b）を追加、または `/arena` 系で登録
6. `kits.yml` を確認（nodebuff / boxing / sumo / gapple 同梱）
7. `/slobby validate` と `/practiceadmin status` で確認

## 主なプレイヤーコマンド

| コマンド | 内容 |
|---|---|
| `/duel` `/d` | ランク戦キュー GUI |
| `/unranked` `/ud` | アンランクド戦キュー GUI |
| `/duel <player>` | ランク戦申請 |
| `/unranked <player>` | アンランクド申請 |
| `/accept` `/deny` | 申請の承認 / 拒否 |
| `/queue leave` | キュー離脱 |
| `/lobby` `/spawn` | ロビーへ帰還 |
| `/setting` `/stats` `/profile` `/ranking` | 設定・統計・ランキング |

## 重要仕様

- **アンランクド戦は Elo / 勝率 / K/D / 公開統計を一切変更しません**（`UnrankedResultProcessor`）
- GUI はタイトルではなく `PracticeGuiHolder` + セッション UUID で識別
- 機能アイテムは表示名ではなく PDC `function_type` で識別
- プレイヤー状態は UUID キーの `PlayerStateManager` で一意管理
- サーバー停止時は切断 ChatBan を発行しません（`MatchSession.shuttingDown`）

## 権限

- `rumilance.user` — 一般（default: true）
- `rumilance.admin` — 管理（default: op）
- `rumilance.lobby.bypass` / `rumilance.punishment.bypass`
- プラン: `rumilance.user.def|mem|vip|vip_plus`

## トラブルシューティング

| 症状 | 対処 |
|---|---|
| FAWE 警告 | FAWE 導入、または `config.yml` の `fawe.enabled: false` |
| 試合が始まらない | `arenas.yml` に enabled な DUEL テンプレートがあるか確認 |
| キューに入れない | ロビー状態か、Kit が enabled か確認 |
| DB エラー | `database.yml`（標準 SQLite）と書込み権限を確認 |

## 最適化メモ（調査結果）

- Paper API `1.21.11-R0.1-SNAPSHOT` + Java 21 + Shadow 9.x を採用
- ItemStack 永続化は `serializeAsBytes()`（旧 Java 直列化より安全）
- Elo は仕様どおり K=64/32、上位10%は K=26（暫定中も優先）
- 非同期 Executor は 1〜4（標準 2）、Scoreboard は中央 10〜20 tick 更新
