# GUI 全機能インベントリ(リビルド用メモ)

GUI全面リビルドにあたり、**機能を一切失わないため**の完全な行動目録。
各GUIの「アクション文字列→挙動」「特殊機構」「開き方」を記録する。
リビルドは **見た目だけ全消し・ロジック保持** が原則:

- **維持(インフラ)**: `GuiListener`(クリック/D&D配信)、`GuiSession`/`GuiSessionRegistry`、
  `AbstractGui`(render/refresh/handleClick枠組み)、`ItemBuilder`、`GuiDecorator`(PDCアクション付与)、
  `GuiType`、各GUIのコンストラクタ・`open()`・状態管理・クリックハンドラ本体。
- **全消し→再構築**: `MenuScaffold`のクローム様式、`UiTheme`配色、各`render()`のレイアウト。
  新しいデザインは `gui.json` 準拠(テーマ色ガラスの全周枠・タイトルアイコン(0,4)・
  内側空白・Close=(5,4)バリア・Back=(5,4)矢印・ページ矢印)。

新しい枠描画は `GuiFrame`(`gui/GuiFrame.java`)に集約。

---

## 1. ハブ・基本メニュー

### GameMenuGui(ゲームメニュー=ロビーのメインメニュー)
- 開く: ロビーでメニューアイテム使用/`/menu`相当。6行。
- アクション: `battle`(対戦メニューへ) `teams`(チームハブへ) `ekit`(カスタムキット選択へ)
  `profile`(プロフィール) `settings`(設定) `spectate`(観戦) `titles`(称号) `close`
- `profile`/`decorate`系の装飾表示(ヘッド+統計表示)あり。

### BattleMenuGui(対戦メニュー)
- 開く: GameMenuの`battle`。6行。
- アクション: `ranked` `unranked` `ffa` `player-duel`(デュエル依頼) `history`(戦歴)
  `bot`(ITEM 41: ボット練習部屋一覧へ) `leave-queue`(キュー離脱・入っていれば表示) `close`
- キュー中は参加中表示/離脱ボタンに切替。

### PracticeBotSelectGui(ボットモード選択・ITEM 44)
- BattleMenuの`bot`から。5モード(クリスタル/ネザポット/メイス/カート/ソード)を
  2行目に間隔を空けて配置。各タイル=説明+運営紐づけキット+紐づけマップ+空き状況。
- `mode:<TYPE>` クリックで紐づけマップの部屋へ(未設定なら最初の空き部屋)
  `practiceService.join`。部屋なし/満杯は鍵表示+エラー音。`close` `back`。
- Bot戦は10分固定(待機ホットバーに時間アイテムなし: 難易度/開始/シールドのみ)。

### BotDifficultyGui(ボット難易度・ITEM 44・6行・PURPLE)
- WAITホットバーのネザースターから。1行目=配布マップ準拠プリセット梯子
  (木〜ネザライト剣6種+カスタム星)、2〜4行目=詳細パラメータタイル。
- パラメータ: 体力/攻撃力/攻撃間隔/移動速度/回復/コンボ間隔/シールドスタン(トグル)/
  シールド軽減/トーテム目標。左+右−シフト=プリセット値へ。
- クリック即保存(`practiceService.saveDifficulty`)+リフレッシュ。`close` `back`。

### SettingsGui(個人設定)
- アクション(全部トグル): `toggle:scoreboard` `toggle:sounds` `toggle:spectators`
  `toggle:match_report` `toggle:auto_requeue` `toggle:deny_duels` `toggle:hide_chat`
  `toggle:team_armor` `toggle:team_glow` + `name_color`(VIP名称色) `whitelist`(フレンド招待制) `close`
- 各トグルはPlayerSettingsService経由で即時保存。

### ProfileGui
- 自分の戦績・ランキング表示(モード別)、装飾ヘッド。`close`のみ。

### LocaleSelectGui(言語選択・3行)
- 初回参加/未設定時に自動表示。国旗アイテムで7言語、クリックで即保存。`close`。

### ConfirmGui(3行・汎用確認)
- `yes`/`no`の2ボタン。コンストラクタでコールバック注入(破棄/解散等の確認に使用)。

---

## 2. パーティー・チーム

### TeamsBrowserGui(公開チーム一覧)
- `create_public`(公開チーム作成) `create_private`(非公開) + 公開チーム一覧(1ページ複数件)
  `page:next`/`page:prev` `close`。
