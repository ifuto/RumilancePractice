advancement revoke @s only quantum:shielding
execute if entity @s[tag=xlib_target] at @s if score @p[tag=xlib_bot] disablecd matches ..0 run scoreboard players set @s disable_shield_decision 1
scoreboard players set @s shielding 1