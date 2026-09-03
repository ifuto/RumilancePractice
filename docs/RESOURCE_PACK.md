# リソパ（カスタムフォントアイコン）配布ガイド

Admin / VIP+ / VIP の **ランクバッジ** はリソースパックのカスタムフォントで描画します。
（`resourcepack/` がパックのソース、`dist/RumilanceResourcePack.zip` が配布用ビルド済みパック）

- フォント: `rumilance:icons`（`resourcepack/assets/rumilance/font/icons.json`）
- グリフ（未割り当て文字 = Private Use Area）:
  - `\uE001` admin / `\uE002` VIP / `\uE003` VIP+
- グリフ文字・フォントIDは `config.yml` の `icons.*` で変更可能（`/rpadmin reload` 対応）

> **チーム判別はリソパ不要です**: チーム戦では名前の前にチーム色の `●`（赤=RED / 青=AQUA）が
> 付きます。通常のテキストなのでパック未適用のプレイヤーにもそのまま見えます。
> ランクバッジの画像だけをパックから取得しているため、パックは小さく（約30KB）、
> 画像差し替えもランク画像3枚だけで済みます。
>
> ランクアイコンの大きさ・位置を調整したい場合は `icons.json` の `height` / `ascent`
> （現在は 9 / 8）を変更して再ZIP化してください。

## 配布方法（本命）: Cloudflare Pages

HTTPS + CDN + 無料枠で配信できます。クライアントは **ZIP ファイルそのもの**を URL から
ダウンロードします。

1. [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages** →
   **Create application** → **Pages** → **Connect to Git**
2. GitHub を連携して `ifuto/RumilancePractice` を選択し、次の設定でデプロイ:
   - Build command:
     ```bash
     mkdir -p public && cd resourcepack && zip -qr ../public/RumilanceResourcePack.zip .
     ```
   - Output directory: `public`
3. デプロイ後の配布 URL:
   `https://<プロジェクト名>.pages.dev/RumilanceResourcePack.zip`
   ブラウザで開いて ZIP が落ちてくれば OK。
4. サーバー設定:
   ```properties
   resource-pack=https://<プロジェクト名>.pages.dev/RumilanceResourcePack.zip
   resource-pack-sha1=<zip の SHA1>
   require-resource-pack=true
   resource-pack-prompt={"text":"Rumilanceのアイコン表示に必要です","color":"aqua"}
   ```

現在コミット済みパック（チーム画像削除済み版）の SHA1:

```
d2305f86808ea3b44e085276341f24991d6f64a0
```

> パックの中身を変えたら再デプロイ（push すれば自動）→ サーバーの
> `resource-pack-sha1` も新しい ZIP の SHA1 に更新してください
> （SHA1 が合わないとクライアントが拒否します）。

### フォールバック: リポジトリの配布パックを直接使う

Cloudflare を用意する前は、`dist/` にコミット済みのパックを GitHub raw URL で
そのまま配信することもできます:

```properties
resource-pack=https://raw.githubusercontent.com/ifuto/RumilancePractice/main/dist/RumilanceResourcePack.zip
resource-pack-sha1=d2305f86808ea3b44e085276341f24991d6f64a0
require-resource-pack=true
```

（マージ前はブランチ指定でも可: `.../arena/01a06257-rumilancepractice/dist/RumilanceResourcePack.zip`）

### パックの中身を変えたとき

```bash
./gradlew resourcePackZip     # build/libs/RumilanceResourcePack.zip を再生成し、新しいSHA1を表示
cp build/libs/RumilanceResourcePack.zip dist/
cp build/libs/RumilanceResourcePack.sha1 dist/   # ※ sha1 が変わったら配信設定も更新
```

手動で zip する場合（Gradle なし）:

```bash
mkdir -p dist && (cd resourcepack && zip -qr ../dist/RumilanceResourcePack.zip .)
sha1sum dist/RumilanceResourcePack.zip
```

> ポイント: `pack.mcmeta` が ZIP の**ルート**に来るように圧縮すること
> （`cd resourcepack && zip -r ... .` の形）。`resourcepack` フォルダごと入れると認識されません。

## 補足

- 1.21.9+ の `min_format`/`max_format` 入り `pack.mcmeta` 済み（対象 1.21.11 / pack_format 75）
- `require-resource-pack=true` にすると、パック未適用のプレイヤーにグリフが
  豆腐（□）に見える問題をそもそも防げます
- リソースパックを入れていない環境では空白グリフ扱いになるだけなので、
  プラグイン動作自体は壊れません（チームの `●` はテキストなので常に表示されます）

## プラグイン側の表示ロジック

- `config.yml` → `icons.enabled: true`（デフォルト）で有効
- ランクバッジは **ロビー・FFA・キューではランクアイコンのみ**、
  **チーム戦ではチーム色の `●` と併記** で TABリストとネームタグに表示されます
  （ロビー系: `RankIconNameTags` / 試合中: `MatchTeamVisuals`）
- ランク判定は保存ランクと権限（`rumilance.admin` 等）の両方を見て、
  Admin > VIP+ > VIP の優先順で1つだけ表示します（NORMは何も付きません）
- 試合中のアクションバーは `config.yml` → `match.action-bar-mode`（`score`=点数〔既定〕/
  `time`=経過時間 `min:sec`）で切り替え可能
