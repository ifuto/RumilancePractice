function quantum:look
player @s stop
player @s hotbar 4
player @s attack once
kill @e[type=end_crystal,distance=..1.5]

scoreboard players set @s hitcd 7
scoreboard players set @s real_hitcd 12
scoreboard players set @s crystal2 0