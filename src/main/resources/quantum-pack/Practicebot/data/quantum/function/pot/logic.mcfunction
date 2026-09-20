execute unless entity @s[tag=pot] run function quantum:look
player @s move
player @s sprint
player @s move forward
item replace entity @s weapon.offhand with totem_of_undying
execute unless score @s bowcharge matches 1.. unless entity @s[tag=pot] if score @s windcd matches ..18 run player @s hotbar 4