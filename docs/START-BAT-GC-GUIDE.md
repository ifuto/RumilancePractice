# start.bat — GC 戦略ガイド (2026-09-29)

N Arena は「攻撃⇄ノックバック」が 1 tick 単位で効くサーバーです。GC の大きな停止
(数百 ms) はプレイヤー視点で「ノックバックが遅れる・瞬間凍結する」として現れます。
方針は 1 つ:

> **軽い GC を短い周期で回し、大きな停止を物理的に発生させない**

以下、そのまま貼れる `start.bat` と、各スイッチの意味・チューニング手順です。

---

## 推奨 start.bat (コピペで使える形)

```bat
@echo off
rem JVM 21+ を使う (Eclipse Adoptium 21 LTS が安定)
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ==== メモリ: 最小=最大で固定し、拡縮コストをゼロに ====
set "MEM=-Xms4096M -Xmx4096M"

rem ==== G1: 軽い GC を短周期で、Major GC をほぼ出させない ====
set "G1=-Xmn1024M"
set "G1=%G1% -XX:MaxGCPauseMillis=150"
set "G1=%G1% -XX:InitiatingHeapOccupancyPercent=25"
set "G1=%G1% -XX:G1MixedGCCountTarget=16"
set "G1=%G1% -XX:G1HeapRegionSize=16M"
set "G1=%G1% -XX:+ParallelRefProcEnabled"
set "G1=%G1% -XX:ExplicitGCInvokesConcurrent"
set "G1=%G1% -XX:ConcGCThreads=2"

java %MEM% %G1% -jar server.jar nogui
pause
```

`server.jar` のパスと `JAVA_HOME` だけ自分の環境に合わせれば即運用できます。

---

## 各スイッチの意味

| スイッチ | 効果 | 解説 |
|---|---|---|
| `-Xms4096M -Xmx4096M` | ★★★ | 最小=最大に固定し、運用中のヒープ拡張(リサイズ停止)をなくす |
| `-Xmn1024M` | ★★★ | New 世代を 1G に固定。短命オブジェクトは Young GC で即回収されるため Major GC の頻度が激減する。大きな一時停止の原因はほぼ Major なので、これだけで体感が大きく変わる |
| `-XX:MaxGCPauseMillis=150` | ★★★ | 1 回の GC 停止を 150 ms 以内に抑えさせる。上限を指定しないと G1 はスループット優先で長い停止を許容してしまう |
| `-XX:InitiatingHeapOccupancyPercent=25` | ★★★ | ヒープ全体が 25% を超えた時点で Mixed GC(= Major に相当) を始める。既定の 45% だとギリギリで VF → Full GC に落ち、長時間停止になりがち。早く始めるほど 1 回が小さく済む |
| `-XX:G1MixedGCCountTarget=16` | ★★ | Mixed GC の回収対象を 16 分割。ヒープが育っても 1 回の作業量を小さく保つ |
| `-XX:G1HeapRegionSize=16M` | ★★ | G1 の作業単位(region)を大きめに。Minecraft のチャンク・ブロック郡と相性が良い |
| `-XX:+ParallelRefProcEnabled` | ★★★ | Weak/Soft/Phantom Reference の処理を並列化。残りがちな「最後の長い一発」を消す定番 |
| `-XX:ExplicitGCInvokesConcurrent` | ★★ | プラグインが `System.gc()` を呼んでも同期 Full GC ではなく並行 GC に置き換える。誤爆による数秒停止を防ぐ保険 |
| `-XX:ConcGCThreads=2` | ★ | 並行 GC スレッドを 2 本に固定(4 Core 以上で有効)。低スペック PC ならこの行は削除 |

### GC ログで検証する

```bat
java %MEM% %G1% -Xlog:gc+:tags:file=logs/gc.log:time,uptime:filecount=7,filesize=2M -jar server.jar nogui
```

`logs/gc.log` の `Pause Young` / `Pause Mixed` が 100 ms をほとんど超えなければ OK。
超えるようなら `-XX:MaxGCPauseMillis` を 100 に下げるか `Xmn` を 1.25G へ増やす。

---

## 小さな PC (2G / 1 Core クラス) で回したい場合

```bat
@echo off
set "MEM=-Xms2048M -Xmx2048M"
set "G1=-Xmn512M -XX:MaxGCPauseMillis=150 -XX:InitiatingHeapOccupancyPercent=25 -XX:+ParallelRefProcEnabled -XX:+ExplicitGCInvokesConcurrent"
java %MEM% %G1% -jar server.jar nogui
pause
```

潰れやすくなるのは Mixed GC の頻度だけなので、体感差は比較的小さい想定です。

---

## まとめ

- **Young 世代を大きく** (Xmn), **停止時間上限を明示** (MaxGCPauseMillis)
- **Mixed GC を早く・小さく始める** (IHOP 25% + MixedGCCountTarget 16)
- **残弾処理は並列** (ParallelRefProcEnabled) で最後の長停止を消す
