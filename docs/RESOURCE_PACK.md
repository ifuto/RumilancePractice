# リソパ（カスタムフォントアイコン）配布ガイド

Admin / VIP+ / VIP の **ランクバッジ** はリソースパックのカスタムフォントで描画します。
（`resourcepack/` がパックのソース、`dist/RumilanceResourcePack.zip` が配布用ビルド済みパック）

- フォント: `rumilance:icons`（`resourcepack/assets/rumilance/font/icons.json`）
- グリフ（未割り当て文字 = Private Use Area）:
  - `\uE001` admin / `\uE002` VIP / `\uE003` VIP+
- グリフ文字・フォントIDは `config.yml` の `icons.*` で変更可能（`/rumireload` 対応）

> **チーム判別はリソパ不要です**: チーム戦では名前の前にチーム色の `●`（赤=RED / 青=AQUA）が
> 付きます。通常のテキストなのでパック未適用のプレイヤーにもそのまま見えます。
> ランクバッジの画像だけをパックから取得しているため、パックは小さく（約30KB）、
> 画像差し替えもランク画像3枚だけで済みます。
>
> ランクアイコンの大きさ・位置を調整したい場合は `icons.json` の `height` / `ascent`
> （現在は 9 / 8）を変更して再ZIP化してください。

## 配布方法（本命・既定でON）: プラグインが直接プレイヤーに送る

このプラグインは **join 時にプラグイン自身からパックを送信** します
（`server.properties` の設定は一切不要）。`required: true` のときは
**パックを拒否・ダウンロード失敗したプレイヤーはキック** されます
（アイコン表示にパックが必須のため）。

`config.yml` → `resource-pack.*`（`/rumireload` で再読込＋オンライン全員へ再送）:

```yaml
resource-pack:
  enabled: true          # false でプラグインからの配布を無効化
  url: "https://…/RumilanceResourcePack.zip"   # パックの直接ダウンロードURL
  sha1: "c4cf82d41ba0564aaac4752cb6a95225e768b2e2"   # ZIP の SHA1（40桁hex）
  required: true         # true = 拒否/失敗でキック
  prompt: "…"            # クライアントのパック適用ダイアログに出す文
  kick-message: "…"      # キック時の表示文（\n で改行可）
```

既定値はリポジトリの `dist/` パック（上記の GitHub raw URL）になっているので、
**何も設定しなくてもこのまま動きます**。自前のホスティングを用意したら
`url`（と、中身を変えた場合は `sha1`）だけ書き換えて `/rumireload` してください。

> **⚠ `server.properties` の `resource-pack=` 系を使っていた場合は削除してください。**
> 両方が有効だと、クライアントにパックが二重に要求されることがあります。
> （代替手段として server.properties 配布を使いたい場合は下の「代替」節へ）

### パックの置き場所（ホスティング）

**既定はリポジトリの `dist/` を GitHub raw で直接配信**（`config.yml` の
`resource-pack.url` 初期値がそれ）。`dist/RumilanceResourcePack.zip` と
`dist/RumilanceResourcePack.sha1` を更新して push するだけで配信も更新されます。

自前の CDN が欲しい場合の代替として、HTTPS + CDN + 無料枠の **Cloudflare Pages**
でも配信できます。クライアントは **ZIP ファイルそのもの**を
URL からダウンロードします。

