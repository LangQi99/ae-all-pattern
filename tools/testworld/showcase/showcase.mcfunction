gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doMobSpawning false
gamerule keepInventory true
gamerule spawnRadius 0
time set noon
weather clear
kill @e[type=!minecraft:player,x=-20,y=0,z=-30,dx=40,dy=24,dz=68]
fill -18 5 -28 18 18 4 minecraft:air replace
fill -18 5 5 18 18 38 minecraft:air replace
fill -18 4 -28 18 4 38 minecraft:smooth_quartz replace
fill -16 4 -26 16 4 36 minecraft:light_gray_concrete replace
fill -2 4 -26 2 4 36 minecraft:purple_concrete replace
fill -16 4 -18 16 4 -7 minecraft:white_concrete replace
fill -16 4 -5 16 4 6 minecraft:light_gray_concrete replace
fill -16 4 8 16 4 19 minecraft:white_concrete replace
fill -16 4 21 16 4 34 minecraft:light_gray_concrete replace
fill -16 5 -26 -16 6 36 minecraft:purple_stained_glass replace
fill 16 5 -26 16 6 36 minecraft:purple_stained_glass replace
setworldspawn 0 5 -24
forceload add -18 -28 18 38

# Floating entrance title. All labels are text displays; the arena contains no signs.
summon minecraft:text_display 0 9 -20 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:440,text:'{"text":"AE 全样板 · 纯净演示场\\n","color":"light_purple","bold":true,"extra":[{"text":"快捷栏 1 绑定器 · 快捷栏 2 生成器\\n沿紫色中线进入五个演示环节","color":"white","bold":false}]}' }

# Station 1: compact powered ME network. Terminals face north, toward the filming route.
setblock 0 5 -15 ae2:controller replace
setblock -1 5 -15 ae2:creative_energy_cell replace
setblock 1 5 -15 ae2:drive replace
item replace block 1 5 -15 container.0 with ae2:item_storage_cell_64k 1
setblock 3 5 -15 aeallpattern:pattern_linker replace
setblock 5 5 -15 aeallpattern:tianshu_pattern_selector[facing=north] replace
setblock -3 5 -15 ae2:pattern_provider replace
setblock -4 5 -15 ae2:molecular_assembler replace
setblock 0 5 -16 ae2:64k_crafting_storage replace
setblock 1 5 -16 ae2:crafting_unit replace
setblock 0 6 -16 ae2:crafting_unit replace
setblock 1 6 -16 ae2:crafting_monitor replace
setblock -2 6 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},north:{id:"ae2:terminal",enabledKeyTypes:["ae2:i","ae2:f"]}} replace
setblock -1 6 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},north:{id:"ae2:crafting_terminal",enabledKeyTypes:["ae2:i","ae2:f"]}} replace
setblock 0 6 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},north:{id:"ae2:pattern_encoding_terminal",enabledKeyTypes:["ae2:i","ae2:f"]}} replace
setblock 1 6 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},north:{id:"ae2:pattern_access_terminal",enabledKeyTypes:["ae2:i","ae2:f"]}} replace
setblock -8 5 -15 minecraft:barrel[facing=up] replace
item replace block -8 5 -15 container.0 with aeallpattern:pattern_binder 1
item replace block -8 5 -15 container.1 with aeallpattern:all_pattern_generator 1
item replace block -8 5 -15 container.2 with ae2:blank_pattern 64
setblock -2 5 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
summon minecraft:text_display 0 8 -12 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:430,text:'{"text":"① 连接与连续绑定\\n","color":"light_purple","bold":true,"extra":[{"text":"右击紫色链接器 → Shift+右击多台机器\\n紫色立体框 = 连接成功","color":"white","bold":false}]}' }

# Binding targets, spaced symmetrically and left unbound for filming. Every
# machine has its own provider so the same output can demonstrate AE priority.
setblock -9 5 -9 minecraft:furnace[facing=north] replace
item replace block -9 5 -9 container.1 with minecraft:coal 64
setblock -9 5 -10 ae2:pattern_provider[push_direction=south] replace
setblock -3 5 -9 minecraft:blast_furnace[facing=north] replace
item replace block -3 5 -9 container.1 with minecraft:coal 64
setblock -3 5 -10 ae2:pattern_provider[push_direction=south] replace
setblock 3 5 -9 minecraft:smoker[facing=north] replace
item replace block 3 5 -9 container.1 with minecraft:coal 64
setblock 3 5 -10 ae2:pattern_provider[push_direction=south] replace
setblock 9 5 -9 mekanism:energized_smelter replace
setblock 9 5 -10 ae2:pattern_provider[push_direction=south] replace
setblock 9 5 -8 mekanism:creative_energy_cube[facing=north] replace
data merge block 9 5 -8 {component_config:{eject0:1b},energy_containers:[{container:0b,stored:9223372036854775807L}]}
fill -9 5 -11 14 5 -11 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
summon minecraft:text_display 0 7.6 -7 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:500,text:'{"text":"② 同产物 · 多机器优先级\\n","color":"light_purple","bold":true,"extra":[{"text":"四台机器各接独立样板供应器\\n生成样板后调供应器优先级，观察 AE 选择哪台","color":"white","bold":false}]}' }
summon minecraft:text_display 0 6.7 -7 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,line_width:490,text:'{"text":"铁锭可竞争：熔炉 · 高炉 · 充能冶炼炉　烟熏炉保留作类型对照","color":"white"}' }

