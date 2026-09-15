# parity:start_round — 通常のラウンド開始（マップの start2 + 開始スイッチ + 戦場への配置）。
function parity:resurface
function quantum:map/start2
effect give @a regeneration 1 255 true
effect give @a absorption 120 0 true
tp quantumbot -698.5 31 88.5 90 0
tp qbot2 -704.5 31 88.5 -90 0
scoreboard players set .start start 1
scoreboard players set pari_round parity_t 80
