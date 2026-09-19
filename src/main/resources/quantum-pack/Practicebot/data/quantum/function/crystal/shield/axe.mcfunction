# Disable shield
execute at @s[scores={disablecd=..0}] if score .axe toggles matches 1 if entity @p[tag=xlib_target,distance=..3,scores={usingshield=1..}] run function quantum:shield/disable
scoreboard players set @p[tag=xlib_target,scores={shielding=0}] usingshield 0