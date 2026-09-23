# システム基盤からの軽量化 — 管理者権限 + JNI/ネイティブ層の設計書 (2026-09)

> 前段の `lightweight-optimization-research.md` で「GC/分散/公式」の可否を整理した。
> この文書はその続きで、**「UAC 昇格は許容 → 管理者権限を得て → JNI 経由で OS 基盤を最適化
> する」という方針が、どこまで・どの API で・どんな論文裏付けで成立するか**を具体化した実装設計書。
>
> **結論の一言:** 昇格 + JNI で OS 基盤を触るのは可能。ただし文献が示す効果は
> 「ティックの周期的な**固まり(ラグスパイク/jitter)の除去**」であり、
> 「単一スレッドの tick が魔法のように速くなる」ことではない。狙いを jitter 除去に置けば、
> Minecraft サーバーに非常に効く(1秒固まる系の TPS 低下は正にこれ)。

---

## 1. 全体アーキテクチャ

```
[ユーザーが jar をダブルクリック]
        │  非昇格 JVM でランチャーが動く
        ▼
[ NarenaLauncher (Java, 自己昇格) ]
   ├─ Windows: app.manifest requireAdministrator + ShellExecute("runas") で自分自身を
   │           昇格再起動 (Java.exe の再起動ではなく、管理権限を得た親プロセスになる)
   ├─ Linux  : systemd ユニット / setuid な起動専用バイナリ / sudo --preserve-env
   ▼
[ OS 前処理 (昇格済みプロセス内、一度だけ) ]          ← ★管理者権限が要るのはここだけ
   1. 電源管理: C-state ディープスリープ制限 / governor=performance (Linux)
               / Windows 電源プラン切替 (powercfg)
   2. コア分離: istate 分離 (Linux isolcpus / Windows はコアマスク退避)
   3. HugeTLB / THP 予約 (Linux) / Windows Large Pages 権限付与
   4. I/O 優先度・受信バッファ調整 (任意)
        │
        ▼  子プロセス起動
[ java -XX:+UseZGC -Xmx4G ... -jar paper.jar nogui ]
        │
        ▼  プラグインが JNI(cdylib) で呼ぶ
[ NativeRumilance (Rust/C, プラグインに同梱) ]
   - 自プロセス内でできる最適化(昇格不要分): スレッド親和性、/dev/cpu_dma_latency 保持、
     madvise(MADV_HUGEPAGE)、ioprio_set、優先度微調整
   - 昇格が必要な大域設定は「ランチャーの仕事」と明確に分離する
```

**設計原則:**
- **昇格が必要な特権操作はランチャープロセスが一度だけ行い、サーバー本体 JVM は非 root のまま。**
  （サーバーが root/PermissionSet を持つと、プラグインやパックのスクリプトが root で暴走する
  リスクを一生背負う。システム最適化に root 常在は不要。)
- **JNI ライブラリは「自プロセス内の合法操作」だけ**を持つ。これにより、ネイティブ層が
  サーバーの堅牢性を壊す面を最小にできる。

---

## 2. システム基盤の最適化メニュー(優先度順・論文裏付け付き)

### 2.1 電源管理 / C-state ディープスリープ制限 — **最も効く**
Minecraft の「普段 20TPS なのに突然 1 秒固まる」は、CPU がアイドル時に深い省電力状態(C6 等)へ
落ち、要求が来たときに**復帰(wake)に数100µs〜数msかかる**のが原因の典型。

