player quantumbot stop
player quantumbot hotbar 1
player quantumbot use continuous
scoreboard players set @s bowcharge 6
scoreboard players operation @s rail_timer = quantumbot rail_cd
execute if predicate quantum:random60 at @s facing entity @e[tag=rail,type=marker,limit=1,sort=nearest] feet positioned ^ ^ ^1 if function quantum:g1gc/block positioned ~ ~-1 ~ unless function quantum:g1gc/block positioned ~ ~1 ~ if function xaniclelib:check/intersect run setblock ~ ~ ~ oak_log
player quantumbot look at ~ ~ ~