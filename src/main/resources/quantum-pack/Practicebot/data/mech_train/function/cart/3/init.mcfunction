scoreboard players set .mode mode 607
title @a times 2 5 2
title @a title {"text":"3","color": "gold"}
execute as quantumbot at @s run function mech_train:crystal/common_init
execute at @p[tag=xlib_target] run fill ~-1 ~ ~-1 ~1 ~2 ~1 netherite_block replace air
execute at @p[tag=xlib_target] run fill ~-2 ~3 ~-2 ~2 ~3 ~2 netherite_block hollow
execute at @p[tag=xlib_target] positioned ~ ~3 ~1 run fill ~-1 ~ ~-1 ~1 ~ ~1 air
execute as @a[tag=xlib_target] at @s run tp @s ~ ~ ~3.5
attribute quantumbot max_health base set 10
item replace entity @a armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
item replace entity @a armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
execute as quantumbot at @s run function mech_train:cart/3/loop