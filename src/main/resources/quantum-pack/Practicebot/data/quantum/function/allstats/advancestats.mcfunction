advancement revoke @s only quantum:hit
# Player hitcd for pcrits
execute if entity @s[tag=xlib_target] run scoreboard players add .num_of_player_hits stats 10
execute at @s[tag=xlib_target] anchored eyes store result score @s distance_to_target run distance from ^ ^ ^ toHitbox @p[tag=xlib_bot] e 1
execute if entity @s[tag=xlib_target] run function stats:sword/avg_distance
execute if entity @s[tag=xlib_target] unless score .mode mode matches 2 if score .start start matches 1 run function quantum:prac_stats/dist/init
execute if entity @s[tag=xlib_target] if score .mode mode matches 2 if score .difficulty difficulty matches 0 if score .start start matches 1 run function quantum:prac_stats/dist/init
execute if score .gear toggles matches 1 run scoreboard players set @s[tag=xlib_target] hitcd 14
execute if score .gear toggles matches 2 run scoreboard players set @s[tag=xlib_target] hitcd 15
scoreboard players set @s real_hitcd 11