# Shield
execute if score @s combo matches ..-3 run scoreboard players set @s tempshield 1
execute at @s[scores={tempshield=1..,bowcharge=..0,shield_cd=..0,real_hitcd=1..10},predicate=!quantum:fall_distance15] run function quantum:shield/use
execute at @s[scores={tempshield=1..,bowcharge=..0,shield_cd=..0,real_hitcd=0},predicate=!quantum:fall_distance15] if entity @p[tag=xlib_target,distance=..3] run function quantum:shield/use
execute if score @s airborne matches 1.. run player @s use
execute positioned ~-5 ~ ~-5 unless entity @a[tag=xlib_target,dx=9,dz=9,dy=100] run player @s use
execute if score .difficulty difficulty matches 0 if score .shield toggles matches 1 unless score @s shield_cd matches 1.. run player @s use continuous