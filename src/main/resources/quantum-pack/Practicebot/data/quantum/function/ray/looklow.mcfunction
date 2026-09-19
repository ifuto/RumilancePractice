kill @e[tag=lowpos,distance=0..,type=marker]
function quantum:ray/marklow
execute at @s at @e[tag=lowpos,distance=20..,type=marker] run player @s look at ~ ~4 ~
execute at @s at @e[tag=lowpos,distance=7..20,type=marker] run player @s look at ~ ~1.4 ~
execute at @s at @e[tag=lowpos,distance=3..7,type=marker] run player @s look at ~ ~0.8 ~
execute at @s at @e[tag=lowpos,distance=..3,type=marker] run player @s look at ~ ~-1.5 ~