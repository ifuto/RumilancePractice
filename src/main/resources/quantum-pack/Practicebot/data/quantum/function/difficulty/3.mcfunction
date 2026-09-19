function quantum:difficulty/resetnpc
scoreboard players set .difficulty difficulty 3
title @a actionbar [{"text": "Difficulty set to", "color": "yellow"},{"text": " Hard", "color": "gold"}]

scoreboard players set @a[tag=xlib_bot] crystal_cd 6
scoreboard players set @a[tag=xlib_bot] obby_cd 3
scoreboard players set @a[tag=xlib_bot] anchor_cd 4
scoreboard players set @a[tag=xlib_bot] charge_cd 3
scoreboard players set @a[tag=xlib_bot] explosion_cd 3
scoreboard players set @a[tag=xlib_bot] totem_cd 21
scoreboard players set @a[tag=xlib_bot] adaptdifficulty 6
scoreboard players set @a[tag=xlib_bot] hitcd 10
scoreboard players set @a[tag=xlib_bot] reach 20
scoreboard players set @a[tag=xlib_bot] aim 3
scoreboard players set .tempaim aim 3
scoreboard players set @a[tag=xlib_bot] pearl_reaction_cd 5
scoreboard players set @a[tag=xlib_bot] slowcast.step.max_rotation_per_tick 10

scoreboard players set @a[tag=xlib_bot] rail_cd 15
scoreboard players set @a[tag=xlib_bot] crystalpopsdif 0
scoreboard players set @a[tag=xlib_bot] combo 0
weather clear
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
scoreboard players operation @a[tag=xlib_bot] break_crystal_cd = @p[tag=xlib_bot] crystal_cd
scoreboard players remove @a[tag=xlib_bot] break_crystal_cd 1
function quantum:binomial_dist/reach/init
function quantum:binomial_dist/aim/init