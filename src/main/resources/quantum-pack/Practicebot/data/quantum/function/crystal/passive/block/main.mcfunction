execute unless score @s pearlcd matches ..18 run return 0
kill @e[tag=block,type=marker]
execute at @s at @e[tag=slib,type=marker,tag=usable] positioned ~ ~1 ~ facing entity @s feet rotated ~ 0.0 at @s positioned ^ ^ ^-1 if function quantum:g1gc/block positioned ~ ~-1 ~ unless function quantum:g1gc/block positioned ~ ~1 ~ if function xaniclelib:check/intersect2 run summon marker ~ ~-1 ~ {Tags:["block","defense"]}
execute unless entity @e[tag=block,type=marker] at @s facing entity @p[tag=xlib_target] eyes rotated ~ 0.0 positioned ^ ^1 ^1 if function quantum:crystal/passive/block/cmd run summon marker ~ ~-1 ~ {Tags:["block","defense"]}
execute unless entity @e[tag=block,type=marker] at @s facing entity @p[tag=xlib_target] eyes rotated ~ 0.0 positioned ^ ^ ^1 if function quantum:crystal/passive/block/cmd run summon marker ~ ~-1 ~ {Tags:["block","defense"]}
execute unless entity @e[tag=block,type=marker] at @p[tag=xlib_target] run player @s look at ~ ~1 ~
execute unless entity @e[tag=block,type=marker] run player @s move backward
execute at @n[tag=block,type=marker] positioned ~ ~ ~ unless score @s obby_timer matches 1.. run function quantum:g1gc/placeobsidian