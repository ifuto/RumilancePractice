# qlog — QuantumBOT 行動ロガー(比較実験用)

Practicebot と同じ world に入れるだけで、QuantumBOT の行動タイムラインが
サーバーコンソールに流れます(0.5秒間隔):

```
[qlog] pos=12.34,64.00,-5.67 hp=17.3 hitcd=6 totem=0 ct=4 cry=1
```

- `hitcd` が頻繁に小さい値=剣を振っている/コンボ中
- `totem` がラング値(totem_cd)から減る=ポップ後の休止
- `ct`(crystal_timer)の減算サイクル=クリスタル設置間隔
- `cry`=?  近く(8blk)にエンドクリスタルが存在
- `pos` の推移=前進・足止め・後退の判定

## 使い方(Fabricサーバー)

1. この `qlog` フォルダごと `<ワールド>/datapacks/` にコピー
   (Quantumマップのworldルートに `datapacks/Practicebot` がある階層)
2. サーバーコンソールで `/reload`(または再起動)
3. ボットをスポーンして 30〜60 秒戦う
4. `latest.log` の `[qlog]` 行をエージェントに貼る/リポジトリに push

これで「マップの本物のBOTがどう戦うか」の実測タイムラインが手に入り、
RumilancePractice 側の fight trace と並べて比較できる。
