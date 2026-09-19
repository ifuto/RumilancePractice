# Complete:
#> Look, Shield, bettter shield, Strafe, Strafe_fb, No Pearl Land, Wind (needs to be added to display settings), Far Pearl, Wind Pearl, Simfire, Pflash, Auto wind, Move Forward, Refill, everything
scoreboard players set .move temp -1
scoreboard players set .move2 temp -1
execute if function quantum:bin/4 run function quantum:look
execute if score .shield npc matches 1 unless score .better_shield npc matches 1 unless score @s shield_cd matches 1.. run function npc:shield
execute if score .better_shield npc matches 1 if score @s OnGround matches 0 unless score @s shield_cd matches 1.. run function npc:shield
execute if score .better_shield npc matches 1 if score @s OnGround matches 0 unless score @s shield_cd matches 1.. if items entity @p[tag=xlib_target] weapon.mainhand #swords if score .blast_prot npc matches 1..2 run player @s stop
execute if score .strafe npc matches 1 run function npc:actions/strafe
execute if score .strafe_fb npc matches 1 unless score .move_forward npc matches 1 run function npc:actions/strafe_fb
execute if score .no_pearl_land npc matches 1 as @e[type=ender_pearl] at @s on origin if entity @s[tag=xlib_target] if function quantum:mark/blockplace run kill @n[type=ender_pearl]
# execute if score .wind temp matches 1 run function quantum:mace/wind
# execute if score .far_pearl temp matches 1 run function quantum:mace/far_pearl
# execute at @s[tag=wind_pearl] run function quantum:mace/wind_pearl
# execute if score .wind_pearl temp matches 1 run function quantum:mace/wind_pearl_main
execute if score .refill npc matches 1 as @a[tag=xlib_target] run function quantum:miscellaneous/treats/refill

# Unimplmented
#> Enable tutorial, add wind to display settings, (((((((((((escape from divebomb with pearl, shield, wind charge, move backward))))))))), initiate
function quantum:bin/25