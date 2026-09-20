execute unless items entity @s hotbar.4 shield run particle trial_spawner_detection ~ ~ ~ .4 .4 .4 0 30 force @a
execute unless items entity @s hotbar.4 shield run item replace entity @s hotbar.4 with shield[enchantments={unbreaking:3,mending:1}]
player @s use continuous
player @s hotbar 5
execute if score .display_shield_dura npc matches 0 run return 1
execute store result score @s ShieldDurability run data get entity @s SelectedItem.components."minecraft:damage"
scoreboard players operation @s ShieldDurability *= -1 Math
scoreboard players add @s ShieldDurability 336