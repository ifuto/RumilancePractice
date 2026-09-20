function quantum:difficulty/resetnpc
scoreboard players set .difficulty difficulty 5
title @a actionbar [{"text": "Difficulty set to", "color": "yellow"},{"text": " MASTER", "color": "#ff0000"}]

scoreboard players set @a[tag=xlib_bot] crystal_cd 2
scoreboard players set @a[tag=xlib_bot] obby_cd 1
scoreboard players set @a[tag=xlib_bot] anchor_cd 1
scoreboard players set @a[tag=xlib_bot] charge_cd 2
scoreboard players set @a[tag=xlib_bot] explosion_cd 2
scoreboard players set @a[tag=xlib_bot] totem_cd 0
scoreboard players set @a[tag=xlib_bot] adaptdifficulty 10
scoreboard players set @a[tag=xlib_bot] hitcd 0
scoreboard players set @a[tag=xlib_bot] reach 29
scoreboard players set @a[tag=xlib_bot] aim 2
scoreboard players set .tempaim aim 2
scoreboard players set @a[tag=xlib_bot] pearl_reaction_cd 0
scoreboard players set @a[tag=xlib_bot] slowcast.step.max_rotation_per_tick 20

scoreboard players set @a[tag=xlib_bot] rail_cd 5
scoreboard players set @a[tag=xlib_bot] crystalpopsdif 0
scoreboard players set @a[tag=xlib_bot] combo 0
weather clear
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
scoreboard players operation @a[tag=xlib_bot] break_crystal_cd = @p[tag=xlib_bot] crystal_cd
scoreboard players remove @a[tag=xlib_bot] break_crystal_cd 1
function quantum:binomial_dist/reach/init
function quantum:binomial_dist/aim/init