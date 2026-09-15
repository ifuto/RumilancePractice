# Paper 版 HeroBot(動詞ブリッジ)実現性スパイク

Quantum の datapack(`quantum:` / `g1gc:` / `xaniclelib:` …)を Paper 上でそのまま走らせる案の、
**最初の関門だけ**を確かめるための最小プラグイン + datapack。

## 分かったこと(2026-09-15 実測、Paper 1.21.11-132)

| 試行 | 結果 |
|---|---|
| `plugin.yml`(Bukkit コマンド)で `player`/`playerspawn`/`herobot` を登録 | ❌ 起動時の関数パースが `Unknown or incomplete command`。datapack の関数パースは**プラグイン有効化より前**に走る |
| `MinecraftServer#getCommands().getDispatcher()` へ直接登録(NMS) | ❌ 登録直後のコンソールからも `Unknown`(Paper のコマンド同期で消える) |
| `LifecycleEvents.COMMANDS`(Paper Brigadier API)で登録 | ✅ コンソール/RCON から herobot と**同一の引数文字列**で動く(`player @s stop` 等がそのままハンドラへ) |
| `Server#reloadData()` | ❌ レシピ等は再読込するが**関数は再パースしない** |
| `/reload` | ❌ 関数は再パースされるが、その瞬間プラグインは無効化されており `player` は Unknown のまま |

→ **datapack ローダーに任せる限り `player …` の行はパースできない**。移行するなら、`.mcfunction` を自前で読み、
生きている dispatcher で 1 行ずつパースし、`player`/`playerspawn`/`herobot`/`function` だけ自前実装へ横取りする
関数エンジンをプラグイン側に持つ必要がある(バニラコマンドはそのまま dispatcher に流せる)。

## 使い方(スパイクの再現)

```bash
# ビルド(Paper の API + mojang マップ jar に対して)
JDK=/tmp/toolchain/jdk-21.0.12.1+1
CP="<base_ci.jar>:/tmp/paper-run/versions/1.21.11/paper-1.21.11.jar:/tmp/paper-run/cache/mojang_1.21.11.jar:$(find /tmp/paper-run/libraries -name '*.jar' | tr '\n' ':')"
$JDK/bin/javac -nowarn -proc:none -d out -cp "$CP" HeroSpike.java
cp plugin.yml out/ && (cd out && $JDK/bin/jar cf <server>/plugins/HeroSpike-0.1.jar .)

# datapack を world/datapacks/spike に置いて起動し、RCON から:
#   player @s stop
#   function spike:test      ← こちらは失敗する(上表のとおり)
```
