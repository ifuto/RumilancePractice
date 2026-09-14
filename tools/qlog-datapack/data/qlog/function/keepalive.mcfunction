# === 計測ハーネス(通常プレイを邪魔しない最小限) ===
# 1) 相手が即死しないよう延命(ラウンドを長く観測するため / プレイヤーは元々レジ耐性は持たない)
effect give @a[tag=xlib_target] resistance 2 1 true
effect give @a[tag=xlib_target] regeneration 2 3 true
# 2) ラウンド自動開始(プレイヤーが部屋に入る操作の代替)。開始後 80t は再開始しない
scoreboard players remove #qlog_round qlog_t 1
execute if score .start start matches 0 if score #qlog_round qlog_t matches ..0 if entity @a[tag=xlib_target,scores={death=0}] if entity @a[tag=xlib_bot,scores={death=0}] run function qlog:start_round
