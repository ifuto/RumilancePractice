scoreboard players set @s cobweb_cd 15
execute unless function quantum:miscellaneous/random run return 0
item replace entity @s hotbar.2 with cobweb 64
player @s hotbar 3
player @s look at ~ ~ ~
player @s swing once
setblock ~ ~ ~ cobweb
scoreboard players set @s[scores={using_item=..2}] using_item 2