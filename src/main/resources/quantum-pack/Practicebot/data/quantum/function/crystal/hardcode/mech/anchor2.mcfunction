tag @n[tag=anchor,type=marker] add chosen
kill @e[tag=anchor,type=marker,tag=!chosen]
execute align xyz run player @s look at ~.5 ~.5 ~.5
execute if score @s using_item matches 1.. run return 0
scoreboard players set @s using_item 4
execute if block ~ ~ ~ respawn_anchor[charges=1] run return run function quantum:crystal/hardcode/mech/explode
execute if block ~ ~ ~ respawn_anchor[charges=0] unless block ~ ~ ~ respawn_anchor[charges=1] run return run setblock ~ ~ ~ respawn_anchor[charges=1]
execute unless block ~ ~ ~ respawn_anchor run return run setblock ~ ~ ~ respawn_anchor