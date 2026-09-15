# Paper 版 HeroBot(呼び出し名完全一致 + 関数エンジン)実現性スパイク

Quantum の datapack(`quantum:` / `g1gc:` / `xaniclelib:` …)を Paper 上で**そのまま**走らせる案の、
詰まっていた関門を実測で潰した記録と、動く最小プロトタイプ。

## 結論(2026-09-15 実測、Paper 1.21.11-132)

| 試行 | 結果 |
|---|---|
| `plugin.yml` のコマンドで `player`/`playerspawn`/`herobot` を登録 | ❌ datapack の関数パースは**プラグイン有効化より前**。`Unknown or incomplete command` |
| `MinecraftServer#getCommands().getDispatcher()` に直接登録(NMS) | ❌ Paper のコマンド同期で消える |
| Paper Brigadier API(`LifecycleEvents.COMMANDS`)で登録 | ✅ コンソール/RCON から herobot と**同一の引数文字列**で動く |
| datapack ローダーに再パースさせる(`reloadData()` / `reloadResources(...)`) | ❌ どちらも失敗。**理由をバイトコードで特定**: `ReloadableServerResources` はリロードのたびに `new Commands(selection)` を作り、その dispatcher で `new ServerFunctionLibrary(...)` を組む。プラグインコマンドは後に別 dispatcher 側へ入るため、**関数パーサは永久に `player` を知らない**(Fabric は `Commands` 生成時にコールバックするので動く) |
| 関数だけ自前で再パース + `ServerFunctionManager#replaceLibrary` | △ 実装は可能(`new ServerFunctionLibrary(permissionSet, liveDispatcher)` + `reload(SharedState, exec, barrier, exec)` は全て public)。ただし **Paper のプラグインリマッパーが NMS ディスクリプタを書き換えて壊す**(`NoSuchMethodError: MinecraftServer.getResourceManager()MultiPackResourceManager` / `VerifyError: CloseableResourceManager is not assignable to MultiPackResourceManager`)。paperweight 相当のビルドが無い限り NMS は踏まない方がよい |
| **関数エンジンを自作し、1 行ずつ `Bukkit.dispatchCommand` で流す** | ✅ **これが正解。** `player @s hotbar 3` が関数行から我々のハンドラに到達し、`execute as <entity> at @s run ...` の文脈も保たれる |

### 実測ログ(エンジンから `t3.mcfunction` を実行)

```
[BRIDGE] spikerun t3                             (src=Rcon)
[ENGINE]   ok   say t3-start
[ENGINE]   ok   execute as @e[type=armor_stand,limit=1] at @s run player @s hotbar 3
[BRIDGE] player @s hotbar 3                      (entity=true)      ← ★ 関数行から到達
[BRIDGE] herobot shieldStunning true perm world  (entity=true)      ← ★ 同上
[Server] [Armor Stand] from-the-stand                               ← 文脈(as/at)も正しい
```

## エンジンの仕組み(HeroSpike.java)

1. `player` / `playerspawn` / `herobot`(と検証用 `spikerun`)を Paper Brigadier API で**herobot と同名で**登録。
   引数は `greedyString` なので、herobot の複雑な構文をそのまま受け取って自前で解釈できる。
2. `.mcfunction` を自前で読む(`plugins/<plugin>/functions/<name>.mcfunction`)。`#` 行と空行は捨てる。
3. 1 行ずつ実行する。
   - `function ns:name` → エンジン内で再帰(名前空間は無視して名前で解決)
   - `execute <節> run function X` → **前置き文脈を積んで**再帰(`execute as @a at @s run ` + 以降の各行)。
     これで `as`/`at`/`positioned`/`if` などの文脈が、dispatch される各行にもそのまま乗る。
   - それ以外 → `Bukkit.dispatchCommand(Bukkit.getConsoleSender(), <前置き + 行>)`。
     バニラコマンドも、我々のブリッジコマンドも、**生きている dispatcher** が解決する。
4. `$` 行(マクロ)と `return`/`return run` は未実装(下記の残作業)。

## 実装上の落とし穴(実測で判明、重要)

- **プラグインリマッパー**: Paper は plugin jar を起動時に remap する。mojang 名で NMS を直接叩くと
  `NoSuchMethodError`/`VerifyError` になる(NARENA 本体が使っている `CraftServer#getServer()` 程度は無事)。
  → **この方式のエンジンは NMS を一切使わない**ので影響を受けない。
- **チャンク未ロードだとエンティティが消える**: プレイヤー不在のヘッドレス検証では `summon` した entity が
  セレクタから見えない(チャンクが落ちるため)。`forceload add -16 -16 16 16` で解決。
  ハーネス検証でも `forceload` を忘れると「エンティティがいない」と誤診する。

## 使い方

```bash
# ビルド(paper.jar は versions/1.21.11/paper-1.21.11.jar)
JDK=/tmp/toolchain/jdk-21.0.12.1+1
CP="<server>/versions/1.21.11/paper-1.21.11.jar:$(find <server>/libraries -name '*.jar' | tr '\n' ':')"
$JDK/bin/javac -nowarn -proc:none -d out -cp "$CP" HeroSpike.java
cp plugin.yml out/ && (cd out && $JDK/bin/jar cf <server>/plugins/HeroSpike-0.1.jar .)
mkdir -p <server>/plugins/HeroSpike/functions && cp functions/*.mcfunction <server>/plugins/HeroSpike/functions/
# 起動 → RCON:  spikerun t3
```

## 次にやること(本実装 = Paper 版 HeroBot)

1. **動詞の実装**(herobot 互換の意味論): `player <target> stop|sprint|move [forward|backward|left|right]|jump|hotbar <n>|use once|attack once`、
   `playerspawn <name> at <x> <y> <z> facing <yaw> <pitch> in <gamemode> on <dimension>`、
   `herobot <option> <value> perm <world>`。当プラグインの PacketBot 基盤(ServerPlayer を直接動かす実装)に接続する。
2. **エンジン拡張**: `$` マクロ(`data get storage` 由来の変数)、`return` / `return run` の戻り値、
   `execute if function`、`schedule function`、`#minecraft:tick` / `#minecraft:load` タグ、`function #tag`。
3. **世界の用意**: map 側は座標依存(アリーナ、キットチェスト、`positioned over world_surface`)。当側の石 100 床アリーナで
   走らせるための座標対応表と、足りない `kits/*`(チェスト)を `item replace` で埋める。
4. **突き合わせ**: 同じ `tools/fight_profile.py` で計測し、Quantum の関数が駆動する BOT の数値を参照実測と比較。
   以後「定数合わせ」は不要になり、**map 側の関数そのものが仕様**になる。
