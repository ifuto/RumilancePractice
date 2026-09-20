kill @e[type=marker,tag=hardcode]
execute if score .random random matches ..49 at @s facing entity @p[tag=xlib_target] feet rotated ~ 0.0 positioned ^1.5 ^ ^3 run summon marker ~ ~ ~ {Tags:["crystal","hardcode"]}
execute if score .random random matches 50.. at @s facing entity @p[tag=xlib_target] feet rotated ~ 0.0 positioned ^-1.5 ^ ^3 run summon marker ~ ~ ~ {Tags:["crystal","hardcode"]}
execute at @s run data modify entity @n[tag=crystal,type=marker] Pos[1] set from entity @p[tag=xlib_target] Pos[1]
execute as @e[tag=crystal,type=marker] at @s run function quantum:crystal/hardcode/mark/markerdown
execute at @n[tag=crystal,type=marker] unless function quantum:crystal/hardcode/mark/crystalcmd at @s run function quantum:crystal/hardcode/mark/init_fill
schedule function quantum:crystal/hardcode/mech/place_obby 2t
schedule function quantum:crystal/hardcode/mech/marker 7t