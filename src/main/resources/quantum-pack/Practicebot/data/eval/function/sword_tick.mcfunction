execute if score .healing toggles matches 0 unless score .mode mode matches 4..5 run return run scoreboard players set @s state 1
execute unless score .mode mode matches 4..5 run function eval:stats/saturation
function eval:stats/sword/hit
function eval:stats/sword/factor
function eval:stats/hp
function eval:debug
function eval:sword_eval
execute if score @s gap_timer matches 1.. run scoreboard players remove @s eval 2500
execute unless score @p[tag=xlib_target] pos matches 1 if score @s[scores={state=1}] Health matches 12.. if entity @a[tag=xlib_target,distance=..7] run return run scoreboard players set @s state 1
execute if score .mode mode matches 4..5 if score @s Health matches 19.. run return run scoreboard players set @s state 1
function eval:biased
# function eval:experiment/healing