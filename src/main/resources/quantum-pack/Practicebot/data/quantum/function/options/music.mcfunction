$execute if score .music toggles matches $(music) run stopsound @a
$execute if score .music toggles matches $(music) run playsound block.note_block.bass master @a ~ ~ ~ 1 1 1
$execute if score .music toggles matches $(music) run schedule clear @a function quantum:miscellaneous/play_otherside
$execute if score .music toggles matches $(music) run schedule clear @a function quantum:miscellaneous/play_undertale
$execute if score .music toggles matches $(music) run title @a actionbar [{"text":"Music: ","color":"yellow"},{"text":"OFF!", "color":"#ff0000"}]
$execute if score .music toggles matches $(music) as @e[name=Otherside] run data modify entity @s CustomName set value {"text":"Otherside","color": "#ff0000"}
$execute if score .music toggles matches $(music) as @e[name=Undertale] run data modify entity @s CustomName set value {"text":"Undertale","color": "#ff0000"}
$execute if score .music toggles matches $(music) run return run scoreboard players set .music toggles 0


$scoreboard players set .music toggles $(music)
execute if score .music toggles matches 1 run function quantum:options/toggles/undertale
execute if score .music toggles matches 2 run function quantum:options/toggles/otherside