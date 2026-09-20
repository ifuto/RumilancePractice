execute if items entity @s hotbar.4 air run particle trial_spawner_detection ~ ~ ~ .4 .4 .4 0 30 force @a
execute unless items entity @s hotbar.4 shield if score .mode mode matches 2 run item replace entity @s hotbar.4 with shield[enchantments={unbreaking:3}]
execute if score .mode mode matches 2 run player @s hotbar 5
player @s use continuous