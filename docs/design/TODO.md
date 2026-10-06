# RumilancePractice — やるべきことリスト

更新: 2026-10-06 / ブランチ `arena/01a106b3-rumilancepractice` / v1.92.98

> 定期自動要約で文脈が消えても追えるようにするためのメモ。
> `/tmp/todo.md` にも同じものを置いているが、サンドボックスの /tmp はスナップショットに
> 残らないので**本体はこのファイル**。

## 2026-10-06 バッチ

- [x] **ProtocolLib → PacketEvents 全面移行** — v1.92.89〜**v1.92.95**（CI 37456129041 success）。
      対象8ファイル + `build.gradle.kts` + `plugin.yml` softdepend。
      `src/main/java` から `com.comphenix.protocol` は **0 件**。詳細は下の専用セクション。
- [x] **Ready エメラルドのホバーでアクションバーを変えない** — v1.92.96 → **v1.92.98 でやり直し**。
      v1.92.96 は「Ready を見ている時だけ出さない」だったが、それだと**見つめた瞬間に
      アクションバーが消える**＝これも「変化」なので不十分（ユーザーから同じ指摘が2度来た）。
      v1.92.98 で**ホバーとアクションバーの結合そのものを撤去**。
      `updateViewer` から gaze 分岐を削除し、`readyTotal` / `leaveLine` と
      `countdown.gaze-ready` + `countdown.gaze-leave`（7言語）を削除。
      アクションバーは「相手が Ready を押した」通知だけになり、どこを見ているかは一切関係しない。
- [x] **キルされたプレイヤーに飛行権限を与えて強制的に飛行状態へ** — v1.92.98。
      新規 `match/MatchFlightService`。致死判定時に `setAllowFlight(true)` + `setFlying(true)`。
      剥がす箇所は3系統 + 保険2つ（下記「飛行権限のリセット経路」）。
- [x] **デッドコード一掃** — v1.92.97。
      `LethalPresentationService#animationAvailable` / `#stagedPairs`、
      `PlayerListCommand#serverMaxPlayers` を削除。`PlayerListCommand#sortedOnline` は
      public のテストシームをやめて実装から使う形にした。

### 飛行権限のリセット経路（v1.92.98）

`MatchFlightService` は「自分が付与した相手」だけを `Set<UUID> granted` に覚え、その分だけ剥がす。
Creative / Spectator は最初から飛べるので**付与も剥奪もしない**（観戦やリプレイが与えた分を
誤って奪わないため）。

| タイミング | 箇所 |
|---|---|
| 試合開始（毎回） | `MatchService#beginCountdown` → `revokeAll(participants)` |
| 試合終了 | `MatchService#endMatch` → `revokeAll(participants)` |
| `/hub`・ロビー復帰 | `LobbyService#ensureHubReturn` が `setAllowFlight(false)` / `setFlying(false)`（既存） |
| 再ログイン | `MatchFlightService#onJoin` で記録ごと破棄 |
| ログアウト | `MatchFlightService#onQuit` で記録ごと破棄 |

### 「A 宛のパケットをキャンセル」は再戦・ロビーで復活するか

**復活する。** `LethalPresentationService` は抑制を「viewer → 相手の entity id」で持ち、
以下の経路で必ず解除される（= `killer.showEntity` + `suppressed` からの削除）。

| タイミング | 箇所 |
|---|---|
| 試合終了（再戦ウィンドウ含む） | `MatchService#endMatch` → `restoreAll(session.participants())` |
| 相手側が再ログイン | `#onJoin`：その UUID を hidden/suppressed から外す |
| 自分か相手がログアウト | `#onQuit`：両方向とも解除（`Player` オブジェクトが生きているうちに `showEntity`） |

`restoreAll` は「参加者が加害者側に回っている場合」も全キーを走査して双向に解除する。
つまり**再戦でもロビー戻りでも必ず元通り**。残る穴はサーバーの強制停止（= メモリ上のマップごと消えるので
実質無害）と、試合を抜けずにサーバーだけリロードした場合（プラグイン無効化で全員ロビーに戻る）。

### 残っている宿題

- [ ] **`docs/shield-web.md`**（タスク23のドキュメント。実装は v1.92.78 で完了済み）
- [ ] タスク18 `/admin` 管理者 GUI、19 Player 紐付けデータ画面、21 チャット通報（上のリスト）

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
- [x] **20. パーティの強制解散** — v1.92.64–65
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
- 既存の `ChatPolicy.receivesMessage`（`receiveStrangerMessages`）より**優先**。
  ブロックは「設定」ではなく「明示的な拒否」なので上に置く。
- lang: `tell.blocked-you` を7ロケール追加。

## 21. チャット通報 — 実装メモ（v1.92.61、Reports 画面は未着手）

- **`ChatLogService`**（`com.rumilance.practice.chat`、純粋）:
  - 直近N件のリングバッファ。`record()` が単調増加の **id** を返す。
  - `find(id)` / `context(id, before, after)`。**前後の文はここから動的に引く**。
  - 容量は `chat-log.capacity`（既定 2000）。前後件数は
    `chat-log.context-before` / `context-after`（既定 5）。
