clear @s
item replace entity @s weapon.offhand with totem_of_undying

item replace entity @s armor.head with minecraft:netherite_helmet[minecraft:enchantments={"minecraft:protection":4,"unbreaking":3},minecraft:unbreakable={}]
item replace entity @s armor.chest with minecraft:netherite_chestplate[minecraft:enchantments={"minecraft:protection":4,"unbreaking":3},minecraft:unbreakable={}]
item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:blast_protection":4,"unbreaking":3,"minecraft:protection":4},minecraft:unbreakable={}]
execute if score .mode mode matches 2 run item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:blast_protection":4,"unbreaking":3},minecraft:unbreakable={}]
execute if score .mode mode matches 3 run item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:protection":4,"unbreaking":3},minecraft:unbreakable={}]
execute if score .mode mode matches 6 run item replace entity @s armor.legs with minecraft:netherite_leggings[minecraft:enchantments={"minecraft:blast_protection":4,"unbreaking":3},minecraft:unbreakable={}]
item replace entity @s armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:protection":4,"unbreaking":3},minecraft:unbreakable={}]
execute if score .dbp toggles matches 1 if score .mode mode matches 2 if score .old_kb toggles matches 0 run item replace entity @s armor.feet with minecraft:netherite_boots[minecraft:enchantments={"minecraft:feather_falling":4,"minecraft:blast_protection":4,"unbreaking":3},minecraft:unbreakable={}]

item replace entity @s hotbar.0 with totem_of_undying[enchantments={knockback:1}]
execute if score .mode mode matches 3 if score .spear toggles matches 1 run item replace entity @s hotbar.0 with netherite_spear[enchantments={lunge:3,unbreaking:3,sharpness:5},unbreakable={}]
execute if score .mode mode matches 3 if score .elytra toggles matches 1 run item replace entity @s hotbar.0 with elytra[unbreakable={}]
execute if score .mode mode matches 6 run item replace entity @s hotbar.0 with bow[minecraft:enchantments={power:5,punch:1,flame:1,infinity:1}]
execute if score .mode mode matches 6 if score .difficulty difficulty matches 6 run item replace entity @s hotbar.0 with bow[minecraft:enchantments={power:255,punch:10,flame:10,multishot:10,piercing:10,riptide:5,infinity:1,channeling:1,"infinity":1}]

item replace entity @s hotbar.1 with minecraft:obsidian[enchantments={knockback:1}] 64
execute if score .mode mode matches 4..5 run item replace entity @s hotbar.1 with splash_potion[max_stack_size=99,potion_contents={potion:"minecraft:strong_healing"}] 99
execute if score .mode mode matches 3 run item replace entity @s hotbar.1 with wind_charge[max_stack_size=99] 99
execute if score .mode mode matches 6 run item replace entity @s hotbar.1 with powered_rail[max_stack_size=99] 99

execute if score .cobweb toggles matches 1 run item replace entity @s hotbar.2 with minecraft:cobweb[enchantments={knockback:1}] 64
execute if score .mode mode matches 3 run item replace entity @s hotbar.2 with mace[enchantments={density:5,wind_burst:1},unbreakable={}] 1
execute if score .mode mode matches 2 run item replace entity @s hotbar.2 with minecraft:end_crystal[enchantments={knockback:1}] 64
execute if score .mode mode matches 6 run item replace entity @s hotbar.2 with tnt_minecart[max_stack_size=99] 99

item replace entity @s hotbar.3 with minecraft:netherite_sword[minecraft:enchantments={"minecraft:sharpness":5},minecraft:unbreakable={}]
execute if score .shield toggles matches 1 unless score .mode mode matches 3..5 run item replace entity @s hotbar.3 with minecraft:netherite_sword[minecraft:enchantments={"minecraft:sharpness":5,"knockback":1},minecraft:unbreakable={}]
execute if score .mode mode matches 2 run item replace entity @s hotbar.3 with netherite_sword[enchantments={sweeping_edge:3,sharpness:5,knockback:1},unbreakable={}]
execute if score .mode mode matches 6 run item replace entity @s hotbar.3 with netherite_sword[enchantments={sweeping_edge:3,sharpness:5,knockback:1},unbreakable={}]

item replace entity @s hotbar.4 with minecraft:golden_apple 64
execute if score .mode mode matches 2 if score .shield toggles matches 1 run item replace entity @s hotbar.4 with minecraft:shield[unbreakable={},enchantment_glint_override=true]

item replace entity @s hotbar.5 with minecraft:netherite_axe[minecraft:enchantments={"sharpness":5},minecraft:unbreakable={}]

item replace entity @s hotbar.6 with minecraft:ender_pearl[enchantments={knockback:1}] 16

item replace entity @s hotbar.7 with minecraft:respawn_anchor[enchantments={knockback:1}] 64
execute unless score .mode mode matches 2 run item replace entity @s hotbar.7 with minecraft:water_bucket[enchantments={knockback:1}] 1

item replace entity @s hotbar.8 with minecraft:glowstone[enchantments={knockback:1}] 64
execute unless score .mode mode matches 2 run item replace entity @s hotbar.8 with minecraft:lava_bucket[enchantments={knockback:1}] 1
execute if score .mode mode matches 3 run item replace entity @s hotbar.8 with minecraft:mace[enchantments={breach:4},unbreakable={}]

item replace entity @s inventory.2 with water_bucket
item replace entity @s inventory.3 with water_bucket
item replace entity @s inventory.4 with water_bucket
item replace entity @s inventory.7 with lava_bucket
execute unless score .mode mode matches 2 run item replace entity @s inventory.0 with tipped_arrow[potion_contents={potion:"minecraft:strong_harming"},max_stack_size=99] 99
execute unless score .mode mode matches 2 run item replace entity @s inventory.1 with tipped_arrow[potion_contents={potion:"minecraft:strong_harming"},max_stack_size=99] 99
execute if score .mode mode matches 2 run item replace entity @s inventory.0 with tipped_arrow[potion_contents={potion:"minecraft:long_slow_falling"},max_stack_size=99] 99
execute if score .mode mode matches 2 run item replace entity @s inventory.1 with tipped_arrow[potion_contents={potion:"minecraft:long_slow_falling"},max_stack_size=99] 99

execute unless score .mode mode matches 2 unless score .mode mode matches 4..5 if score .shield toggles matches 1 if score .healing toggles matches 0 run item replace entity @s weapon.offhand with shield[death_protection={death_effects:[{type:"clear_all_effects"},{type:"apply_effects",effects:[{id:"minecraft:regeneration",amplifier:1,duration:900},{id:"minecraft:fire_resistance",amplifier:0,duration:800},{id:"minecraft:absorption",amplifier:1,duration:100}]}]},enchantments={unbreaking:3,mending:1},unbreakable={}]
execute unless score .mode mode matches 2 unless score .mode mode matches 4..5 if score .shield toggles matches 1 if score .healing toggles matches 1 run item replace entity @s weapon.offhand with shield[enchantments={unbreaking:3,mending:1},unbreakable={}]

scoreboard players set .gear toggles 1

data modify entity @n[type=item_display,name="Netherite gear"] CustomName set value {"text":"Netherite gear","color": "#00ff00"}
data modify entity @n[type=item_display,name="Diamond gear"] CustomName set value {"text":"Diamond gear","color": "#ff0000"}
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1