# Recipe-viewer catalyst inputs

The Remove processing catalysts option preserves explicit per-input evidence from JEI/EMI
in aggregate catalogs. It does not guess from item names or compare arbitrary recipe outputs
to infer reusable tools.

- Standalone JEI CATALYST slots / EMI workstation catalysts remain outside material inputs.
- An actual processing input matching a complete, explicit per-recipe catalyst declaration
  can be marked reusable. Candidate identities and amounts must match; partial or truncated
  declarations are not sufficient. JEI tag-compacted inputs are conservatively left unchanged.
- EMI inputs can carry an explicit EmiStack.getRemainder(). Every candidate must return
  precisely the same item, amount and NBT/components with certainty. Buckets returning empty
  buckets, damaged tools and chance-based returns do not qualify.
- If the same item is also published in recipe outputs, keep the input. Paired input/output
  removal is deliberately outside this implementation, to avoid phantom craftable products.
- Existing aeallpattern:processing_catalysts tag support is retained.
- The option only affects processing patterns. Off retains actual input slots; on removes
  marked inputs. Recipes with no remaining inputs are not published.

Keep reusable tools in the real machine; removing an AE input does not supply a tool.
Unmarked ordinary inputs remain required. Mods that expose reusable gears only as ordinary
INPUT slots with no explicit declaration/remainder still need an upstream viewer integration
or a separate machine-specific adapter.

Old saves default missing evidence to false. Rescan the machine (or let startup catalog
refresh finish) to obtain viewer evidence. Evidence survives disk/codec/network roundtrips.
The network protocol is now 2: update the client and server together.
