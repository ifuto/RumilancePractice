scoreboard players set @s xlib 0
execute if block ~ ~-2 ~ #quantum:stone run function quantum:bin/16
execute unless block ~ ~-2 ~ #quantum:stone run function quantum:bin/17
execute if score @p[tag=xlib_target] totem_timer matches 1.. run scoreboard players add @s xlib 5
scoreboard players operation @s xlib *= .xlib factor