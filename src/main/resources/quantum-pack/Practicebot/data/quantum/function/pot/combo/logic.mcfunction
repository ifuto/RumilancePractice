execute unless entity @s[tag=pot] run function quantum:look
player @s move
player @s sprint
item replace entity @s weapon.offhand with totem_of_undying

execute unless entity @s[tag=pot] run player @s hotbar 4
player @s move forward