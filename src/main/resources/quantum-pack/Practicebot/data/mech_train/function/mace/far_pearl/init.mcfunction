scoreboard players set .mode mode 302
title @a times 2 5 2
title @a title {"text":"Far Pearl","color": "gray"}
execute as quantumbot at @s run function mech_train:crystal/common_init
item replace entity @a armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
item replace entity @a armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
execute as quantumbot at @s run function mech_train:mace/far_pearl/loop
attribute quantumbot gravity base set 0
attribute quantumbot max_health base set 2