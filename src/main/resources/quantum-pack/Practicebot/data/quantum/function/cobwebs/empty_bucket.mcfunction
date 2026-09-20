scoreboard players set @s water_cd 5
execute unless function quantum:miscellaneous/random run return 0

player @s look at ~ ~ ~
player @s hotbar 8
player @s use once
scoreboard players set @s[scores={using_item=..2}] using_item 2
scoreboard players set @s empty_water_cd 20