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
| 他起因の速度ノイズ | 押し出し方向が「攻撃者→被害者」と逆/横向き | サンプル却下 |
| 爆発・KB棒などの特大速度 | 生速度 > 2.5 | 外れ値として集計から除外 |
| 動いている/落下中の相手 | ヒット直前の速度が 水平0.06/垂直0.1 以上 | **サンプル却下**（vanilla式は現在速度との混合のため、静止標的以外は外部係数の掛かり方が一意に定まらない） |
| 相手の耐衝撃が高い | **実装備の属性コンポーネントを合算**して推定 100% | 水平は計算不能として却下（垂直のみ有効） |
| 空中の相手 | 殴った瞬間に非接地 | 垂直係数は評価しない（バニラは空中で Y を変えないため） |

## 測定の仕組み

1. `ClientPlayerInteractionManager#attackEntity` — 自分の殴りを捕捉。攻撃方向（yaw
   由来）、スプリント状態、自分の Knockback エンチャント、相手の接地状態、そして
   **相手の装備一式（エンチャント・属性コンポーネント込み）** を記録。他プレイヤーの
   装備は描画のためにサーバーからクライアントへ同期されているので、防具4部位＋両手の
   アイテム、`generic.knockback_resistance` の属性値、各エンチャントとレベルをすべて
   読み取れます（初ヒット時にチャットへ一覧表示します）。
2. `ClientPlayNetworkHandler#onDamage`（EntityDamageS2CPacket）— ヒット成立を確認。
3. `ClientPlayNetworkHandler#onEntityVelocityUpdate`（EntityVelocityUpdateS2CPacket）—
   ヒット直後に相手へ送られたサーバー決定速度を捕捉。前回パケット値との差分＝
   **実際に適用された押し出し速度**。
4. 推定係数（vanilla 1.21.1 のソースと突合して検証済み）:
   - 基礎誘発: `damage()` が `takeKnockback(0.4)` を1回
   - 攻撃誘発: `k = attack_knockback属性 + (疾走かつチャージ率>0.9なら +1.0)` が 0 超なら
     さらに `takeKnockback(k × 0.5)`（1段目後の速度を半減合成するため単純加算ではない）
   - `takeKnockback` 内で `強さ ×= (1 − 耐衝撃属性)` → `setVelocity(vx/2 − s·dir,
     接地なら min(0.4, vy/2 + s), vz/2 − s·dir)`
   - 静止・接地の標的（それ以外は計測対象外）:
     - `k = 0`: `Δh = 0.4(1−r)`, `Δy = 0.4(1−r)`
     - `k > 0`: `Δh = (0.2 + 0.5k)(1−r)`, `Δy = min(0.4, Δh)`
   - 係数: `fH = |Δxz| / Δh`、 `fV = Δy / Δy_期待`

結果はアクションバー（各サンプル）とチャット（5件ごとの集計）に表示され、
`config/kbprobe.json` にサーバー別で永続化されます。

## ビルド

JDK 21 とネットワーク（Fabric/Mojang の maven へのアクセス）が必要です。

```bash
cd kb-probe
../gradlew build        # リポジトリルートの Gradle ラッパーを流用
# → build/libs/kb-probe-0.2.0.jar
```

リポジトリルート（プラグイン）のビルドや CI とは完全に分離されています
（root `settings.gradle.kts` から include していないため）。

## 導入

1. Fabric Loader 0.16+ の 1.21.1 クライアントを用意
2. `kb-probe-0.2.0.jar` を `.minecraft/mods/` へ（fabric-api 不要）
3. 計測したいサーバーに入り、アリーナ/デュエル等の **PvP が有効な場所** で
   普通にプレイヤーを殴るだけ。測定は完全受動です。

## 使い方のコツ

- **地面に立って静止している相手**を殴ってください（動いている相手は計測対象外です）
- 素手・立ち撃ちが最もシンプル（k=0: 基本誘発のみ）。疾走攻撃では k=+1.0 で
  Δh=(0.2+0.5k)(1−r) のモデルに切り替わります（どちらの殴りでも係数は求まります）
- まずロビーで殴って警告が出ることを確認 → PvP エリアで数発殴れば係数が出ます
- 5 サンプルごとに集計がチャットに出ます。10〜20 件取ると落ち着きます

## 限界（正直な注意点）

