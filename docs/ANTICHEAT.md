# クライアント連携アンチチート (rumilance-ac) — 設計書

## 目的と脅威モデル

このサーバーのACは「サーバー・クライアント両立型」です。先行事例（Badlion BAC,
CheatBreaker, Lunar/Apollo, GrimAC）の調査に基づき、次の原則で設計しています。

1. **クライアントの「申告」は一切信用しない。** すべて証明（proof）で代替する。
2. **強制は対象者限定。** 既定では全員任意（BAC方式）。OP/console が
   `/anticheat require <player>` で個別に必須化できる。
3. **キック判定は決定的条件のみ（誤検出ゼロ保証）。** ヒューリスティックでキックしない
   （Grimの「物理的に不可能なもの以外フラグしない」思想と同じ）。
4. **obscurityに依存しない。** プロトコルは公開。CheatBreakerが破られた歴史的教訓。

## 構成要素（証明の3層）

### 層1: 存在証明 — 一回使い切りノンス

- サーバーは参加時と60秒毎のハートビートで `HELLO|<nonce>` を送信。
- Modは `VERIFY|2|…|<nonce>|…` で即応答。古い/録画した応答は無効。
- 必須化ユーザーが応答しなければ猶予後キック（`anticheat.kick-not-verified` /
  `kick-heartbeat`）。応答のない通常ユーザーには何も起きない（任意制）。

### 層2: 正統ビルド証明 — JAR SHA-256 ピン留め

- Modは自身のjarのSHA-256を報告（改変・リパック検知）。
- OPが `/anticheat trust <sha256>` を1つでも設定すれば、必須ユーザーは
  ピン留めビルド以外で接続不可（`kick-untrusted-build`）。
- これにより「rumilance-acの名前で握手を真似るだけの偽Mod」は
  **公式jarと完全に同一バイト列を実際に実行する以外**突破不可。

### 層3: ゲームプレイ束縛（アンチ・オラクル中核） — 移動系列ダイジェスト

層1+2だけでは**オラクル攻撃**（クリーンな公式Modを別JVMで動かし、
チートクライアントの通信だけを中継＝握手だけ本物）が残ります。これを閉じます。

- Modは `ClientConnection#send` のHEAD mixinで**送信した移動パケット**を観測し、
  x/y/z(double bits)・yaw/pitch(float bits)を64-bit FNV-1aで連結ダイジェスト化。
- サーバーは `PlayerMoveEvent` から**同一式で独立にダイジェスト再構築**し、
  pongのパケット番号位置の値と照合。
- TCPは順序保存・欠損なしなので、正規クライアントでは**必ず一致**します
  （Sodium・Iris・Lithium・ShieldStats等は移動シリアライズに触れない＝誤検出ゼロ）。
- 逆に、チート側が「modの後段でパケット注入/改竄/ドロップ」をすると即乖離。
  オラクル側は「不正クライアントの動作を完全に再現する移動系列」を提示できない
  （それができたらそれは最早「チートしていない」のと同じ）。
- 暫定乖離は1セッション3回・10分間隔を上限に**リシンク猶予**（テレポート嵐などの
  端折れ吸収）。それを超えた乖離＝必須ユーザーならキック（`kick-digest`）。

## 『理論上突破されない』の正確な範囲（正直な明記）

**防げる（数学的に検知可能）**: 1) Modなしでの必須接続、2) 応答の使い回し/録画、
3) 偽Modによる握手偽装（ピン稼働時）、4) attested接続へのチートパケット混入・改竄・除去、
5) クリーンな分身を使ったオラクル（層3により、チート動作と整合する提示列を作れない）。

**防げない（全ユーザー空間ACの共通天井）**: 同一JVM内でmodのtapより先手で
フックされた場合（-javaagentのクラス置き換え等でmod自身の計算根を握られる場合）。
BAC/CheatBreaker/Vanguard級も同一権限レベルの攻撃者には原理的に勝てません。
対抗は権限分離（カーネル/TEE）のみで、Fabric Modの範囲外です。

## 運用（OP/console）

```
/anticheat require <player>      # 指定ユーザーをMod必須化（オンラインなら30秒猶予）
/anticheat unrequire <player>    # 解除
/anticheat status <player>       # 検証状態/ビルド/ブランド/検出/JAR信頼
/anticheat list                  # 必須化一覧
/anticheat trust <sha256>        # 正統ビルドをピン留め（CIビルドのjarに対して sha256sum）
/anticheat untrust <sha256>      # ピン解除
/anticheat trustlist             # ピン一覧
```

Mod jarはCIの `build-mod` ワークフローが `mod/` 変更Push（または手動）で生成し、
`mod-jars` アーティファクトとして出力します。リリース後はOPが
`sha256sum rumilance-ac-*.jar` で値を取り、`/anticheat trust` に登録してください。

## スキャナについて（誤検知しない設計）

Mod側のチート検出は**ブラックリスト限定・完全一致・2シグナル**方式です。
Sodium・ShieldStatsを含む一般Modはテーブルに存在しないため**原理的に**検出対象外。
mod idが一致してもエントリクラス同居まではSUSPECT扱い（キックに使わない）。

## 参考にした先行事例

- Badlion BAC: 提携サーバー参加時のみクライアント走査を起動・評価。任意参加＋
  サーバー側ACは基盤として残す方式。
- CheatBreaker: 全員必須＋obscurity方式の限界（同一権限チートに破られる歴史）。
- Lunar/Apollo: クライアントACではなくサーバー側からMod機能を制御するオプションAPI型。
- GrimAC: クライアントを信用しない予測型サーバーAC。「不可能動作のみ判定」の誤検出対策。
