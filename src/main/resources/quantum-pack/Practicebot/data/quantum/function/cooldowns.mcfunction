execute unless score .mode mode matches 2 unless score .mode mode matches 100.. as @a unless items entity @s weapon.mainhand #enchantable/sharp_weapon run scoreboard players set @s hitcd 13

# Util timers
scoreboard players remove .title_timer map 1
scoreboard players remove @a[scores={gap_timer=1..}] gap_timer 1
scoreboard players remove @a[scores={crossbow_timer=1..}] crossbow_timer 1

scoreboard players remove @a[scores={real_hitcd=1..}] real_hitcd 1
scoreboard players remove @a[scores={hitcd=1..}] hitcd 1
scoreboard players remove @a[scores={strafecd=1..}] strafecd 1
scoreboard players remove @a[scores={resetcd=1..}] resetcd 1
scoreboard players remove @a[scores={totem_timer=1..}] totem_timer 1
scoreboard players remove @a[scores={pearlcd=1..}] pearlcd 1
scoreboard players remove @a[scores={pearlcd2=1..}] pearlcd2 1
scoreboard players remove @a[scores={shield_cd=1..}] shield_cd 1
scoreboard players remove @a[scores={disablecd=1..}] disablecd 1
scoreboard players remove @a[scores={anchor_timer=1..}] anchor_timer 1
scoreboard players remove @a[scores={explosion_timer=1..}] explosion_timer 1
scoreboard players remove @a[scores={charge_timer=1..}] charge_timer 1
scoreboard players remove @a[scores={obby_timer=1..}] obby_timer 1
execute if score .random random matches ..30 run scoreboard players remove @a[scores={totem_timer=1..}] totem_timer 1
execute if score .mode mode matches 2 unless score .difficulty difficulty matches 0 run return run scoreboard players remove @a[scores={crystal_timer=1..}] crystal_timer 1
###########################
scoreboard players remove @a[scores={bow_timer=1..}] bow_timer 1
scoreboard players remove @a[scores={wind_pearl_cd=1..}] wind_pearl_cd 1
scoreboard players remove @a[scores={pot_cd=1..}] pot_cd 1
scoreboard players remove @a[scores={windcd=1..}] windcd 1
scoreboard players remove @a[scores={water_cd=1..}] water_cd 1
scoreboard players remove @a[scores={cobweb_cd=1..}] cobweb_cd 1
scoreboard players remove @a[scores={lava_cd=1..}] lava_cd 1
scoreboard players remove @a[scores={using_item=1..}] using_item 1
scoreboard players remove @a[scores={rail_timer=1..}] rail_timer 1
scoreboard players remove @a[scores={bowcharge=1..}] bowcharge 1
scoreboard players remove @a[scores={random_cd=1..}] random_cd 1
scoreboard players remove @a[scores={empty_water_cd=1..}] empty_water_cd 1