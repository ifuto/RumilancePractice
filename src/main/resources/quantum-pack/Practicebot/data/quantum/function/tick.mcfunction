function quantum:prac_stats/tick
execute if score .mode mode matches 100.. as @a[tag=xlib_bot] at @s run return run function mech_train:tick
execute if score .difficulty difficulty matches 0 if score .start start matches 1 as @a[tag=xlib_bot] run return run function npc:tick
function quantum:main_tick
scoreboard players set @a shielding 0
