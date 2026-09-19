execute store result score .random random run random value 1..3
execute if score .random random matches 1 run return run function mech_train:crystal/dtap/init
execute if score .random random matches 2 run return run function mech_train:crystal/ledge/init
execute if score .random random matches 3 run return run function mech_train:crystal/hit_anchor/init