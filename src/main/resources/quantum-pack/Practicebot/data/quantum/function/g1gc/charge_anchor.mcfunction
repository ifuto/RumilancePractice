kill @e[tag=glowstone,type=marker,distance=..5]
setblock ~ ~ ~ respawn_anchor[charges=1]
execute as @p[tag=xlib_target] at @s run playsound minecraft:block.respawn_anchor.charge block @s ~ ~ ~
player @s look at ~ ~ ~
player @s hotbar 9
player @s swing once

scoreboard players operation @s explosion_timer = @s charge_cd
scoreboard players operation @s anchor_timer = @s charge_cd
scoreboard players operation @s crystal_timer = @s charge_cd

tag @n[tag=anchor_2,type=marker] add used

# Safe anchor
execute align y positioned ~-5 ~ ~-5 if entity @s[dx=9,dy=0,dz=9] positioned ~5 ~ ~5 facing entity @s feet positioned ^ ^ ^1 if function quantum:g1gc/block positioned ~ ~-1 ~ unless function quantum:g1gc/block positioned ~ ~1 ~ if function xaniclelib:check/intersect run summon marker ~ ~ ~ {Tags:["glowstone"]}
# execute facing entity @s feet positioned ^ ^ ^1 if function xaniclelib:check/raycast3 if function quantum:g1gc/block positioned ~ ~-1 ~ unless function quantum:g1gc/block positioned ~ ~1 ~ if function xaniclelib:check/intersect run summon marker ~ ~ ~ {Tags:["glowstone"]}