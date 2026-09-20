execute if score .mode mode matches 3 run item replace entity @s weapon.offhand with totem_of_undying
item replace entity @s hotbar.4 with shield[enchantments={unbreaking:3},unbreakable={}]
player @s hotbar 5
player @s use continuous
execute if score @s pearlcd matches ..17 run function quantum:look
execute unless score .mode mode matches 100.. at @n[tag=slib,distance=0..,type=marker] run player @s look at ~ ~1 ~
execute unless score .mode mode matches 100.. if entity @e[tag=slib,distance=0..,type=marker] at @n[distance=..7,type=end_crystal] run player @s look at ~ ~ ~
tag @s add used
return 1