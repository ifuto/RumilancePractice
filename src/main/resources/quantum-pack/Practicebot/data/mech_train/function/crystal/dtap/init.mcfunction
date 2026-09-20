scoreboard players set .mode mode 201
title @a times 2 5 2
title @a title {"text":"D-tap","color": "light_purple"}
execute as @a[tag=xlib_bot] at @s run function mech_train:crystal/common_init
execute as @a[tag=xlib_bot] at @s run function mech_train:crystal/dtap/loop