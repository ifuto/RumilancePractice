player @s stop
player @s hotbar 1
player @s use continuous
scoreboard players set @s bowcharge 6
# execute if predicate quantum:random60 at @s facing entity @n[tag=rail,type=marker] feet positioned ^ ^ ^1 if function quantum:g1gc/block positioned ~ ~-1 ~ unless function quantum:g1gc/block positioned ~ ~1 ~ if function xaniclelib:check/intersect run setblock ~ ~ ~ oak_log
execute align y run summon marker ~ ~ ~ {Tags:["cart_look"]}