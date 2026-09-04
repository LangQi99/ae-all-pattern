#!/usr/bin/env python3
"""Create a new AE All Pattern test-lab datapack staging directory.

This intentionally writes no region or level NBT. Minecraft must load the new
world and execute aeallpattern_test:build so the game owns every world write.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


PACK_FORMAT = 15
MOD_VERSION = "0.2.1"
NAMESPACE = "aeallpattern_test"


def sign(x: int, y: int, z: int, *lines: str, color: str = "dark_purple") -> str:
    messages = []
    for line in (*lines, "", "", "", "")[:4]:
        component = json.dumps({"text": line, "color": color}, ensure_ascii=False, separators=(",", ":"))
        messages.append("'" + component.replace("'", "\\'") + "'")
    return (
        f"setblock {x} {y} {z} minecraft:oak_sign[rotation=8]"
        f"{{front_text:{{messages:[{','.join(messages)}]}}}} replace"
    )


def barrel(commands: list[str], x: int, y: int, z: int, items: list[tuple[str, int]]) -> None:
    commands.append(f"setblock {x} {y} {z} minecraft:barrel[facing=up] replace")
    for slot, (item, count) in enumerate(items):
        commands.append(f"item replace block {x} {y} {z} container.{slot} with {item} {count}")


def build_commands() -> list[str]:
    commands = [
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule keepInventory true",
        "gamerule spawnRadius 0",
        "gamerule doMobSpawning false",
        "time set day",
        "weather clear",
        "kill @e[type=minecraft:item,distance=..96]",
        "fill -24 5 -18 24 18 24 minecraft:air replace",
        "fill -24 4 -18 24 4 24 minecraft:smooth_stone replace",
        "fill -22 4 -16 22 4 -10 minecraft:purple_concrete replace",
        "fill -22 4 -8 22 4 2 minecraft:light_gray_concrete replace",
        "fill -22 4 4 22 4 10 minecraft:orange_concrete replace",
        "fill -22 4 12 22 4 22 minecraft:cyan_concrete replace",
        "setworldspawn 0 5 -14",
        "forceload add -24 -18 24 24",
        sign(0, 5, -15, "全样板测试场", f"版本 {MOD_VERSION}", "创造模式 · 和平", "从紫色区域开始"),
        sign(-16, 5, -11, "紫色区域", "AE2 主网络", "链接器 + 合成 CPU", "原料已存入 AE"),
        sign(-16, 5, 3, "橙色区域", "原版熔炉组", "燃料已经放入", "产物自动回收"),
        sign(-16, 5, 11, "青色区域", "通用机械", "单机 + 工厂", "能量立方向上弹出"),
        sign(14, 5, -7, "诊断区域", "/aeallpattern status", "/aeallpattern perf", "/reload"),
        sign(14, 5, -3, "压力配方包", "1000 个熔炼配方", "用于重载测试", "重载后检查性能"),
        # AE2 core and a compact 2x2 crafting CPU.
        "setblock 0 5 -5 ae2:controller replace",
        "setblock -1 5 -5 ae2:creative_energy_cell replace",
        "setblock 1 5 -5 ae2:drive replace",
        "item replace block 1 5 -5 container.0 with ae2:item_storage_cell_64k 1",
        "setblock -1 6 -5 ae2:cable_bus{cable:{id:\"ae2:fluix_glass_cable\"},south:{id:\"ae2:terminal\",enabledKeyTypes:[\"ae2:i\",\"ae2:f\"]}} replace",
        "setblock 0 6 -5 ae2:cable_bus{cable:{id:\"ae2:fluix_glass_cable\"},south:{id:\"ae2:crafting_terminal\",enabledKeyTypes:[\"ae2:i\",\"ae2:f\"]}} replace",
        "setblock 1 6 -5 ae2:cable_bus{cable:{id:\"ae2:fluix_glass_cable\"},south:{id:\"ae2:pattern_encoding_terminal\",enabledKeyTypes:[\"ae2:i\",\"ae2:f\"]}} replace",
        "setblock 2 6 -5 ae2:cable_bus{cable:{id:\"ae2:fluix_glass_cable\"},south:{id:\"ae2:pattern_access_terminal\",enabledKeyTypes:[\"ae2:i\",\"ae2:f\"]}} replace",
        "setblock -1 5 -4 minecraft:air replace",
        "setblock 0 5 -4 aeallpattern:pattern_linker replace",
        "setblock 0 5 -6 ae2:1k_crafting_storage replace",
        "setblock 1 5 -6 ae2:crafting_unit replace",
        "setblock 0 6 -6 ae2:crafting_unit replace",
        "setblock 1 6 -6 ae2:crafting_monitor replace",
        "setblock 2 5 -5 ae2:molecular_assembler replace",
        "setblock 2 5 -6 ae2:interface replace",
        "setblock -2 5 -5 ae2:pattern_provider replace",
        sign(0, 7, -7, "AE 主网络", "4 种终端朝南", "原料写入 64K", "CPU + 存储磁盘"),
        sign(-7, 5, -7, "步骤一", "从木桶拿绑定器", "右击紫色链接器", "不要潜行"),
        sign(7, 5, -7, "步骤二", "潜行右击机器", "可连续绑定多台", "有效距离 64 格"),
    ]

    barrel(commands, -5, 5, -5, [
        ("aeallpattern:pattern_binder", 1),
        ("aeallpattern:pattern_linker", 8),
        ("ae2:fluix_glass_cable", 64),
        ("ae2:crafting_terminal", 4),
        ("ae2:pattern_encoding_terminal", 2),
        ("ae2:import_bus", 16),
        ("ae2:export_bus", 16),
        ("ae2:storage_bus", 16),
        ("ae2:certus_quartz_wrench", 1),
        ("ae2:blank_pattern", 64),
        ("ae2:item_storage_cell_64k", 1),
        ("ae2:controller", 8),
        ("ae2:creative_energy_cell", 8),
        ("ae2:terminal", 2),
        ("ae2:pattern_access_terminal", 2),
    ])

    # This is a manual backup supply. The seed function writes the real test
    # inventory directly into the 64K cell so terminal visibility does not
    # depend on an external-storage channel or side configuration.
    barrel(commands, -1, 5, -3, [
        ("minecraft:raw_iron", 16),
        ("minecraft:raw_gold", 16),
        ("minecraft:raw_copper", 16),
        ("minecraft:cobblestone", 16),
        ("minecraft:beef", 16),
        ("minecraft:potato", 16),
        ("minecraft:redstone", 16),
        ("minecraft:quartz", 16),
        ("minecraft:diamond", 8),
        ("minecraft:oak_log", 16),
        ("minecraft:stone_bricks", 16),
        ("mekanism:raw_osmium", 16),
        ("mekanism:raw_tin", 16),
        ("mekanism:raw_lead", 16),
        ("mysticalagriculture:prosperity_seed_base", 16),
        ("mysticalagriculture:inferium_essence", 64),
        ("mysticalagriculture:tertium_essence", 32),
    ])
    commands.append(sign(-1, 6, -3, "备用原料仓", "仅供手动取用", "预存物品在磁盘", "不接存储总线"))

    # Vanilla stations. Hopper below each machine demonstrates deterministic output extraction.
    vanilla = [
        (-12, "minecraft:furnace", "熔炉"),
        (-6, "minecraft:blast_furnace", "高炉"),
        (0, "minecraft:smoker", "烟熏炉"),
    ]
    for x, block, label in vanilla:
        commands.extend([
            f"setblock {x} 6 7 {block}[facing=south] replace",
            f"setblock {x} 5 7 minecraft:smooth_stone replace",
            f"item replace block {x} 6 7 container.1 with minecraft:coal 64",
            sign(x, 7, 6, label, "绑定任意有效面", "燃料已经放入", "产物自动回到 AE"),
        ])
    barrel(commands, 6, 5, 7, [
        ("minecraft:raw_iron", 64),
        ("minecraft:raw_gold", 64),
        ("minecraft:raw_copper", 64),
        ("minecraft:coal", 64),
        ("minecraft:oak_log", 64),
        ("minecraft:beef", 64),
        ("minecraft:potato", 64),
        ("minecraft:cobblestone", 64),
    ])

    # Mekanism single machines and matching basic factories, each on creative power.
    mekanism_rows = [
        (-12, "mekanism:energized_smelter", "充能冶炼炉"),
        (-6, "mekanism:crusher", "粉碎机"),
        (0, "mekanism:enrichment_chamber", "富集仓"),
    ]
    for x, block, label in mekanism_rows:
        commands.extend([
            f"setblock {x} 5 15 mekanism:creative_energy_cube[facing=up] replace",
            f"data merge block {x} 5 15 {{component_config:{{eject0:1b,config0:[I;4,0,0,0,0,0]}},energy_containers:[{{container:0b,stored:9223372036854775807L}}]}}",
            f"setblock {x} 6 15 {block} replace",
            f"data merge block {x} 6 15 {{component_config:{{config0:[I;1,1,1,1,1,1]}}}}",
            sign(x, 7, 14, label, "能量立方向上弹出", "绑定任意有效面", "产物自动回到 AE"),
        ])
    factories = [
        (-12, "mekanism:basic_smelting_factory", "基础冶炼工厂"),
        (-6, "mekanism:basic_crushing_factory", "基础粉碎工厂"),
        (0, "mekanism:basic_enriching_factory", "基础富集工厂"),
        (6, "mekanism:basic_infusing_factory", "基础灌注工厂"),
    ]
    for x, block, label in factories:
        commands.extend([
            f"setblock {x} 5 20 mekanism:creative_energy_cube[facing=up] replace",
            f"data merge block {x} 5 20 {{component_config:{{eject0:1b,config0:[I;4,0,0,0,0,0]}},energy_containers:[{{container:0b,stored:9223372036854775807L}}]}}",
            f"setblock {x} 6 20 {block} replace",
            f"data merge block {x} 6 20 {{component_config:{{config0:[I;1,1,1,1,1,1]}}}}",
            sign(x, 7, 19, label, "能量立方向上弹出", "绑定任意有效面", "产物自动回到 AE"),
        ])
    barrel(commands, 6, 5, 15, [
        ("minecraft:raw_iron", 64),
        ("minecraft:raw_gold", 64),
        ("minecraft:cobblestone", 64),
        ("minecraft:quartz", 64),
        ("minecraft:redstone", 64),
        ("minecraft:diamond", 64),
        ("mekanism:raw_osmium", 64),
        ("mekanism:raw_tin", 64),
        ("mekanism:raw_lead", 64),
        ("mekanism:configurator", 1),
    ])
    # This multiblock is deliberately discovered through its JEI catalyst.
    # No Mystical Agriculture class or machine whitelist is used by the mod.
    commands.extend([
        "setblock 14 6 18 mysticalagriculture:infusion_altar replace",
        "setblock 12 6 16 mysticalagriculture:infusion_pedestal replace",
        "setblock 14 6 16 mysticalagriculture:infusion_pedestal replace",
        "setblock 16 6 16 mysticalagriculture:infusion_pedestal replace",
        "setblock 12 6 18 mysticalagriculture:infusion_pedestal replace",
        "setblock 16 6 18 mysticalagriculture:infusion_pedestal replace",
        "setblock 12 6 20 mysticalagriculture:infusion_pedestal replace",
        "setblock 14 6 20 mysticalagriculture:infusion_pedestal replace",
        "setblock 16 6 20 mysticalagriculture:infusion_pedestal replace",
        sign(14, 7, 14, "神秘农业注魔祭坛", "潜行右击中央祭坛", "由 JEI 通用扫描", "无机器白名单"),
    ])
    commands.extend([
        sign(8, 5, 5, "推荐配方一", "粗铁 → 铁锭", "粗金 → 金锭", "从合成终端下单"),
        sign(12, 5, 5, "推荐配方二", "牛肉 → 熟牛排", "马铃薯 → 烤马铃薯", "使用烟熏炉"),
        sign(8, 5, 13, "推荐配方三", "圆石 → 沙砾", "粗锇 → 锇粉×2", "尝试粉碎与富集"),
        sign(13, 5, 12, "测试清单", "生成聚合样版", "放入样板供应器", "重启后再次测试"),
        "setblock 14 5 2 minecraft:command_block{Command:\"aeallpattern perf\",auto:0b} replace",
        "setblock 14 6 2 minecraft:stone_button[face=floor] replace",
        "setblock 18 5 2 minecraft:command_block{Command:\"reload\",auto:0b} replace",
        "setblock 18 6 2 minecraft:stone_button[face=floor] replace",
        sign(14, 5, 1, "性能诊断按钮", "执行性能诊断", "结果显示在聊天栏", "并写入日志"),
        sign(18, 5, 1, "重载按钮", "重新加载数据包", "配方目录代数", "应当增加一次"),
        "spawnpoint @a 0 5 -14",
        "gamemode creative @a",
        "effect give @a minecraft:night_vision infinite 0 true",
        "schedule function aeallpattern_test:seed 2s replace",
        f"tellraw @a {{\"text\":\"全样板 {MOD_VERSION} 中文测试场已准备完成。请从紫色区域开始测试。\",\"color\":\"light_purple\"}}",
    ])
    return commands


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", type=Path, required=True, help="new, non-existing world directory")
    args = parser.parse_args()
    world = args.world.resolve()
    if world.exists():
        raise SystemExit(f"refusing to modify existing path: {world}")

    pack = world / "datapacks" / "aeallpattern_test"
    write_json(pack / "pack.mcmeta", {
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": f"AE All Pattern {MOD_VERSION} deterministic test lab for Minecraft 1.20.1",
        }
    })
    function_path = pack / "data" / NAMESPACE / "functions" / "build.mcfunction"
    function_path.parent.mkdir(parents=True, exist_ok=True)
    function_path.write_text("\n".join(build_commands()) + "\n", encoding="utf-8")
    seed_function_path = pack / "data" / NAMESPACE / "functions" / "seed.mcfunction"
    seed_function_path.write_text(
        "aeallpattern seed-test-materials 0 5 -4\n"
        "tellraw @a {\"text\":\"AE 预存原料已经写入 64K 存储元件。\",\"color\":\"aqua\"}\n",
        encoding="utf-8",
    )

    recipe_dir = pack / "data" / NAMESPACE / "recipes"
    for index in range(1000):
        write_json(recipe_dir / f"stress_{index:04d}.json", {
            "type": "minecraft:smelting",
            "category": "misc",
            "ingredient": {"item": "minecraft:cobblestone"},
            "result": "minecraft:stone",
            "experience": 0.0,
            "cookingtime": 20,
        })
    write_json(recipe_dir / "same_output_from_stone_bricks.json", {
        "type": "minecraft:smelting",
        "category": "misc",
        "ingredient": {"item": "minecraft:stone_bricks"},
        "result": "minecraft:stone",
        "experience": 0.0,
        "cookingtime": 40,
    })

    guide = f"""# AE All Pattern {MOD_VERSION} 中文测试场

