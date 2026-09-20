scoreboard players set @s lava_cd 5
execute unless function quantum:miscellaneous/random run return 0

player @s look at ~ ~ ~
player @s hotbar 9
player @s swing once
setblock ~ ~ ~ lava
item replace entity @s hotbar.8 with bucket
scoreboard players set @s[scores={using_item=..2}] using_item 2