- **`ChatReportRepository`** + マイグレーション 38 で `chat_reports` テーブル
  （`chat_line_id`, `reporter_uuid`, `reported_uuid`, `reported_name`, `reported_ts`, `status`、
  複合主キー = 同一行の二重通報を防ぐ）。
  - **本文は保存しない。id だけ。** これが「報告時セーブではなく動的取得」の実体。
    バッファを流れた行は `find()` が empty を返し、UI 側で「古すぎる」と出す。
- **`ChatReportService`**: `recent()` / `open()` が `Report` を返す。
  `Report` は `Optional<ChatLine> line` と `List<ChatLine> context` を持つ。
  `timestamp()` は本文の投稿時刻（行が消えていれば通報時刻）。
- **ホバーとクリック**: `MatchChatListener#reportable` が、完成したチャット行に
  `hoverEvent(showText(report.hint))` と `clickEvent(runCommand("/reportchat <id>"))` を付ける。
  - **発言者本人には付けない**。`rumilance.user` 権限がない視点にも付けない。
  - Duel Chat と全体チャットの**両方**に効く（Duel は自前レンダラ、
    全体は元のレンダラをラップする）。
- **`/reportchat <id>`**: バッファから引けなければ `report.expired`、
  自分の発言は `report.self`、二重は `report.duplicate`、成功で `report.filed`。
- **未着手**: `/admin` → 「Cheat / Alt / Reports」の Reports 画面（項目18と一緒に作る）。

## 18. `/admin` ハブ — 実装メモ（v1.92.62–63）

- **`/admin` は既存コマンドと衝突していた**（`/admin reset point [player]` / `/admin orkit …`）。
  → **共存で解決**: `args.length == 0` なら新ハブを開き、サブコマンドは従来どおり。
  （ユーザー確認は取れていないので、別名にしたい場合は `/admintool` へ移すだけで済む。）
- **`AdminHubGui`**（新規、`gui/menus/`）+ `GuiType.ADMIN_HUB`。6行、PURPLE テーマ。
  - 配置は1列ずつ間を空けた7タイル:
    - 2行目: Arena/FFA(1) / Player Data(3) / Punishment(5) / Cheat・Alt・Reports(7)
    - 4行目: Match Management(2) / Statistics(4) / Server Settings(6)
  - 遷移先は `Consumer<Player>` の setter 注入。**ハブ自身は遷移先を知らない**ので、
    セクション画面が未実装でも落ちない（noop）。
- **現在の委譲先**（すべて既存画面）:
  | セクション | 委譲先 |
  |---|---|
  | Arena / FFA | `ArenaSourceGui` |
  | Player Data | `AdminPlayersGui` |
  | Punishment | `BanListGui` |
  | Cheat / Alt / Reports | `ReportListGui`（**チャット通報はまだ未接続**） |
  | Match Management | `AdminMatchesGui` |
  | Statistics | `AdminStatsGui` |
  | Server Settings | `AdminToggleGui` |
- **ハマりどころ**: `UiTheme.line()` / `UiTheme.hint()` は **`Component` ではなく `String`**。
  v1.92.62 はこれでコンパイル落ちした（v1.92.63 で修正）。

## 20. パーティ強制解散 — 実装メモ（v1.92.64–65）

- `TeamService#forceDisband(UUID memberId)` を追加。**所有者チェックを飛ばす**以外は
  `disband(Player)` と同じで、必ず private の `disbandTeam(team, true)` を通るので
  Queue / Duel の状態も消える（幽霊パーティと組まされる事故を防ぐ）。
  パーティに入っていなければ `Result.NOT_IN_TEAM` を返し、呼び側が正直に報告する。
- **`teamOf(UUID)` は既に存在した**（`Optional<Team>` を返す）。同じ名前を足して
  コンパイルが落ちた（v1.92.65 で重複を削除して既存側に合わせた）。
  **既存 API を足す前に必ず grep する。**
- `AdminPlayerDataGui` の `GuiSlots.slot(3, 4)` に「Force disband party」タイル。
  パーティ名とメンバー数を表示し、未所属なら灰色染料で `decorate`。
- `/admin` ハブを FeatureBootstrap の**フィールド**にした（`private AdminHubGui adminHubGui`）。
  生成が後ろ（2256行）でも、前方（1360行）の戻り先から参照できるようにするため。
- 管理者サブ画面（`AdminPlayersGui` / `AdminMatchesGui` / `AdminToggleGui` /
  `KbDefaultGui` / `AltFlagsGui`）と **`/practiceadmin menu`** の戻り先を
  旧 `AdminMenuGui` から新ハブへ変更。

## 21. 通報の Reports 画面 — 実装メモ（v1.92.66）

