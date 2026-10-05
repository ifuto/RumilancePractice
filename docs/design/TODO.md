# RumilancePractice — やるべきことリスト

更新: 2026-10-05 / ブランチ `arena/01a106b3-rumilancepractice` / HEAD `3b053c1` (v1.92.51)

> 定期自動要約で文脈が消えても追えるようにするためのメモ。
> `/tmp/todo.md` にも同じものを置いているが、サンドボックスの /tmp はスナップショットに
> 残らないので**本体はこのファイル**。

## 進行中

- なし

## 完了（8項目リスト + 追加1件）

| # | 内容 | バージョン | commit |
|---|---|---|---|
| 1 | Party Setting をパーティホットバーへ（スロット7・OWNER のみ） | 1.92.44 | `338d006` |
| 2 | `/kit` のキット内 GUI（編集画面）を整備 | 1.92.49 | `42442fb` |
| 3 | 削除可能プリセットアイテムを OP が右クリック設定画面から指定 | 1.92.45–46 | `73571b7` `ecb128d` |
| 4 | lore / タイトルを簡潔に（文章を使わない） | 1.92.48 | `b9dc39d` |
| 5 | More Enchant Item カスタマイズ（下インベントリ3段） | 1.92.47 | `8188671` |
| 6 | キットごとのハートインジケーター ON/OFF（デフォルト ON） | 1.92.44 | `338d006` |
| 7 | Duel 中スニーク+TAB でロビーと同じ TAB | 1.92.44 | `338d006` |
| 8 | `/ekit <player>` を全員が閲覧のみで実行可 | 1.92.44 | `338d006` |
| 9 | FFA FreeHit 対策（下に仕様・実装メモ） | 1.92.50–51 | `c28d407` `3b053c1` |

- gui.json（docs/design/gui.json）10画面の完全再現: **完了**（v1.92.42、監査レポートは
  `docs/design/gui-json-audit.md`、未解決3件あり）

## 未判断・保留

- [ ] Party MAIN GUI の (5,5) パーティ設定タイルを外すか（1番でホットバーに移したので二重に
      なっている）。外すと gui.json の r5 にも一致するが、明示的な指示はまだ無いので保留。
- [ ] FreeHit 対策の ON/OFF をアリーナ単位ではなく**キット単位**（/kit のコンフィグ画面）に
      置くべきか。現在はアリーナ単位。各 FFA アリーナはキットを1つしか持たないので実用上は
      同じだが、複数アリーナでキットを共有すると挙動が分かれる。
- [ ] FreeHit のヒット演出は現在「音だけ」。cancel するとバニラの被弾フラッシュとパーティクル
      まで消えるため。赤い点滅まで欲しい場合は別途パケット処理が必要。

## 作業ルール（このリポジトリでの約束）

- 変更ごとに `gradle.properties` のバージョンを上げ、CI（`.github/workflows/build.yml`）で
  確認する。ローカルコンパイルは不可（paper-api が取得できない）。
- `docs/design/gui.json` がレイアウトの唯一の情報源。過去のレイアウト判断は無効。
- lore / タイトルは簡潔に。状態 + 1行ヒントのみ。文章を使わない。
- GUI に該当セルが無い機能は、その画面に無理やり入れない。
- 数値は検証済みのものだけを結果として出す。
- 作業の途中で止めない。

### よくある事故と対処

- **リポジトリが再クローンされてローカル履歴が巻き戻る**ことがある（v1.92.50 push 時に発生）。
  症状: push が non-fast-forward で弾かれ、`git log` の親が古いベースコミットになっている。
  作業ツリーは無事なので、次で復旧できる:
  ```
  git fetch origin
  git reset --soft origin/arena/01a106b3-rumilancepractice
  git diff --cached --stat     # 今回の変更だけが差分に出ることを確認
  git commit && git push origin arena/01a106b3-rumilancepractice
  ```
  `reset --soft` 前の HEAD の sha をメモしておけば、`git reset --soft <sha>` で戻せる。
- レコードに項目を足したら**テストのコンストラクタ呼び出しも直す**。`FfaArena` 追加時、
  本体は通って `compileTestJava` だけ落ちた（v1.92.50 → 1.92.51 で修正）。

