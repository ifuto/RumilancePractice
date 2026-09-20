kill @e[tag=lowpos,distance=0..,type=marker]
kill @e[tag=target,distance=0..,type=marker]
function quantum:ray/cast2
execute if score @s pearlcd matches 1.. run return 0
execute at @s run kill @e[distance=..3,type=end_crystal]
player @s hotbar 7
player @s use once
scoreboard players set @s pearlcd 20