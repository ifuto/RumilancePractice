team join rtp @a
kill @e[tag=rtp,type=marker]
execute if score .mode mode matches 203 in quantum:stone run spreadplayers ~ ~ 0 20000 true @a
execute if score .mode mode matches 200..399 unless score .mode mode matches 203 in quantum:plains run spreadplayers ~ ~ 0 20000 true @a
execute unless score .mode mode matches 200..399 in quantum:netherite run spreadplayers ~ ~ 0 20000 true @a
execute as quantumbot at @s align y run summon marker ~ ~ ~ {Tags:["rtp"]}