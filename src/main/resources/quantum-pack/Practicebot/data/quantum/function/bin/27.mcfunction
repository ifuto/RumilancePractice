execute if entity @s[tag=used] run function quantum:crystal/passive/stop_use

execute if score @s hitcd matches 0 run tag @s remove cannot_anchor
execute if score @s hitcd matches 6 if entity @e[tag=loc,tag=usable,distance=0..,type=marker] run tag @s add cannot_anchor
function quantum:crystal/shield/axe
execute unless entity @e[tag=loc,tag=usable,distance=0..,type=marker] if score @s hit_decision_without_cd matches 0 at @p[tag=xlib_target,distance=0..] facing entity @s feet rotated ~ 0.0 run function quantum:g1gc/anchor_tick
execute if score @s hitcd matches 6 run kill @e[tag=xlib,distance=0..,type=marker]
execute at @p[tag=xlib_target] facing entity @s feet rotated ~ 0.0 unless score @s hitcd matches 6 run function quantum:g1gc/crystal_tick
function quantum:g1gc/botlogic