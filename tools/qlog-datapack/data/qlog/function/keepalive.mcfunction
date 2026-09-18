# === 計測ハーネス(通常プレイを邪魔しない最小限) ===
# 1) 相手が即死しないよう延命(ラウンドを長く観測するため / プレイヤーは元々レジ耐性は持たない)
#    ※ regeneration は意図的に注入しない。毎tick再適用の amp3 は fabric(herobot MOD で間欠的に
#      しか乗らない)と paper(vanilla で全量持続)で正味回復速度が ~4 倍差になり、crystal-b の
#      hp_avg を偽装した(cryR51-54: pop後20t回復 F-B +0.3..0.55 vs P-B +1.5..1.7)。resistance
#      は回復でなく被ダメ軽減のみなので痕跡を残さない両エンジン公平な延命として維持。
effect give @a[tag=xlib_target] resistance 2 1 true
# 2) ラウンド自動開始(プレイヤーが部屋に入る操作の代替)。開始後 80t は再開始しない
scoreboard players remove #qlog_round qlog_t 1
execute if score .start start matches 0 if score #qlog_round qlog_t matches ..0 if entity @a[tag=xlib_target,scores={death=0}] if entity @a[tag=xlib_bot,scores={death=0}] run function qlog:start_round