- **`ChatReportsGui`**（新規、`GuiType.CHAT_REPORTS`、6行 RED）:
  - `ChatReportService#recent(45)` を `MenuScaffold.gridSlot()` に並べる。
  - タイルの lore: 通報対象 / ステータス / **投稿日時**（`MM-dd HH:mm:ss`、
    `ZoneId.systemDefault()`）/ **前後のプレイヤーメッセージ**（`» ` が通報行本体）。
  - **前後の文は毎回 `ChatLogService` から取り直す**（保存しない）。
    バッファを流れていれば `(original message expired)` と通報日時を出す。
    窓が9行を超えたら古い側を切る。
  - 左クリック = HANDLED、右クリック = DISMISS。どちらも DB に書く。
- **`CheatReportsGui`**（新規、`GuiType.CHEAT_REPORTS`）:
  「Cheat / Alt / Reports」のセクション画面。3タイルを1列おきに配置 —
  Chat Reports(2,1) / Alt Flags(2,4) / Match Reports(2,7)。Back は(5,4)。
- **`/admin` ハブの「Cheat / Alt / Reports」は `reportListGui` 直結（仮実装）だったのを、
  このセクション画面を開くように変更。**

### 見つけた不具合（v1.92.66 で修正）

- **`AdminHubGui` を `guiListener.register()` し忘れていた。**
  → ハブのタイルをクリックしても **何も起きない**状態で v1.92.62–65 が出ていた。
  AbstractGui は `guiListener.register()` しないとクリックを受け取れない。
  **`GuiType` を足したら必ず `guiListener.register()` する。**

## Arena / FFA セクション画面 — 実装メモ（v1.92.67）

- **`ArenaFfaGui`**（新規、`GuiType.ARENA_FFA_ADMIN`、6行 GREEN）:
  Arenas(2,1) / FFA Settings(2,4) / Kits(2,7)、Back(5,4)。1列おきに配置。
  遷移先は `ArenaSourceGui` / `FfaSettingsGui` / `KitAdminGui`。
- これで **`/admin` ハブの7セクション全部が実画面につながった**（委譲スタブは解消）。

### ハブ各セクションの行き先（v1.92.67 時点）

| セクション | 行き先 |
|---|---|
| Arena / FFA | `ArenaFfaGui` → Arenas / FFA Settings / Kits |
| Player Data | `AdminPlayersGui` |
| Punishment | `BanListGui` |
| Cheat / Alt / Reports | `CheatReportsGui` → Chat Reports / Alt Flags / Match Reports |
| Match Management | `AdminMatchesGui` |
| Statistics | `AdminStatsGui` |
| Server Settings | `AdminToggleGui` |

## 19. Player データ画面の編集 — 実装メモ（v1.92.68〜）

「リセットするだけじゃなくて値や配置をいじったり」への対応。既存で**編集できていた**ものと、
**今回編集できるようにした**もの:

| 項目 | 従来 | 現在 |
|---|---|---|
| Rank | 左右で上下（shift で逆） | 同じ（既存で編集可） |
| Settings | sounds / scoreboard / locale | 同じ（既存で編集可） |
| Ranked stats (W/L) | `openWlEditor` で数値編集 | 同じ（既存で編集可） |
| **Name color** | **クリアのみ** | **単色8種 / グラデ4種を設定できる** |
| Party | （なし） | **強制解散**（v1.92.64） |

### Name color の編集

- `AdminPlayerDataGui` にパレット定数 `NAME_SOLIDS` / `NAME_GRADIENTS` を追加。
- 左クリック = 次の単色、右クリック = 次のグラデ、shift = クリア。
- `nameColorService.save(target, next.withChangedAt(now))` し、オンラインなら
  `applyToPlayer()` で即反映。
- **3日クールダウンは管理者操作では無視する**（`canChange()` は通さない）。
  プレイヤー自身の変更だけが制限対象。

## FFA 子Kit（inner kit）— 実装メモ（v1.92.69）

- `FfaArena` レコードに19個目の項目 **`innerKitId`**（String、null/空 = 指定なし）を追加。
  `withInnerKit()` を用意。呼び出し22箇所（本19 + テスト3）を paren-balanced スクリプトで更新。
- 保存: `arenas.<id>.settings.inner-kit`（未指定なら `null` を書いてキーを消す）。
- 読み込み: `blankToNull(entry.getString("settings.inner-kit"))`。
  `blankToNull` を `FfaService` の private static ヘルパーとして追加（空文字と null を
  同じ「未指定」にそろえるため）。
- **適用の優先順位**（`FfaService#applyKit`）:
  1. Crystal FFA の KIT1..K9（既存の経済の仕組みなので最優先）
  2. **アリーナの子Kit**（今回追加）— `InnerKitService.layoutKey(kit.name(), arenaInner)`
  3. 個人の K1..K4 バリアント
  4. キット本体のレイアウト
- GUI: `FfaSettingsGui` の `gridSlot(21)`（LFF の隣）に **Child kit** タイル。
  クリックで `none → 子Kit1 → 子Kit2 → … → none` を巡回。
  `InnerKitService` を `setInnerKitService()` で注入（FeatureBootstrap の `innerKits`）。
