particle trial_spawner_detection_ominous ~ ~1 ~ .3 .4 .3 0 30 force
playsound minecraft:block.anvil.use master @s ~ ~ ~ 1 1 1
execute if score @s kit matches 1 positioned -632 78 11 run return run function quantum:kits/kit1
execute if score @s kit matches 2 positioned -631 78 11 run return run function quantum:kits/kit2
execute if score @s kit matches 3 positioned -630 78 11 run return run function quantum:kits/kit3
execute if score @s kit matches 4 positioned -637 78 18 run return run function quantum:kits/kit4
execute if score @s kit matches 5 positioned -637 78 17 run return run function quantum:kits/kit5
execute if score @s kit matches 6 positioned -637 78 16 run return run function quantum:kits/kit6
execute if score @s kit matches 7 positioned -630 78 23 run return run function quantum:kits/kit7
execute if score @s kit matches 8 positioned -631 78 23 run return run function quantum:kits/kit8
execute if score @s kit matches 9 positioned -632 78 23 run return run function quantum:kits/kit9
execute if score @s kit matches 13 positioned -625 78 16 run return run function quantum:kits/kit10
execute if score @s kit matches 14 positioned -625 78 17 run return run function quantum:kits/kit11
execute if score @s kit matches 15 positioned -625 78 18 run return run function quantum:kits/kit12



execute if score .mode mode matches 1 if score @s kit matches 10 positioned -657 31 89 run return run function quantum:kits/kit10
execute if score .mode mode matches 1 if score @s kit matches 11 positioned -657 31 90 run return run function quantum:kits/kit11
execute if score .mode mode matches 1 if score @s kit matches 12 positioned -657 31 91 run return run function quantum:kits/kit12

execute if score .mode mode matches 2 if score @s kit matches 10 positioned -689 31 89 run return run function quantum:kits/kit10
execute if score .mode mode matches 2 if score @s kit matches 11 positioned -689 31 90 run return run function quantum:kits/kit11
execute if score .mode mode matches 2 if score @s kit matches 12 positioned -689 31 91 run return run function quantum:kits/kit12

execute if score .mode mode matches 3 if score @s kit matches 10 positioned -625 31 89 run return run function quantum:kits/kit10
execute if score .mode mode matches 3 if score @s kit matches 11 positioned -625 31 90 run return run function quantum:kits/kit11
execute if score .mode mode matches 3 if score @s kit matches 12 positioned -625 31 91 run return run function quantum:kits/kit12

execute if score .mode mode matches 4..5 if score @s kit matches 10 positioned -593 31 89 run return run function quantum:kits/kit10
execute if score .mode mode matches 4..5 if score @s kit matches 11 positioned -593 31 90 run return run function quantum:kits/kit11
execute if score .mode mode matches 4..5 if score @s kit matches 12 positioned -593 31 91 run return run function quantum:kits/kit12

execute if score .mode mode matches 6 if score @s kit matches 10 positioned -593 31 133 run return run function quantum:kits/kit10
execute if score .mode mode matches 6 if score @s kit matches 11 positioned -625 78 134 run return run function quantum:kits/kit11
execute if score .mode mode matches 6 if score @s kit matches 12 positioned -625 78 135 run return run function quantum:kits/kit12