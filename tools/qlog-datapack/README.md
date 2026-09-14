# qlog — QuantumBOT 完全比較用サンプラ(0.1秒粒度)

Practicebot と同じ world の `datapacks/` に置くだけで、QuantumBOT の全行動が
0.1秒間隔(2tick毎)でサーバーコンソールに流れる:

```
[q] 12.345,64.000,-5.678 v=0.216,0.078,0.000 y=178.5 p=12.3 hp=19.6 g=1 i=minecraft:netherite_sword hit=4 tot=0 ct=3 ob=0 pc=0 cry=1
```

| 項目 | 意味 |
|---|---|
| `p= x,y,z` | 位置(0.001 blk分解能)= 一歩一歩の移動軌跡 |
| `v= vx,vy,vz` | 速度ベクトル(加速・摩擦・ジャンプ放物線がそのまま出る) |
| `y= / p=` | 視点 yaw/pitch(スナップ+エイム歪みの実測) |
| `hp / g` | 体力 / 接地 |
| `i=` | 手持ちアイテム(使うアイテムの遷移) |
| `hit / tot / ct / ob / pc` | マップ自身のタイマ(hitcd/トーテム/クリスタル/オブシディアン/パール) |
| `cry` | 近傍(9blk)にエンドクリスタルが存在 |

## 試合開始の自動化(実データ確認済み)

```
/function quantum:options/crystal                          # モード選択
/player quantumbot spawn at 11 34 10 facing 0 0 in survival
/scoreboard players set .start start 1                     # (promptトグル既定なら自動でも開始)
```

タグは自動(quantumbot等=xlib_bot、他=xlib_target)。**BOT vs BOT** は2体目を別名で
スポーンするだけ。`latest.log` の `[q]` 行をエージェントに渡す = 実測データ完成。

## 比較手順(数値完全一致ループ)

1. Fabric側: 上記で30〜60秒戦う → `[q]` 行を回収
2. 当側: RumilancePractice で同条件(難易度/モード)を戦う → fight trace の `s` 行を回収
3. `tools/compare_fights.py fabric.log ours.log` → 軸を揃えた差分レポート
4. 差分がある箇所をコードで直す → 3へ(完全一致まで)
