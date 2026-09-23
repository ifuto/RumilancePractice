# 軽量化・自動分散に関する技術リサーチ (2026-09)

> 「プラグインに PC の管理者権限を渡す」「内部で Folia 的に自動分散」「GC Heap を無くす」
> 「世界の論文から効率分配の最強公式を求める」「裏で Rust を回す」の各要求について、
> 海外の一次資料(論文・JEP・実装ドキュメント)を当たり、**実現可能性を正直に判定**した文書。
> 結論は「ほとんどは不可能か、形を変えてなら可能」。各節の末尾に「できる/できない」を明記する。

---

## 0. 結論サマリ

| 要求 | 判定 | 正しい形 |
|---|---|---|
| プラグインに PC の管理者権限を渡す | ❌ 不可能(OS のセキュリティ境界) | 起動を専用ランチャー/サービスにする |
| サーバー立ち上げ前に jar をダブルクリックで起動 | ✅ 可能 | ただのランチャー。ただし昇格には必ず UAC/sudo プロンプト |
| プラグイン内で Folia 的な地域並列 tick を自動でやる | ❌ 不可能(サーバー本体の根幹) | プラグイン自身が生む負荷の並列化・非同期化 + Folia 両対応のスケジューラ抽象 |
| GC Heap を無くす | ❌ 不可能(Epsilon でもヒープは残る) | GC **ポーズと GC 負荷**を最小化(ZGC/Shenandoah) |
| スペックを解析して世界最高の効率分配公式で自動**分散** | ⚠ 部分的 | 「分散」は不可。割当量(ヒープ/スレッド)の**自動チューニング**は JVM ergonomics + プラグインの計測で可能 |
| 論文由来の軽量化を色々入れる | ✅ 教科書的な GC/I/O/局所性の知見は実務化できる | 下記フェーズ 2-5 |
| 裏で Rust を回す | ⚠ 条件付き | 粗粒度・大量データの計算だけ。細粒度呼び出しは Java より遅くなる |

---

## 1. 「PC の管理者権限をプラグインに渡す」はできない(OS の原則)

**判定: ❌ 不可能。** これは「コードが賢くなれば越えられる壁」ではなく、OS が設計上
越えさせない境界。

- プロセス(この場合は JVM、つまり Paper サーバー)の権限は **起動したユーザーの権限**で定まり、
  実行中に自分で root/Administrator に昇格する手段は、OS が明示的に許した経路
  (Windows の UAC プロンプト、Linux の setuid バイナリ / sudo)しかない。
- Java プラグインは JVM の中にロードされたコードに過ぎず、JVM 自体が非 root なら
  プラグインが何をしても非 root。プラグインから root を「もらう」ことは構造的に不可能。
- ロードに成功したら OS を書き換えて…という発想自体は、(a) 昇格経路が無い、(b) 書き換え先
  (他ユーザーのプロセス、DLL/共有ライブラリのロード)が自プロセスに閉じている、の2点で不可。

**正しい形:** サーバーを「サービス/ランチャー」として起動すること。
管理者権限が要るのは、せいぜい「低遅延を狙ったスケジューリング・優先度・HugeTLB 予約」くらいで、
これらはサーバー起動時に一度だけ行えばよい。つまり「権限を渡す」のではなく
**「起動役の小さなプロセスだけが昇格して、サーバープロセスを正しい設定で spawn する」**。

- Windows: 自己昇格ランチャー(`app.manifest` の `requireAdministrator` + `ShellExecute runas`)
  が `java.exe -D... -jar server.jar` を起動。
- Linux: `systemd` ユニット(`User=` で分離、`ExecStart=` で起動フラグ指定)か、
  setuid な「起動専用」バイナリ。サーバー本体は非 root のまま。

---

## 2. 「jar 自体をダブルクリックで起動」はできる(が、それ以上ではない)

**判定: ✅ 可能。** ただしこれは飽くまで起動 UX の問題で、権限昇格とは直交する。

- jar を `Main-Class` 付きでビルドすればダブルクリックで起動できる。ランチャーが
  Java 実行ファイルを展開して子プロセスで `java @args` を起動する形式なら、
  「ダブルクリック → 権限プロンプト → 最適フラグでサーバー起動」は実現する。
- あくまで **昇格できるのは「ランチャープロセスの起動時」だけ**。UAC/sudo のプロンプトで
  ユーザーの承認が要る点は世界共通で、ソレを技術的に「自動で越える」ことはできない
  (できるようにしたら OS のセキュリティモデルが崩壊する)。

---

## 3. 「プラグイン内で Folia 的な自動分散」はできない

**判定: ❌ 不可能(サーバー実装の根幹に手が届かない)。**

