function quantum:look
player @s sprint
player @s move forward
player @s hotbar 4
player @s attack once
scoreboard players set @s hitcd 11
execute unless score .difficulty difficulty matches 0..1 run function quantum:crystal/hardcode/mech/spawncrystal