适用版本：Minecraft 1.20.1 / Forge 47.4.20 / AE2 15.4.10 / Mekanism 10.4.16 / Mystical Agriculture 7.0.18。

## 第一次使用

1. 进入存档后前往紫色 AE2 区域。
2. 从补给木桶取出“全样板绑定器”。
3. 不潜行，右击已供电的“全样板链接器”。
4. 潜行右击受支持机器的输入面；选择不会清空，可以继续绑定其他机器，机器周围会出现紫色立体框架。
5. 打开朝南摆放的 ME 合成终端，在可合成项目中选择输出并下单。
6. 链接器会强制接管输入，并将机器可抽取的全部产物自动送回同一个 ME 网络。

## 已准备设备

- 紫色区域：AE2 控制器、创造能源元件、64K 存储元件、链接器、合成 CPU、分子装配室、接口、样板供应器，以及朝南摆放的 ME 终端、ME 合成终端、ME 样板编码终端和 ME 样板管理终端。
- AE 预存原料：包含原版、通用机械和神秘农业测试材料，已直接写入 64K 存储元件；旁边的原料木桶仅供手动取用，不连接存储总线。
- 橙色区域：熔炉、高炉、烟熏炉，燃料已经放入。
- 青色区域：充能冶炼炉、粉碎机、富集仓、基础灌注工厂，以及神秘农业完整注魔祭坛；全部通过 JEI 催化剂通用扫描，不使用生成器白名单。
- 灰色区域：`/aeallpattern status`、`/aeallpattern perf` 和 `/reload` 诊断按钮。

