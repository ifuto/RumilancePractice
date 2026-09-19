# execute if score @s state matches 3 run return run function quantum:sword/tick
function quantum:decisions/tick
function eval:experiment/sword_tick
execute if score @s state matches 1..2 run tag @s remove pot
execute if score @s pot_count matches 2.. as @e[type=splash_potion] on origin if entity @s[tag=xlib_bot] run scoreboard players add @s pot_cd 1
execute if score @s pot_cd matches 0 if score @s pot_count matches 2.. run scoreboard players set @s pot_count 0
execute if score @s state matches 3 run tag @s add pot
execute if entity @s[tag=pot,scores={gap_timer=0}] if score @s pot_cd matches ..0 run function quantum:pot/pot
execute if score @s gap_timer matches 1.. run player @s jump once

function quantum:sword/tick
execute if entity @s[tag=pot] facing entity @p[tag=xlib_target] eyes rotated ~ 0.0 run player @s look at ^ ^ ^-1.3