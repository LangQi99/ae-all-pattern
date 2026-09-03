gamemode creative @s
effect give @s minecraft:night_vision infinite 0 true
effect give @s minecraft:saturation infinite 0 true
item replace entity @s hotbar.0 with aeallpattern:pattern_binder 1
item replace entity @s hotbar.1 with aeallpattern:all_pattern_generator 1
item replace entity @s hotbar.7 with ae2:certus_quartz_wrench 1
item replace entity @s hotbar.8 with minecraft:air
tp @s 0 5 -24 0 0
spawnpoint @s 0 5 -24
scoreboard players set @s aeap_showcase_v12 1
title @s title {"text":"AE 全样板","color":"light_purple","bold":true}
title @s subtitle {"text":"五个演示环节，一镜展示全部功能","color":"white"}
