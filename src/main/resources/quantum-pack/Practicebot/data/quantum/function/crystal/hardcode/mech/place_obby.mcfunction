execute if score .random random matches ..49 at @a[tag=xlib_bot,limit=1,sort=nearest] facing entity @p[tag=xlib_target] feet rotated ~ 0.0 positioned ^1.5 ^ ^3 as @n[tag=crystal,type=marker] run tag @s add chosen
execute if score .random random matches 50.. at @a[tag=xlib_bot,limit=1,sort=nearest] facing entity @p[tag=xlib_target] feet rotated ~ 0.0 positioned ^-1.5 ^ ^3 as @n[tag=crystal,type=marker] run tag @s add chosen
kill @e[tag=crystal,type=marker,tag=!chosen]
execute at @e[tag=crystal,type=marker] run setblock ~ ~ ~ obsidian