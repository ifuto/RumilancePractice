execute store result score .bot_count start run execute if entity @a[tag=xlib_bot,name=quantumbot]
execute if score .bot_count start matches 2.. as @a[tag=!xlib_target] run player @s disconnect

execute at @s run function quantum:look
execute at @s run player @s stop
execute as @e[tag=terrain,nbt={interaction:{}},type=interaction] run function quantum:set_terrain
effect give @a speed 1 3 true
effect give @a resistance 1 4 true
stopsound @a * block.stone_button.click_off
stopsound @a * block.stone_button.click_on
execute as @e[type=splash_potion] run function quantum:kits/loadkit
kill @e[type=minecraft:splash_potion]
execute positioned -725.00 131.61 107.94 as @a[tag=xlib_target,dz=5,dy=5,dx=-20] run item replace entity @s hotbar.0 with splash_potion[custom_name={"color":"light_purple","text":"Loads the last opened chest"},lore=[{"color":"green","text":"The source of all items"}],enchantment_glint_override=true,potion_contents={custom_color:16718982}] 1
execute at @a[tag=xlib_target] as @a[tag=xlib_bot,dx=0] unless score .difficulty difficulty matches 0 unless dimension quantum:caves at @s positioned ~ ~ ~15 positioned over motion_blocking_no_leaves run function quantum:bin/5
function quantum:miscellaneous/tags
execute as @a at @s if dimension quantum:caves positioned over world_surface if entity @s[dx=0] run tp @s ~ ~-10 ~
execute at @a[tag=xlib_bot,limit=1] if dimension quantum:caves unless block ~ ~ ~ #xaniclelib:nonsolid run fill ~-5 ~ ~-5 ~5 ~5 ~5 air replace #quantum:stone
forceload remove all
effect give @a[tag=xlib_target] night_vision 1 0 true
effect give @a[tag=xlib_target] saturation 1 0 true