- チーム項目クリック→参加申請/招待待ち表示。

### TeamHubGui(自分のチームのハブ)
- `team_settings`(チーム設定) `choose_kit`(チームキット選択) `open_browser`(チーム一覧)
  `leave`(脱退・ConfirmGui経由) `page:next/prev`(メンバーページ) `close`
- メンバーをヘッド表示(クリックでキック等の権限操作・ホストのみ)。

### TeamSettingsGui(ITEM 40b でスリム化)
- ルール系のみ: `invite`(招待) `toggle_ff`(フレンドリーファイア)
  `toggle_public`(公開/非公開) `select_map`(マップ選択)
  `open_team_manage`(チーム管理メニューへ) `close`
- 運営系(構成・自動振り分け・陣営クリア・解散)は TeamManageGui へ分離。

### TeamManageGui(チーム管理・ITEM 40b 新設)
- `open_team_config`(チーム構成) `autosplit`(自動振り分け) `clearsides`(陣営クリア)
  `disband`(解散・ConfirmGui確認あり) `back`(設定へ戻る) `close`

### TeamConfigGui(チーム構成・ITEM 40b でボタン追加方式に再構築)
- `team:add`(チーム追加・ライムガラス) `team:remove`(チーム削除・赤ガラス)
  `reset_all` `back_to_hub`(管理へ戻る) `close`
- 従来の `count:cycle` 本(クリック循環)は廃止。チーム列(羊毛)の右隣に
  「チームを追加」ボタン、ランク上限超過列はバリア+ランク表示
  (4-5枠=VIP / 6-7枠=VIP+)。未作成チーム列は淡色フィラー。
- チーム色羊毛で色設定、各チームのHP/サイズ/効果/キットを列ごとに調整。

### PartyInviteGui(招待一覧)
- 届いた招待をリスト表示、クリックで承諾/拒否。`page:next/prev` `back` `close`。

### PartyMapSelectGui / DuelMapSelectGui
- マップ一覧+`map:random`。`back`/`close`。選択後は依頼/チーム設定に反映。

### PlayersGui(プレイヤー一覧)
- オンライン一覧ヘッド、ページ送り、クリックでデュエル依頼/プロフィール。
  `page:next/prev` `close`。

---

## 3. キット関連(プレイヤー側)

### KitSelectGui(キット選択・ランク/アンク対応)
- 有効キットをアイコン表示、クリックで選択してキューへ。`back`/`close`。
- プレビュー表示、ロック表示(ランク不足)。

### QueueKitGui(看板キューのキット選択)
- 看板キット固定フロー用の選択画面。`close`。

### EditKitGui(キットエディタ・5行・最重要)
- **FreeInventoryではなく独自**: セクション切替 `Armor` `Gear` `Potions` `Consumables`
- `save`(保存) `back` `close` `gui.delete`(削除)
- `BottomInventoryClickHandler`: 下部インベントリ操作でアイテム出し入れ(装備系は装着スロットへ)。
- レイアウト編集モード(キット配置の並べ替え)を内包:`slot:N`アクション、
  `KitLayoutEditor`連携。プリセット編集モードでは自由配置+削除。

### EkitSelectGui(カスタムキット選択)
- 自分のカスタムキット一覧+`original`(オリジナルキット)へ。`back`/`close`。

### EkitChoiceGui(3行・新規/コピー選択)
- `create` `copy` `back` `close`。

### EkitCopyGui(コピー元選択)
- 他プレイヤー/プリセットからコピー元を選ぶ。`back`/`close`。

### EkitAdminGui(オリジナルキット管理・6行)
- `BottomInventoryClickHandler`、`back`/`close`/`empty`。

### KitPreviewGui(キットプレビュー)
- キット内容を全表示(防具/アイテム)。`back`/`close`。

### StatsKitGui(キット別統計)
- キットごとの使用率/勝率をページ表示。`page:next/prev` `close`。

### SignKitSelectGui(看板キット選択)
- 看板用キットの選択。`back`/`close`。

### TeamKitSelectGui(チームキット選択)
- チーム共通キットの選択。`close`。

### EnchantGui / PotionGui(エンチャント/ポーション編集)
- キットエディタから遷移。レベル/効果を選択してキットに反映。`back`/`continue`。

### PracticeLayoutGui(3行) / PracticeBotGui(3行) / PracticeMaceGui(3行)
- プラクティス関連の小メニュー。`PracticeMaceGui`: `breach`/`density`/`wind`トグル。
- `PracticeBotGui`: `toggle_shield`。

