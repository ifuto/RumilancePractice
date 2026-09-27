# Shield Web — カスタム盾のブラウザ管理

裏ランク `custom_shield`（任意PNGを丸ごと盾に貼る特別装備）を、**サーバーPC上の超軽量
内蔵HTTPサーバー**でWeb管理する機能です。既存の手動フロー
（`tools/add_custom_shield.py` → pack再ビルド → Release再アップ → 再起動）を
**ブラウザからの1操作＋数秒**に置き換えます。

```
[ブラウザ] ドロップで盾PNGをアップ → プレイヤー名を入れて「紐づけ」
     │
     ▼
[プラグイン内蔵HTTP :8765]  PNG検証/リサイズ → pack-srcへ注入 → zip再構築(決定的) → SHA-1更新
     │                                          │
     ├─ /pack.zip  …… プレイヤーへ再送(即時) ◄───┤
     └─ /admin     …… 管理UI(トークン+LAN限定)
```

## 有効化（初回のみ）

1. `config.yml` を編集:
   ```yaml
   shield-web:
     enabled: true
     bind: "0.0.0.0"      # LANやTailscaleからも管理したい。同一PCのみなら "127.0.0.1"
     port: 8765
     manage-pack-hash: true
     admin-allow-external: false
   ```
2. サーバー再起動（`/rumireload` はHTTPサーバーを再開させない点に注意）。
3. コンソール or ゲーム内OPで `/urank web` → **管理URL+トークン** が出る:
   ```
   管理画面: http://localhost:8765/admin?token=<64hex>
   ```
   そのURLをサーバーPCのブラウザで開くだけ。LAN内の別PCからは
   `http://<サーバーPCのIP>:8765/admin?token=<同じトークン>`。

## プレイヤーへのパック配布（外部公開したい場合）

`/pack.zip` が常に最新のリソースパックを返します（通常のMinecraftリソースパックURLと同じ）。
デフォルトのGitHub Release URLから切り替えると、**アップロード即時に全員が新しい盾を見られる**ようになります。

### 推奨: Tailscale Funnel（ポート開放不要）

```bash
# サーバーPCで（Tailscale導入済みなら1行）
tailscale funnel --bg 8765
tailscale funnel status   # → https://<マシン名>.<テールネット>.ts.net が払い出される
```

`plugins/n-arena/resource-pack.json` の `url` を次に書き換えて `/rumireload`:

```json
{ "url": "https://<マシン名>.<テールネット>.ts.net/pack.zip" }
```

- FunnelはHTTPS終端まで面倒を見てくれるので、**プラグイン側は平HTTPのまま**でOK
- クラフター全員がTailscale不要（Funnelは公衆インターネットへ公開される）
- `manage-pack-hash: true`（既定）なら、upload/rebuildのたびにSHA-1が自動で json に反映されます

### LAN運営の場合

`url`: `http://<サーバーPCのIP>:8765/pack.zip`（例: `http://192.168.1.20:8765/pack.zip`）。
LANのプレイヤーはこれで受け取れます。従来のGitHub Release配布に戻したいときは元のURLに戻すだけです。

## Web画面の操作

| やりたいこと | 操作 |
|---|---|
| **盾を追加** | ①にPNGをドロップ → 表示名（CMDは空欄で100から自動発番）→ アップロード |
| **画像差し替え** | ①で既存CMDを指定してアップ（再送まで自動）|
| **プレイヤーに適用** | ②の盾カードでプレイヤー名（オンラインはドロップダウン）→「紐づけ」|
| **解除** | ③側の解除欄 or ②で個別に。オフラインでもOK（次のキット適用から有効/無効）|
| **盾を削除** | 各カードの「削除」→ 確認。紐づいていた人からも自動で外れる |
| **再送だけしたい** | ③「最新パックを全員に再送」 |

画像は **PNG・8MBまで・透過対応**。アップ直前に**縦長（盾フェイス比 32:44）へ自動トリミング**され、
木製ボディのベーステクスチャへ合成されて 512×512 の完成品が登録されます — 写真でも四角い画像でも
そのまま盾になります。ベースは内蔵のプロシージャル生成（ダークオーク板＋上部の縁）ですが、
`plugins/n-arena/shield-web/pack-src/assets/rumilance/textures/shield/_base.png` (512×512推奨) を置くと
それが使われます（例: バニラ `shield_base_nopattern` 相当の正確なベースに差し替えたいとき）。

