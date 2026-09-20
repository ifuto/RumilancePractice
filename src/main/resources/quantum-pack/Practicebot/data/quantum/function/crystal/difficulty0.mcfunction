function quantum:look
item replace entity @s weapon.offhand with totem_of_undying
item replace entity @s hotbar.0 with totem_of_undying
player @s hotbar 1
execute if score .shield toggles matches 1 unless score @s shield_cd matches 1.. run function quantum:crystal/passive/shield/main
execute unless score .shield toggles matches 1 if score .better_npc_shield toggles matches 1 unless score @s shield_cd matches 1.. run function quantum:crystal/passive/shield/main
execute if score .better_npc_shield toggles matches 1 if score @s OnGround matches 1 run player @s stop
execute if score .shield toggles matches 1 if score @s shield_cd matches 1.. run player @s stop
function quantum:crystal/pearl_spam