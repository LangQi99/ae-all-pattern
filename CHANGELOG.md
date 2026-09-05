# Changelog

## Unreleased

## 0.2.2 - 2026-09-05

- Aggregate generation now keeps a machine catalog in one physical pattern item instead of creating numbered `[1/2]` parts.
- Raised the single Aggregate Pattern recipe limit to 1,048,576 while retaining bounded network-transfer and server-storage pages.
- Existing aggregate catalogs are rescanned once per server startup by the first available JEI or EMI/TMRV client and are replaced under the same server-library UUID.
- Recipe selections now discard deleted IDs and automatically store the smaller of the enabled-ID and disabled-ID sets after catalog changes.
- Item substitution and output-component ignoring are now enabled by default; aggregate management uses compact AE-style side tabs and one combined input/output search field.
- Aggregate selection now has server-backed previous/next controls; each UI page loads 1,024 recipes by default while search and bulk selection still cover the complete catalog.
- Added a default-on Tianshu Router qualification for direct self-amplifying recipes such as `A + D -> 2 A`; planning reserves a real startup seed, schedules net growth, and rejects zero-seed or non-growing loops.
- Added four dedicated amplifying-cycle CI gates covering recipe classification, closed-form demand planning, the AE adapter policy gate, and policy migration/persistence.
- Added a Forge 1.20.1 build line with Java 17 and Applied Energistics 2 15.4.10 compatibility.
- Fixed large Aggregate Patterns failing to open their management screen by moving initial recipes and selection state to bounded, server-backed pages.

## 0.2.1 - 2026-09-03

- Unified aggregate-pattern settings and child-pattern selection into one right-click management panel with separate tabs, search scopes, and clearer enabled-state styling.
- Added a default-on safeguard that excludes recipes which consume tool durability while keeping unchanged catalysts and returned containers available.
- Added paged aggregate metadata and paged generation transfers so catalogs with thousands of recipes no longer depend on one oversized payload.
- Expanded compatibility coverage for AE provider add-ons, PackagedAuto, Extended Crafting, Mekanism, Mekanism Extras, Mekanism More Machine, Create, Mystical Agriculture, Industrial Foregoing, and Neo ECO AE Extension.
- Added JEI and EMI/TMRV-specific scanner tests, real client startup smoke tests, and nine no-GUI GameTest dependency profiles that download pinned mod fixtures in CI.

## 0.1.25 - 2026-09-01

- Added aggregate-pattern support for ExtendedAE and ExtendedAE Plus assembler matrices, including slot validation and full child-pattern publication.
- Fixed PackagedExCrafting tables omitting recipes supported by lower table tiers.
- Fixed tagged Extended Crafting recipes being dropped when an ingredient expanded beyond the old 32-alternative limit.
- Added a configurable ingredient-tag expansion limit, defaulting to 1024 with a supported range of 1-2147483647, and exposed it in the config screen.
- Unified aggregate recipe limits across server adapters, JEI, EMI, network uploads, saved libraries, and selection paging.

## 0.1.24 - 2026-09-01

- Fixed aggregate provider refresh callbacks being lost when expansion ticks hit their time budget.
- Ensured completed aggregate jobs always republish to every registered provider after reloads and world reopen.

## 0.1.23 - 2026-09-01

- Mapped all Mekanism More Machine and Mekanism Extras factories to their real single-block workstations.
- Fixed incorrect workstation ids for injection, sawing, chemical processing, and MekMM machines.
- Added support for every shipped MekMM factory tier and normalized mismatched JEI category names.
- Fixed aggregate patterns disappearing after reopening a world or reloading datapacks until manually reinserted.
- Refresh loaded AE2, ECO, matrix, Advanced AE, AE2 Lightning Tech, and packaged providers after recipe reloads.
- Added aggregate expansion and reload refresh support for AE2 Crystal Science integrated and mirror providers.

## 0.1.22 - 2026-09-01

- Fixed EMI aggregate scans failing when a category and recipe produced a pattern id longer than the 160-character protocol limit.
- Kept long pattern ids stable and unique by retaining a readable prefix with a SHA-256 suffix.

## 0.1.21 - 2026-09-01

