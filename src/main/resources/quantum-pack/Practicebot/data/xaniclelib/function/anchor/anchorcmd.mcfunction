execute at @s if entity @e[distance=..9,type=ender_pearl] if score @s pearl_count matches 1.. run return 0
tag @s add marked_anchor
execute unless entity @e[tag=loc1,tag=!anchor_1,tag=usable,distance=0..,type=marker] run fill ~-2 ~-2 ~-2 ~2 ~1 ~2 command_block{auto:1b,Command:"function xaniclelib:anchor/anchortest"} replace #xaniclelib:nonsolidnosnow
execute if entity @s[tag=airplace,distance=0..] at @n[tag=anchor_3,tag=usable,distance=0..,type=marker] run setblock ~ ~ ~ command_block{auto:1b,Command:"function xaniclelib:anchor/anchortest"}

execute if score .terrain terrain matches 5 unless entity @e[tag=loc1,tag=!anchor_1,tag=usable,distance=0..,type=marker] run fill ~-2 ~-2 ~-2 ~2 ~1 ~2 command_block{auto:1b,Command:"function xaniclelib:anchor/anchortest_snow"} replace snow

fill ~-3 ~-2 ~-3 ~3 ~2 ~3 command_block{auto:1b,Command:"function xaniclelib:anchor/0"} replace respawn_anchor[charges=0]

fill ~-3 ~-2 ~-3 ~3 ~2 ~3 command_block{auto:1b,Command:"function xaniclelib:anchor/1"} replace respawn_anchor[charges=1]
# fill ~-3 ~-2 ~-3 ~3 ~2 ~3 command_block{auto:1b,Command:"function xaniclelib:anchor/2"} replace respawn_anchor[charges=2]