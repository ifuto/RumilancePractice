execute unless score .temp temp matches ..0 run tp @s ~1 ~ ~
execute unless score .temp2 temp matches ..0 run tp @s ~ ~1 ~
execute unless score .temp temp matches ..0 run scoreboard players remove .temp temp 1
execute unless score .temp2 temp matches ..0 run scoreboard players remove .temp2 temp 1
execute if score .temp temp matches ..0 if score .temp2 temp matches ..0 run return 0
function quantum:bin/37