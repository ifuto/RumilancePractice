function mech_train:crystal/common_actions
# execute if predicate quantum:random50 run scoreboard players add @s[scores={pearlcd2=1..}] pearlcd2 1
execute at @s if score @s pearlcd2 matches 20.. run scoreboard players set @s pearlcd2 20
execute if score @s pearlcd2 matches 1 run function quantum:g1gc/pearl
execute as @e[type=ender_pearl] on origin run scoreboard players set @s[tag=xlib_bot] anchor_timer 7
execute if score @s[scores={anchor_timer=0,reset_trigger=0}] pearlcd2 matches 0 run scoreboard players set @s reset_trigger 1
execute if score .trigger2 reset_trigger matches 0 run scoreboard players set @s[scores={reset_trigger=1,hitcd=0}] hitcd 6
execute if score @s hitcd matches 1.. run scoreboard players set .trigger2 reset_trigger 1
scoreboard players set @s[scores={hitcd=1,reset_trigger=1},nbt={OnGround:1b}] resetcd 1
execute if score @s pearlcd2 matches 0 run function mech_train:escape/main
execute if score @s[scores={resetcd=0}] pops matches 1.. run scoreboard players set @s resetcd 6
execute unless entity @a[tag=xlib_target,distance=..10] run scoreboard players set @s[scores={resetcd=0,pearlcd2=0,reset_trigger=1}] resetcd 2
function quantum:crystal/shield/tick
execute if score @s resetcd matches 1 run function mech_train:crystal/ledge/loop