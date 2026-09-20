execute if items entity @a[tag=xlib_bot] hotbar.0 totem_of_undying if items entity @a[tag=xlib_bot] weapon.offhand totem_of_undying run return 1
kill @e[tag=xlib,distance=0..,type=marker]
execute at @s if score .difficulty difficulty matches 1.. if score .dbp toggles matches 1 if score @s Pos1 > @p[tag=xlib_target,distance=0..] Pos1 at @e[distance=..5,limit=1,sort=nearest,type=end_crystal] run player @s look at ~ ~ ~
execute at @s if score .difficulty difficulty matches 1.. if score .dbp toggles matches 1 if score @s Pos1 > @p[tag=xlib_target,distance=0..] Pos1 at @e[distance=..5,limit=1,sort=nearest,type=end_crystal] run player @s move forward
execute at @s if score .difficulty difficulty matches 1.. unless score @s Pos1 > @p[tag=xlib_target] Pos1 run player @s move
scoreboard players set @s[scores={pearlcd2=..3}] pearlcd2 3
function quantum:look
return 0