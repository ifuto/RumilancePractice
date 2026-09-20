$scoreboard players set .crystal_playstyle toggles $(playstyle)
execute if score .crystal_playstyle toggles matches 2 run function quantum:options/toggles/anchor_spam
execute if score .crystal_playstyle toggles matches 1 run function quantum:options/toggles/crystal_spam