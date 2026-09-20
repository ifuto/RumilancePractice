schedule clear quantum:miscellaneous/play_otherside
execute unless score .music toggles matches 2 run return 0
execute as @a at @s run playsound music_disc.otherside master @s ~ ~ ~ 100000000000000000000000000000 1 1
schedule function quantum:miscellaneous/play_otherside 194s