execute if score .crystal_hardcode toggles matches 1 as @n[type=text_display] run data modify entity @s text set value [{"text":"QuantumAI","color": "#00ffff"},{"text":" | ","color":"yellow"},{"text":"True Power","color": "#00ff00"}]
execute unless score .crystal_hardcode toggles matches 1 as @n[type=text_display] run data modify entity @s text set value [{"text":"TheobaldTheGoat","color": "gold"},{"text":" | ","color":"yellow"},{"text":"1% Power","color":"red"}]

execute if score .crystal_hardcode toggles matches 1 run return run function quantum:options/toggles/hardcode_off
execute unless score .crystal_hardcode toggles matches 1 run return run function quantum:options/toggles/hardcode_on