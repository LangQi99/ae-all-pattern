---
navigation:
  title: Generator and Aggregate Patterns
  position: 1
  icon: aeallpattern:all_pattern_generator
item_ids:
  - aeallpattern:all_pattern_generator
  - aeallpattern:aggregate_pattern
---

# Generator and Aggregate Patterns

1. Install JEI, or EMI with TooManyRecipeViewers, on the client.
2. Hold an All Pattern Generator and **sneak-right-click the machine/controller**.
3. Put the resulting Aggregate Pattern into an AE2 Pattern Provider or supported provider add-on.
4. Supply the machine with power and arrange its item/fluid inputs and outputs. Return finished products to the ME network.

Crafting-table patterns use a Molecular Assembler. Processing patterns still need the actual machine; generating a pattern does not construct or power it.

## Managing a catalog

Hold an Aggregate Pattern and right-click to open its management panel. Use the tabs to select recipes and edit encoding settings. Inputs are ingredients consumed or required by the recipe; outputs are the products returned. Searching filters the catalog, not the machine's physical inventory.

Large catalogs are stored in the server library and transferred in bounded pages. Keep the world/server data when moving a save, not just the pattern item. Back up the world before updating the mod. Recipe selections belong to each physical pattern.

Disabling a recipe removes it from the published catalog. Encoding options such as removing fluids, chemicals or catalysts can make patterns unsuitable for the real machine: enable them only when your automation supplies those requirements separately.

[Back to overview](index.md)