- `FfaService#setInnerKit(arenaId, innerId)` / `innerKitOf(arenaId)`。
  未知の子Kit id も保存する（リネーム・再構築で消えても選択が飛ばないように）。
  スポーン時に解決できなければ上の3・4へフォールバックする。
- テスト: `FfaArena*FlagTest` 3本の `copies()` に `withInnerKit` を追加し、
  「どの with* も他のフラグを壊さない」網羅に子Kit を含めた。

## 19 の現状（v1.92.69 時点）

編集できるもの: Rank / Settings / Ranked stats (W/L) / **Name color** / Chat whitelist（追加・クリア両方）
実行できるもの: Kick / Force-end match / **Party 強制解散** / 処分解除 / FULL WIPE
**v1.92.70/71 で追加**: original kit の**スロット個別操作** —
左クリックで保存済みスロットを巡回選択（選択中は `[#3]` のように括弧付きで表示）、
右クリックで**そのスロットだけ**削除、Shift クリックで全削除（従来どおり）。
**v1.92.72 で追加**: **キット配置（layout）の編集** — 対象プレイヤーの保存済みレイアウトを
エディタで開いて並べ替え、その人に保存し直せる。下記メモ参照。
**リセットのみ（仕様上これでよい）**: 対象プレイヤーのレイアウト全削除 / 全員分リセット。
まとめて消す安全弁なので、編集対象にはしない。

## LFF アイテムのスロット衝突（v1.92.73）

- 症状: LFF トグル（9番目 = index 8）が、編集後のキットで同じ位置に置いたアイテムを
  **無条件で上書きして消していた**。`FfaLookingForFight` の `setLooking` / `refresh` が
  どちらも `inventory.setItem(SLOT, item(...))` を直接呼んでいたため。
- 修正: `placeToggle()` を挟む。9番目に LFF アイテム**以外**のアイテムがあれば、
  先に空き枠へ退避してから置く。
  - 退避先は **ホットバー 0〜7 を優先**、埋まっていればストレージ 9〜35。
    9番目（index 8）自体は退避先にしない。
  - 空き枠が1つもなければ**破棄**（LFF が枠を取る）。
  - 9番目にあるのが LFF アイテム本身なら退避しない（毎回の refresh で動かないように）。
- `firstFreeSlot(boolean[])` は Bukkit を触らない純粋関数に分離。
  `ItemStack` はテストで生成できないため「埋まっているか」の配列を受け取る。
  単体テスト `FfaLffSlotRelocationTest` で優先順位を固定。

## リソースパックのバグ — 判明した原因（v1.92.74）

**根本原因: `assets/minecraft/font/default.json` と `uniform.json` を同梱していた。**

- `minecraft:default` / `minecraft:uniform` / `minecraft:alt` は**予約済み id**。
  これをリソースパックが置くと、バニラフォントに**追加**されるのではなく
  **丸ごと置換**される。
- 同梱していた2ファイルは U+E001〜E004 のビットマップ4件しか定義していなかったため、
  バニラの全グリフ（英数字・記号・スペース）の定義が消え、
  **サーバー内の全テキストが表示されなくなる**状態だった。
  → config.yml の「the resource pack merges the glyph providers into minecraft:default」
    というコメントは誤りだった。マージは「パック間」であって「バニラとは」ではない。
- 修正:
  - `resourcepack/assets/minecraft/font/{default,uniform}.json` を**削除**
    （`assets/minecraft/` 自体がなくなった＝バニラを一切上書きしない）。
  - グリフは自前名前空間 `rumilance:icons`（`assets/rumilance/font/icons.json`）のみに登録。
    カスタム名前空間は**加算**なのでバニラを壊さない。
  - `config.yml` の `icons.font` を `"default"` → `"rumilance:icons"` に変更。
  - `IconFontService.font()` の既定値も `"rumilance:icons"` に。
    バッジは**必ず font 属性を付けて送る**（グリフは icons にしか無いため）。
  - `ConfigService` のマイグレーションを逆方向に
    （`"default"` → `"rumilance:icons"`。「予約 id を上書きしない」ため）。
- 検証: `tools/release/build-pack.sh` が通過。差分は
  `assets/minecraft/font/default.json` / `uniform.json` の削除のみ（9 → 7 エントリ）。
  `dist/RumilanceResourcePack.zip` と `.sha1` を再生成してコミット済み。
  **再配布が必要**（`tools/release/attach-pack.sh <tag>`）。
- テクスチャは正常（admin 27x8 / vip 16x8 / vip_plus 20x8 / pro 16x8、すべて1グリフ8px）。
  pack.png 512x512、pack.mcmeta は pack_format=75 / min=[34,0] / max=[100,0] で妥当。
- `/rankicon test` の Probe C の説明も修正（デフォルトフォントでは**何も出ない**のが正解。
  出るなら別パックが minecraft:default を上書きしている＝全テキスト消失のサイン）。

## port 1010 に入れない件

