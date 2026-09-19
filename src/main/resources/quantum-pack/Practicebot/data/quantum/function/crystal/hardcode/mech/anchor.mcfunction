tag @n[tag=obby_anchor,type=marker] add chosen
kill @e[tag=obby_anchor,type=marker,tag=!chosen]
execute align xyz run player @s look at ~.5 ~.5 ~.5
execute if score @s using_item matches 1.. run return 0
scoreboard players set @s using_item 2
execute as @e[tag=hardcode,type=marker] at @s run function quantum:crystal/hardcode/mark/markerdown
execute if block ~ ~ ~ respawn_anchor[charges=1] facing entity @s feet rotated ~ 0.0 if score .difficulty difficulty matches 3.. positioned ^ ^ ^1 if block ~ ~ ~ #xaniclelib:nonsolid if function quantum:mark/blockplace if function xaniclelib:check/intersect run return run setblock ~ ~ ~ glowstone
execute if block ~ ~ ~ respawn_anchor[charges=1] run return run function quantum:crystal/hardcode/mech/explode
execute if block ~ ~ ~ respawn_anchor[charges=0] unless block ~ ~ ~ respawn_anchor[charges=1] run return run setblock ~ ~ ~ respawn_anchor[charges=1]
execute unless block ~ ~ ~ respawn_anchor run return run setblock ~ ~ ~ respawn_anchor