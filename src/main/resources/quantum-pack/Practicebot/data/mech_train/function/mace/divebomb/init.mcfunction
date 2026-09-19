scoreboard players set .mode mode 304
title @a times 2 5 2
title @a title {"text":"Divebomb","color": "gray"}
execute as @a[tag=xlib_bot] at @s run function mech_train:crystal/common_init
item replace entity @a[tag=xlib_bot] armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
item replace entity @a[tag=xlib_bot] armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
execute as @a[tag=xlib_bot] at @s run function mech_train:mace/divebomb/loop