# Side-bus is continuous behind the devices and never relies on machines to relay the grid.
setblock 2 5 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock 2 5 -16 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
fill 2 5 -17 14 5 -17 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock 14 5 -16 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
fill 6 5 -15 14 5 -15 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
fill 14 5 -14 14 5 25 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace

# Station 2: generator, deterministic configuration pattern and provider.
setblock -9 5 1 mekanism:basic_enriching_factory replace
setblock -9 5 2 mekanism:creative_energy_cube[facing=north] replace
data merge block -9 5 2 {component_config:{eject0:1b},energy_containers:[{container:0b,stored:9223372036854775807L}]}
setblock -4 5 1 minecraft:barrel[facing=up] replace
setblock -4 5 3 minecraft:command_block{Command:"aeallpattern seed-showcase-patterns -4 5 1 10 5 25",auto:0b} replace
setblock -4 6 3 minecraft:stone_button[face=floor] replace
fill 10 5 1 14 5 1 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock 10 5 1 ae2:pattern_provider replace
setblock 9 5 1 ae2:molecular_assembler replace
summon minecraft:text_display 0 8 -4 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:450,text:'{"text":"③ 生成与样板配置\\n","color":"light_purple","bold":true,"extra":[{"text":"生成器 Shift+右击机器，一次获得全部 JEI/EMI 配方\\n木桶内演示样板可右击配置并放入供应器","color":"white","bold":false}]}' }
summon minecraft:text_display -7 6.8 1 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,line_width:220,text:'{"text":"生成目标","color":"light_purple","bold":true}' }
summon minecraft:text_display 7 6.8 1 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,line_width:280,text:'{"text":"配置样板 → 样板供应器","color":"light_purple","bold":true}' }

# Station 4: large-tech compatibility gallery. All machines stay on two side
# stages, leaving x=-5..5 completely open so the downstream AE station remains
# visible from the purple filming route.
# Create: paired crushing wheels, a millstone, mixer/basin and press/depot.
setblock -13 5 13 create:crushing_wheel[axis=z] replace
setblock -11 5 13 create:crushing_wheel[axis=z] replace
setblock -13 5 14 create:creative_motor[facing=north]{ScrollValue:64} replace
setblock -11 5 14 create:creative_motor[facing=north]{ScrollValue:-64} replace
setblock -13 5 16 create:millstone replace
setblock -10 5 16 create:basin replace
setblock -10 7 16 create:mechanical_mixer replace
setblock -7 5 16 create:depot replace
setblock -7 7 16 create:mechanical_press[facing=north] replace

# Mekanism: item, infusion and gas/fluid/chemical recipe families.
setblock 7 5 13 mekanism:crusher replace
setblock 7 5 14 mekanism:creative_energy_cube[facing=north] replace
data merge block 7 5 14 {component_config:{eject0:1b},energy_containers:[{container:0b,stored:9223372036854775807L}]}
setblock 10 5 13 mekanism:basic_infusing_factory replace
setblock 10 5 14 mekanism:creative_energy_cube[facing=north] replace
data merge block 10 5 14 {component_config:{eject0:1b},energy_containers:[{container:0b,stored:9223372036854775807L}]}
setblock 13 5 13 mekanism:rotary_condensentrator replace
setblock 13 5 14 mekanism:creative_energy_cube[facing=north] replace
data merge block 13 5 14 {component_config:{eject0:1b},energy_containers:[{container:0b,stored:9223372036854775807L}]}

# Industrial Foregoing: several visually distinct processing families.
setblock 7 5 17 industrialforegoing:dissolution_chamber[subfacing=north] replace
setblock 10 5 17 industrialforegoing:material_stonework_factory[subfacing=north] replace
setblock 13 5 17 industrialforegoing:fluid_sieving_machine[subfacing=north] replace

# Neo ECO: place its official minimum crafting subsystem structure. The pattern
# bus accepts aggregate patterns through the optional ECO compatibility mixin.
fill -14 3 17 14 3 17 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
fill -14 3 17 -14 6 17 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
fill 14 3 17 14 5 17 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
place template aeallpattern_test:eco_craft_min -15 5 18 none none 1.0 0

