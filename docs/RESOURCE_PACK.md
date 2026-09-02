# リソパ（カスタムフォントアイコン）配布ガイド

Team Fight の赤/青マーカーと、Admin / VIP+ / VIP ランクバッジは **リソースパックのカスタムフォント**
で描画しています（`resourcepack/` ディレクトリがパックのソースです）。

- フォント: `rumilance:icons`（`resourcepack/assets/rumilance/font/icons.json`）
- グリフ（未割り当て文字 = Private Use Area）:
  - `\uE001` admin / `\uE002` VIP / `\uE003` VIP+ / `\uE004` 赤チーム / `\uE005` 青チーム
- グリフ文字・フォントIDは `config.yml` の `icons.*` で変更可能（`/rpadmin reload` 対応）

> 赤/青の見た目が逆だった場合は `resourcepack/assets/rumilance/textures/font/` の
> `team_red.png` と `team_blue.png` を入れ替えて再ZIP化してください。
> アイコンの大きさ・位置を調整したい場合は `icons.json` の `height` / `ascent`
> （現在は 9 / 8）を変更してください。

## 1. ZIP を作る

リポジトリの `resourcepack/` 内身を**ルートが pack.mcmeta になるように** ZIP 化します。

```bash
cd resourcepack && zip -qr ../RumilanceResourcePack.zip . && cd ..
sha1sum RumilanceResourcePack.zip   # server.properties に貼る SHA1
```

GitHub Actions の `build.yml` に zip ステップを追加できる権限があるなら、
`resourcepack/` を zip して artifact として上げるようにすると配布が楽になります
（現状はローカルで zip する手順で十分です）。

## 2. 配布先 — Cloudflare Pages（おすすめ）

クライアントは **ZIP ファイルそのもの**を URL からダウンロードするので、
Pages のビルドで ZIP を生成して静的アセットとして置きます。
HTTPS + CDN + 無料枠で運用できるので Cloudflare Pages が素直です。

1. [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages** → **Create application** → **Pages**
2. GitHub を連携してこのリポジトリを選択し、次の設定でデプロイ:
   - Build command:
     ```bash
     mkdir -p public && cd resourcepack && zip -qr ../public/RumilanceResourcePack.zip .
     ```
   - Output directory: `public`
3. デプロイ後、配布 URL はこれになります:
   `https://<プロジェクト名>.pages.dev/RumilanceResourcePack.zip`
   ブラウザで開いて ZIP が落ちてくれば OK。

（代替: R2 公開バケット / 既存の Web サーバーに ZIP を置いても同じです。
要は「HTTPS で ZIP が直接取得できる URL」であれば何でも動きます。）

> パックの中身を変えたら再デプロイ → サーバーの `resource-pack-sha1` も
> 新しい ZIP の SHA1 に更新してください（SHA1 が合わないとクライアントが拒否します）。

## 3. サーバー設定（server.properties）

```properties
resource-pack=https://<プロジェクト名>.pages.dev/RumilanceResourcePack.zip
resource-pack-sha1=<上で求めたSHA1>
require-resource-pack=true
resource-pack-prompt={"text":"Rumilanceのアイコン表示に必要です","color":"aqua"}
```

- 1.21.9+ の `min_format`/`max_format` 入り `pack.mcmeta` 済み（対象 1.21.11 / pack_format 75）
- `require-resource-pack=true` にすると、パック未適用のプレイヤーにはグリフが
  豆腐（□）に見える問題をそもそも防げます

## 4. プラグイン側

- `config.yml` → `icons.enabled: true`（デフォルト）で有効
- ランクバッジは **ロビー・FFA・キューでは名前のみ**、**試合中はチームマーカーと併記** で
  TABリストとネームタグに表示されます（`RankIconNameTags` / `MatchTeamVisuals`）
- リソースパックを入れていない環境では空白グリフ扱いになるだけなので、
  プラグイン動作自体は壊れません
