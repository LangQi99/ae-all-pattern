---
navigation:
  title: Tianshu Pattern Router
  position: 3
  icon: aeallpattern:tianshu_pattern_selector
item_ids:
  - aeallpattern:tianshu_pattern_selector
---

# Tianshu Pattern Router

The Router chooses a recipe route when several patterns produce the same item. **It is not a crafting CPU, a Pattern Provider or a machine.** Keep your ordinary AE2 crafting CPU and production machines.

1. Connect the Router to the same ME network and ensure it is online.
2. Right-click the Router to edit the network's default routing preferences.
3. Request an item in a crafting terminal. The crafting confirmation screen offers temporary preferences for this order.

Feasibility always comes first. Other preferences can favor shorter dependency chains, more available materials, higher output per operation or less waiting for busy machines. Reorder, reverse or disable those preferences as needed.

Aggregate patterns have priority -1 by default, so manually encoded patterns at priority 0 take precedence.

## Advanced qualifications

Independent byproduct orders are off by default. Enabling them can start an expensive recipe just to obtain a secondary output.

Amplifying cycles handle direct positive-gain loops. They still need a real starting seed; the Router cannot create the initial material from nothing.

If the routing controls are absent, check that the Router is online in the same network. The Router does not itself expose machine recipes: use physical patterns or a supported Linker binding.

[Overview](index.md)
