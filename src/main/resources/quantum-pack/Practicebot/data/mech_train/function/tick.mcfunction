#> MAKE IT CHANGE ITS GENERIC HEALTH TO 20 IN THE RESET FUNCTION, AND EDIT THE DISCORD 1.21.4 TAG TO SAY THAT AS WELL
execute if entity @e[nbt={Item:{id:"minecraft:netherite_sword",count:1}},type=item] in overworld run return run function quantum:map/reset
execute unless entity @a[tag=xlib_target,scores={death=0}] in overworld run return run function quantum:map/reset
function mech_train:treats
execute as @p[tag=xlib_target] at @s run function quantum:bin/30
scoreboard players set @s in_range 0
scoreboard players set @a OnGround 0
scoreboard players set @a[predicate=quantum:onground] OnGround 1
execute store result score @s Pos1 run data get entity @s Pos[1]
scoreboard players operation @s Pos1_difference = @s Pos1
scoreboard players operation @s Pos1_difference -= @p[tag=xlib_target] Pos1
execute as @a store result score @s shield_cd run player @s itemCd shield
function quantum:cooldowns

# Crystal (mode 2)
execute if score .mode mode matches 201 run return run function mech_train:crystal/dtap/tick
execute if score .mode mode matches 202 run return run function mech_train:crystal/ledge/tick
execute if score .mode mode matches 203 run return run function mech_train:crystal/hit_anchor/tick


# Mace (mode 3)
execute if score .mode mode matches 301 run return run function mech_train:mace/elytra/tick
execute if score .mode mode matches 302 run return run function mech_train:mace/far_pearl/tick
execute if score .mode mode matches 303 run return run function mech_train:mace/stun_slam/tick
execute if score .mode mode matches 304 run return run function mech_train:mace/divebomb/tick


# Pot (mode 4-5)
execute if score .mode mode matches 402 run return run function mech_train:pot/refill_hotbar/tick
execute if score .mode mode matches 403 run return run function mech_train:pot/refill_inventory/tick
execute if score .mode mode matches 401 run return run function mech_train:pot/repot/tick


# Cart (mode 6)
execute if score .mode mode matches 601 run return run function mech_train:cart/-3/tick
execute if score .mode mode matches 602 run return run function mech_train:cart/-2/tick
execute if score .mode mode matches 603 run return run function mech_train:cart/-1/tick
execute if score .mode mode matches 604 run return run function mech_train:cart/0/tick
execute if score .mode mode matches 605 run return run function mech_train:cart/1/tick
execute if score .mode mode matches 606 run return run function mech_train:cart/2/tick
execute if score .mode mode matches 607 run return run function mech_train:cart/3/tick

# Generic
execute if score .mode mode matches 701 run function mech_train:generic/flick_aim.mcfunction/tick