# Mystical Agriculture remains as the multiblock/no-single-machine example.
setblock 10 5 21 mysticalagriculture:infusion_altar replace
setblock 8 5 19 mysticalagriculture:infusion_pedestal replace
setblock 10 5 19 mysticalagriculture:infusion_pedestal replace
setblock 12 5 19 mysticalagriculture:infusion_pedestal replace
setblock 8 5 21 mysticalagriculture:infusion_pedestal replace
setblock 12 5 21 mysticalagriculture:infusion_pedestal replace
setblock 8 5 23 mysticalagriculture:infusion_pedestal replace
setblock 10 5 23 mysticalagriculture:infusion_pedestal replace
setblock 12 5 23 mysticalagriculture:infusion_pedestal replace
summon minecraft:text_display 0 8 9 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:540,text:'{"text":"④ 大型科技 · 全配方兼容\\n","color":"light_purple","bold":true,"extra":[{"text":"机械动力 · Mekanism · 工业先锋 · 神秘农业 · Neo ECO\\n粉碎/压片/搅拌/灌注/气液/多方块，扫描不设白名单","color":"white","bold":false}]}' }
summon minecraft:text_display -10 6.8 11 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,text:'{"text":"机械动力","color":"gold","bold":true}' }
summon minecraft:text_display 10 6.8 11 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,text:'{"text":"Mekanism / 工业先锋","color":"aqua","bold":true}' }
summon minecraft:text_display -12 7.5 17 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:360,text:'{"text":"Neo ECO 合成子系统\\n","color":"aqua","bold":true,"extra":[{"text":"聚合样板可直接放入样板总线","color":"white","bold":false}]}' }
summon minecraft:text_display 10 7.2 21 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,text:'{"text":"神秘农业多方块祭坛","color":"green","bold":true}' }

# Station 4: routing policy and live order recalculation.
fill 10 5 25 14 5 25 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock 10 5 25 ae2:pattern_provider replace
setblock 9 5 25 minecraft:barrel[facing=up] replace
setblock 5 5 25 aeallpattern:tianshu_pattern_selector[facing=north] replace
fill -1 5 26 14 5 26 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock -1 5 25 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock 0 5 25 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"}} replace
setblock 0 5 27 ae2:64k_crafting_storage replace
setblock 1 5 27 ae2:crafting_unit replace
setblock 0 6 27 ae2:crafting_unit replace
setblock 1 6 27 ae2:crafting_monitor replace
setblock -1 6 25 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},north:{id:"ae2:crafting_terminal",enabledKeyTypes:["ae2:i","ae2:f"]}} replace
setblock 0 6 25 ae2:cable_bus{cable:{id:"ae2:fluix_glass_cable"},north:{id:"ae2:terminal",enabledKeyTypes:["ae2:i","ae2:f"]}} replace
summon minecraft:text_display 0 8 23 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:1073741824,shadow:1b,line_width:470,text:'{"text":"⑤ 天枢路由与实时重算\\n","color":"light_purple","bold":true,"extra":[{"text":"终端搜索紫水晶碎片，下单 16 个\\n确认页临时拖动优先级，原料列表会立即变化","color":"white","bold":false}]}' }
summon minecraft:text_display -8 6.8 25 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,line_width:320,text:'{"text":"圆石=短路径  钻石=高产量\\n石英=等待少  红石=含中间步骤","color":"white"}' }

# Minimal replay controls at the end of the route.
setblock -6 5 33 minecraft:command_block{Command:"tp @p 0 5 -24 0 0",auto:0b} replace
setblock -6 6 33 minecraft:stone_button[face=floor] replace
setblock 0 5 33 minecraft:command_block{Command:"aeallpattern seed-test-materials 3 5 -15",auto:0b} replace
setblock 0 6 33 minecraft:stone_button[face=floor] replace
setblock 6 5 33 minecraft:command_block{Command:"function aeallpattern_test:showcase",auto:0b} replace
setblock 6 6 33 minecraft:stone_button[face=floor] replace
summon minecraft:text_display -6 7 33 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,text:'{"text":"回到开场","color":"light_purple"}' }
summon minecraft:text_display 0 7 33 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,text:'{"text":"补充 AE 原料","color":"aqua"}' }
summon minecraft:text_display 6 7 33 {Tags:["aeap_showcase_text"],billboard:"center",alignment:"center",background:0,shadow:1b,text:'{"text":"重置场景","color":"light_purple"}' }

aeallpattern seed-showcase-patterns -4 5 1 10 5 25
schedule function aeallpattern_test:showcase_patterns 2s replace
schedule function aeallpattern_test:showcase_seed 3s replace
schedule function aeallpattern_test:showcase_eco 4s replace
scoreboard players reset @a aeap_showcase_v12
function aeallpattern_test:showcase_player
tellraw @a {"text":"纯净演示场已就绪：沿紫色中线依次完成五个演示环节。","color":"light_purple"}
