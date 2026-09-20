execute if score @s hitcd matches 7 run return run fill ~-2 ~ ~-2 ~2 ~ ~2 command_block{auto:1b,Command:"function xaniclelib:crystal/crystaltest"} replace #xaniclelib:nonsolid

execute unless block ~ ~-.1 ~ #xaniclelib:nonsolid if score @s Pos1_difference matches 0 run return 0
execute if score @s hitcd matches 1.. run fill ~-2 ~-0.7 ~-2 ~2 ~-4 ~2 command_block{auto:1b,Command:"function xaniclelib:crystal/crystaltest"} replace #xaniclelib:nonsolid
execute unless score @s hitcd matches 1.. run fill ~-2 ~-1.01 ~-2 ~2 ~-4 ~2 command_block{auto:1b,Command:"function xaniclelib:crystal/crystaltest"} replace #xaniclelib:nonsolid

# execute if score @s hitcd matches 1.. run fill ~-2 ~-0.7 ~-2 ~2 ~-4 ~2 command_block{auto:1b,Command:"function xaniclelib:crystal/crystaltest"} replace command_block{auto:1b,Command:"function xaniclelib:anchor/anchortest"}
# execute unless score @s hitcd matches 1.. run fill ~-2 ~-1 ~-2 ~2 ~-4 ~2 command_block{auto:1b,Command:"function xaniclelib:crystal/crystaltest"} replace command_block{auto:1b,Command:"function xaniclelib:anchor/anchortest"}

execute at @s run fill ~-5 ~-1 ~-5 ~5 ~-2 ~5 air replace command_block{auto:1b,Command:"function xaniclelib:crystal/crystaltest"}