- 1010 は Minecraft のポートではなく **Shield Web**（プラグイン内蔵 HTTP サーバー）のポート。
- **既定値が `shield-web.enabled: false`**。だから何も待ち受けていない。
  config.yml の `shield-web.enabled` を `true` にして再起動すれば繋がる。
- `bind` は既定 `"0.0.0.0"` なので、有効化すれば LAN の `192.168.0.203:1010` も通る。
  管理画面 URL は起動ログに出る: `http://<bind>:1010/admin?token=<64hex>`。
- 繋がらないときの分岐: ログに `[ShieldWeb] 盾管理Webを開始しました` が出ていない
  → enabled が false。出ているのに繋がらない → ファイアウォール / 別セグメント。

## リソースパック配布 — **これが一番の根本原因**（v1.92.74 調査で判明）

**既定の配布URLが 404 で、パックが1度もクライアントに届いていない。**

- `ResourcePackService.DEFAULT_URL` =
  `https://github.com/ifuto/RumilancePractice/releases/download/v1.76.61/RumilanceResourcePack.zip`
- ところが **v1.76.61 は全リリース中で唯一アセットが空のタグ**（`gh api .../releases`
  で確認: v1.92.17 / v1.92.3 / v1.92.2 / v1.92.1 / v1.76.60 / v1.76.59 / v1.76.58 /
  v1.76.57 / v1.76.52 には zip があるが、**v1.76.61 だけ無い**）。
  → `curl -sIL` の結果は **404**。
- 結果: `fetchSha1(url)` が null を返し、`liveSha1` も `jsonSha1` も無ければ
  「no pack is sent until a hash is known」で **パックが一切送信されない**。
  つまり「リソースパックがバグってる」は、中身以前に**配布が生きていない**のが主因。
- 直し方（`tools/release/attach-pack.sh` のコメントに書いてある想定手順）:
  既定URLが指すタグへ `--clobber` でアップロードすれば、**設定変更なしで**起動中の
  サーバーも次回再起動から直る。

      tools/release/build-pack.sh
      tools/release/attach-pack.sh v1.76.61

- **この環境からは実行できない**: `uploads.github.com` がサンドボックスから遮断されている
  （`curl` が exit 35 / http_code 000。`api.github.com` は 200）。
  `gh release upload` は EOF で失敗する。**ユーザーがローカルで実行する必要あり。**

## 残り（次にやること）

- [ ] **【最優先・ユーザー作業】`tools/release/attach-pack.sh v1.76.61` を実行**して
      配布を復活させる。これをやらないと v1.92.74 のフォント修正はクライアントに届かない。
- [ ] 配布後、`/rankicon test` で Probe A（icons フォント + U+E001 = バッジ）と
      Probe B（icons フォント + "ABC" = 普通の文字）の両方が見えるか目視確認。
- [ ] まだ検証していない項目（ユーザーのチェックリスト残り）:
      フォント描画そのものの目視確認、`/rankicon test` の Probe A/B の結果確認、
      パック適用確認（`ResourcePackService#hasPack`）のバグ有無。


- [x] 19 続き. **キット配置（layout）の編集**（v1.92.72）
- [x] 19 続き. **Original kit のスロット個別操作**（v1.92.70/71）


- [ ] 19 続き. **キット配置（layout）の編集** — 「配置をいじったり」の本体。
      `KitLayoutRepository` / `KitLayoutCache` を触る画面。対象プレイヤーのレイアウトを
      管理者が直接並べ替えられるようにする。
- [ ] 19 続き. **Original kit スロットの個別操作** — 現在は「全削除」のみ。
      スロット単位の削除・閲覧・プラン（`Plan.DEFAULT/MEMBER/VIP/VIP_PLUS`）の変更。
- [ ] 19 続き. **Chat whitelist の編集** — 現在は画面に出ているが操作内容を要確認。



- [ ] 19. **Player データ画面の完全管理** — `AdminPlayerDataGui` は現状リセット中心。
      値の編集（rank / 統計 / 設定 / キット配置）と配置の変更を追加する。
- [x] 「Arena / FFA」専用ページ — v1.92.67


- [ ] 19. **Player データ画面の完全管理** — `AdminPlayerDataGui` は現状リセット中心。
      値の編集・配置の変更を追加する。
- [ ] 20. **パーティの強制解散** — `TeamService` を `AdminPlayerDataGui` / Players 画面から呼ぶ。
- [x] 21 残り. **チャット通報の Reports 画面** — v1.92.66
- [ ] 19. **Player データ画面の完全管理** — `AdminPlayerDataGui` は現状リセット中心。
      値の編集・配置の変更を追加する。
- [x] `/practiceadmin menu` を新ハブに向ける（統合） — v1.92.64
- [ ] 「Arena / FFA」専用ページ（現状は `ArenaSourceGui` に丸投げ）。

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

## キット配置の管理者編集 — 実装メモ（v1.92.72）

