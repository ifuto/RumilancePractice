scoreboard players set .mode mode 202
title @a times 2 5 2
title @a title {"text":"Ledge Dash","color": "light_purple"}
scoreboard players set quantumbot obby_cd 5
execute as quantumbot at @s run function mech_train:crystal/common_init
attribute quantumbot max_health base set 20
execute as quantumbot at @s run function mech_train:crystal/ledge/loop
scoreboard players add quantumbot pearlcd2 100