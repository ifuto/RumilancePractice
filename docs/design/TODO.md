# RumilancePractice — やるべきことリスト

更新: 2026-10-05 / ブランチ `arena/01a106b3-rumilancepractice` / HEAD `3b053c1` (v1.92.51)

> 定期自動要約で文脈が消えても追えるようにするためのメモ。
> `/tmp/todo.md` にも同じものを置いているが、サンドボックスの /tmp はスナップショットに
> 残らないので**本体はこのファイル**。

## 進行中（新バッチ 2026-10-05）

- [x] **10. FFA の `lff` 設定 + `/lff`** — v1.92.53
- [x] **11. Duel アリーナの貼り付けキュー** — v1.92.54
- [x] **12. `/tps` を自前実装** — v1.92.55–56
- [x] **13. Duel Chat の書式 + 受信/送信の切り替え** — v1.92.58
- [x] **14. `/setting` の整理** — 重複機能の統一 — v1.92.58
- [x] **15. `/block` `/ignore`** — Queue でブロック相手と当たらない — v1.92.59
- [x] **16. `/tps` を一般開放**（Paper の `/tps` は塞ぐ） — v1.92.60
- [x] **17. ブロックした相手からの tell は受信しない** — v1.92.60
- [ ] **18. `/admin` 管理者 GUI** — `/practiceadmin` の上位互換。メイン画面に7ページ:
      「Arena / FFA」「Player Data」「Punishment」「Cheat / Alt / Reports」
      「Match Management」「Statics」「Server Settings」
- [ ] **19. Player 紐付けデータ画面の完全管理** — リセットだけでなく値・配置の編集まで。
      適度な間隔を開けて使いやすく。
- [ ] **20. パーティの強制解散**
- [ ] **21. チャット通報（一般ユーザー）** — チャットにホバーで "click to report"、
      クリックで通報。`/admin` の Reports で確認。**投稿日時**と**前後のプレイヤー
      メッセージ**は報告時の保存ではなく**動的取得**。

## 完了（8項目リスト + 追加2件）

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
- [x] FreeHit 対策の ON/OFF は **FFA（アリーナ）ごと** で確定（2026-10-05 ユーザー確認）。
      キット単位にはしない。`arenas.<id>.settings.freehit-guard`（既定 OFF）、
      `FfaSettingsGui` のアリーナ detail ページ `gridSlot(19)`。
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

# 10〜15. 新バッチ（2026-10-05）

## 10. FFA の `lff` — 実装メモ（v1.92.53）

- 設定: `arenas.<id>.settings.lff` / 既定 OFF / `FfaSettingsGui` の `gridSlot(20)`。
  `FfaArena` は18項目（`lffEnabled` = 17→18）。
- アイテム: `FfaLookingForFight`（`com.rumilance.practice.ffa`）。`SLOT = 8`（ホットバー最後）。
  - 待機: `GUNPOWDER` / 灰色 / `⚔️ Looking for fight (/lff) ⚔️`
  - 募集中: `GLOWSTONE_DUST` / 金色 / `⚔️ Now looking for fight ... (/lff)`
  - 識別は PDC `ffa_lff`（`ItemKeys.ffaLff()`）。
- 強制配置: `FfaService#applyKit` の `kitService.apply(...)` の直後。`lffEnabled(arenaId)` なら
  `lookingForFight.refresh(player)`。リスポーンでも呼ばれるので頭上表示の復元も兼ねる。
- 頭上表示: プレイヤーを vehicle にした `TextDisplay`（`Transformation` で y=+2.35、金色）。
  ネームタグの書き換えやチーム prefix はランクアイコンと競合するので使っていない。
- クリック: `FfaListener#onLffToggle`（`PlayerInteractEvent`）。`/lff` は `LffCommand`。
- 解除: `FfaService#leave(...)` で `clear(player)`。

## 11. Duel アリーナの貼り付けキュー — 実装メモ（v1.92.54）

- 対象は `DisposableArenaService#pasteCopy`（マッチごとに schematic を貼る）。
- `ArenaPasteQueue<T>`（純粋・Bukkit 非依存）を新設。`MAX_CONCURRENT_PASTES = 2`。
- `pasteCopy` は受付窓口になり、`tryStart` で空きがあれば即実行、無ければキューへ。
  完了時に `onFinished()` が次の1件を返し、それを**メインスレッド**に戻して実行する
  （配置探索とチャンクチケットが Bukkit 状態を触るため、FAWE の完了スレッドでは駄目）。
