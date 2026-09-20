execute unless function quantum:miscellaneous/random run return 0

item replace entity @s hotbar.5 with netherite_axe[enchantments={efficiency:5,sharpness:5},unbreakable={}]
player @s stop
function quantum:look
player @s sprint
player @s move forward
player @s hotbar 6
player @s attack once
# execute if score .difficulty difficulty matches 3.. if function quantum:miscellaneous/random if function quantum:miscellaneous/random run player @s attack twice
scoreboard players set @s disablecd 2
scoreboard players set @s hitcd 0
scoreboard players set @p[tag=xlib_target] usingshield 0
execute unless score .mode mode matches 2 run scoreboard players set @s hitcd 12
execute unless score .mode mode matches 2 run scoreboard players set @s real_hitcd 12