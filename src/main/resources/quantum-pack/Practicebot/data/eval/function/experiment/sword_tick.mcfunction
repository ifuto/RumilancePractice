execute if score .healing toggles matches 0 unless score .mode mode matches 4..5 run return run scoreboard players set @s state 1
function eval:stats/hp
execute unless score .mode mode matches 4..5 run function eval:stats/saturation
function eval:stats/sword/hit
function eval:stats/sword/factor
function eval:debug
function eval:sword_eval
function eval:experiment/biased