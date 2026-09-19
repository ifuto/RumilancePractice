execute unless score @s crossbow_timer matches 1.. run scoreboard players set @s crossbow_timer 13
execute unless items entity @s hotbar.5 crossbow run item replace entity @s hotbar.5 with crossbow[enchantments={quick_charge:3,piercing:4},unbreakable={}]
player @s hotbar 6
player @s use continuous