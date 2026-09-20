function mech_train:mace/common_actions
function quantum:look
player @s hotbar 4
execute if score @s pops matches 1.. run scoreboard players set @s[scores={resetcd=0}] resetcd 2
execute if entity @e[type=#arrows] run scoreboard players set @s[scores={resetcd=0}] reset_trigger 1
execute unless entity @e[type=#arrows,nbt={inGround:0b}] if score @s reset_trigger matches 1 run scoreboard players set @s[scores={resetcd=0}] resetcd 2
execute if score @s resetcd matches 1 run function mech_train:cart/1/loop