---

## 4. 試合観戦・履歴

### SpectateListGui(観戦)
- モードフィルタ `RANKED`/`UNRANKED`/`FFA`、試合リスト、ページ送り、クリックで観戦開始。
  `page:next/prev` `close`。

### FfaListGui(FFAアリーナ一覧)
- 開催中FFAをリスト表示、ページ送り、参加。`page:next/prev` `close`。

### MatchHistoryGui(戦歴)
- モードフィルタ `RANKED`/`UNRANKED`/`TEAM`/`FFA`、ページ、クリックでレポートへ。
  `back`/`close`。

### MatchReportGui(試合レポート)
- 1試合の詳細(キル/ダメ/アイテム)。`close`。

### MatchInventoryGui(試合中のインベントリ参照)
- `swap`(相手側表示へ切替) `close`。死亡者インベントリ閲覧。

### ReportGui / ReportListGui(通報・通報一覧)
- 通報作成/一覧。`close`。

---

## 5. コスメティック

### NameColorGui(名称色・VIP+以上)
- `mode:cycle`(色切替) `target:toggle`(適用先) `back_settings` `close`
- 3日に1回制限あり(残りはlore表示)。

### TitleGui(称号)
- 称号一覧、`title:none`(外す)、クリックで装備。`close`。

### ShieldPatternGui(盾柄編集・6行)
- `layers`(パターン材一覧) `preview` `undo` `reset` `apply` `back`/`close`
- `GuiCloseHandler`(閉じ時プレビュー盾を処理)。

### SmithingTrimGui(鍛冶型・6行・516行)
- 鍛冶型一覧+素材(`amethyst`/`copper`/`diamond`/`emerald`/`gold`/`iron`/`lapis`/
  `netherite`/`quartz`/`redstone`/`resin`)、`locked`(ランク不足)、`apply`/`remove`
  `back`/`close`。`GuiCloseHandler`。

### KillEffectGui(キルエフェクト)
- 一覧+ロック表示、ページ送り、クリックで装備。`page:next/prev` `locked` `close`。

### ArrowEffectGui(矢エフェクト・4行)
- 同上(小画面)。`close`。

---

## 6. 管理系(OP/管理者)

### AdminMenuGui(管理メニューの入口)
- `kits`(キット管理) `presets`(プリセット管理) `ekitadmin`(オリジナルキット管理)
  `playerdata`(プレイヤーデータ) `packpolicy`(パック方針) `signkit`(看板キット配布)
  `noop` `close`。

### KitAdminGui(キット管理メイン・456行)
- キット一覧+詳細ビュー切替(`view`セッションキー)。
- トグル群: `toggle:enabled` `toggle:ranked` `toggle:preset` `toggle:adventure`
  `toggle:totem` `toggle:pearl` `toggle:autofood` `toggle:autoregen`
  `toggle:swordshieldbreak` `toggle:blockplace` `toggle:blockbreak` `toggle:breakplayerplaced`
- サブGUI遷移: `open:arenas` `open:block-rules` `open:item-rules` `open:preset`
  `open:start-effects`
- `start-effects`、キット移動(左右シフト)、`back`。
- 詳細ビュー: ヘッド/本でメタ表示、羊毛でチーム色、アイテム数カウント。

### KitBlockRulesGui(ブロックルール・6行)
- `toggle:blockplace` `toggle:blockbreak` `toggle:breakplayerplaced`
- **破壊可能ブロック登録(D&D)**: `canbreak:drop`(ホッパードロップゾーン)、
  `canbreak:list`(一覧+手持ちクリック)
- `InventoryDropHandler`(ドロップ受付)+`BottomInventoryClickHandler`(下部クリック登録)。
  左=追加/右=削除/Shift+ドロップ=全消去。アイテムは消費されない。
- `back`/`noop`。

### KitItemRulesGui(アイテムルール・6行)
- アイテムごとの禁止/許可ルール。`admin-gui.timeout`、`back`/`noop`。

### KitArenaSelectGui(キット別アリーナ)
- キットに紐付けるアリーナを選択。`back`/`noop`。

### KitStartEffectsGui(開始エフェクト)
- 9種のポーション効果トグル: `fire_resistance` `invisibility` `jump_boost`
  `night_vision` `regeneration` `slow_falling` `speed` `strength` `water_breathing` + `back`。

