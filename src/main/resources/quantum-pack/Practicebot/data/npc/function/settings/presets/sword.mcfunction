function npc:settings/off/airborne_sf
function npc:settings/off/auto_wind
function npc:settings/off/better_shield
function npc:settings/off/far_pearl
function npc:settings/off/pflash
function npc:settings/off/sf
function npc:settings/off/refill
function npc:settings/off/shield
function npc:settings/off/simfire
function npc:settings/off/strafe_fb
function npc:settings/off/weakness
function npc:settings/off/wind_pearl
function npc:settings/off/wind
function npc:settings/off/res
function npc:settings/on/dia
function npc:settings/on/move_forward
function npc:settings/on/prot
function npc:settings/on/regen_bot
team modify rtp collisionRule never
title @a actionbar [{"text":"Sword Preset ", "color": "yellow"},{"text":"ON!","color": "green"}]
stopsound @a master entity.experience_orb.pickup
stopsound @a master block.note_block.bass
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
execute if score @s kit matches 10 positioned -657 31 89 run return run function quantum:kits/kit10
execute if score @s kit matches 11 positioned -657 31 90 run return run function quantum:kits/kit11
execute if score @s kit matches 12 positioned -657 31 91 run return run function quantum:kits/kit12