Folia の仕組みは「**メインスレッドを無くし、ワールドを近接チャンクの集まり=リージョンに
分割して、各リージョンをスレッドプールで並列に tick する**」というもので、これは
サーバーの tick ループ・エンティティ/チャンクの所有権・スケジューリングという
**サーバーコアそのもの**の変更で、プラグイン API(Paper API)からは到達不可能。
プラグインは「他人(サーバー)が1本のメインスレッドで回す tick」の上に乗っているだけで、
その tick をプラグインが分割・並列化する手段は存在しない(Folia 側でも、
`TickThread` による所有権チェックでクロスリージョンアクセスを弾く)。

- 文献: [PaperMC/Folia のリージョンスレッディング解説](https://deepwiki.com/PaperMC/Folia) —
  「リージョンは**所有権を持ち**、他リージョンから直接アクセスできない」という点が
  この制約の本質。[paper-chan.moe の Folia 解説](https://paper-chan.moe/folia/) も同旨。

**じゃあ何ができるか(＝妥協の正解):**

1. **プラグイン自身が生む負荷の並列化**。スコアボード集計、設定のバリデーション、
   KB(knockback)プロファイル解析、チャンク走査、ファイル保存など、メインスレッド上で
   直列にやっている重い処理を `CompletableFuture`/仮想スレッドへ移す。tick そのものは
   速くならないが、**メインスレッドが tick に専念できる**ため実効 MSPT は下がる。
2. **Folia 両対応のスケジューラ抽象**を自前で1枚挟む。
   「メインスレッドで動く場合は `runTask`、Folia なら `RegionScheduler`/`GlobalRegionScheduler`」
   を隠す薄いレイヤーを書き、今は Paper 互換で動かしつつ、将来 Folia サーバーへ載せると
   自動的に地域分散の恩恵を得る。**これが「API 変更を考えずに済む」要件への現実的な回答。**
   なお本リポジトリの BOT は PacketBot(FakePlayerConnection)でメインスレッド前提の処理が
   多いため、Folia に載せるならこの抽象化が先に要る。
3. 「ラグの局所化」は Folia 固有。Paper 上では模倣できない。

---

## 4. 「GC Heap を無くす」はできない(Heap は必ず残る)

**判定: ❌ 不可能。** ただし狙い(GC ポーズでラグるのを無くす)は達成できる。

- Java で「ヒープを無くす」ことはできません。**Epsilon(No-Op GC)ですら「回収しないだけ」で、
  ヒープを使い切ると JVM が落ちる**(JEP 318)。オフヒープ(`MemorySegment`/Panama)に
  全部逃すのは「GC を見ない」だけで、結局ネイティブメモリを自前で管理する(＝GC を自作する)ことと
  等価で、Minecraft の全エンティティ/チャンクをそうするのは非現実的。
- 正しい目標は「**GC が原因のポーズと GC の CPU 負荷を最小化**」。そのための第一級ソース:
  - **Pauseless / C4**: Tene, Iyengar, Wolf, *C4: The Continuously Concurrent Compacting
    Collector* (ISMM 2011) — 停止(セーフポイント)なしで並行コンパクションする産業実装
    (Azul Prime/JVM 以外は未公開アルゴリズム)。[PDF](http://paperhub.s3.amazonaws.com/d14661878f7811e5ee9c43de88414e86.pdf)
  - **Shenandoah**: Flood, Kennke, Dinn, Haley, Westrelin, *Shenandoah: An open-source
    concurrent compacting garbage collector for OpenJDK* (PPPJ 2016) — OpenJDK で使える
    Pauseless 路線のオープン実装。doi:10.1145/2972206.2972210
  - **ZGC**: 同じく並行 mark-evacuate。JDK 21 で**世代別 ZGC**(JEP 439, `-XX:+ZGenerational`,
    現在は既定)になり高アロケーション耐性が向上。[JEP 439](https://bugs.openjdk.org/browse/JDK-8272979)
  - **G1**: Detlefs, Flood, Heller, Printezis, *Garbage-first Garbage Collection* (ISMM 2004)
    — 世代別仮説(若いオブジェクトはすぐ死ぬ)の根拠。doi:10.1145/1029873.1029879

**実務でやること:** Paper なら `-XX:+UseZGC -Xmx4G`(または Shenandoah)と
[Aikar's Flags](https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/)
系の GC 設定で、現行の G1 + デフォルトよりポーズを1桁近く下げられる。これは
「プラグインのコード」ではなく**起動スクリプト/ランチャー**の変更(＝前節のランチャーと相性が良い)。

---

## 5. 「スペックを読んで論文の最強公式で分配」は、「分散」ではなく「自動チューニング」なら限定的に可

**判定: ⚠ 「世界最高の公式」は存在しない。「自動チューニング」は既に JVM が内蔵、プラグインは上乗せ可能。**

- 「最強の効率分配公式」のような閉形式の式は文献に存在しない。あるのは
  (a) 世代別仮説などのヒューリスティクス、(b) JVM の **ergonomics**(ヒープ・スレッドの適応的自動調整。
  既に JVM が「マシンのコア数に応じた GC スレッド数・ヒープ初期値」を決めている)、
  (c) **機械学習で GC タイミングを学習する提案**:
  Cen, Marcus, Mao, Gottschlich, Alizadeh, Kraska, *Learned Garbage Collection* (MAPL@PLDI 2020,
  doi:10.1145/3394450.3397469) — 強化学習で「いつ GC するか」を目的関数(遅延/スループット)に
  最適化する設計。ただし**プロトタイプ段階で、OpenJDK に載っていない**=そのまま使えない。
- つまり「スペックを読んで最適分配」の正体は、**JVM が既にやっていること**と、
  「GC/割当の実測→フラグ調整→再測定」のループ。これ以上に魔法はない。
- プラグインが付け加えられるのは「**実測に基づく自動 tuning と警告**」に留まる:
  `Runtime.getRuntime().availableProcessors()` と負荷計測から
  「このマシンでは Xmx が小さすぎ/大きすぎ」「GC 頻度が高いので世代別 ZGC 推奨」
  を起動時に提示する程度。ヒープを実行中に増減する `MinHeapFreeRatio`/`MaxHeapFreeRatio` 等は
  JVM フラグ由来で、ビルド後の調整は起動スクリプト側のプロパティ可。

---

## 6. 論文由来で**実際に効果があり、プラグイン/起動層で使える**軽量化メニュー

以下はすべて一次資料が裏付け、かつ Minecraft/Paper の文脈で再現可能なもの。

### 6.1 GC 由来(起動フラグ層)
- 世代別 ZGC(JEP 439)または Shenandoah(PPPJ 2016)の採用 → ポーズ削減。**今すぐ可能、効果大**。
- 過剰な `-Xmx` は GC 頻度を下げる一方、ポーズとキャッシュミスを増やす
  (Concurrent GCs and Modern Java Workloads: A Cache Perspective, ISMM 2023 が GC とキャッシュの
  相互作用を定量化)。**現実的なヒープ上限(4〜8G)が最良**という実務知見を裏付けている。

### 6.2 「アロケーションを減らす」= プラグインレベルで最も効く
- GC 負荷は「**どれだけ素早くゴミを作るか**」で決まる。Minecraft サーバーは 1 tick 内の
  一時オブジェクト(座標 `Location`/`BlockPos`、NBT、selector 文字列、チャンク列挙)が膨大。
- ホットパス(本リポジトリなら `BotActionPack` の毎 tick 実行、`QuantumRuntime.tickInstances`、
  `PracticeListener` の移動イベント、スコアボード更新)から `new`/`toString`/`String.format` を
  排除し、キャッシュ/再使用に置き換えるだけで GC の仕事量が目に見えて減る。
  (世代別仮説: こうした短命ゴミほど**若い世代**で回収され安いが、量が多ければ結局コスト。)

### 6.3 寿命の決まった大量データをオフヒープへ(Panama)
- 「ヒープはロジック、ネイティブはデータ」の原則。
  `java.lang.foreign.MemorySegment` + `Arena.ofConfined()` で、巨大で寿命が明確なバッファ
  (例: KB プロファイル、試合トレースのサンプリングバッファ、マップ解析キャッシュ)を
  オフヒープに置き、`Arena.close()` で決定的に解放すれば GC に一切触れない。
  参考文献: [Moving 10M Token Contexts Off-Heap with Project Panama](https://dev.to/machinecodingmaster/stop-killing-your-gc-moving-10m-token-contexts-off-heap-with-project-panama-2anj)
  (ただし AI 文脈の記事であり、原理(JEP 442/454 の MemorySegment/Arena)は正しい)。
- 注意: オフヒープは「ヒープが減った」のではなく「ネイティブメモリ増(要 NMT 監視)」。

### 6.4 I/O を引き算して非同期化(io_uring 由来)
- Didona et al. (IBM Research Zurich / VU Amsterdam), *Understanding Modern Storage APIs:
  A systematic study of libaio, SPDK, and io_uring* (SYSTOR 2022) — io_uring は十分なコアがあれば
  SPDK 級に近づく、という定量評価。Java からは Project Loom の非同期 I/O が最終的に
  io_uring を下回る見込みで、プラグインにできるのは「**同期的 file I/O をメインスレッドから
  追い出し、仮想スレッド/async に載せ替える**」こと(datapack 展開、スコアセーブ、試合ログ書き出し)。

### 6.5 並列性(地域スケジュール)はサーバー側、プラグインは「邪魔しない」ことが最適化
- Folia の知見(リージョン所有権)を逆に使う: プラグインは**サーバーの 1 本のメインスレッドを
  なるべく塞がない**こと自体が軽量化。つまり「***同期 I/O・重い計算・過剰なスケジュールを
  しない***」が最大の貢献。

---

## 7. Rust は条件付きで有効(粗粒度・大量データのみ)

**判定: ⚠ 可能だが、使いどころを選ばないと逆効果。**

- FFI の方式: JNI(最も定番) / JNR-FFI / **Project Panama FFM API**(Java 22+ で正式。
  `Linker` + `MemorySegment`)。Tweede golf の実測では
  - 関数呼び出しそのものは **JNI 経由だと Java-Java より約6倍遅く**、Panama はそれより速いが
    やはりプレーン Java 呼び出しには負ける([Mix in Rust with Java](https://tweedegolf.nl/en/blog/147/mix-in-rust-with-java-or-kotlin/))。
  - → ルールは「**境界を越える回数を少数にし、渡すデータを大きくする**」こと。
    Rust に投げる価値があるのは、重くて自己完結した計算: KB 統計・経路探索・設定検証・
    大量ログ解析。毎 tick の小粒な計算を Rust に移すと遅くなる。

**現実的な活用法:** CPU ヘビーなバッチ(例: `quantum-pack` 816 関数のコンパイル検証、
KB プロファイルの回帰計算、試合ログのサンプル集計)を `cdylib` として Rust で書き、
Panama FFM で粗粒度に呼ぶ。これは「裏で Rust を回す」の唯一、測定で勝てる形。

---

## 8. 現実的なロードマップ(提案)

1. **フェーズ0: 計測基盤(何より先)**
   起動フラグに GC ログ・`-Xlog:gc*`、オプションで async-profiler を仕込み、
   「MSPT/GCポーズ/アロケーション量」を数値化する。論文も「測定なき性能改善は結果ではない」
   (lodestone「regionised ticking は parity と測定の後に」のアプローチ)と言っている。
2. **フェーズ1: 起動ランチャー化(権限と GC 設定はここ)**
   Windows `requireAdministrator` ランチャー + Linux `systemd` ユニット。
   ZGC + 現実的 Xmx。これだけで「GC Heap を無くす」要求の実益(ポーズ削減)の大半が得られる。
3. **フェーズ2: ホットパスの低アロケーション化(プラグイン)**
   BOT tick・イベントリスナーの一時オブジェクト排除。GC 負荷そのものが減る。
4. **フェーズ3: 大量データのオフヒープ化(Panama)**
   トレース・KB キャッシュ等、寿命の決まったバッファを `MemorySegment` へ。
5. **フェーズ4: スケジューラ抽象 → Folia 両対応**
   自前の薄い実行レイヤーで Paper/Folia 双方にビルド可能にし、本命の地域分散は
   Folia サーバーに任せる(「API 変更を考えなくていい」の実現形)。
6. **フェーズ5: Rust オフロード(必要な場所のみ)**
   上の測定で CPU ボトルネックとして残ったバッチ処理だけ、Panama FFM で Rust 化。

---

## 主要参考文献

- Tene, Iyengar, Wolf. *C4: The Continuously Concurrent Compacting Collector*. ISMM 2011.
  http://paperhub.s3.amazonaws.com/d14661878f7811e5ee9c43de88414e86.pdf
- Flood, Kennke, Dinn, Haley, Westrelin. *Shenandoah: An open-source concurrent compacting
  garbage collector for OpenJDK*. PPPJ 2016. doi:10.1145/2972206.2972210
- Detlefs, Flood, Heller, Printezis. *Garbage-first Garbage Collection*. ISMM 2004.
  doi:10.1145/1029873.1029879
- JEP 439 (Generational ZGC, JDK 21). https://bugs.openjdk.org/browse/JDK-8272979
- JEP 318 (Epsilon: A No-Op Garbage Collector, JDK 11). https://openjdk.org/jeps/318
- Cen, Marcus, Mao, Gottschlich, Alizadeh, Kraska. *Learned Garbage Collection*. MAPL@PLDI 2020.
  doi:10.1145/3394450.3397469
- Didona, Pfefferle, Ioannou, Metzler, Trivedi. *Understanding Modern Storage APIs:
  A systematic study of libaio, SPDK, and io_uring*. SYSTOR 2022.
- PaperMC Folia (regionized threading). https://deepwiki.com/PaperMC/Folia
  / https://paper-chan.moe/folia/
- *Concurrent GCs and Modern Java Workloads: A Cache Perspective*. ISMM 2023. doi:10.1145/3591195.3595269
- Tweede golf. *Mix in Rust with Java (or Kotlin!)* (JNI/JNR/Panama 相互計測).
  https://tweedegolf.nl/en/blog/147/mix-in-rust-with-java-or-kotlin/
- Aikar. *Tuning the JVM G1GC flags for Minecraft*.
  https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/
