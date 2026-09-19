kill @n[tag=hardcode,type=marker]
# execute if score .anchors toggles matches 0 run return 0
# execute if score .difficulty difficulty matches ..1 run return 0
# execute at @s at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^1 ^ ^-1 run summon marker ~ ~ ~ {Tags:["obby_anchor","hardcode"]}
# execute at @s at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^-1 ^ ^-1 run summon marker ~ ~ ~ {Tags:["obby_anchor","hardcode"]}
# execute at @s if score .random random matches ..49 at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^1 ^ ^-1 run summon marker ~ ~ ~ {Tags:["anchor","hardcode"]}
# execute at @s if score .random random matches 50.. at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 positioned ^-1 ^ ^-1 run summon marker ~ ~ ~ {Tags:["anchor","hardcode"]}
# execute at @s run data modify entity @n[tag=anchor,type=marker] Pos[1] set from entity @p[tag=xlib_target] Pos[1]
# execute as @e[tag=hardcode,type=marker] at @s run function quantum:crystal/hardcode/mark/markerdown
# execute at @s at @n[tag=anchor,type=marker] unless function quantum:crystal/hardcode/mark/anchorcmd at @s run function quantum:crystal/hardcode/mark/init_fill2