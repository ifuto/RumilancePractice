# リソパ（カスタムフォントアイコン）配布ガイド

Admin / VIP+ / VIP / PRO の **ランクバッジ** はリソースパックのカスタムフォントで描画します。
（`resourcepack/` がパックのソース、`dist/RumilanceResourcePack.zip` が配布用ビルド済みパック）

- フォント: `rumilance:icons`（`resourcepack/assets/rumilance/font/icons.json`）
  + `minecraft:default` / `minecraft:uniform` にも同じ provider をマージ済み
  （`config.yml` → `icons.font: "default"` でカスタムフォントID解決に依存せず描画される）
- グリフ（未割り当て文字 = Private Use Area）:
  - `\uE001` admin / `\uE002` VIP / `\uE003` VIP+ / `\uE004` PRO
- グリフ文字・フォントIDは `config.yml` の `icons.*` で変更可能（`/rumireload` 対応）

> **チーム判別はリソパ不要です**: チーム戦では名前の前にチーム色の `●`（赤=RED / 青=AQUA）が
> 付きます。通常のテキストなのでパック未適用のプレイヤーにもそのまま見えます。
> ランクバッジの画像だけをパックから取得しているため、パックは小さく（約33KB）、
> 画像差し替えもランク画像4枚だけで済みます。
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
  sha1: "42d41dcee474c577b653049e55d8c4ca87364f7b"   # ZIP の SHA1（40桁hex）
  required: true         # true = 拒否/失敗でキック
  prompt: "…"            # クライアントのパック適用ダイアログに出す文
  kick-message: "…"      # キック時の表示文（\n で改行可）
```

既定値は **GitHub Release のアセット URL**（`ResourcePackService.DEFAULT_URL` =
`config.yml` → `resource-pack.url` の初期値）なので、**何も設定しなくてもこのまま動きます**。
自前のホスティングを用意したら
`url` だけ書き換えて `/rumireload` してください（`sha1` は起動ごとに URL から取得されて
`resource-pack.json` に書き戻されるので、通常は触る必要がありません。offline 運用の
フォールバックに使う場合だけ手書きします）。

> **⚠ `server.properties` の `resource-pack=` 系を使っていた場合は削除してください。**
> 両方が有効だと、クライアントにパックが二重に要求されることがあります。
> （代替手段として server.properties 配布を使いたい場合は下の「代替」節へ）

### パックの置き場所（ホスティング）

**既定は GitHub Release のアセット**（`releases/download/<tag>/RumilanceResourcePack.zip`）。
`dist/` はその Release に上げる zip の置き場で、**push しただけでは配信は更新されません**。
中身を変えたら必ず再ビルド → 再アップロードまで行ってください（下記「パックの中身を変えたとき」）。
同じタグに `--clobber` で上げ直せば、サーバーは起動時に URL から SHA-1 を取り直すため
**config.yml もプラグインも変更不要で次回再起動から反映**されます。

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
42d41dcee474c577b653049e55d8c4ca87364f7b
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
resource-pack-sha1=42d41dcee474c577b653049e55d8c4ca87364f7b
require-resource-pack=true
resource-pack-prompt={"text":"Rumilanceのアイコン表示に必要です","color":"aqua"}

# または Release アセット直接指定（タグは config.yml → resource-pack.url と揃える）
resource-pack=https://github.com/ifuto/RumilancePractice/releases/download/v1.76.52/RumilanceResourcePack.zip
resource-pack-sha1=<tools/release/build-pack.sh が表示する40桁hex>
require-resource-pack=true
```

ただしこの場合、拒否したプレイヤーのキックはサーバー本体の仕様に依存します。
**こだわりがなければプラグイン配布（既定）を推奨します。**

### パックの中身を変えたとき

```bash
# 1) 再ビルド（JDK 不要。配線検査 → dist/ へ zip + sha1）
tools/release/build-pack.sh

# 2) 公開（Release アセットを差し替え。同じタグなら config.yml 変更不要）
tools/release/attach-pack.sh v1.76.52
```

`build-pack.sh` は zip を作る前に次を検査します（**PRO バッジが `resourcepack/` にだけ存在して
配布 zip に入っていなかった事故の再発防止**）:

- `pack.mcmeta` が JSON として読めるか、`pack_format` / `min_format` / `max_format` があるか
- 各 font provider の `"file": "<ns>:<path>"` の実体がパック内に存在するか（無ければ豆腐）
- `textures/**/*.png` がどの provider からも参照されて余っていないか（素材だけ置いて配線忘れ）

`dist/RumilanceResourcePack.sha1` は `sha1sum -c` が読める 2 列形式（`<hash>  <ファイル名>`）で
書き出します（ハッシュだけだと検証できません）。`attach-pack.sh` は上げる前に
`build-pack.sh --check`（dist が `resourcepack/` より古くないか）を通します。

新しいタグに上げる場合は URL も追随させます:

```bash
tools/release/build-pack.sh --tag v1.76.57   # config.yml の resource-pack.url / sha1 を同期
# ResourcePackService.java の DEFAULT_URL も同じタグに更新 → 再ビルド
```

Gradle 経由でも同じ zip が作れます（`build` に組み込み済み、出力は `build/libs/`）:

```bash
./gradlew resourcePackZip     # build/libs/RumilanceResourcePack.zip + .sha1（新しいSHA1を表示）
cp build/libs/RumilanceResourcePack.{zip,sha1} dist/
```

> ポイント: `pack.mcmeta` が ZIP の**ルート**に来るように圧縮すること
> （`cd resourcepack && zip -r ... .` の形）。`resourcepack` フォルダごと入れると認識されません。
> `build-pack.sh` / `resourcePackZip` はどちらもこの形で作ります。

## 補足

- 1.21.9+ の `min_format`/`max_format` 入り `pack.mcmeta` 済み
  （`pack_format: 75` = 1.21.11 向け、適用範囲 `min [34,0]` 〜 `max [100,0]`。
  1.21.x 全クライアントと 26.1/26.2 など今後の新リリースでも「非互換」扱いになりません）
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
- ランク判定は **保存ランク**（`/rank` で付与された値）そのものを1つだけ描画します。
  `Admin / VIP+ / VIP / PRO` はそれぞれのバッジ、`NORM` は無バッジ。
  OP や `rumilance.admin` などの権限は **バッジには反映されません**
  （テスト運用で全員を OP にしても名前が OWNER だらけにならないため。
  権限はコマンド/機能ガード側だけを見ます）
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

その後 `tools/release/build-pack.sh` → `tools/release/attach-pack.sh <tag>` で
パックを再ビルド・再アップロードすれば完了です（手順は上の「パックの中身を変えたとき」）。
