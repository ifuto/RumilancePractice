# プラグイン実機検証の環境を 0 から作り直す(サンドボックス用)

サンドボックスの `/tmp` はターン/セッションをまたぐと消えることがある。ここに書いてある
手順だけ知っていれば、**CI 由来の本物の jar** でヘッドレス戦闘を再開できる(ローカルに
Java も Gradle も要らない)。

## 0. 前提

- 取得に使うのは `gh`(GitHub API)のみ。`*.blob.core.windows.net`(Actions の artifact)と
  Maven/PaperMC の配布元はサンドボックスから遮断されているので、**git ブランチ配送だけが道**。
- 配送ブランチは CI が自動更新する:
  - `java-env-delivery`   … `delivery/jdk21.tar.gz.part-*`(JDK 21 Temurin)+ `paper-1.21.11-*.jar`
  - `paper-server-delivery` … `paperserver/paper-server.tar.gz.part-*`(paper-run 一式:
    server.jar + libraries + versions + server.properties(rcon 25576 / pass rumilance))
  - `plugin-delivery`     … `plugin/RumilancePractice-<version>.jar`(= shadowJar そのもの)+ sha256s.txt

## 1. JDK 21

```bash
mkdir -p /tmp/dl && cd /tmp/dl
for f in part-00 part-01 part-02; do
  gh api "repos/ifuto/RumilancePractice/contents/delivery/jdk21.tar.gz.$f?ref=java-env-delivery" \
    -H "Accept: application/vnd.github.raw" > "jdk.$f"
done
cat jdk.part-* > jdk21.tar.gz && mkdir -p /tmp/toolchain && tar xzf jdk21.tar.gz -C /tmp/toolchain
# → /tmp/toolchain/jdk-21.0.12.1+1/bin/java
```

## 2. Paper 一式(起動済みの libraries 同梱)

```bash
cd /tmp/dl
for f in part-00 part-01 part-02; do
  gh api "repos/ifuto/RumilancePractice/contents/paperserver/paper-server.tar.gz.$f?ref=paper-server-delivery" \
    -H "Accept: application/vnd.github.raw" > "ps.$f"
done
cat ps.part-* > paper-server.tar.gz && tar xzf paper-server.tar.gz -C /tmp
# → /tmp/paper-run/(server.jar, libraries/, versions/1.21.11/, plugins/, rcon 25576)
```

## 3. 検証したいプラグイン jar

```bash
cd /tmp/dl
V=1.76.22                     # gradle.properties の version
gh api "repos/ifuto/RumilancePractice/contents/plugin/RumilancePractice-$V.jar?ref=plugin-delivery" \
  -H "Accept: application/vnd.github.raw" > "/tmp/paper-run/plugins/RumilancePractice-$V.jar"
gh api "repos/ifuto/RumilancePractice/contents/plugin/sha256s.txt?ref=plugin-delivery" \
  -H "Accept: application/vnd.github.raw"          # sha256 が一致することを確認
rm -f /tmp/paper-run/plugins/RumilancePractice-<古い版>.jar
```

## 4. 起動

```bash
cd /tmp/paper-run && /tmp/toolchain/jdk-21.0.12.1+1/bin/java -Xmx2G \
  -Drumilance.harness=true -jar server.jar nogui
```

`logs/latest.log` に `NARENA v<version>` が出れば OK。yggdrasil の SSL エラーは環境要因
(オフラインモードなので無害)。

## 5. 通常戦を回す(RCON 25576 / pass rumilance)

```bash
python3 /tmp/rcon.py "narena-harness ground 40 100"        # 石の床(深さ100)
python3 /tmp/rcon.py "narena-harness room r1 CRYSTAL 40 0 64 0"
python3 /tmp/rcon.py "narena-harness dummy HarnessBot 4 64 4"
python3 /tmp/rcon.py "narena-harness fight CRYSTAL 150 INTERMEDIATE 1"
python3 /tmp/rcon.py "narena-harness status"
```

- `room` の y は **床の上の空気の y**(石100床なら 64)。ずれるとダミーが埋まる/浮く。
- ダミーは 4.0 HP を割ったときだけ全回復する(毎tick全回復だと被弾が起きず、BOT が密着
  したままになる)。`dummy HarnessBot topped up` がログに出る。
- ダミーの実座標は動かない(`data get entity HarnessBot Pos`)ので、距離は必ずこの座標で測る。

## 6. 測定

```bash
cp /tmp/paper-run/logs/latest.log /tmp/run_<version>.log     # 次の再起動で上書きされる
python3 tools/fight_profile.py plugin /tmp/run_<version>.log --dummy 0.5,65,0.5
python3 tools/plugin_bot_report.py /tmp/run_<version>.log --ref docs/parity/fabric_normal_anchor_run8_400s.log.gz
python3 tools/fight_timeline.py /tmp/run_<version>.log /tmp/run_<version>.png   # 目視用
```

## 7. 参照側(Fabric)を回し直す場合

`mc-server-delivery` の `mcserver.tar.gz` を展開すると Fabric + HeroBot MOD + Quantum マップ
+ qlog データパック入りのサーバーが得られる(未検証: このメモを書いた時点では未展開)。
参照の戦闘は `/tmp/ref_dist_run.log` 形式(`[q] … t=… d2=… i=…`)で `logs/latest.log` に
毎 tick 出力されるので、`tail -F … | grep '\[q\]' > /tmp/ref_run.log` で採取する。
