kill @e[tag=rotate.entity,type=marker]
execute at @s anchored eyes run summon marker ^ ^ ^ {Tags:["rotate.entity"]}

execute as @n[tag=rotate.entity,type=marker] at @s run tp @s ~ ~ ~ facing entity @p[tag=xlib_target]
execute store result score @s Rot0 run data get entity @s Rotation[0] 1
execute store result score .marker Rot0 run data get entity @n[tag=rotate.entity,type=marker] Rotation[0] 1
execute if score @s Rot0 matches ..-1 run scoreboard players operation @s Rot0 += .360 Math
execute if score .marker Rot0 matches ..-1 run scoreboard players operation .marker Rot0 += .360 Math
scoreboard players operation @s Rot0 -= .marker Rot0

# # If score dir is 1, add to the bot's angle (left)
execute if score @s Rot0 matches -180..180 if score @s Rot0 matches 0..180 run scoreboard players set @s strafe 0
execute if score @s Rot0 matches -180..180 if score @s Rot0 matches -180..0 run scoreboard players set @s strafe 1
execute unless score @s Rot0 matches -180..180 if score @s Rot0 matches ..-180 run scoreboard players set @s strafe 1
execute unless score @s Rot0 matches -180..180 if score @s Rot0 matches 180.. run scoreboard players set @s strafe 0
scoreboard players set @s[scores={Pos1_difference=..-1,strafe=0}] strafe 1
scoreboard players set @s[scores={Pos1_difference=..-1,strafe=1}] strafe 0
execute if score @s Pos1_difference matches ..-1 run scoreboard players set @s[scores={strafe=0}] strafe 1
execute if score @s Pos1_difference matches ..-1 run scoreboard players set @s[scores={strafe=1}] strafe 0
execute unless score @s Rot0 matches -30..30 unless score @s Rot0 matches 180..210 unless score @s Rot0 matches -210..-180 run function quantum:g1gc/strafe2