kill @e[tag=highpos,distance=0..,type=marker]
execute if score @s[scores={OnGround=1}] Pos1_difference matches ..-2 at @s if block ~ ~2 ~ #xaniclelib:nonsolid run player @s jump once
execute if score @s[scores={OnGround=1}] Pos1_difference matches ..-2 at @s if block ~ ~2 ~ #xaniclelib:nonsolid run return run scoreboard players set @s pearlcd 4
function quantum:ray/markhigh
execute at @s at @e[tag=highpos,distance=20..,type=marker] run player @s look at ~ ~5 ~
execute at @s at @e[tag=highpos,distance=7..20,type=marker] run player @s look at ~ ~2.4 ~
execute at @s at @e[tag=highpos,distance=5..7,type=marker] run player @s look at ~ ~1.8 ~
execute at @s at @e[tag=highpos,distance=..5,type=marker] run player @s look at ~ ~1 ~