- テスト: `ArenaPasteQueueTest`（2件まで同時、3件目は待つ、FIFO、上限厳守、0は1に丸める）。

## 12. `/tps` — 実装メモ（v1.92.55–56）

- `plugin.yml` に `tps:` コマンドと `rumilance.tps`（`default: op`）を宣言。
  未宣言ノードだと OP 全員に通ってしまうので明示している（既存コメントと同じ理由）。
- `TpsTracker`（`com.rumilance.practice.util`、純粋）:
  - 20秒ごとに1サンプル × 60件 = 20分窓。MSPT→TPS は `TickHealth` と同じ式。
  - `%` は `averageTps / 20 × 100`（20 TPS = 100%）。
  - 閾値 `17.0` 以下を `Dip` として記録、24時間で忘れる。継続中は `ongoing()`。
- `TpsCommand`: 権限なしは Bukkit 側で拒否（Built-in へのフォールバックなし）。
  プレイヤーは10秒クールダウン、コンソールは対象外。
- **履歴はメモリのみ。再起動で24時間の履歴は消える。**
- テスト: `TpsTrackerTest`。

---

# 16〜21. 新バッチ（2026-10-06）

ユーザー原文（要約）:

> `/admin` で管理者用GUIページに行けるように。`/practiceadmin` の上位互換。
> まず開いたら「Arena / FFA」「Player Data」「Punishment」「Cheat / Alt / Reports」
> 「Match Management」「Statics」「Server Settings」のメイン画面がある。
> あと Player に紐付けられたデータを見れる全画面だけど、リセットするだけじゃなくて
> 値や配置をいじったりだとか、本当に全部管理できるようにして。適度な間隔を開けて
> 使いやすくする。Party の強制解散だとかね。
> また、一般ユーザーは他の人のチャットクリックでそのチャットを通報できる
> （チャットにカーソル合わせたら "click to report" みたいなの出してもいいかも）。
> `/admin` の Reports で確認できる。メッセージ投稿日時、その前後のプレイヤー
> メッセージ（報告時セーブではなく動的取得）等々が見れる。
> あと `/tps` は一般プレイヤー使用可。使えないのは Paper の `/tps`。
> block したユーザーからの tell は受信しない。

## 16. `/tps` 一般開放 — 実装メモ（v1.92.60）

- `plugin.yml` の `rumilance.tps` を `default: op` → **`default: true`**。
  `TpsCommand` 側に明示的な権限チェックは無く、plugin.yml の宣言だけに依存している。
- **`TpsPaperGuard`**（新規）: `PlayerCommandPreprocessEvent` を `LOWEST` で受け、
  `/minecraft:tps` `/paper:tps` `/bukkit:tps` `/spigot:tps` を cancel して
  「Use /tps instead.」を出す。`rumilance.admin` は対象外（両方使える）。
  - 素の `/tps` は plugin.yml の登録が優先されるので、こちらを塞ぐ必要はない。
  - 名前空間付きだけが Paper 側へ抜ける抜け道になる。

## 17. ブロック相手の tell は受信しない — 実装メモ（v1.92.60）

- `TellCommand#deliver` の**一番最初**に判定を追加。
  `BlockListService#isBlocked(to, from)` が true なら送信者に `tell.blocked-you` を出して
  終了（受信者には届かない）。コンソール（`from == null`）は常に除外。
- 既存の `ChatPolicy.receivesMessage`（`receiveStrangerMessages`）より**優先**。
  ブロックは「設定」ではなく「明示的な拒否」なので上に置く。

---

# 13〜15. 残り（仕様は上にそのまま記載）

## 13. Duel Chat — 実装メモ（v1.92.58）

- 書式: `<青>[Duel]</青> <頭> <白>名前</白> : 本文`。1v1 は `[Duel]`、パーティ戦は `[Match]`。
- 頭は `HeadFontService.of(uuid)`（MiniMessage の `<head:uuid>`）。パック不要、
  クライアントがスキンを解決する。アクションバーと同じ仕組み。
- 判定は `MatchChatListener` が1箇所で行う（`AsyncChatEvent`、`EventPriority.NORMAL`）。
  - 試合中 かつ 送信先=Duel Chat → 参加者+観戦者のみに配信し、上の書式で描画。
  - それ以外 → 全体チャット。受信側の「全体チャットを受信」が OFF なら落とす
    （チャットホワイトライストに入っていれば届く）。
- 設定は `PlayerSettings` に2項目追加（DB 保存、再起動で残る）:
  - `receiveDuelChat` — **既定 ON**
  - `duelChatGlobal` — **既定 OFF**（= Duel Chat に送る）
