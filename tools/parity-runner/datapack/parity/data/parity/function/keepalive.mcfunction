# parity:keepalive — ラウンドが終わったら（.start=0）少し待って開始し直す。
# 死亡→reset の判定はマップ自身（quantum:main_tick）がやる。ここは「次のラウンドを踏む」だけ。
scoreboard players remove pari_round parity_t 1
execute if score .start start matches 0 if score pari_round parity_t matches ..0 if entity @a[name=quantumbot,scores={death=0}] if entity @a[name=qbot2,scores={death=0}] run function parity:start_round