- Fixed pure-JEI pattern generation for Mekanism's Chemical Oxidizer by mapping its machine id to the oxidizing recipe category.
- Added a configurable aggregate-pattern picker result limit, defaulting to 1024 with a supported range of 1-16384.
- Made aggregate-pattern searches cover the complete recipe library while limiting only the displayed results.
- Right-aligned the result-limit notice and shortened it to "First xx results".
- Prevented the inventory key from closing the picker while typing in its search box.

## 0.1.20 - 2026-09-01

- Deferred aggregate expansion callbacks to keep provider updates off the current tick and preserve compatibility with optional add-ons.
- Restored compatibility and null-safety guards across routing, machine adapters, and client integrations.

## 0.1.19 - 2026-08-30

- Let Advanced AE's advanced pattern encoder open infusing-factory aggregate patterns: the editor now sees the first selected child and can edit it.
- Kept every other aggregate non-editable in external editors by handing back the marker wrapper, and never expose deselected children.
- Gated the encoder mixin behind `advanced_ae` so clean builds without the addon stay untouched.
- Added regression coverage for editable infusing aggregates, editor-locked non-infusing aggregates, and deselected children.

## 0.1.18 - 2026-08-30

- Fixed the generator picking the wrong JEI category for a machine: it matched the first category sharing the namespace, so a Mekanism infusing factory produced infusion-conversion recipes instead of metallurgic infusing ones.
- Category selection now derives a keyword from the machine id, prefers a category whose path contains it, falls back to the longest common prefix, and only then to the previous behaviour.
- Added unit coverage that an `infusing_factory` must resolve to `metallurgic_infusing` rather than `infusion_conversion`.

## 0.1.17 - 2026-08-30

- Fixed a startup crash (`IllegalClassLoadError`) introduced in 0.1.16: the shared `Reflect` helper lived inside the package owned by `aeallpattern.mixins.json`, which transformed target classes may not reference at runtime.
- Moved the helper to `io.github.langqi99.aeallpattern.util` and added a GameTest that loads the addon provider mixins when the addon is present.

## 0.1.16 - 2026-08-30

- Fixed add-on pattern providers that ship their own pattern list logic reading only the first child of an aggregate: Pigmee provider (AE2 Lightning Tech), advanced and extended advanced providers (Advanced AE), and the stable/packaged provider (ae2ltpp).
- Shared the expansion plumbing through `AggregateProviderExpansion` and a small reflection helper, injecting before each host's `requestUpdate` so the complete list is in place before the network is notified.
- Guarded against re-entrant self-scheduling when an aggregate has no selected children.
- Note: this release was superseded by 0.1.17 because of the class-loading crash above.

## 0.1.15 - 2026-08-29

- Fixed opening an AE2 terminal stalling on large aggregates: the per-recipe expansion cache was far smaller than the recipe count, so every refresh re-encoded the whole aggregate.
- Raised the expansion caches to hold a full multi-thousand-recipe aggregate for the whole session.
- Stopped notifying the network when a provider's expanded list did not actually change, so the crafting index is no longer rebuilt on every refresh.
- Moved the Lightning Tech matrix, ECO pattern bus, and overloaded-catalog hosts from synchronous full expansions to the scheduled cross-tick expansion.

## 0.1.14 - 2026-08-29

- Fixed a startup crash where the Lightning Tech matrix mixin used a `@Shadow` field type that does not match the shipped jar; the host is now accessed reflectively so version differences cannot break mixin application.

## 0.1.13 - 2026-08-29

- Made aggregate patterns insertable into the AE2 Lightning Tech matter warping matrix pattern storage: the decoder marker now implements `IMolecularAssemblerSupportedPattern`, which the matrix slot validation requires.
- Published every assembler-capable child into the matrix pattern cache instead of the single decode marker.
- Made the decoder use a lightweight first-child expansion so slot validation no longer pays for a full aggregate expansion.
- Fixed add-on providers only recognizing the first child pattern, and fixed full-selection aggregates being rejected while single-selection ones were accepted.

## 0.1.12 - 2026-08-29

- Made the picker search cover every stored recipe instead of only the first 1024 synced entries: the server now filters the complete library and streams the matches back in bounded pages.
- Added the search request/result payloads and a request id so stale responses from earlier queries are ignored.

