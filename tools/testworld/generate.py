#!/usr/bin/env python3
"""Generate a deterministic test-lab plan without modifying a Minecraft save."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


LAB_PLAN = {
    "schema": 1,
    "minecraft": "1.20.1",
    "writes_world": False,
    "stations": [
        {
            "id": "vanilla_furnace",
            "requires": ["minecraft", "ae2", "aeallpattern"],
            "checks": ["bind", "catalog", "craft", "return_output", "restart"],
        },
        {
            "id": "mekanism_smelting",
            "requires": ["minecraft", "ae2", "mekanism", "aeallpattern"],
            "checks": ["bind", "catalog", "factory", "side_config", "restart"],
        },
        {
            "id": "reload_and_stress",
            "requires": ["minecraft", "ae2", "aeallpattern"],
            "checks": ["datapack_reload", "kubejs_reload", "1000_recipes", "idle_profile"],
        },
    ],
    "safety": [
        "stop_game",
        "absolute_world_path",
        "zip_backup",
        "empty_target_scan",
        "dry_run",
        "temporary_copy",
        "post_write_assertions",
    ],
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(LAB_PLAN, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote plan only (no world modified): {args.output.resolve()}")


if __name__ == "__main__":
    main()
