# KB Probe（他鯖ノックバック実測 Mod）

他の Minecraft サーバーで「実際にプレイヤーに掛かっているノックバック」をクライアント
側から実測し、バニラ基準との係数（水平 ×N / 垂直 ×N）を推定する Fabric クライアント
Mod です。自分がプレイヤーを殴った結果として観測される速度パケットを解析します。

## コンセプト: 「正しくない KB は計らない」

ロビー等でプレイヤーを殴ってもノックバックが発生しない（保護されている）サーバーは
多く、そこで「係数 0」と記録してしまうと統計が壊れます。KB Probe は以下のガードで
**不正な状況からは一切サンプルを取りません**:

| 状況 | 検出方法 | 挙動 |
| --- | --- | --- |
| 保護領域で攻撃自体が無効 | 殴ったのにダメージ成立パケットが 12 tick 以内に来ない | 計測せず「攻撃が成立しませんでした」と通知 |
| ダメージは入るのに KB が無効（ロビー等） | ダメージ成立後 12 tick 以内に速度パケットが来ない | 「KB無効領域」と判定、係数は記録しない |
| 相手が無敵時間中 | 殴る時点で `hurtTime > 0` | その殴りは最初から保留しない |
| 他起因の速度ノイズ | 押し出し方向が攻撃方向と逆/横向き | サンプル却下 |
| 爆発・KB棒などの特大速度 | 生速度 > 2.5 | 外れ値として集計から除外 |
| 相手の耐衝撃が高い | 見えているネザライト装備から推定 100% | 水平は計算不能として却下（垂直のみ有効） |
| 空中の相手 | 殴った瞬間に非接地 | 垂直係数は評価しない（バニラは空中で Y を変えないため） |

## 測定の仕組み

1. `ClientPlayerInteractionManager#attackEntity` — 自分の殴りを捕捉。攻撃方向（yaw
   由来）、スプリント状態、持ち物の Knockback エンチャント、相手の接地・装備を記録。
2. `ClientPlayNetworkHandler#onDamage`（EntityDamageS2CPacket）— ヒット成立を確認。
3. `ClientPlayNetworkHandler#onEntityVelocityUpdate`（EntityVelocityUpdateS2CPacket）—
   ヒット直後に相手へ送られたサーバー決定速度を捕捉。前回パケット値との差分＝
   **実際に適用された押し出し速度**。
4. 推定係数:
   - 水平 `fH = |Δxz| / (強さ × (1 − 耐衝撃))`、強さ ≈ 0.4 + 0.5×KBエンチャ(+ 走攻 0.5)
   - 垂直 `fV = Δy / 0.4`（相手接地時のみ）

結果はアクションバー（各サンプル）とチャット（5件ごとの集計）に表示され、
`config/kbprobe.json` にサーバー別で永続化されます。

## ビルド

JDK 21 とネットワーク（Fabric/Mojang の maven へのアクセス）が必要です。

```bash
cd kb-probe
../gradlew build        # リポジトリルートの Gradle ラッパーを流用
# → build/libs/kb-probe-0.1.0.jar
```

リポジトリルート（プラグイン）のビルドや CI とは完全に分離されています
（root `settings.gradle.kts` から include していないため）。

## 導入

1. Fabric Loader 0.16+ の 1.21.1 クライアントを用意
2. `kb-probe-0.1.0.jar` を `.minecraft/mods/` へ（fabric-api 不要）
3. 計測したいサーバーに入り、アリーナ/デュエル等の **PvP が有効な場所** で
   普通にプレイヤーを殴るだけ。測定は完全受動です。

## 使い方のコツ

- 素手で、走らずに、地面に立っている相手を殴るのが一番きれいに取れます
- まずロビーで殴って警告が出ることを確認 → PvP エリアで数発殴れば係数が出ます
- 5 サンプルごとに集計がチャットに出ます。10〜20 件取ると落ち着きます

## 限界（正直な注意点）

- **垂直係数**: サーバーが「計算途中の強さ」に係数を掛ける実装（式そのものを弄る）
  場合、バニラ式の `min(0.4, …)` クランプで Y が頭打ちになり、垂直は ×1.0 に見える
  ことがあります。本 Mod は水平を主指標としてください（RumilancePractice プラグインの
  ように「最終ベクトルにだけ掛ける」実装では垂直も正しく推定できます）。
- **走り攻撃の加算は近似値**（0.5 固定）として計算に使っています。気になる場合は
  立ち撃ちで測ってください。
- 相手の耐衝撃は「見えているネザライト装備」からの推定です（属性を持つ防具や
  スキル系プラグインには対応しません）。
- この Mod は測定結果を自動では送信しません。データはローカルの
  `config/kbprobe.json` にのみ保存されます。

## 他の MC バージョンへの移植

`gradle.properties` の `minecraft_version` / `yarn_mappings` / `loader_version` を
対象バージョンに合わせれば基本的にそのまま動きます。壊れやすい yarn 名は次の 4 点
（バージョン差が出たらここを直す）:

- `ClientPlayNetworkHandler#onDamage(EntityDamageS2CPacket)` / `packet.entityId()`
- `ClientPlayNetworkHandler#onEntityVelocityUpdate(EntityVelocityUpdateS2CPacket)`
  / `packet.getId()/getVelocityX/Y/Z()`
- `ClientPlayerInteractionManager#attackEntity(PlayerEntity, Entity)`
- `EnchantmentHelper.getKnockbackBonus(LivingEntity)`
