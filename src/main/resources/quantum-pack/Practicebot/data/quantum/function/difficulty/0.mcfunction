scoreboard players set .var var 0
function npc:settings/toggle/bot_scale
function npc:settings/toggle/bot_slowness
function npc:settings/toggle/bot_speed
function npc:settings/toggle/scale
function npc:settings/toggle/slowness
function npc:settings/toggle/speed
function npc:settings/toggle/strength
function npc:settings/toggle/jump_boost
function npc:settings/toggle/reach
function npc:settings/toggle/react
execute if score .rain npc matches 0 run function npc:settings/off/rain
execute if score .stun npc matches 0 run function npc:settings/off/stun
execute if score .uhc npc matches 0 run function npc:settings/off/uhc
execute if score .falldmg npc matches 0 run function npc:settings/off/falldmg
execute if score .rain npc matches 1 run function npc:settings/on/rain
execute if score .stun npc matches 1 run function npc:settings/on/stun
execute if score .uhc npc matches 1 run function npc:settings/on/uhc
execute if score .falldmg npc matches 1 run function npc:settings/on/falldmg
scoreboard players set .difficulty difficulty 0
title @a actionbar [{"text": "Difficulty set to", "color": "yellow"},{"text": " NPC", "color": "aqua"}]

scoreboard players set @a[tag=xlib_bot] crystal_cd 0
scoreboard players set @a[tag=xlib_bot] obby_cd 0
scoreboard players set @a[tag=xlib_bot] anchor_cd 0
scoreboard players set @a[tag=xlib_bot] charge_cd 0
scoreboard players set @a[tag=xlib_bot] explosion_cd 0
scoreboard players set @a[tag=xlib_bot] crystalpopsdif 0
scoreboard players set @a[tag=xlib_bot] combo 0

playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
scoreboard players operation @a[tag=xlib_bot] break_crystal_cd = @p[tag=xlib_bot] crystal_cd
scoreboard players remove @a[tag=xlib_bot] break_crystal_cd 2
scoreboard players set .var var 0
execute if score .disable_tutorial npc matches 0 run function npc:tutorial
function npc:display_settings
function npc:botgear/loadkit
stopsound @a master entity.experience_orb.pickup
stopsound @a master block.note_block.bass
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1