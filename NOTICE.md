# Third-party notice

AE All Pattern is an independent add-on and is not affiliated with Mojang,
Microsoft, Applied Energistics 2, JEI, or Mekanism.

- Applied Energistics 2 is used through its published API and Maven artifact.
- AE2 Lightning Tech is the source of the adapted gameplay textures, models,
  and single-block CPU host listed below.
- Selected Thunderbolt Core 1.0.6 planning code is adapted into the private
  `io.github.langqi99.aeallpattern.internal.routing` package. It is part of
  AE All Pattern rather than a nested or runtime Thunderbolt mod.
- JEI is an optional client-side contextual-help integration.
- MixinExtras 0.4.1 is bundled as a runtime library under the MIT license;
  its nested JAR includes `LICENSE_MixinExtras`.
- Mekanism is an optional machine integration target used through its public API.
- Minecraft and all referenced mod names and assets belong to their respective owners.

## AE2 Lightning Tech adaptations

The following files are adaptations of assets from
[AE2 Lightning Tech](https://github.com/ae2lt/AE2-Lightning-Tech) at revision
`379b99a3ef188218caab0071b08d1c707d7e9e27`:

- `pattern_binder.png` is based on
  `wireless_tianshu_pattern_encoding_terminal.png`. Its screen contains a
  six-by-six crop adapted from `matter_warping_matrix_overload_main_core.png`.
- `pattern_linker.png` is based on
  `matter_warping_matrix_overload_main_core.png`. Its pink core pixels were
  remapped to the Tianshu terminal's purple palette and two diagonal pixels
  were changed to a blue link marker.
- `tianshu_pattern_selector.json` and
  `tianshu_pattern_selector_active.json` are namespace-renamed adaptations of
  the upstream Tianshu supercomputer controller block models.
- `tianshu_pattern_selector.png` and
  `tianshu_pattern_selector_active.png` are namespace-renamed copies of the
  matching 64x64 controller textures.

The Java single-block host in `io.github.langqi99.aeallpattern.tianshu` is
adapted from AE2 Lightning Tech's removed `TestTimeWheelCraftingCpuBlock` and
`TestTimeWheelCraftingCpuBlockEntity` at revision
`fe8590ea45becd0c5f4ab67f4e779612eff09a8a`. It has been renamed, integrated
with this mod's registries and resource namespace, given an active blockstate,
and converted into a routing-only network controller. It does not register
storage, co-processors, or any crafting CPU capability.

AE2 Lightning Tech credits its project team on the upstream repository. Its
textures and other visual assets are licensed under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).
The adapted texture and model files are distributed under the same license.

AE2 Lightning Tech source code is licensed under GNU LGPL 3.0. The adapted
single-block host remains covered by that license.

## Adapted routing engine

The private routing engine is adapted from
[Thunderbolt Core](https://github.com/ae2lt/Thunderbolt-Core), upstream release
1.0.6 (source snapshot 1.0.6.1). Selected planner and AE2 adapter code was moved
to the private AE All Pattern namespace and modified for router-scoped
activation. It remains licensed under GNU LGPL 3.0; the upstream `LICENSE` is
retained under `third_party/thunderbolt-core` and included in the release JAR.

No upstream asset namespace is bundled: adapted assets live under the
`aeallpattern` namespace. The remaining project artwork, including the mod
icon, is an original project asset.