- **Tales of the Tail (SOCC 2014, UW)**: 低負荷時に省電力 C-state が入ると tail latency が
  **悪化**することを実測。電源管理を切ることで改善する。 [PDF](https://drkp.net/papers/latency-socc14.pdf)
  （同研究は C3 復帰に約 200µs を観測）
- **Schöne, Molka, Werner. *Wake-up latencies for processor idle states on current x86
  processors* (2014)**: x86 の各 C-state の復帰遅延を実測。
- **Govtva (2019, 学位論文)**: Intel Xeon 各世代の C-state 最大復帰遅延を計測し、
  C1E を境に遅延が有意に跳ね上がる。 [PDF](https://www.theseus.fi/bitstream/handle/10024/169205/Vladislav%20Govtvas%20Thesis.pdf)
- **Gil Tene (Azul) の低遅延講演**: 「GC と TTSP を除くと、次に支配的になるのは OS 側。
  **Scheduling pressure / Hyper-threading / Swapping / Power management / THP** に真剣に
  取り組むべし」と明言。 [slides](https://www.slideshare.net/slideshow/java-latencyzuluazul/43637421)

**実装:**
- Linux: `/dev/cpu_dma_latency` に保持する fd へ「目標最大応答時間(0)」を書いて開いたままにする
  （PM QoS。カーネル再ビルド不要・プロセス終了で自動解除）。加えて `cpupower idle-set -D N`
  （governor/ディープ C-state 制限）。参考: [Red Hat — PM QoS による C-state 制御](https://access.redhat.com/articles/65410)
- Windows: 高パフォーマンス電源プランへ `powercfg /setactive`、必要なら
  `powercfg /change standby-timeout-ac 0`。ゲーム配信では「Ultimate Performance」プランの有効化。

**注意(正直ベース):** 消費電力/発熱が増える。これは「遅延とのトレードオフ」であって無料ではない。
また可逆（プロセス終了で戻る）。

### 2.2 スレッド親和性・コア分離 — **次に効く(条件付き)**
- 純 Java ではスレッドを CPU に固定できない。**JNI/JNA で `sched_setaffinity` /
  `pthread_setaffinity_np`(Linux)、`SetThreadAffinityMask`(Windows)を呼ぶ**のが定番。
  OpenHFT `Java-Thread-Affinity` がその実装として広く使われている。
  [OpenHFT/Java-Thread-Affinity](https://github.com/OpenHFT/Java-Thread-Affinity)
- 効果の本質は「サーバーの tick メインスレッドが他の負荷(OS ハウスキーピング、GC スレッド、
  割り込み)に邪魔されないコアを確保する」こと。Gil Tene の「scheduling pressure を重視せよ」に対応。

**実装方針:**
- 全 JVM を `taskset cpuset-mask`(Linux)/`SetProcessAffinityMask` で予約コアに載せ、
  さらに JNI で「tick メインスレッド」だけを分離コアへピンする(サーバー起動直後、メイン
  スレッド上で実行される起動タスクから)。
- Linux 専用サーバーなら起動時の `isolcpus=` / `nohz_full=` / `rcu_nocbs=` で
  「他のプロセスが乗れないコア」を kernel 起動パラメータで予約するのが最も堅い。
  （これが昇格ランチャーに任せる部分）

**注意:** ピン止めはやり方を間違えると**遅くなる**（ピンしたコアに他スレッドが流れ込む、
ハイパースレッド兄弟コアと共有される等。OpenHFT の issue でも「ピンされたコアで他スレッドが
動いて却って劣化」が報告されている）。また bigLITTLE(Intel P/E コア)では P コア/E コアの
見極めが必要。

### 2.3 Transparent Huge Pages / HugeTLB — **ヒープ大のとき有効**
- **Navarro, Iyer, Druschel, Cox. *Practical, transparent operating system support for
  superpages* (OSDI 2002)**: TLB ミスで性能が **30〜60%** 落ちるアプリがあり、superpage で
  dTLB ミスがほぼ消える、と言う根拠論文。これは THP の理論的基盤。
  [PDF](https://www.usenix.org/legacy/event/osdi02/tech/full_papers/navarro/navarro.pdf)
- Minecraft サーバーはワーキングセットが大きく、ページウォークが発生しやすい。
  ヒープ(2〜8GB)が実メモリに乗るなら、2MB(THP)/1GB(HugeTLB)ページで打率が上がる。
- Gil Tene のスライドも「Transparent Huge Pages」を OS 側ハイカップ要因の一つに挙げる（
  設定次第で jitter 源にもなり得る、の意）。

**実装:**
- Linux: 起動時に `THP=madvise` を推奨にし、ネイティブ層からヒープに対して `madvise(MADV_HUGEPAGE)`
  （または ZGC の `-XX:+UseLargePages` と `HugePages_Total` 予約＝ランチャーの特権仕事）。
- Windows: 「Lock pages in memory」権限をサービスアカウントへ付与 + `-XX:+UseLargePages`
  （これは管理者権限が要る典型例。ランチャーがやってよい）。

**注意:** THP `always` は逆に jitter になる場合がある(カーネルのデフラグ/コンパクションが
突発)。`madvise` × 対象限定が安全。

### 2.4 I/O 優先度・非同期化
- サーバーのチャンク読み書き・datapack 展開・ログ書き出しは同期的 file I/O。
  前段で参照した **Didona et al. (SYSTOR 2022)** の io_uring 比較が示すのは「最新 API は
  十分なコアで高速」であり、プラグインに応用するなら「**同期 I/O をメインスレッドから追い出す**」。
- ネイティブ層でサーバープロセスを IO ベストエフォートクラスに置く(`ioprio_set`,
  class=BE/IDLE)のは任意（チャンクセーブを優先度下げるとセーブが遅れるので慎重に）。

### 2.5 NUMA — 専用サーバー限定
- 2 ソケット以上なら、メモリを近いノードから割り当てる（`numactl --membind`、
  ヒープは `-XX:+UseNUMA`）。Tales of the Tail も「thread/process pinning + interrupt routing」
  を推奨するが、**NUMA は物理サーバー専用**。VPS/家庭用 PC では無効であり、無理にやると劣化。

---

## 3. OS 別 API 対照表

| 最適化 | Linux (root 起動時に設定) | Windows (管理者トークン) | 自プロセス内(非 root 可) |
|---|---|---|---|
| 電源/C-state | `cpupower idle-set` / `intel_idle.max_cstate=` / `/dev/cpu_dma_latency` | `powercfg /setactive <高性能>` / hidden "Ultimate Performance" | `/dev/cpu_dma_latency` open+write(要 読み書き権限のみ) |
| コア分離 | `isolcpus=` `nohz_full=` を systemd/kernel へ | コアマスク退避は運用で | `sched_setaffinity` / `SetThreadAffinityMask` |
| プロセス優先度 | `nice/chrt` / systemd `Nice=` | `SetPriorityClass(HIGH)` | `SetPriorityClass`(自プロセス可) |
| ラージページ | `HugePages_Total` 予約 + `-XX:+UseLargePages` | "Lock pages in memory"+`-XX:+UseLargePages` | `madvise(MADV_HUGEPAGE)`(自プロセス) |
| I/O 優先度 | `ionice` / `ioprio_set` | I/O priority(WS2012+ は間接的) | `ioprio_set`(自プロセス) |
| NUMA | `numactl --membind` + `-XX:+UseNUMA` | SQL Server 系のみ(実質対象外) | — |

**要するに：** 管理者権限が要るのは「マシン全体に効く電源/カーネル/HugeTLB 予約」で、
それは**起動時に一度、ランチャーがやる**。常駐する JNI ライブラリは「自プロセス内の合法操作」
（親和性・PM QoS・madvise）に限定する、という分離が最も安全で保守しやすい。

---

## 4. 危険性と「効かないもの」(正直な設計境界)

1. **単一スレッドの純計算は速くならない。** 論文が裏付けるのは jitter/テイル遅延の除去。
   「tick が 3ms→1ms」になるのではなく「たまに 150ms 固まるのが消える」。Minecraft の体感は
   むしろ後者で大きく改善するが、期待値の置き方を間違えないこと。
2. **ハイパースレッドは正解が無い。** Gil Tene 自身が「good? bad?」と疑問符を付けている。
   兄弟コア同時使用が逆効果になるケースがある。
3. **共有 VPS / クラウド / 仮想マシンでは全部効かない。** ハイパーバイザーが電源/コア/物理
   メモリを握っており、ゲストからの C-state・NUMA・HugeTLB 制御は無効か無意味。
4. **周波数(オーバークロック)はやらない。** P-state の `governor=performance` は
   「クロックを下げない」だけであって、定格を超える設定は OS 経由では行わない（基盤破損リスク）。
5. **可逆性を保証する。** ランチャーの設定は「起動時だけ」「終了時にもとに戻す」を原則にする。
   でないと、サーバー終了後も PC が全力（発熱・ファン全開）のままになる事故を起こす。
6. **root 常在はしない。** サーバー JVM 自体は非 root。ネイティブ層にも「特権昇格を要求する
   インターフェース」を持たせない（プラグイン改ざんからの横展開を防ぐ）。

---

## 5. このリポジトリでのロードマップ(案)

| フェーズ | 内容 | 備考 |
|---|---|---|
| 0 | 計測基盤: GC ログ・MSPT 記録・async-profiler | 「固まり」の実測値がないと最適化の効果が測れない |
| 1 | **昇格ランチャー**(Windows 自己昇格 + Linux systemd/setuid 起動スキム) | 権限と GC フラグ(ZGC)はここ |
| 2 | **ネイティブ基盤ライブラリ**(Rust `cdylib` + JNI): 親和性ピン / `/dev/cpu_dma_latency` / `madvise` | 昇格不要分から先に |
| 3 | ランチャー側のマシン全体設定(電源プラン / C-state / HugeTLB / isolcpus) | root 一回分 |
| 4 | プラグイン側: ホットパスの低アロケーション化(BOT tick 等) | 前段 GC メニューと合流 |

**ビルド上の注意:** ネイティブ部は Rust/C ツールチェーンと OS 別バイナリ配布が要るため、
リポジトリの `build` CI(Gradle のみ)に直接混ぜず、**独立モジュール**として管理するのが安全
（プラグイン本体は `ServiceLoader`/`OptionalDependency` 的に「無ければ無効」で動く）。

---

## 6. 実装状況 (2026-09-23)

フェーズ1〜3相当のうち「Windows のマシン全体設定」を、**ネイティブコードを一切使わず**
Microsoft 標準ツール `powercfg.exe` への委任として実装した（`/turbo on|off|status`）。

- `src/main/java/com/rumilance/practice/turbo/WindowsOptimizationService.java`
  - `powercfg -getactivescheme / -duplicatescheme / -setacvalueindex / -setactive` を
    単一の使い捨て昇格 PowerShell で実行。`config.yml` の `turbo:` セクションで全項目を制御。
  - 適用項目: `PROCTHROTTLEMIN 100`（P-state 下限）、`CPMINCORES 100`（コアパーキング解除）、
    `DISTRIBUTEUTIL 0`（低負荷時の全コア分散）、`IDLEDISABLE 1`（深い C-state 回避・任意/電力増）。
  - **可逆性**: 既定では現在のプランを書き換えず、ベースプラン（既定 Ultimate Performance）を
    複製して編集・有効化。`/turbo off` で適用前プランへ復元し、IDLEDISABLE を 0 に戻す。
    複製に失敗した場合は一切書き込まずに中止（オペレーターの現行プランを汚さない）。
  - **昇格**: サーバー JVM は非昇格のまま。mutation は毎回 `Start-Process -Verb RunAs` で
    昇格子プロセスに閉じ込め、初回に UAC を 1 回だけ出す。各呼び出しはタイムアウト付きで
    メインスレッド外で実行。
- `src/main/java/com/rumilance/practice/turbo/TurboCommand.java` — コマンド実体（`rumilance.admin`）。
- `src/main/java/com/rumilance/practice/turbo/TurboIdleManager.java` — プレイヤー在/不在による
  自動切替（「誰もいないなら休ませる・人が来たら即復帰」）。EcoQoS(プロセス電力スロットリング,
  非昇格で自プロセスに適用可能) と電源プラン復元/再適用を組み合わせる。`/stop` のような
  コールドスタートは行わず、サーバープロセスは終始生きたまま（復帰は数秒）。
  - 退出側: 最後のプレイヤーが抜けたら（`turbo.auto.idle-delay-seconds` 秒後）プランを通常へ
    戻し、サーバープロセスを EcoQoS で省エネ化。
  - 復帰側: `PlayerJoinEvent` で EcoQoS 解除→ターボプラン再適用。
  - **自動切替は無プロンプト**: 管理者が一度 `/turbo on` で立てた常駐ヘルパー（コンフィグ
    セクション参照）だけを使い、ヘルパー不在時は静かにスキップ。乗っ取り等でも自動で
    UAC ダイアログを出すことはない。
- `src/main/java/com/rumilance/practice/turbo/TurboCommand.java` — コマンド実体（`rumilance.admin`）。
  `status` に EcoQoS / 自動切替の状態も表示。
- 未実装（残る設計上の宿題）：Linux 側の governor/HugeTLB/isolcpus、Rust `cdylib` による
  スレッド親和性ピン・`/dev/cpu_dma_latency` 保持。これらは別 OS・別配布物になるため、
  本リポジトリの Gradle ビルドには同梱していない。

---

## 参考文献

- Li, Sharma, Ports, Gribble. *Tales of the Tail: Hardware, OS, and Application-level Sources of
  Tail Latency*. SOCC 2014. https://drkp.net/papers/latency-socc14.pdf
- Schöne, Molka, Werner. *Wake-up latencies for processor idle states on current x86 processors*.
  Computing (2014). doi:10.1007/s00450-014-0270-z
- Navarro, Iyer, Druschel, Cox. *Practical, transparent operating system support for superpages*.
  OSDI 2002. https://www.usenix.org/legacy/event/osdi02/tech/full_papers/navarro/navarro.pdf
- Gil Tene (Azul). *Enabling Java in Latency Sensitive Applications*.
  https://www.slideshare.net/slideshow/java-latencyzuluazul/43637421
- Red Hat. *Are hardware power management features causing latency spikes in my application?*
  （/dev/cpu_dma_latency / PM QoS） https://access.redhat.com/articles/65410
- OpenHFT. *Java-Thread-Affinity*（JNI/JNA によるスレッドピン）. https://github.com/OpenHFT/Java-Thread-Affinity
- Govtva. *Intel Xeon Server CPU Maximum Wake Latency Measurement*（B.Eng. thesis, 2019）.
- Lai, Lam, Wang et al. *Latency-aware DVFS for efficient power state transitions on many-core
  architectures*. J Supercomput (2015). doi:10.1007/s11227-015-1415-y
- Didona et al. *Understanding Modern Storage APIs: libaio, SPDK, and io_uring*. SYSTOR 2022.
- Tweede golf. *Mix in Rust with Java*（FFI オーバーヘッド実測）.
- （前段からの継続参照）ZGC/JEP429/439、Shenandoah PPPJ2016、C4 ISMM2011、Epsilon JEP318、
  Learned GC MAPL2020、Folia 解説。
