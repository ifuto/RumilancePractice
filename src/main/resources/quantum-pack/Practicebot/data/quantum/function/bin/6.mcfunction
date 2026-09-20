execute if score .res toggles matches 1 run effect give @s resistance 4 4 true
execute if score .mode mode matches 2 if score .res toggles matches 1 run effect give @s fire_resistance 4 0 true
execute if items entity @s weapon.mainhand recovery_compass run kill
execute if score .breakable toggles matches 1 if score .start start matches 1 run function quantum:miscellaneous/breakable
execute if score .breakable toggles matches 0 if score .start start matches 1 run function quantum:miscellaneous/unbreakable