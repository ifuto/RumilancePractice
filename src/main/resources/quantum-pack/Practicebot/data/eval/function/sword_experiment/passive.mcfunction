function quantum:look
function quantum:sword/bot_mech/strafe
execute if score .passive_distance temp matches 1.. run player @s move backward
scoreboard players remove .passive_distance temp 1
say hi