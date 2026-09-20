scoreboard players set .mode mode 202
title @a times 2 5 2
title @a title {"text":"Ledge Dash","color": "light_purple"}
scoreboard players set @a[tag=xlib_bot] obby_cd 5
execute as @a[tag=xlib_bot] at @s run function mech_train:crystal/common_init
attribute @a[tag=xlib_bot] max_health base set 20
execute as @a[tag=xlib_bot] at @s run function mech_train:crystal/ledge/loop
scoreboard players add @a[tag=xlib_bot] pearlcd2 100