# 🎨 MC Skin Poser（単一ファイル版）

**Minecraft スキンの 3D ポーザー / サムネイル作成ツール**
Cloudflare Workers で動く Web アプリです。**ファイルは `worker.js` 1つだけ**で、
HTML・CSS・クライアントJS・スキン取得APIが全部入っています。

## デプロイ方法（推奨・一番簡単）

1. [Cloudflare ダッシュボード](https://dash.cloudflare.com/) → **Workers & Pages** → **Create → Worker**
2. **Quick Edit** に `worker.js` の中身を全部貼り付けて **Deploy**
3. 公開URL を開いてプレイヤー名を入力 → 「取得」

ビルド不要・追加ファイル不要。これだけで動きます。

## ローカル開発（おまけ）

```bash
cd mcskin-poser
npm install
npm run dev        # wrangler dev → http://localhost:8787
```

## 機能

| 機能 | 内容 |
|---|---|
| スキン取得 | `GET /api/skin?name=<名前>` — Mojang API（名前→UUID→テクスチャ）。スリム/クラシック自動判別、ケープ対応、5分キャッシュ |
| 視点操作 | ドラッグで回転・ズーム・パン |
| ポーズ編集 | 頭/体/右腕/左腕/右足/左足 × XYZ の数値スライダー（-180〜180°） |
| クリック選択 | 3Dモデルの部位をクリック → 回転/移動ギズモ（TransformControls） |
| プリセット | アイドル / 歩く / 走る / 座る / Tポーズ / 手を振る / 指差し / 力こぶ |
| 手持ちアイテム | **公式アイテムテクスチャ**（diamond_sword 等）を手に表示。右手/左手それぞれに剣・斧・ツルハシ・弓・盾・金リンゴ・たいまつ・トライデント・トーテム |
| 防具 | **公式アーマーレイヤーテクスチャ**（leather/chainmail/iron/gold/diamond/netherite）。レザーは色で染色可。ヘルメット/チェスト/レギンス/ブーツ |
| エンチャント | アイテム・防具に公式 glint テクスチャのキラキラ |
| 表示 | 背景色変更 / グリッド切替 / カメラリセット / 自動フレーミング |
| 出力 | **2K（2560×1440）・背景透過 PNG ダウンロード** |

## テクスチャの取得元

- アイテム: `https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/master/assets/minecraft/textures/item/*.png`
- 防具: `.../textures/models/armor/<素材>_layer_1.png` / `_layer_2.png`
- 光沢: `.../textures/misc/enchanted_glint_item.png` / `enchanted_glint.png`

> ブランチは `master` を使用。将来アセットが更新されても主要アイテムのファイル名は安定しています。
> もし取得に失敗する場合は、`worker.js` 内の `TEXTURE_BASE` をバージョン付きブランチ（例: `1.21`）に変えてください。

## 実装メモ / 既知の制限

- **このサンドボックスでは実行確認ができていません**（外部ネットワーク不可）。
  `node --check` による構文検証・HTML構造の検証は実施済みです。初回はブラウザの
  コンソール（F12）を確認してください。
- 防具の UV はバニラのモデル JSON 準拠で組んでいますが、細部の見た目は実機で
  微調整が必要かもしれません（特に両腕のショルダー部分）。
- アイテムは「カメラ向きの平面（ビルボード）」に実テクスチャを貼った方式です。
- Mojang API はレート制限（429）があります。連打すると少し待つ必要があります。
- スキンテクスチャ・アセットCDN は CORS 対応済みのためブラウザから直読みできます。

## 構成

```
worker.js          ← これだけで全部（Worker API + HTML + CSS + クライアントJS 内蔵）
wrangler.toml      ← ローカル dev / deploy 用（main = worker.js）
package.json       ← wrangler 依存のみ
```
