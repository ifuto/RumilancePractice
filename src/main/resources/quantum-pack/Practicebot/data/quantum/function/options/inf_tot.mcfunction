execute unless score .inf_tot toggles matches 1 as @e[type=item_display,name="Infinite totems (CPVP)"] run data modify entity @s CustomName set value {"text":"Infinite totems (CPVP)","color": "#00ff00"}
execute if score .inf_tot toggles matches 1 as @e[type=item_display,name="Infinite totems (CPVP)"] run data modify entity @s CustomName set value {"text":"Infinite totems (CPVP)","color": "#ff0000"}

execute if score .inf_tot toggles matches 1 run return run function quantum:options/toggles/inf_tot_off
execute unless score .inf_tot toggles matches 1 run return run function quantum:options/toggles/inf_tot_on