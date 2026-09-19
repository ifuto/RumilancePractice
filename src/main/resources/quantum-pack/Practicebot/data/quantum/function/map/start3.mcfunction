execute unless score .prompt_activation toggles matches 1 run scoreboard players set .start start 1
execute if score .mode mode matches 1 if score .prompt_activation toggles matches 1 run scoreboard players set .start start 1
execute if score .mode mode matches 3..5 if score .prompt_activation toggles matches 1 run scoreboard players set .start start 1
effect clear @a[tag=xlib_bot] resistance
tp @a[tag=xlib_bot] quantumbot
execute as @a[tag=xlib_bot] if score .gear toggles matches 1 run function quantum:botgear/neth
execute as @a[tag=xlib_bot] if score .gear toggles matches 2 run function quantum:botgear/dia


execute if score .prompt_activation toggles matches 1 unless score .mode mode matches 1 unless score .mode mode matches 3..5 run title @a title {"text":"Click in chat to Start!","color": "gold"}
execute if score .prompt_activation toggles matches 1 unless score .mode mode matches 1 unless score .mode mode matches 3..5 run return run tellraw @a {"text":"Click to start!","color": "gold","click_event": {action:"run_command",command:"/scoreboard players set .start start 1"}}
execute if score .difficulty difficulty matches 0 run function quantum:difficulty/0
execute if score .difficulty difficulty matches 1 run function quantum:difficulty/1
execute if score .difficulty difficulty matches 2 run function quantum:difficulty/2
execute if score .difficulty difficulty matches 3 run function quantum:difficulty/3
execute if score .difficulty difficulty matches 4 run function quantum:difficulty/4
execute if score .difficulty difficulty matches 5 run function quantum:difficulty/5

execute if score .crit toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempcrit 1
execute if score .crit toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempcrit 0
execute if score .shield toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempshield 1
execute if score .shield toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempshield 0
execute if score .scrit toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempscrit 1
execute if score .scrit toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempscrit 0
execute if score .stap toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempstap 1
execute if score .stap toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempstap 0
execute if score .strafe toggles matches 1 run scoreboard players set @a[tag=xlib_bot] tempstrafe 1
execute if score .strafe toggles matches 0 run scoreboard players set @a[tag=xlib_bot] tempstrafe 0

tag @a[tag=xlib_bot] remove macing
tag @a[tag=xlib_bot] remove pot
tag @a[tag=xlib_bot] remove wind_pearl

effect give @a regeneration 1 255 true

scoreboard players reset * pops
scoreboard players set @a combo 0

# This checks if the player is a new player, and if so resets all stats and samples
execute as @a[tag=xlib_target] unless score @s samples matches 1 run function stats:reset_stats
# execute if score .music toggles matches 1 run function quantum:miscellaneous/play_undertale
# execute if score .music toggles matches 2 run function quantum:miscellaneous/play_otherside
scoreboard players set @a[tag=xlib_bot] lava_bucket_count 2
scoreboard players set @a[tag=xlib_bot] water_bucket_count 4

scoreboard players set .100 Math 100
scoreboard players set .10 Math 10
scoreboard players set .90 Math 90
scoreboard players set .2 Math 2
scoreboard players set .3 Math 3
scoreboard players set .5 Math 5
scoreboard players set .25 Math 25
scoreboard players set .50 Math 50
scoreboard players reset * damage_absorbed
scoreboard players reset * damage_dealt
scoreboard players set @a wind_pearl_cd 0
scoreboard players set @a crystal2 0
scoreboard players set @a pearlcd 0
scoreboard players set @a pearlcd2 0
scoreboard players set @a wind_pearl_cd 0
scoreboard players set @a lava_cd 0
scoreboard players set @a cobweb_cd 0
scoreboard players set @a in_cobweb_decision 0
scoreboard players set @a airborne 0
scoreboard players set @a slam_decision 0
scoreboard players set @a hit_decision 0
scoreboard players set @a crit_decision 0
scoreboard players set @a disable_shield_decision 0
scoreboard players set @a bowcharge 0
scoreboard players set @a windcd 0
scoreboard players set @a crystal_timer 0
scoreboard players set @a obby_timer 0
scoreboard players set @a anchor_timer 0
scoreboard players set @a explosion_timer 0
scoreboard players set @a charge_timer 0
scoreboard players set @a totem_timer 0
scoreboard players set @a resetcd 0
scoreboard players set @a death 0
scoreboard players set @a hurtTime 0
scoreboard players set @a disablecd 0
scoreboard players set @a usingshieldtest 0
scoreboard players set @a usingshield 0
scoreboard players set @a strafe 0
scoreboard players set @a strafecd 0
scoreboard players set @a hitcd 0
scoreboard players set @a pearlcd 0
scoreboard players set @a crystalpopsdif 0
scoreboard players set @a combo 0
scoreboard players set @a pot_cd 0
scoreboard players set @a pot_count 0
scoreboard players set @a gap_timer 0
scoreboard players set @a empty_water_cd 0
scoreboard players set @a crossbow_timer 0
scoreboard players set @a slowcast.step.count 0
scoreboard players set @a arrows_in_air 0

function quantum:kits/loadkit
execute if score .mode mode matches 4..5 if score .gear toggles matches 2 as @a run attribute @s attack_damage base set 1.33
execute unless score .gear toggles matches 2 as @a run attribute @s attack_damage base set 1.0
execute unless score .mode mode matches 4..5 as @a run attribute @s attack_damage base set 1.0
execute if score .breakable toggles matches 1 as @a run function quantum:miscellaneous/breakable
execute if score .breakable toggles matches 0 as @a run function quantum:miscellaneous/unbreakable