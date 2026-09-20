kill @e[type=marker,tag=highpos]
function quantum:ray/markhigh
execute at @s at @e[tag=highpos,distance=20..] run player @s look at ~ ~5 ~
execute at @s at @e[tag=highpos,distance=7..20] run player @s look at ~ ~2.4 ~
execute at @s at @e[tag=highpos,distance=5..7] run player @s look at ~ ~1.8 ~
execute at @s at @e[tag=highpos,distance=..5] run player @s look at ~ ~1 ~