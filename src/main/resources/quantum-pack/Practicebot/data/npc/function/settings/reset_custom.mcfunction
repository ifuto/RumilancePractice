scoreboard players set .var var 0
scoreboard players set .reach var 6
scoreboard players set .react var 1
scoreboard players set .scale var 2
scoreboard players set .speed var -1
scoreboard players set .slowness var -1
scoreboard players set .jump_boost var -1
scoreboard players set .strength var -1

scoreboard players set .bot_scale var 2
scoreboard players set .bot_speed var -1
scoreboard players set .bot_slowness var -1
function npc:settings/toggle/reach
function npc:settings/toggle/react
function npc:settings/toggle/scale
function npc:settings/toggle/speed
function npc:settings/toggle/slowness
function npc:settings/toggle/jump_boost
function npc:settings/toggle/strength
function npc:settings/toggle/bot_slowness
function npc:settings/toggle/bot_speed
function npc:settings/toggle/bot_scale
title @a actionbar {"text":"Customisable Settings Reset!", "color": "#ff0000"}
stopsound @a master entity.experience_orb.pickup
stopsound @a master block.note_block.bass
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1