1. [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages** →
   **Create application** → **Pages** → **Connect to Git**
2. GitHub を連携して `ifuto/RumilancePractice` を選択し、次の設定でデプロイ:
   - Build command:
     ```bash
     mkdir -p public && cd resourcepack && zip -qr ../public/RumilanceResourcePack.mczip .
     ```
   - Output directory: `public`

   > **⚠ 拡張子は `.zip` にしないこと**: Cloudflare Pages は出力ディレクトリの
   > `.zip` ファイルをデプロイ時に自動展開してしまいます（中身がバラで置かれ、
   > ダウンロードできなくなる）。`.mczip` など別拡張子ならそのまま配信されます。
   > Minecraft クライアントは拡張子ではなく中身（ZIP構造）で判定するので、
   > `resource-pack.url` に `.mczip` のURLをそのまま指定して問題ありません。
   > （ファイル内容が変わらないので **SHA1 もそのまま使えます**）

3. デプロイ後の配布 URL:
   `https://<プロジェクト名>.pages.dev/RumilanceResourcePack.mczip`
   ブラウザで開いてファイルが落ちてくれば OK（落ちたファイルを `.zip` に
   リネームして開ける＝正常なZIP、と確認できます）。
4. `config.yml` → `resource-pack.url` に上記 URL を設定して `/rumireload`。

現在コミット済みパックの SHA1:

```
c4cf82d41ba0564aaac4752cb6a95225e768b2e2
```

> **グリフの描画について（1.21.6+ 対策）**: このパックはアイコンのグリフプロバイダーを
> `rumilance:icons` だけでなく `minecraft:default` と `minecraft:uniform` にも
> マージしています（フォントは同じID間でパック同士マージされるため、既存フォントを
> 壊しません）。`config.yml` → `icons.font: "default"`（既定）ならサーバーは
> フォント属性を付けずにグリフを送るため、クライアントがカスタムフォントを解決
> できない環境でもアイコンが表示されます。

> パックの中身を変えたら再デプロイ（push すれば自動）→ `resource-pack.sha1` も
> 新しい ZIP の SHA1 に更新してください（SHA1 が合わないとクライアントが拒否します）。

### 代替: server.properties で配布（プラグイン配布を無効化する場合のみ）

プラグイン配布を使わず従来どおり server.properties で配布したい場合は
`resource-pack.enabled: false` にした上で、次のように設定します:

```properties
# Cloudflare Pages 利用時
resource-pack=https://<プロジェクト名>.pages.dev/RumilanceResourcePack.mczip
resource-pack-sha1=c4cf82d41ba0564aaac4752cb6a95225e768b2e2
require-resource-pack=true
resource-pack-prompt={"text":"Rumilanceのアイコン表示に必要です","color":"aqua"}

# またはリポジトリの dist パック直接指定（マージ後は .../main/...）
resource-pack=https://raw.githubusercontent.com/ifuto/RumilancePractice/main/dist/RumilanceResourcePack.zip
resource-pack-sha1=c4cf82d41ba0564aaac4752cb6a95225e768b2e2
require-resource-pack=true
```

ただしこの場合、拒否したプレイヤーのキックはサーバー本体の仕様に依存します。
**こだわりがなければプラグイン配布（既定）を推奨します。**

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
- パック未適用のプレイヤーにグリフが豆腐（□）に見える問題は、プラグイン配布の
  `resource-pack.required: true`（拒否/失敗でキック）でそもそも防げます
  （server.properties 方式なら `require-resource-pack=true` が相当）
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

## カスタム盾（裏ランク `custom_shield`）

表には一切表示されない「裏ランク」`custom_shield` を持つプレイヤーは、試合中に
OP が割り当てた **Custom Model Data** を付与された盾を受け取ります。リソースパック側で
その Model Data に高精細な盾イラストを割り当てておくと、そのプレイヤーの盾だけが
特別な見た目になります。

### 1. プレイヤーに裏ランクと Model Data を割り当てる（ゲーム内・OP）

```
/urank custom_shield <player>     # 裏ランクを付与
/urank shield <player> <cmd>      # 盾の Custom Model Data を指定（例: 90001）
/urank gui                        # 一覧からクリックで増減できる管理画面
/urank list                       # 保有者一覧
/urank remove <player>            # 裏ランクを剥奪
```

- 裏ランクはどの表示（ネームタグ・TAB・アイコン）にも出ません
- `custom_shield` 保有者は VIP+ の盾模様エディタを使えなくなります
  （専属イラストの盾のため）。耐久・エンチャントなどは通常通りです
- 割り当てた盾の Custom Model Data は**ドロップした瞬間に消え**、ただの盾になります

### 2. リソースパックに盾イラストを登録する（運営）

盾のテクスチャ（バニラ盾の UV 配置、推奨 512x512）を用意して:

```bash
python3 tools/add_custom_shield.py <cmd> <image.png> [--pack-root resourcepack]
```

例: `python3 tools/add_custom_shield.py 90001 art/my_shield.png`

実行内容:

1. `assets/rumilance/textures/shield/shield_<cmd>.png` に画像をコピー
2. `assets/rumilance/models/item/shield_<cmd>.json`（+ `_blocking` 版）を生成
   （バニラ盾の表示トランスフォームをそのまま使用）
3. `assets/minecraft/models/item/shield.json` に `custom_model_data` override を追加
   （既存の登録は `_rumilance_shields` キーに記録され、何度実行してもマージされます）

その後パック zip を再ビルドして再アップロードすれば完了です。
