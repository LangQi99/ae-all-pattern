---
navigation:
  title: Multiblocks and Troubleshooting
  position: 4
---

# Multiblocks and Troubleshooting

## Masterful Machinery

Sneak-right-click a **controller block** with the Generator, not an input port, casing or a blueprint icon. A JEI category icon is only artwork and is not necessarily a registered workstation.

The integration resolves structure/controller membership rather than guessing from the icon. If a controller belongs to several structures, their processing recipes must be collected without including unrelated machines. Your pack's structure definitions and recipes must load successfully first.

This concerns **Aggregate Pattern generation**, not automatic Linker support. Connect the Pattern Provider to the actual input ports and extract finished outputs back to AE. Keep energy, special resources and multiblock formation requirements supplied.

## No recipes or incomplete patterns?

- Wait for the world and recipe viewer to finish loading.
- Check that the controller and its recipes exist in this pack.
- Verify the generated input/output amounts against the machine.
- Not every custom ingredient type can be represented as an AE key. Energy, mana, heat, chance results and reusable tools may need special handling.
- Check encoding options before assuming a missing recipe is a discovery problem.
- After changing pack scripts, reload/restart as required by the machine mod.

For crashes, attach the failing run's **logs/latest.log** and crash report, plus Minecraft, loader and mod versions. Back up the save before testing updates.

[Generator workflow](aggregate_patterns.md) · [Overview](index.md)
