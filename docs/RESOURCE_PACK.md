# リソパ（カスタムフォントアイコン）配布ガイド

Team Fight の赤/青マーカーと、Admin / VIP+ / VIP ランクバッジは **リソースパックのカスタムフォント**
で描画しています（`resourcepack/` がパックのソース、`dist/RumilanceResourcePack.zip` が配布用ビルド済みパック）。

- フォント: `rumilance:icons`（`resourcepack/assets/rumilance/font/icons.json`）
- グリフ（未割り当て文字 = Private Use Area）:
  - `\uE001` admin / `\uE002` VIP / `\uE003` VIP+ / `\uE004` 赤チーム / `\uE005` 青チーム
- グリフ文字・フォントIDは `config.yml` の `icons.*` で変更可能（`/rpadmin reload` 対応）

> 赤/青の見た目が逆だった場合は `resourcepack/assets/rumilance/textures/font/` の
> `team_red.png` と `team_blue.png` を入れ替えて再ZIP化してください。
> アイコンの大きさ・位置を調整したい場合は `icons.json` の `height` / `ascent`
> （現在は 9 / 8）を変更してください。

## 配布方法その1（おすすめ・手間ゼロ）: リポジトリの配布パックをそのまま使う

ビルド済みパックを `dist/` にコミット済みです。マージ後は **main** の raw URL を
そのまま `server.properties` に書けます（GitHub が HTTPS でそのまま配信します）:

```properties
resource-pack=https://raw.githubusercontent.com/ifuto/RumilancePractice/main/dist/RumilanceResourcePack.zip
resource-pack-sha1=94b37174995766f686fe6e0dae70136d8f5533ee
require-resource-pack=true
resource-pack-prompt={"text":"Rumilanceのアイコン表示に必要です","color":"aqua"}
```

（マージ前はブランチ指定でも可: `.../arena/01a06257-rumilancepractice/dist/RumilanceResourcePack.zip`）

### パックの中身を変えたとき

```bash
./gradlew resourcePackZip     # build/libs/RumilanceResourcePack.zip を再生成し、新しいSHA1を表示
cp build/libs/RumilanceResourcePack.zip dist/
cp build/libs/RumilanceResourcePack.sha1 dist/   # ※ sha1 が変わったら server.properties も更新
```

手動で zip する場合（Gradle なし）:

```bash
cd resourcepack && zip -qr ../dist/RumilanceResourcePack.zip . && cd ..
sha1sum dist/RumilanceResourcePack.zip
```

> ポイント: `pack.mcmeta` が ZIP の**ルート**に来るように圧縮すること
> （`cd resourcepack && zip -r ... .` の形）。`resourcepack` フォルダごと入れると認識されません。

## 配布方法その2: Cloudflare Pages（自前ホスティングしたい場合）

HTTPS + CDN + 無料枠で運用できるので、独自ドメインや配信元を分けたいならこちらも良いです。
クライアントは **ZIP ファイルそのもの**を URL からダウンロードします。

1. [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages** → **Create application** → **Pages**
2. GitHub を連携してこのリポジトリを選択し、次の設定でデプロイ:
   - Build command:
     ```bash
     mkdir -p public && cd resourcepack && zip -qr ../public/RumilanceResourcePack.zip .
     ```
   - Output directory: `public`
3. 配布 URL: `https://<プロジェクト名>.pages.dev/RumilanceResourcePack.zip`
   ブラウザで開いて ZIP が落ちてくれば OK。

```properties
resource-pack=https://<プロジェクト名>.pages.dev/RumilanceResourcePack.zip
resource-pack-sha1=<sha1sum の値>
require-resource-pack=true
```

（代替: R2 公開バケット / 既存の Web サーバーに ZIP を置いても同じです。
要は「HTTPS で ZIP が直接取得できる URL」であれば何でも動きます。）

> パックの中身を変えたら再デプロイ → サーバーの `resource-pack-sha1` も
> 新しい ZIP の SHA1 に更新してください（SHA1 が合わないとクライアントが拒否します）。

## 補足

- 1.21.9+ の `min_format`/`max_format` 入り `pack.mcmeta` 済み（対象 1.21.11 / pack_format 75）
- `require-resource-pack=true` にすると、パック未適用のプレイヤーにグリフが
  豆腐（□）に見える問題をそもそも防げます
- リソースパックを入れていない環境では空白グリフ扱いになるだけなので、
  プラグイン動作自体は壊れません

## プラグイン側の表示ロジック

- `config.yml` → `icons.enabled: true`（デフォルト）で有効
- ランクバッジは **ロビー・FFA・キューではランクアイコンのみ**、
  **チーム戦ではチームマーカーと併記** で TABリストとネームタグに表示されます
  （ロビー系: `RankIconNameTags` / 試合中: `MatchTeamVisuals`）
- ランク判定は保存ランクと権限（`rumilance.admin` 等）の両方を見て、
  Admin > VIP+ > VIP の優先順で1つだけ表示します（NORMは何も付きません）
