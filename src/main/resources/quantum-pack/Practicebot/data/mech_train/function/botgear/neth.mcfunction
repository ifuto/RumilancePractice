clear @s
item replace entity @s weapon.offhand with totem_of_undying

item replace entity @s armor.head with minecraft:netherite_helmet[minecraft:enchantments={"minecraft:protection":4},minecraft:unbreakable={}]
item replace entity @s armor.chest with minecraft:netherite_chestplate[minecraft:enchantments={"minecraft:protection":4},minecraft:unbreakable={}]
item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:blast_protection":4,"protection":4},minecraft:unbreakable={}]
execute if score .mode mode matches 300..399 run item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"protection":4},minecraft:unbreakable={}]
execute if score .mode mode matches 600..699 run item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"blast_protection":4},minecraft:unbreakable={}]
item replace entity @s armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:protection":4},minecraft:unbreakable={}]
execute if score .dbp toggles matches 1 if score .mode mode matches 200..299 if score .old_kb toggles matches 0 run item replace entity @s armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4},minecraft:unbreakable={}]

item replace entity @s hotbar.0 with totem_of_undying[enchantments={knockback:1}]
execute if score .mode mode matches 600..699 run item replace entity @s hotbar.0 with bow[minecraft:enchantments={power:5,punch:1,flame:1,infinity:1}]
execute if score .mode mode matches 600..699 if score .difficulty difficulty matches 6 run item replace entity @s hotbar.0 with bow[minecraft:enchantments={power:255,punch:10,flame:10,multishot:10,piercing:10,riptide:5,infinity:1,channeling:1,"infinity":1}]

item replace entity @s hotbar.1 with minecraft:obsidian[enchantments={knockback:1}] 64
execute if score .mode mode matches 400..599 run item replace entity @s hotbar.1 with splash_potion[max_stack_size=99,potion_contents={potion:"minecraft:strong_healing"}] 99
execute if score .mode mode matches 300..399 run item replace entity @s hotbar.1 with wind_charge[max_stack_size=99] 99
execute if score .mode mode matches 600..699 run item replace entity @s hotbar.1 with powered_rail[max_stack_size=99] 99

execute if score .cobweb toggles matches 1 run item replace entity @s hotbar.2 with minecraft:cobweb[enchantments={knockback:1}] 64
execute if score .mode mode matches 300..399 run item replace entity @s hotbar.2 with mace[enchantments={density:5,wind_burst:1},unbreakable={}] 1
execute if score .mode mode matches 200..299 run item replace entity @s hotbar.2 with minecraft:end_crystal[enchantments={knockback:1}] 64
execute if score .mode mode matches 600..699 run item replace entity @s hotbar.2 with tnt_minecart[max_stack_size=99] 99

item replace entity @s hotbar.3 with minecraft:netherite_sword[minecraft:enchantments={"minecraft:sharpness":5},minecraft:unbreakable={}]
execute if score .shield toggles matches 1 unless score .mode mode matches 300..599 run item replace entity @s hotbar.3 with minecraft:netherite_sword[minecraft:enchantments={"minecraft:sharpness":5,"knockback":1},minecraft:unbreakable={}]
execute if score .mode mode matches 200..299 run item replace entity @s hotbar.3 with netherite_sword[enchantments={sweeping_edge:3,sharpness:5,knockback:1},unbreakable={}]
execute if score .mode mode matches 600..699 run item replace entity @s hotbar.3 with netherite_sword[enchantments={sweeping_edge:3,sharpness:5,knockback:1},unbreakable={}]

item replace entity @s hotbar.4 with minecraft:golden_apple 64
execute if score .mode mode matches 200..299 if score .shield toggles matches 1 run item replace entity @s hotbar.4 with minecraft:shield[unbreakable={},enchantment_glint_override=true]

item replace entity @s hotbar.5 with minecraft:netherite_axe[minecraft:enchantments={"sharpness":5},minecraft:unbreakable={}]

item replace entity @s hotbar.6 with minecraft:ender_pearl[enchantments={knockback:1}] 16

item replace entity @s hotbar.7 with minecraft:respawn_anchor[enchantments={knockback:1}] 64
execute unless score .mode mode matches 200..299 run item replace entity @s hotbar.7 with minecraft:water_bucket[enchantments={knockback:1}] 1

item replace entity @s hotbar.8 with minecraft:glowstone[enchantments={knockback:1}] 64
execute unless score .mode mode matches 200..299 run item replace entity @s hotbar.8 with minecraft:lava_bucket[enchantments={knockback:1}] 1
execute if score .mode mode matches 300..399 run item replace entity @s hotbar.8 with minecraft:mace[enchantments={breach:4},unbreakable={}]

execute unless score .mode mode matches 200..299 run item replace entity @s inventory.0 with tipped_arrow[potion_contents={potion:"minecraft:strong_harming"},max_stack_size=99] 99
execute unless score .mode mode matches 200..299 run item replace entity @s inventory.1 with tipped_arrow[potion_contents={potion:"minecraft:strong_harming"},max_stack_size=99] 99
execute if score .mode mode matches 200..299 run item replace entity @s inventory.0 with tipped_arrow[potion_contents={potion:"minecraft:long_slow_falling"},max_stack_size=99] 99
execute if score .mode mode matches 200..299 run item replace entity @s inventory.1 with tipped_arrow[potion_contents={potion:"minecraft:long_slow_falling"},max_stack_size=99] 99

execute unless score .mode mode matches 200..299 unless score .mode mode matches 400..599 if score .shield toggles matches 1 run item replace entity @s weapon.offhand with shield[unbreakable={},enchantments={unbreaking:1}]

scoreboard players set .gear toggles 1

data modify entity @n[type=item_display,name="Netherite gear"] CustomName set value {"text":"Netherite gear","color": "green"}
data modify entity @n[type=item_display,name="Diamond gear"] CustomName set value {"text":"Diamond gear","color": "red"}
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1