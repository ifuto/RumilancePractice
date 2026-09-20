clear @s
item replace entity @s weapon.offhand with totem_of_undying

item replace entity @s armor.head with chainmail_helmet[minecraft:enchantments={"minecraft:protection":4},minecraft:unbreakable={}]
item replace entity @s armor.chest with chainmail_chestplate[minecraft:enchantments={"minecraft:protection":4},minecraft:unbreakable={}]
item replace entity @s armor.legs with minecraft:chainmail_leggings[minecraft:enchantments={"protection":4},minecraft:unbreakable={}]
item replace entity @s armor.feet with minecraft:chainmail_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:protection":4},minecraft:unbreakable={}]
execute if score .blast_prot npc matches 1 run item replace entity @s armor.legs with minecraft:chainmail_leggings[minecraft:enchantments={"blast_protection":4},minecraft:unbreakable={}]
execute if score .blast_prot npc matches 2 run item replace entity @s armor.legs with minecraft:chainmail_leggings[minecraft:enchantments={"minecraft:blast_protection":4},minecraft:unbreakable={}]
execute if score .blast_prot npc matches 2 run item replace entity @s armor.feet with minecraft:chainmail_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]

item replace entity @s hotbar.0 with shield[enchantments={unbreaking:3,mending:1}]

item replace entity @s hotbar.1 with wind_charge[max_stack_size=99] 99

# Hotbar.2 is empty

item replace entity @s hotbar.3 with stone_sword[minecraft:enchantments={"minecraft:sharpness":5},minecraft:unbreakable={}]

# Hotbar.4, and 5 are empty

item replace entity @s hotbar.6 with minecraft:ender_pearl[enchantments={knockback:1}] 16

scoreboard players set .botgear npc 4

# Hotbar.7 and 8 are empty
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1