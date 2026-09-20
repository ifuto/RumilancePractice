effect give @a speed 4 1 false
effect give @a strength 4 1 false
execute if score .gear toggles matches 2 if score .mode mode matches 4..5 run effect give @a regeneration 4 0 false
execute if score .mode mode matches 6 run effect give @a fire_resistance 4 1 false
execute if score .gear toggles matches 1 unless score .mode mode matches 2 run effect give @a fire_resistance 4 1 false