## 0.1.11 - 2026-08-29

- Added a search bar to the aggregate pattern picker with input/output mode tabs and the same query syntax as the AE2 terminal.
- Made the select-all button smaller so the picker reads better with thousands of patterns.

## 0.1.10 - 2026-08-29

- Fixed aggregate generation silently failing on very large recipe libraries: the server rejected uploads once the player walked away during the multi-minute scan, so the distance check was dropped in favour of machine and held-item validation.
- Sped up large scans dramatically by resolving vanilla crafting and stonecutting recipes directly from the recipe manager on a background thread instead of building JEI layouts for each one.
- Cached tag lookups, limited tooltip parsing to processing categories, and reported truncation past the recipe cap.

## 0.1.9 - 2026-08-29

- Fixed machines with thousands of recipes never producing an aggregate: a single failing recipe aborted the incremental scan and left the job suspended.
- Fixed oversized pages being dropped by the protocol: pages are now cut by a byte budget instead of a fixed entry count.
- Added a scan progress message so large machines show that they are still working.

## 0.1.8 - 2026-08-29

- Added the aggregate pattern picker: shift-right-click an aggregate pattern to enable or disable individual children, with selected entries ordered first, drag-capable scrolling, split input/output icons, and select-all support.
- Fixed a placeholder pattern leaking to the network when every child was deselected.
- Cached aggregate expansions server-side so providers no longer re-encode every child on each refresh.
- Spread cold aggregate expansions and client JEI scans across ticks with a per-tick budget.
- Rebuilt linker route refresh as a cross-tick job that keeps publishing the previous routes until the new table is complete.
- Made incoming-buffer work checks O(1) instead of scanning all bindings every tick.

## 0.1.7 - 2026-08-29

- Expanded aggregate-pattern configuration and execution for candidate inputs, input ordering, probabilistic outputs, and multi-output recipes.
- Added aggregate-pattern support for PackagedAuto packaging providers while preserving the selected target-machine workflow.
- Added optional compatibility for the Useless Mod advanced alloy furnace.
- Added aliases for Mekanism Extras factories and fixed TMRV package recipe ID resolution.
- Fixed aggregate generation leaking recipes from unrelated machines into the selected target.
- Let the pattern binder select linkers reliably and extended linker support to automated workstations.
- Added regression coverage for machine resolution, aggregate input limits, binding validation, packaging, and expanded pattern options.
- Fixed the merged optional-mixin gate so clean builds and both dependency-matrix GameTest runs compile again.

## 0.1.6 - 2026-08-28

- Added EMI + TooManyRecipeViewers support to the aggregate-pattern generator, including JEI compatibility recipes exposed through TMRV.
- Preserved candidate ingredients, Mekanism chemicals, and probabilistic output metadata when scanning EMI recipes.
- Added per-pattern safeguards for chance-based main outputs and byproducts.
- Fixed Mekanism recipe retention and oversized custom-payload failures during client-side aggregate scans.
- Standardized the Chinese “样板” terminology across the interface.
- Reworked the aggregate-pattern right-click settings into compact AE-style option rows with inline states and hover help.
- Added an explicit EMI/TMRV development runtime while keeping the release JAR and dedicated server independent of recipe-viewer mods.
- Isolated automated GameTests from the playable test save so minimal dependency runs cannot remove optional-mod machines.

## 0.1.0 - 2026-08-25

- Added the channel- and power-aware All Pattern Linker AE2 node.
- Added server-authoritative two-step binding, ownership validation, world persistence, and owner-only purple target outlines.
- Added virtual AE2 processing patterns backed by deterministic recipe snapshots and stable binding-specific identities.
- Added persistent, all-or-nothing incoming material buffering with safe recovery on unbind or linker removal.
- Added vanilla furnace, blast furnace, and smoker support.
- Added optional Mekanism smelting, crushing, and enriching machine/factory adapters.
- Added optional JEI contextual help without making JEI a server recipe authority.
- Added reload-aware caches, diagnostics commands, a 10,000-fingerprint performance guard, dual dependency-matrix GameTests, and release JAR verification.
- Added original binder/linker pixel art and a mod icon.
