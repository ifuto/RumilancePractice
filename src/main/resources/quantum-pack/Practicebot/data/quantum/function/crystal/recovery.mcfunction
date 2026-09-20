kill @e[tag=anchor_4,distance=0..,type=marker]
player @s move
player @s sprint
execute if score .difficulty difficulty matches 1.. if score .dbp toggles matches 1 if score @s Pos1 > @p[tag=xlib_target] Pos1 if function quantum:miscellaneous/random at @p[tag=xlib_target] run player @s look at ~ ~ ~
execute if score .difficulty difficulty matches 1.. if score .dbp toggles matches 1 if score @s Pos1 > @p[tag=xlib_target] Pos1 if function quantum:miscellaneous/random at @p[tag=xlib_target] run player @s move forward
player @s hotbar 1
scoreboard players set @s[scores={pearlcd2=..2}] pearlcd2 2