## サーバー管理タブ（盾タブの横にあります）

| タブ | 内容 |
|---|---|
| **コンソール** | サーバーへコンソールコマンドを送信。**入力するとサーバー本体と同じタブ補完候補**が出ます（モバイルで打ち間違えないため）。実行は1秒に1回まで・全履歴はサーバーログに記録。`shield-web.console-enabled: false` で整タブ仰止 |
| **サーバーログ** | `logs/latest.log` の末尾100/200/400行を表示（最大192KBに制限）。開いている間だけ読み、自動更新(5秒)は任意トグル |
| **バトルログ** | 直近50試合の勝敗・キル・所要時間（7日保持の履歴ストアから） |
| **観戦** | 進行中の試合の HP・チーム・脱落状態を3秒おきの**状態スナップショット**で表示。映像ではないのでラグの心配なし。タブを閉じれば通信も完全停止 |
| **セルフテスト** | 「動作テストを実行」ボタン1発で pack.mcmeta／zip構成／盾の配線／PNGデコード／再構築の決定性を検査し、オンライン各プレイヤーのパック適用状態も表示 |

設計上、**この画面はサーバーに常駐ポーリングさせません**（開いているタブの分だけ、しかも開いている間だけ取得）。ログ読み出し・補完・観戦スナップショットはすべて上限付きの一括応答で、ストリーム・動画の類は入れていません。

## 既存フローとの併用

- `/urank shield <player> <cmd>` や `/urank gui`（ホルダー一覧・CMD微調整）は**そのまま併用可**。
  Webで発番したCMDと手打ちしたCMDは同じ `custom_model_data` を指します
- `tools/add_custom_shield.py` はリポジトリの `resourcepack/` を直す従来経路。Shield Webは
  サーバー上の `plugins/n-arena/shield-web/pack-src/` にある**別の作業コピー**を更新します
  （jar内の `pack-base/` から初回だけ展開される）。リポジトリ側の配布pack(dist)を盾入りに
  したい場合は従来どおり `add_custom_shield.py` + `build-pack.sh` を使ってください

## セキュリティの設計

| 対策 | 内容 |
|---|---|
| **トークン認証** | 初回起動時に256bitを自動生成 (`shield-web/token.txt`)。全 `/admin/**` 要求で必須（`?token=` or `Authorization: Bearer`、定数時間比較） |
| **ソースIP制限** | 管理画面は loopback / RFC-1918 / link-local / ULA / **Tailscale CGNAT (100.64.0.0/10)** からのみ。外部からは `admin-allow-external: true` を足さない限り403 |
| **アップロード検証** | PNGマジックバイト + ImageIOでのデコード必須。8MB上限。HTMLの埋め込み等は拒否 |
| **公開面の最小化** | 無認証で開いているのは `/pack.zip` だけ（pack本体はどのみち公開URLで配るもの） |

## トラブルシュート

| 症状 | 対処 |
|---|---|
| `起動できませんでした（port 8765 が使用中?）` | 他アプリがポートを占有。`shield-web.port` を変えるか相手を止める |
| 管理画面は開けるがプレイヤーに届かない | `resource-pack.json` の `url` がこのサーバー（or Funnel）を向いているか、`manage-pack-hash: true` かを確認 |
| 画像は入ったが盾がバニラのまま | 紐づけされていない（stateのholdersを確認）。**パックの再DL中は旧バッジのまま** — 数秒待って切り替わる |
| 外部から `403` | `admin-allow-external: true` は最終手段。Tailscale (100.x) 経由なら既定のまま通ります |
| 配布しているzipを人がダウンロードして中身を見たい | `http://<host>:8765/pack.zip` をブラウザで直接開けば取れる（誰でもOK＝設計どおり） |

データはすべて `plugins/n-arena/shield-web/` 配下(`pack-src/`・`pack.zip`・`shields.json`・`token.txt`)。
紐づけは従来どおり `hidden_ranks.yml`（`/urank` 系と同じ保存先）。
