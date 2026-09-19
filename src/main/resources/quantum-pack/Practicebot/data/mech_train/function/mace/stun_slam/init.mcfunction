scoreboard players set .mode mode 303
title @a times 2 5 2
title @a title {"text":"Stun slam","color": "gray"}
execute as quantumbot at @s run function mech_train:crystal/common_init
item replace entity quantumbot armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
item replace entity quantumbot armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]
execute as quantumbot at @s run function mech_train:mace/stun_slam/loop