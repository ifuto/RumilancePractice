function npc:settings/off/auto_wind
function npc:settings/off/bot_sf
function npc:settings/off/falldmg
function npc:settings/off/far_pearl
function npc:settings/off/insta_shieldcd
function npc:settings/off/jreset
function npc:settings/off/move_forward
function npc:settings/off/no_pearl_land
function npc:settings/off/pflash
function npc:settings/off/regen_bot
function npc:settings/off/sf
function npc:settings/off/simfire
function npc:settings/off/strafe_fb
function npc:settings/off/strafe
function npc:settings/off/weakness
function npc:settings/off/wind
function npc:settings/off/wind_pearl
function npc:settings/off/airborne_sf
function npc:settings/off/res
function npc:settings/on/better_shield
function npc:settings/on/neth
function npc:settings/on/sbp
function npc:settings/on/display_shield_dura
function npc:settings/on/refill
function npc:settings/on/stun
team modify rtp collisionRule always
title @a actionbar [{"text":"Crystal Preset ", "color": "yellow"},{"text":"ON!","color": "green"}]
stopsound @a master entity.experience_orb.pickup
stopsound @a master block.note_block.bass
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1
execute if score @s kit matches 10 positioned -689 31 89 run return run function quantum:kits/kit10
execute if score @s kit matches 11 positioned -689 31 90 run return run function quantum:kits/kit11
execute if score @s kit matches 12 positioned -689 31 91 run return run function quantum:kits/kit12