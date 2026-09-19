setblock ~ ~ ~ cobweb
execute align xyz run scoreboard players set @a[dx=0,dy=0,dz=0] in_cobweb_decision 1
summon marker ~ ~ ~ {Tags:["cobweb"]}