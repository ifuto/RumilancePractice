# 通常のラウンド開始(map/start + start3 の必須部分のみ)
function quantum:map/start2
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
scoreboard players set .start start 1
scoreboard players set #qlog_round qlog_t 80
