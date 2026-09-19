execute if score .wind npc matches 1 at @s[scores={windcd=..0,OnGround=1}] if block ~ ~ ~ #xaniclelib:nonsolid if score @p[tag=xlib_target] OnGround matches 1 run function quantum:mace/wind
execute if score .move_forward npc matches 1 at @s unless entity @a[tag=xlib_target,distance=..1.5] run scoreboard players set .move temp 1

execute if score .far_pearl npc matches 1 at @s[scores={pearlcd=..0}] at @p[tag=xlib_target,distance=5..,predicate=quantum:vmotion2_3] if score @s Pos1 < @p[tag=xlib_target] Pos1 if function quantum:miscellaneous/random if function quantum:decisions/airborne2 run function quantum:mace/far_pearl

execute at @s[tag=wind_pearl] run function quantum:mace/wind_pearl
execute if score .wind_pearl npc matches 1 at @s[scores={pearlcd=..0,windcd=..0,wind_pearl_cd=..0,Pos1_difference=..-5},predicate=!quantum:vmotion_m5] if score .far_pearl npc matches 1 positioned ~-7 ~ ~-7 at @p[tag=xlib_target,dx=13,dz=13,dy=30,predicate=!quantum:vmotion_m1] positioned ~7 ~ ~7 run function quantum:mace/wind_pearl_main
execute if score .wind_pearl npc matches 1 at @s[scores={pearlcd=..0,windcd=..0,wind_pearl_cd=..0,Pos1_difference=..-5},predicate=!quantum:vmotion_m5] if score .far_pearl npc matches 0 at @p[tag=xlib_target,distance=5..20,predicate=!quantum:vmotion0_1] run function quantum:mace/wind_pearl_main

execute if score .pflash npc matches 1 if score @s OnGround matches 0 if score @p[tag=xlib_target] hitcd matches 1.. run function quantum:crystal/pearl_spam

execute if score .simfire temp matches 1 if score @s crystal_timer matches 0 run function npc:actions/simfire

execute if score .auto_wind npc matches 1 as @a[tag=xlib_target,scores={OnGround=1,anchor_timer=0}] at @s run summon armor_stand ~ ~ ~ {NoGravity:1b,Silent:1b,Invulnerable:1b,Marker:1b,Invisible:1b,equipment:{feet:{id:"minecraft:brick",count:1,components:{"minecraft:enchantments":{"quantum:large_wind_burst":1}}}}}
execute if score .auto_wind npc matches 1 as @a[tag=xlib_target,scores={OnGround=1}] at @s run scoreboard players set @s anchor_timer 4