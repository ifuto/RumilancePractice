scoreboard players operation $seconds math = @s rtptimer
scoreboard players operation $seconds math /= #20 math
tellraw @s [{"text":"You cant rtp yet! Try again in ","color":"red"},{"score":{"name":"$seconds","objective":"math"},"color":"green"},{"text":" seconds.","color":"green"}]