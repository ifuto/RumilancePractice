# Lunar Client Server Mappings — N Arena

Discord の Play Stats / フレンドメニューに **N Arena** と 1:1 ロゴを出すには、Lunar Client の [Server Mappings](https://www.lunarclient.com/news/what-is-lunar-clients-server-mappings) への登録が必要です。Apollo の Rich Presence だけでは一覧アイコンは変わりません。

## 手順（公式どおり）

1. GitHub で [LunarClient/ServerMappings](https://github.com/LunarClient/ServerMappings) を **Fork**
2. Fork 上で `servers/narena/` を作成（フォルダ名 = `metadata.json` の `id`）
3. このディレクトリの内容をアップロード:
   - `metadata.json`（必須）
   - `logo.png`（必須・512×512・PNG・できれば透過・1:1・アップスケール禁止）
4. **Pull Request** を作成 → 自動チェック → Lunar スタッフレビュー

詳細: https://lunarclient.dev/server-mappings/adding-servers/overview

## 提出前に必ず直す項目

`metadata.json` の次を実サーバー情報に書き換えてください。

| フィールド | 現状 | 要件 |
|-----------|------|------|
| `addresses` | `example.com` | **ルートドメインのみ**（`play.` なし）。例: `n-arena.net` |
| `primaryAddress` | `play.example.com` | 接続用 FQDN（サブドメイン可）。公開・解決可能であること |
| `socials` / `website` / `store` | 未設定 | 任意だが推奨（`https://` 必須） |

ID `narena` は一度採用するとリネーム不可です。リブランディング時は新 ID フォルダを作り、旧 ID を `inactive.json` に入れます。

## ブランド色（ライト水色）

- `primaryColor`: `#55FFFF`
- `secondaryColor`: `#7DD3FC`

プラグイン GUI も同じライト水色テーマに揃えています。

## ロゴ

`logo.png` はプロジェクト直下の `N-ARENA-LOGO.png`（512×512）から生成しています。透過が必要な場合は差し替えてください。
