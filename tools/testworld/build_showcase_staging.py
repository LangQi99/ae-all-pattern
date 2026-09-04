#!/usr/bin/env python3
"""Create a fresh Minecraft 1.20.1 showcase staging directory.

The script only installs the datapack and guide. Minecraft creates level.dat,
chunks, block entities and player data when the dedicated server opens the
directory, keeping all version-specific world writes inside the game.
"""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path


PACK_FORMAT = 15
MOD_VERSION = "0.2.1"
NAMESPACE = "aeallpattern_test"


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

    source = Path(__file__).resolve().parent / "showcase"
    pack = world / "datapacks" / "aeallpattern_test"
    functions = pack / "data" / NAMESPACE / "functions"
    structures = pack / "data" / NAMESPACE / "structures"
    recipes = pack / "data" / NAMESPACE / "recipes"

    write_json(pack / "pack.mcmeta", {
        "pack": {
            "pack_format": PACK_FORMAT,
            "description": (
                f"AE All Pattern {MOD_VERSION} Minecraft 1.20.1 showcase and compatibility lab"
            ),
        }
    })

    for function in sorted(source.glob("*.mcfunction")):
        functions.mkdir(parents=True, exist_ok=True)
        shutil.copy2(function, functions / function.name)

    structures.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / "structure" / "eco_craft_min.nbt", structures / "eco_craft_min.nbt")

    write_json(pack / "data" / "minecraft" / "tags" / "functions" / "load.json",
               json.loads((source / "load.json").read_text(encoding="utf-8")))
    write_json(recipes / "same_output_from_stone_bricks.json", {
        "type": "minecraft:smelting",
        "category": "misc",
        "ingredient": {"item": "minecraft:stone_bricks"},
        "result": "minecraft:stone",
        "experience": 0.0,
        "cookingtime": 40,
    })

    shutil.copy2(source / "AE_ALL_PATTERN_TEST_GUIDE.md", world / "AE_ALL_PATTERN_TEST_GUIDE.md")
    write_json(world / "showcase-plan.json", {
        "schema": 1,
        "world": world.name,
        "minecraft": "1.20.1",
        "loader": "forge-47.4.20",
        "mod_version": MOD_VERSION,
        "function": f"{NAMESPACE}:showcase",
        "writes_region": False,
    })

    print(f"Created safe Minecraft 1.20.1 showcase staging directory: {world}")
    print(f"Next: let Minecraft create the world, then run `function {NAMESPACE}:showcase`")


if __name__ == "__main__":
    main()