## 推荐测试配方

- 粗铁 → 铁锭；粗金 → 金锭；粗铜 → 铜锭。
- 牛肉 → 熟牛排；马铃薯 → 烤马铃薯。
- 圆石 → 沙砾；粗锇 → 锇粉；也可尝试粗锡、粗铅和红石相关富集配方。

## 必测项目

- 绑定在保存和重启后仍然存在，解绑后消失。
- 缺少频道或能源时停止发布虚拟样板。
- 替换机器后旧路线安全失效。
- 输入堵塞时原料仍由链接器持有；解绑或破坏链接器时能够回收。
- ME 存储已满时，产物留在机器内；恢复容量后继续自动回收。
- `/reload` 只增加一次目录代数，不产生重复虚拟样板。
- 1000 条压力配方能够加载，并被确定性去重逻辑安全折叠。

本环境不包含 KubeJS；数据包重载探针用于当前依赖组合的重载测试。
"""
    (world / "AE_ALL_PATTERN_TEST_GUIDE.md").write_text(guide, encoding="utf-8")
    write_json(world / "lab-plan.json", {
        "schema": 1,
        "world": world.name,
        "minecraft": "1.20.1",
        "loader": "forge-47.4.20",
        "mod_version": MOD_VERSION,
        "function": f"{NAMESPACE}:build",
        "stress_recipes": 1000,
        "writes_region": False,
    })
    print(f"Created safe staging directory: {world}")
    print(f"Next: start Minecraft server and run `function {NAMESPACE}:build`")


if __name__ == "__main__":
    main()