- `EditKitGui` に **`admin-edit` モード**を追加。既存の `view` モードと同じく
  `session.setTargetPlayer(uuid)` を使うが、`isViewOnly()` は **false** のまま
  （＝ドラッグも保存もできる）。
- 所有者の解決を `layoutOwner(Player, GuiSession)` に一本化。
  `view` と `admin-edit` のときだけ `session.targetPlayer()`、それ以外は自分。
  - 読み込み: `loadLayout(layoutOwner, …)`
  - 保存: `persistLayout(…)` の最終段で `KitLayoutSnapshot.create(owner, …)` /
    `layoutCache.put(owner, …)`。**クリックした管理者の UUID には一切書き込まない**。
- `openKitEditorFor(admin, targetId, targetName, kitName)` — キットが存在しない場合は
  false を返し（空のエディタを開かない）。
- 戻り先: `setOnAdminExit(BiConsumer<Player, UUID>)`。
  FeatureBootstrap では `editKitGui.setOnAdminExit(adminPlayerDataGui::openFor)`。
  `AdminPlayerDataGui` の open は `openFor(Player, UUID)` なので Consumer ではなく
  BiConsumer にしてある（対象 UUID を戻す必要があるため）。
- `AdminPlayerDataGui` の **(2,1)** に **Layout editor** タイル。
  左クリックで対象キットを巡回選択、右クリックでエディタを開く。
- **開けるのは素のキット行だけ**（キーに `#` を含まないもの）。
  `kit#preset#x` / crystal `#v` / K1..K4 `#k` は、素のキットを開くと
  **別の並びを表示して上書きしてしまう**ので、編集対象にせずリセット系に任せる。
  黙って違う行を書き換えないための意図的な制限。
- 「ekit スロット数」という概念はコードに存在しなかった（前回メモの誤り）。

## 配布元は Tailscale Funnel（Shield Web）— ユーザー確認済み（2026-10-06）

ユーザー自己申告により、配布元は GitHub Release ではなく **Tailscale Funnel → Shield Web
:1010 の `/pack.zip`**。`resource-pack.json` の url は `DEFAULT_URL` より優先される
（`configuredUrl()`）ため、**v1.76.61 の 404 はこの環境の原因ではない**（別件の実バグではある）。

### 3症状は全部これで説明できる

| 症状 | 原因 |
|---|---|
| `localhost:1010` / `192.168.0.203:1010` に入れない | `shield-web.enabled` が**既定 false** → 待ち受け自体が無い |
| バッジが □ | Shield Web が止まっている → Funnel の proxy 先が無く URL が取れない → `fetchSha1` が null → **パック未送信** |
| （LFF の衝突） | 別件。v1.92.73 で修正済み |

### 直し方（docs/shield-web.md の「有効化（初回のみ）」）

1. `config.yml` の `shield-web.enabled: true`（`bind` は既定 `0.0.0.0` のままで良い）
2. **サーバーを完全再起動**（`/rumireload` は HTTP サーバーを再開させない — ドキュメント明記）
3. `tailscale funnel --bg 1010` → `tailscale funnel status` でホスト名確認
4. `plugins/n-arena/resource-pack.json` の `url` を
   `https://<マシン名>.<テールネット>.ts.net/pack.zip` に書き換えて `/rumireload`
5. `/urank web` で管理URL+トークン確認

### 上記で直らない場合の第2候補: `pack-src` が古い

`ShieldWebService#unpackBaseIfNeeded()` は **`pack-src/pack.mcmeta` が既にあれば即 return**
（作業コピー＝盾PNGを保持するため）。つまり **jar を更新してもパックの中身は更新されない**。
古い `pack-src` に `assets/rumilance/font/icons.json` が無い/古い場合、
パックは配られているのにグリフだけ欠けて □ になる。
→ `plugins/n-arena/pack-src/` を確認し、グリフ定義が無ければ作り直す。

### v1.92.74 のフォント変更は無害（ただし主因ではない）

- `icons.json`（自前名前空間）は**当初から同梱**されているので、
  `icons.font: "rumilance:icons"` への切り替えは旧 zip でも動く。
- `default.json` / `uniform.json` の削除は潜在的な危険の除去だが、
  ユーザー環境で「全フォントが壊れた」事実は無い（今回の □ の原因ではない）。

---

# 2026-10-06 一括対応（v1.92.83 – v1.92.88）

## 26. 観戦中に /hub すると FFA 退出メッセージが出る — v1.92.83

- **真の原因は lang のインデント崩れ**。`ffa-bot:` 見出しが `ffa:` ブロックの**途中に挿入**され、
  FFA のメッセージ **12個**が全部 `ffa-bot.*` に吸い込まれていた（全7ロケール同一）。
  - 影響: `ffa.left` `ffa.joined` `ffa.kills-bar` `ffa.kill-streak` `ffa.killed-by`
    `ffa.lff-on/off` `ffa.lff-disabled` `ffa.teleport-failed` `ffa.leave-hint`
    `ffa.repairing-title` `ffa.combat-bar` — **存在しないキー**だった。
  - `ffa:` は `maintenance/unavailable/cannot-join/kit-missing/arena-full/no-arenas` の6個だけ。
  - 全ロケールで `ffa`=18キー / `ffa-bot`=4キー に再構成。
