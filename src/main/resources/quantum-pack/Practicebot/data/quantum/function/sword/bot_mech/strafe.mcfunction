execute as @s[scores={OnGround=1,strafecd=..0}] run function quantum:sword/strafe
execute as @s[scores={strafe=1,airborne=0}] run player @s move left
execute as @s[scores={strafe=0,airborne=0}] run player @s move right