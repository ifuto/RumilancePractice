schedule clear quantum:miscellaneous/play_undertale
execute unless score .music toggles matches 1 run return 0
execute as @a at @s run playsound test:test.sound.enigmatic_encounter master @s ~ ~ ~ 100000000000000000000000000000 1 0.5
schedule function quantum:miscellaneous/play_undertale 240s