- `LobbyCommand` の `SPECTATING` 分岐は `ffa.left` → `lobby.teleported` に変更。
  観戦者は FFA に居ないので「FFAから退出しました」は事実誤り。

## 27. ロビーでエリトラ・革靴が配布されない — v1.92.84

`LobbyWearService` の2つの欠陥。

1. **`isInLobby` が `Cuboid.contains()`（Y軸まで厳密）で判定していた。**
   Region の設定高さと実際に立つ高さが少しでもズレると（段上・ジャンプ中・滑空中）即 `strip()` 側に落ち、
   革靴/エリトラが消え続ける。Region の本来の目的（遠くの AFK 部屋・練習場を除外）は**水平方向**で十分。
   → `containsHorizontal()` に変更。
2. **Region がロビースポーンを水平方向に含まない＝設定が古い**場合はワールド判定にフォールバック + 警告を1回だけ出す。
3. `tick()` の `catch (Throwable t) {}` が**空**で、何も記録されなかった。
   → プレイヤーごと30秒レート制限で WARNING 出力。
4. ついでに `LobbyService.reload()` が `region` をクリアしていなかったのを修正。

## 28. `/player` — v1.92.85

- 新規 `command/PlayerListCommand.java`。**`CommandSender` ベース**なので Console から実行可。
- 表示: 名前・状態・ping・ワールド。`RealPlayers.online()` で Bot を除外。
- `plugin.yml` に `player:` コマンド + `rumilance.player.list`（既定 op。Console は常に可）。
- lang に `player-list.header/line/empty` を7ロケール追加。

## 29. End inventory を開く名前にホバー説明 — v1.92.86

- "End inventory" という文字列は `killfeed.view-inventory-hover` にしか無く、
  キルフィードの名前ホバーは**既に実装済み**だった。
- もう一つの「名前をクリックして終了インベントリを開く」箇所＝
  `MatchInventoryGui` の切替矢印（`→ <name>`）。ロアが「選手を切替」だけで
  何が開くか書いてなかったので、`gui.inv-view-hint`（クリックで <name> の終了インベントリを表示）を追加（7ロケール）。

## 30. 死亡時の挙動変更 — v1.92.87 / v1.92.88

- 新規 `combat/LethalPresentationService.java`。A が B を倒したら **A にだけ**：
  1. ProtocolLib で `ENTITY_STATUS`(byte 3) の**偽の死亡パケット**を B の entity id で送信 → A の画面で B が倒れる。
  2. 20 tick 後に `killer.hideEntity(plugin, victim)` → A に B のパケットが一切届かなくなる。
- **B 側は完全に不変**（装備クリア・無敵・以降の挙動すべて据え置き）。
- ProtocolLib が無い場合は 2. だけ動く（縮退）。
- 隠し状態の**解除漏れを防止**: `endMatch` で `restoreAll(participants)`、
  `PlayerJoinEvent` / `PlayerQuitEvent` でも解除（放置するとロビーで相手が見えなくなる）。

---

# PacketEvents への移行（2026-10-06, v1.92.89〜v1.92.95）— 完了

指示: 「ProtocolLib に依存している実装があったら、すべて PacketEvents ベースに。」

## 依存（build.gradle.kts）

```
maven { name = "codemc-releases"; url = uri("https://repo.codemc.io/repository/maven-releases/") }
compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
// ProtocolLib は v1.92.95 で完全に削除済み。ソース・依存・plugin.yml から一扫。
```

## 状況

| ファイル | 状態 |
|---|---|
| `combat/LethalPresentationService` | ✅ v1.92.89 |
| `match/TeamGlowLosService` | ✅ v1.92.90 |
| `combat/DuelHitSoundPackets` | ✅ v1.92.90 |
| `sight/FfaChunkMaskService` | ✅ v1.92.90（キャッシュは座標保持→`World#refreshChunk`で再送） |
| `spectator/SpectatorViewIsolationPackets` | ✅ v1.92.90 |
| `practice/afk/AfkRoomIsolationPackets` | ✅ v1.92.90 |
| `replay/ReplayNpcService` | ✅ v1.92.95 |
| `security/sign/SignProbeService` | ✅ v1.92.95 |

**v1.92.95 / CI 37456129041 = success**。`src/main/java` から `com.comphenix.protocol` は 0 件。
`plugin.yml` の softdepend も `ProtocolLib` → `PacketEvents`。全判定は小文字の
`"packetevents"` プラグイン id を見る（大文字だと Bukkit は見つけない）。

新規ヘルパ: `packets/PacketEntityIds` — パケット種別→エンティティ id→Bukkit Player。
（`event.getPlayer()` は Object、`getPacketType()` は `PacketTypeCommon` を返す点に注意。）

## 確定済み API 対応表（ProtocolLib → PacketEvents）