### PresetAdminGui(プリセット管理・5行)
- `FreeInventoryEdit`(上部が自由編集領域)+`GuiCloseHandler`
- `save` `back`/`close` `next`/`prev`(ページ) `gui.preset-admin-free`
- セクション `Armor`/`Gear`/`Potions`、レイアウト編集は`slot:N`。
- D&Dでアイテム配置→キット内容として保存。

### ArenaAdminGui(アリーナ管理)
- アリーナ一覧+ページ、クリックで詳細/編集。`page:next/prev` `close`。

### AdminPlayerDataGui(プレイヤーデータ管理)
- `act:clear_namecolor` `act:reset_ekits` `act:reset_ekits_all` `act:reset_locale`
  `back_admin` `close`。

### BanListGui(BAN一覧)
- リスト+ページ。`page:next/prev` `close`。

### CustomShieldAdminGui(カスタム盾管理)
- 盾の一覧/配布。`close`。

### OriginalKitGui / OriginalKitEditGui(オリジナルキット)
- `OriginalKitEditGui`: `BottomInventoryClickHandler`、セクション `Blocks`/`Offhand`/`Potions`、
  `save`/`back`/`gui.continue`/`gui.delete`。
- 編集は**部屋のシュルカー経由**(OriginalKitRoomListener)と連動。

---

## 7. 特殊機構(リビルドで壊さないこと)

1. **FreeInventoryEdit**(PresetAdminGui): 上部自由編集。`isFreeEditActive`/`isControlSlot`。
2. **BottomInventoryClickHandler**(EditKitGui, EkitAdminGui, KitBlockRulesGui, OriginalKitEditGui):
   下部インベントリのクリックをGUI側で処理。
3. **InventoryDropHandler**(KitBlockRulesGui): D&Dドロップゾーン(ホッパー)。
4. **GuiCloseHandler**(PresetAdminGui, ShieldPatternGui, SmithingTrimGui): 閉じた時の後処理。
5. **ページ送り**: 大半が`page:next`/`page:prev`+セッションキー(`page`)。
6. **セッションキー**: `view`(KitAdminGui) `layout`(EditKitGui) 等は`GuiSession.put/get`。
7. **GuiListenerの先頭処理**: トップクリックは装飾(`decorate`)/アクション無しでも
   レイアウト編集の`slot:N`に流れる。この挙動は維持。
8. **ConfirmGui**: コールバック注入型。閉じ方次第で分岐。

---

## 8. 新デザイン規約(適用方針)

- `GuiFrame.chest(inv, theme)`: テーマ色のガラス板で全周枠(0/5行全列+両端列)。
- `GuiFrame.title(inv, icon, name)`: (0,4)にテーマアイコン+名前。
- `GuiFrame.close(inv)`=(5,4)バリア / `GuiFrame.back(inv)`=(5,4)矢印。
- `GuiFrame.page(inv, prevSlot, nextSlot)`: ページ矢印。
- テーマ例: 白=チーム/一覧、空色=対戦、ライム=観戦/パーティー、黄=キット編集、
  緑=その他ハブ、紫=管理。
- 内側(1〜4行×1〜7列)は**空白**を基本とし、アイコンは中央寄せ・間隔広め。
- ロケールキーは既存を流用し、新規キーは7言語に同時追加。

## 8a. リビルド進捗(状態メモ)

- **完了(フェーズ1)**: `GuiFrame`基盤作成・全55GUIへテーマ色フレーム+タイトルアイコン(0,4)
  適用、ナビをバリア「閉じる」/矢印「戻る」に統一、ページ矢印を最下行両端に統一。
  GameMenu/BattleMenuはgui.json準拠のピラミッド配置に再構成、Settingsは4/4/3行に整理。
  EditKitGui(黄・下開放)/PresetAdminGui(紫・下部バーのみ)は専用フレーム。
- **残作業(フェーズ2)**: ヒーロー画面の個別レイアウト深化(チーム系ハブ・キット選択・
  デュエル依頼・コスメティック系)、`MenuScaffold`/`UiTheme`の旧クローム呼び出しが
  無くなり次第それらの削除。
- **テーマ割当**: 白=ハブ/チーム/プレイヤー一覧、空色=対戦/履歴、ライム=観戦/パーティー
  /矢、黄=キット系、オレンジ=FFA/盾/鍛冶、紫=管理/コスメティック、赤=通報/実践。