- `/matchchat` は同じ `duelChatGlobal` を書き換えるコマンド版ショートカット。
  旧実装の static なメモリ Map は廃止。
- マイグレーション 36 で `receive_duel_chat` / `duel_chat_global` を追加。

## 14. `/setting` の整理 — 実装メモ（v1.92.58）

- **重複を統一**: `gui.hide-chat`（旧 `hideOtherChat`）は
  `receiveGlobalChat`（Chat Settings の「全体チャット」）と同じ意味だったので廃止。
  `PracticeSideListener` の隠匿フィルタも削除し、`MatchChatListener` に一本化。
  `hide_other_chat` 列は DB 互換のため残す（新規コードからは参照しない）。
- Chat Settings は 2列目に Duel Chat 受信（`PLAYER_HEAD`）を追加し、行末に
  送信先トグル（`ENDER_PEARL`、Duel Chat ⇔ 全体チャットの2者択一）を配置。
- `/setting` の (1,7) は「チャット設定」へのショートカットに変更。(4,4) にあった
  同じ入り口は削除して、操作はパネル行に集約した。

## 15. `/block` `/ignore` — 実装メモ（v1.92.59）

- `plugin.yml`: `block:` / エイリアス `[ignore, unblock]`。権限 `rumilance.user`。
  サブコマンドなしで **トグル**（未登録なら追加、済みなら解除）。
- `BlockListService`（`com.rumilance.practice.social`）:
  - メモリ Map が本体。読み取りは DB を叩かない（マッチング tick が毎回走るため）。
  - 書き込みは非同期で投げっぱなし。join で `load`、quit で `unload`。
  - リポジトリ未接続ならメモリのみで動作する。
- `BlockRepository` + マイグレーション 37 で `player_blocks` テーブル
  （`blocker_uuid`, `blocked_uuid`, `created_ts`、複合主キー）。
- **片方向で十分**: `isBlockedEitherWay` を `QueueService#pollMatches` の
  `pairBlocked` に渡す。既存の alt 検知制限とは `QueueCoordinator#pairBlocked` で合成。
  「2人しか待っていない」時の lonelyPair 救済は alt 側だけで、ブロックは常に有効。

## 10. FFA の `lff` 設定 + `/lff`

- FFA 設定に `lff` を追加。**既定 OFF**。
- ON のアリーナでは、**キットの9番目（ホットバー最後のスロット = レイアウト index 8）に火薬を
  強制配置**する。
- 火薬の名前: 灰色で `⚔️ Looking for fight (/lff) ⚔️`
- クリックすると**グロウストーンダスト**に変化し、名前にオレンジ色で
  `⚔️ Now looking for fight ... (/lff)`
- この状態になると**ネームタグの上に** `⚠ Looking For Fight ⚠` をちょっと濃い黄色で表示。
- もう一度グロウストーンダストをクリックすると火薬に戻り、LFF 表示が消える。
- **`/lff` コマンドでも同じことができる**。

## 11. Duel アリーナの貼り付けキュー

- Duel でアリーナのコピーが**ほぼ同時に3件以上**発生しそうなら、**3件目以降は
  「Arena Paste Queue」に入れて順番に貼り付ける**。

## 12. `/tps` を自前実装

- **LuckPerms で一般ユーザーは `/tps` を使用不可**にする。
- 代わりにこのプラグインで `/tps` を実装。
- **直近20分の TPS を ×100 して % 表示**（TPS を % で出す）。
- **コマンド連打は禁止**（クールダウン）。
- **24時間以内に TPS が 17 以下になったことがあれば、何時から何時の間に発生していたか**を
  表示する。

## 13. Duel Chat の書式 + 受信/送信の切り替え

- 書式: `<青>[Duel] <普通>%PlayerHead%<白>%PlayerName% : %Message%`
- Setting で試合中のチャットを Duel Chat にするか全体チャットにするか変更できる:
  - 全体チャットを受信するか T/F（**既定 ON**）
  - Duel Chat を受信するか T/F（**既定 ON**）
  - 送信先を Duel Chat / 全体チャットでトグル（**既定 Duel Chat**）

## 14. `/setting` の整理

- わかりやすく、使いやすくする。**重複する機能があったら片方に統一**する。

## 15. `/block` `/ignore`

- `/block <ign>` と `/ignore <ign>` を追加。
- **Queue でブロックした人とマッチしない**ようにする。

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