| ProtocolLib | PacketEvents |
|---|---|
| `PacketType.Play.Server.MAP_CHUNK` | `CHUNK_DATA` |
| `NAMED_ENTITY_SPAWN` | `SPAWN_PLAYER` |
| `REL_ENTITY_MOVE` / `_LOOK` | `ENTITY_RELATIVE_MOVE` / `ENTITY_RELATIVE_MOVE_AND_ROTATION` |
| `ENTITY_LOOK` | `ENTITY_ROTATION` |
| `ANIMATION` | `ENTITY_ANIMATION` |
| `ENTITY_HEAD_ROTATION` | `ENTITY_HEAD_LOOK` |
| `TILE_ENTITY_DATA` | `BLOCK_ENTITY_DATA` |
| `LIGHT_UPDATE` | `UPDATE_LIGHT` |
| `NAMED_SOUND_EFFECT` | `NAMED_SOUND_EFFECT` |
| `WrappedGameProfile` / `PlayerInfoData` | PE の `UserProfile` / `WrapperPlayServerPlayerInfoUpdate` |
| `BuiltinSound`（存在しない） | **`Sounds`**（複数形。`protocol.sound.Sounds`） |
| `packet.getIntegers().read(0/1)`（chunk） | `WrapperPlayServerChunkData#getColumn().getX()/getZ()` |
| `WrappedBlockData.createData(BlockData)` | `SpigotConversionUtil.fromBukkitBlockData(BlockData)` |
| Bukkit `Location` | `SpigotConversionUtil.fromBukkitLocation(Location)` |
| `NbtFactory.ofCompound` / `ofList` | `new NBTCompound()` / `new NBTList<>(NBTType.STRING, List.of(...))` |
| `nbt.put("k", value)` | **`compound.setTag(key, tag)`**（`setString/setInt/setByte` は無い） |
| `ENTITY_DESTROY` | `WrapperPlayServerDestroyEntities(int...)` |
| `ENTITY_TELEPORT` | `WrapperPlayServerEntityTeleport(int, Location, boolean)` |
| `ENTITY_HEAD_ROTATION` | `WrapperPlayServerEntityHeadLook(int, float)` |
| `PLAYER_INFO`(add/remove) | `WrapperPlayServerPlayerInfoUpdate(Action.ADD_PLAYER, List<PlayerInfo>)` / `WrapperPlayServerPlayerInfoRemove(UUID...)` |

**パッケージの罠**: `protocol.nbt.*`、`protocol.sound.Sounds`、
`protocol.world.blockentity.BlockEntityTypes` は `com.github.retrooper.packetevents.*` だが、
**`SpigotConversionUtil` だけ `io.github.retrooper.packetevents.util`**（spigot モジュール）。
javadocs は API モジュールしか載っていないので、spigot 側は `gh api
repos/retrooper/packetevents/contents/<path>` でソースを引いて確認する。

## はまった点（次ターン用メモ）

- `PacketWrapper#getChunkX()/getChunkZ()` は**引数あり**。chunk 座標は `getColumn()` 経由で取る。
- `event.getPacketType()` の戻りは `PacketTypeCommon`。`PacketType.Play.Server` 変数に入れない。
- `Set.of(...)` は `Set.<PacketTypeCommon>of(...)` と書かないと不変性で落ちる。
- `WrapperPlayServerAttachEntity` に `getEntityId()` は無い → ATTACH_ENTITY は除外。
- AFK のブロック系は検証済みの `CHUNK_DATA` + `BLOCK_CHANGE` のみに絞った
  （`MULTI_BLOCK_CHANGE` の `getSectionPosition()` は API に無い）。
- `NBTCompound` に `setString`/`setInt`/`setByte` は無い。`setTag(key, new NBTString(...))` 等を使う。
- import 置換を `s.index("import com.comphenix...")` 〜 `s.index("import java.util.List;")` で
  切ると Bukkit 側の import まで消える。**置換後は必ずコンパイルエラー行を確認する**。
- ローカルに JDK が無く Maven にも到達不可（api.github.com のみ）なので、
  **javadoc を fetch して署名を確認してから書く**こと。推測で書くと CI 1往復2分を消費する。

## hideEntity と F3+B（ユーザー指摘）

`hideEntity` はモデルを隠すだけで F3+B の箱が残り得る、という指摘は正当。
死亡演出は3段構え:

1. `WrapperPlayServerEntityStatus(id, 3)` — 死亡アニメ＋音（A だけに送信）
2. `WrapperPlayServerDestroyEntities(id)` — A のクライアントから実体を削除。F3+B の箱も消える。
3. `SPAWN_PLAYER` を A 宛てに cancel — 再出現を遮断（＝「A のパケットを送るのをやめる」）

B 側は不変。解除は `endMatch` / join / quit で `showEntity` + 抑制解除。

## 「Thinking 中に Let me run を連呼する」について（ユーザー指摘）

internal reasoning にツール呼び出しの前置きを書き込んでいたのが漏れていた。以降やらない。
