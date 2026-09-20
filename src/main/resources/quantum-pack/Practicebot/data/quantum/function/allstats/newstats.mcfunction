execute as @p[tag=xlib_target] at @s run function quantum:bin/30
scoreboard players set @s in_range 0
scoreboard players set @a OnGround 0
scoreboard players set @s can_see_target 0
scoreboard players set @a[predicate=quantum:onground] OnGround 1
execute store result score @s Pos1 run data get entity @s Pos[1]
scoreboard players operation @s Pos1_difference = @s Pos1
scoreboard players operation @s Pos1_difference -= @p[tag=xlib_target] Pos1
scoreboard players set @a pos 0
execute positioned ~-10 ~ ~-10 as @a[tag=xlib_target,dx=20,dy=100,dz=20,predicate=quantum:fall_distance60,predicate=!quantum:sf] run scoreboard players add @s pos 1
execute if entity @s[predicate=!quantum:sf,predicate=quantum:fall_distance15] run scoreboard players remove @s pos 1
scoreboard players operation @s pos *= .pos factor
execute if entity @a[tag=xlib_target,limit=1,distance=..3.2] if function xaniclelib:check/raycast4 run scoreboard players set @s can_see_target 1

execute if score .mode mode matches 2 run return 0
# Do not remove the fall distance data get because it's used for greater than or equal to etc.
execute store result score @s fall_distance run data get entity @s fall_distance 10
scoreboard players operation .old distance_to_target = @s distance_to_target
execute store result score @s horiz_distance_to_target run distance from @s to @p[tag=xlib_target] horizontal e 1
execute store result score @s vertical_distance_to_target run distance from @s to @p[tag=xlib_target] vertical e 1
execute positioned ~ ~1.6 ~ store result score @s distance_to_target run distance from ~ ~ ~ toHitbox @p[tag=xlib_target] e 1
execute store result score .old_bot distance_to_target run distance from @n[tag=old_pos_marker,tag=bot,distance=0..,type=marker] to @p[tag=xlib_target] e 1
execute store result score .old_target distance_to_target run distance from @n[tag=old_pos_marker,tag=target,distance=0..,type=marker] to @s e 1
scoreboard players set @s[scores={OnGround=1}] airborne 0
execute if function quantum:decisions/airborne2 run scoreboard players set @s airborne 1
execute if entity @a[tag=xlib_target,distance=..4] if function quantum:sword/combo/distance2 run scoreboard players set @s in_range 1
function quantum:allstats/pos
kill @e[tag=old_pos_marker,distance=0..,type=marker]
summon marker ~ ~ ~ {Tags:["old_pos_marker","bot"]}
execute at @p[tag=xlib_target] run summon marker ~ ~ ~ {Tags:["old_pos_marker","target"]}