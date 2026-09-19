execute unless score .anchors toggles matches 0 if score @p[tag=xlib_target] hurtTime matches 2.. if score @s real_hitcd matches 1.. run scoreboard players add @s crystal2 1
execute unless score .anchors toggles matches 0 if score .crystal_playstyle toggles matches 2 if score @p[tag=xlib_target] hurtTime matches ..1 run scoreboard players add @s crystal2 1
summon end_crystal ~ ~1.5 ~ {ShowBottom:0b}
execute positioned ~ ~1.5 ~ align y facing entity @p[tag=xlib_target] feet rotated ~ 0.0 run player @s look at ^ ^ ^.3
player @s hotbar 3
player @s swing once
tag @n[tag=crystal_2,type=marker] add used

scoreboard players operation @s crystal_timer = @s crystal_cd
scoreboard players operation @s anchor_timer = @s crystal_cd
scoreboard players operation @s obby_timer = @s crystal_cd