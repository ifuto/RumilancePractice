execute if entity @p[tag=xlib_target,tag=checked] run return 0
execute if score .crystals toggles matches 1 unless score @s Pos1 > @p[tag=xlib_target] Pos1 run function xaniclelib:crystal/crystalcmd
tag @p[tag=xlib_target] add checked