kill @e[tag=lowpos,type=marker]
kill @e[tag=target]
kill @e[tag=pearllib,type=marker]
summon marker ~ ~ ~ {Tags:["escape","pearllib"]}
execute facing entity @p[tag=xlib_target] feet positioned ^ ^ ^-15 run spreadplayers ~ ~ 0 5 false @n[tag=escape,type=marker]
function quantum:ray/ray2/cast
player @s stop
player @s hotbar 7
player @s use once
scoreboard players set @s pearlcd 20