execute if score .no_fall toggles matches 0 run function quantum:options/toggles/no_fall_off
execute if score .stun toggles matches 0 run function quantum:options/toggles/stun_off
execute if score .stun toggles matches 1 run function quantum:options/toggles/stun_on
execute if score .no_fall toggles matches 1 run function quantum:options/toggles/no_fall_on
function npc:settings/off/uhc
weather clear
execute if score .gear toggles matches 1 run function quantum:botgear/neth
execute if score .gear toggles matches 2 run function quantum:botgear/dia
execute as @a run attribute @s scale base set 1
execute as @a run attribute @s block_interaction_range base set 4.5
execute as @a run attribute @s entity_interaction_range base set 3
effect clear @a
stopsound @a master entity.experience_orb.pickup
stopsound @a master block.note_block.bass
playsound entity.experience_orb.pickup master @a ~ ~ ~ 1 1 1