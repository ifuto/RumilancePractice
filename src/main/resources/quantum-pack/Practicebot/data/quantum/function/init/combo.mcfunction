# Shield
execute unless score .mode mode matches 4..5 at @s[scores={state=1}] if score .shield toggles matches 1 run function quantum:shield/tick

# Sword
execute if score .mode mode matches 1 run function quantum:sword/combo/tick

# Mace
execute if score .mode mode matches 3 run function quantum:mace/combo/tick

# Pot
execute if score .mode mode matches 4..5 run function quantum:pot/combo/tick

# Cart
execute if score .mode mode matches 6 run function quantum:carttest/combo/tick