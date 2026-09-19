execute unless score .far_pearl toggles matches 1 as @e[type=item_display,name="Far Pearl"] run data modify entity @s CustomName set value {"text":"Far Pearl","color": "#00ff00"}
execute if score .far_pearl toggles matches 1 as @e[type=item_display,name="Far Pearl"] run data modify entity @s CustomName set value {"text":"Far Pearl","color": "#ff0000"}

execute if score .far_pearl toggles matches 1 run return run function quantum:options/toggles/far_pearloff
execute unless score .far_pearl toggles matches 1 run return run function quantum:options/toggles/far_pearlon