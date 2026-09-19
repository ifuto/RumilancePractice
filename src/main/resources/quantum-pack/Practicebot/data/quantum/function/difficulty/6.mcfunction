function quantum:difficulty/resetnpc
scoreboard players set .difficulty difficulty 6
title @a actionbar [{"text": "Difficulty set to", "color": "yellow"},{"text": "SURVIVAL MASTER", "color": "dark_purple"}]

scoreboard players set @a[tag=xlib_bot] crystal_cd 3
scoreboard players set @a[tag=xlib_bot] obby_cd 0
scoreboard players set @a[tag=xlib_bot] anchor_cd 1
scoreboard players set @a[tag=xlib_bot] charge_cd 2
scoreboard players set @a[tag=xlib_bot] explosion_cd 2
scoreboard players set @a[tag=xlib_bot] totem_cd 1
scoreboard players set @a[tag=xlib_bot] adaptdifficulty 10
scoreboard players set @a[tag=xlib_bot] hitcd 0
scoreboard players set @a[tag=xlib_bot] reach 30

scoreboard players set @a[tag=xlib_bot] rail_cd 5
scoreboard players set @a[tag=xlib_bot] crystalpopsdif 0
scoreboard players set @a[tag=xlib_bot] combo 0
weather clear
scoreboard players operation @a[tag=xlib_bot] break_crystal_cd = @p[tag=xlib_bot] crystal_cd
scoreboard players remove @a[tag=xlib_bot] break_crystal_cd 2