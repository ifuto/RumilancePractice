kill @e[type=marker,tag=lowpos]
execute at @s at @n[tag=escape,type=marker] run function quantum:ray/marklow
execute at @s at @e[tag=lowpos,distance=20..] run player @s look at ~ ~4 ~
execute at @s at @e[tag=lowpos,distance=7..20] run player @s look at ~ ~1.4 ~
execute at @s at @e[tag=lowpos,distance=3..7] run player @s look at ~ ~0.8 ~
execute at @s at @e[tag=lowpos,distance=..3] run player @s look at ~ ~-1.5 ~