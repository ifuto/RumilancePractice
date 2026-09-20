# execute if score @s pearlcd matches 1.. if score @n[distance=0..,type=ender_pearl] id = @s id run return 0
execute if score .holding toggles matches 1 at @s as @p[tag=xlib_target] unless predicate quantum:distance5 run return 1
scoreboard players set @s crystal 0
execute if entity @p[tag=xlib_target,tag=checked] run return 0
execute if score .crystals toggles matches 1 if score @s[tag=!airplace] Pos1_difference matches ..0 run function xaniclelib:crystal/crystalcmd
execute if score .anchors toggles matches 1 if score @s[tag=!cannot_anchor,distance=0..] hit_decision_without_cd matches 0 unless entity @e[tag=loc,tag=usable,distance=0..,type=marker] unless score @s hitcd matches 7 if entity @p[tag=xlib_target,predicate=!quantum:fall_distance15] run function xaniclelib:anchor/anchorcmd
tag @p[tag=xlib_target] add checked
tag @s remove airplace