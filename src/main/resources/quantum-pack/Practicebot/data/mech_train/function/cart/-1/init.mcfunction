scoreboard players set .mode mode 603
title @a times 2 5 2
title @a title {"text":"-1","color": "gold"}
execute as quantumbot at @s run function mech_train:crystal/common_init
execute at @p[tag=xlib_target] run fill ~-1 ~ ~-1 ~1 ~ ~1 netherite_block replace #air
execute as @a[tag=xlib_target] at @s positioned over world_surface run tp @s ~ ~ ~ facing ~ ~-1 ~3
attribute quantumbot max_health base set 10
item replace entity @a armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
item replace entity @a armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
execute as quantumbot at @s run function mech_train:cart/-1/loop