---

# 9. FFA FreeHit 対策

ユーザー原文（2026-10-05）:

> /ffaで、そのキットにFreeHit対策(相手が準備していないのに殴り始めるのの対策)の機能を導入するか
> どうかON/OFFできます。デフォはOFF。ONの場合は、まずFFAに入って敵を無差別に殴ってもダメージや
> KB、Combat判定は入りません(しかしヒットの音等は通常通り)。ただし殴ったら、プレイヤーには表示
> されませんが、仮Combat状態が10s継続します。仮Combat中に抜けたりしてもペナルティはありません。
> 相手を殴って10秒以内に相手から殴られたらexpのpickup音と同時に「⚠ Combat with {Opponent} has
> started」って黄色で流れる。プレイヤーA vs Bだと仮定した際、AとBがCombatし始めた時、どちらか
> 片方が死ぬ(抜ける)又は最後の攻撃から30sが経つまではcombatが付き、AとBはそれぞれAはBを、BはA
> しか殴ることができません。AやBが他の人を殴ったりしてもダメージKB、仮Combatにならず、他の人が
> AやBを殴ってもKbやダメージ、仮combatが付きません。

## 実装メモ

### 設定

- `FfaSettingsGui` のアリーナ detail ページ、`MenuScaffold.gridSlot(19)`（FFA Bot の隣）。
  アクション `toggle:freehit` → `FfaService#setFreehitGuard`。
- 保存先は `arenas.<id>.settings.freehit-guard`（ffa.yml）。**既定 OFF**。
- `FfaArena` レコードに17個目の項目 `freehitGuard` を追加し、`withFreehitGuard` を用意。
  コンストラクタ呼び出し17箇所すべて更新済み（`create()` は `false`）。

### 状態機械

`src/main/java/com/rumilance/practice/ffa/FfaFreeHitGuard.java`（Bukkit 非依存、時刻は引数）

- `Verdict evaluate(attacker, victim, now)` → `ALLOW` / `FREE_HIT` / `BLOCKED`
  - 互いが本Combat の相手どうし → `ALLOW`
  - どちらかが本Combat 中（相手ではない） → `BLOCKED`
  - どちらも本Combat 中でない → `FREE_HIT`
- `PROVISIONAL_MS = 10_000`（仮Combat）、`COMBAT_IDLE_MS = 30_000`（本Combat の無攻撃期限）
- `registerFreeHit` が仮Combat を記録し、相手からの仮Combat が生きていれば本Combat を成立
  させて `true` を返す。
- インスタンスは `FfaService#freeHitGuard()` が1つだけ持つ。
  解除は `leave(Player, boolean)`（退出・切断・マッチ移動すべての共通経路）と
  `handleLethal`（死亡）で `clear()`。

### ダメージの差し替え

`FfaListener#onFreeHitGuard` — `EntityDamageByEntityEvent`、`EventPriority.HIGH` + `ignoreCancelled`。

- `FfaListener#onDamage` は `HIGHEST` + `ignoreCancelled = true` なので、ここで cancel すると
  そちらが走らず `tagCombat` も呼ばれない＝「Combat 判定が入らない」の実体。
- cancel は ダメージ・ノックバック両方を消す。
- 演出: cancel 後に `Sound.ENTITY_PLAYER_HURT` を被害者座標で鳴らし直す。
- 本Combat 成立時: 両者に `Sound.ENTITY_EXPERIENCE_ORB_PICKUP` + 黄色の
  `⚠ Combat with <name> has started`。
- 攻撃者は近接・素手に加え `Projectile` の shooter も解決する。

### テスト

- `FfaArenaBotFlagTest` — 17引数に更新、`copies()` に `withFreehitGuard` を追加。
- `FfaArenaFreeHitFlagTest` — 同じことを `freehitGuard` 側について検証（新規）。
- `FfaFreeHitGuardTest` — 仕様そのものを固定（新規）。10秒の境界（9s で成立 / 10.001s で
  不成立）、組の排他、30秒の期限延長、退出・死亡での解放。
