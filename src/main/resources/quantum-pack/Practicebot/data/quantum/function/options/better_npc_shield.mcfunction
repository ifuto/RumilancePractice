execute unless score .better_npc_shield toggles matches 1 as @e[type=item_display,name="NPC Shield+"] run data modify entity @s CustomName set value {"text":"NPC Shield+","color": "#00ff00"}
execute if score .better_npc_shield toggles matches 1 as @e[type=item_display,name="NPC Shield+"] run data modify entity @s CustomName set value {"text":"NPC Shield+","color": "#ff0000"}

execute if score .better_npc_shield toggles matches 1 run return run function quantum:options/toggles/better_npc_shield_off
execute unless score .better_npc_shield toggles matches 1 run return run function quantum:options/toggles/better_npc_shield_on