- 計測モデルは「静止・接地の相手への melee」に限定しています。歩行中・落下中の相手は
  サンプルを捨てます（vanilla式が現在速度と混合するため）。実用上はデュエル前の対面や
  リスポーン直後の相手が測りやすいです。
- **垂直係数**: サーバーが「計算途中の強さ」に係数を掛ける実装（式そのものを弄る）
  場合、バニラ式の `min(0.4, …)` クランプで Y が頭打ちになり、垂直は ×1.0 に見える
  ことがあります。本 Mod は水平を主指標としてください（RumilancePractice プラグインの
  ように「最終ベクトルにだけ掛ける」実装では垂直も正しく推定できます）。
- 相手の耐衝撃は「同期された装備の属性コンポーネント」から実計算します（ネザライトは
  もちろん、独自属性アイテムにも対応）。一方で**装備とは別にエンティティへ直接付与される
  属性**（キット/クラス適用・スキル系プラグイン・コマンド付与など）と、**装備を非表示にする
  プラグイン**（vanish系など）には対応できません。読み取れた装備は初ヒット時に表示される
  ので、推定の根拠はその都度確認できます。
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
- `LivingEntity#getAttributeValue(EntityAttributes.GENERIC_ATTACK_KNOCKBACK)`
  （1.21.2+ では属性名が `ATTACK_KNOCKBACK` に改名されている点に注意）
- `PlayerEntity#getAttackCooldownProgress(float)`
- 装備系: `LivingEntity#getEquippedStack(EquipmentSlot)`、
  `DataComponentTypes.ATTRIBUTE_MODIFIERS` / `AttributeModifiersComponent.Entry`、
  `AttributeModifierSlot` 列挙、`ItemStack#getEnchantments()`

## 変更履歴

- **0.4.0** — 論文レベル例外監査（レポート: `docs/kb-probe-verification.md`）。致命的2件と例外7カテゴリを修正:
  1. **集計チャットがコンパイル不能だった（出荷版 0.3.1 の致命傷）**:
     `ServerStats` は Gson 永続化のため public フィールドなのに、呼び出し側が
     `stats.hSamples()` メソッド形式で構えており `KbProbe.java` がコンパイルを通らなかった。
     フィールド参照に修正。
  2. **通知が永久ミュートだった潜伏バグ**: クールダウン初期値 `Long.MIN_VALUE` との差分が
     オーバーフローして「KB無効領域」「攻撃不成立」の通知が一回も出なかった。
     `-NOTICE_COOLDOWN` 初期化に修正。
  例外カテゴリへの新規ガード（いずれも係数に混入させない）:
  - 盾ブロッキング中・クリエイティブ/スペクテイターの対象は保留ヒット自体を作らない
    （偽の「保護領域」警告を根絶）
  - 窓内ダメージパケット2発以上 → 第三者同時攻撃の合成KBと判定して除外（混戦カウント表示）
  - 速度パケットがダメージ確認より先着（順序逆転）→ 「KB無効領域」誤判定せず静かに破棄
  - 対象のテレポート/除去（EntityPositionS2CPacket / EntitiesDestroyS2CPacket 新規捕捉）
    → 静かに破棄
  - 攻撃〜成立の間のジャンプ: 速度到着時点の再接地チェック + 垂直係数の妥当域
    [0.05, 8.0]（`KbProbeMath.verticalFactorPlausible`）で fV≈0 の偽サンプルを排除
  - メイスのスマッシュ攻撃（fallDistance > 1.5）は vanilla melee モデル外なので計測しない
- **0.3.1** — 徹底監査（vanilla 1.21.1 正確モデルとの照合 + 実測シミュレーション `sim/KbProbeSim.java`）で2件修正:
  1. **速度パケットの単位（致命的）**: `getVelocityX/Y/Z()` は `速度×8000` の生 int。
     そのまま比較すると全サンプルが外れ値ガード (>2.5) に捌かれ、mod が一切計測できなかった。
     `/8000`（`KbProbeMath.unscaleVelocity`）を挟んで正規化（実証: 捨てられる vs 記録される）。
  2. **疾走ヒット判定**: 旧コードはチャージ率>0.9 も要求していたが vanilla 1.21.1 の
     疾走ノックバックには当該ゲートは無い（検証済）。素の疾走中 attack で +1.0 に変更。
  また式・閾値を `KbProbeMath`（MC非依存）に集約し、本番コードとシミュレータで同一実装を共有。
