---
navigation:
  title: All Pattern Linker and Binder
  position: 2
  icon: aeallpattern:pattern_linker
item_ids:
  - aeallpattern:pattern_linker
  - aeallpattern:pattern_binder
---

# All Pattern Linker and Binder

The Linker publishes **live virtual processing patterns** from supported machine adapters. It is different from the Generator, which reads recipe-viewer catalogs into physical Aggregate Patterns.

1. Connect an All Pattern Linker to a powered ME network with an available channel.
2. Hold an All Pattern Binder and **sneak-right-click the Linker** to select it.
3. Keep sneak-right-clicking supported machines to bind them. A purple outline indicates a successful binding.
4. Provide the machine's required power and return its outputs to the network.

The Linker uses one channel and 2 AE/t. It buffers accepted inputs before transferring them to the bound machine. The Binder is reusable; selecting an anchor allows multiple consecutive bindings.

## Which machines work?

Binding requires an implemented machine adapter. Seeing a machine's recipes in JEI does **not** mean the Linker can bind it. For a generic or custom multiblock, try the Generator and a Pattern Provider instead.

Check that the Linker is online, its channel is available, the machine/chunk is loaded, and the configured distance/dimension rules allow the binding.

[Generator workflow](aggregate_patterns.md) · [Routing](tianshu_router.md) · [Overview](index.md)
