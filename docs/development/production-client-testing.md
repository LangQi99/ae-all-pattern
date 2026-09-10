# Forge 1.20.1 production client regression testing

`runClient` uses development method names and is not a production-JAR test.
Issue #20 exposed two Minecraft overrides with missing runtime mappings. Fixing
`broadcastChanges` alone let startup proceed until `keyPressed` failed.

## Mapping audit

- `CraftConfirmMenu.broadcastChanges`: Minecraft override, explicitly remapped to `m_38946_()V`.
- `CraftConfirmScreen.keyPressed`: Minecraft override, explicitly remapped to `m_7933_(III)Z`.
- Screen overrides `render`, `mouseClicked`, `mouseDragged`, and `mouseReleased`
  are ordinary Java overrides, remapped by `reobfJar` to `m_88315_`, `m_6375_`,
  `m_7979_`, and `m_6348_`. They are not string injection selectors.
- Other injection selectors target AE2/add-on methods, constructors, or Java
  `Runnable.run`. Their names must not be Minecraft-remapped.
- Shadow fields/accessors target AE2/add-on-owned members. Invocation selectors
  target AE2 APIs or Java executors. Reflection in optional provider integrations
  targets the provider's own API names.

`verifyReleaseJar` runs after `reobfJar`, requires the configured refmap, and
checks both Minecraft injection mappings. Production startup additionally
exercises real Mixin transformation. Optional add-on compatibility still needs
the corresponding dependency matrix; a minimal AE2 startup does not cover it.

The production run also caught a missing MixinExtras runtime (now bundled via
Jar-in-Jar) and unconditional JEI scanner registration (now guarded by viewer
availability). Maven AE2/GuideME artifacts are development artifacts: the
production script pins public Modrinth JARs and verifies their checksums instead.

## Run the packaged client

Use Java 17 and a Python environment containing `minecraft-launcher-lib==8.0`:

```sh
./gradlew check
xvfb-run --auto-servernum python tools/production_client_smoke.py \
  --jar build/libs/aeallpattern-1.20.1-forge-0.2.3-beta.2.jar \
  --directory /tmp/ae-production-test
```

On a desktop with a display, omit `xvfb-run`. The script installs official Forge
47.4.20 and checksummed AE2 15.4.10/GuideME, puts the final JAR in `mods`, and
requires a successful title-screen marker. It does not use Gradle's development
classpath or remapping service. CI runs this against the build job's artifact,
both without a viewer and with `--jei`.

Add `--world /path/to/test-save` to copy a test world into the isolated directory.
The opt-in screen check opens the crafting confirmation screen, lets real frames
render its routing popup, and checks mouse and Escape handling. It requires
`PRODUCTION_SCREEN_SMOKE_TEST_PASSED` as well as the startup marker. The source
save is never opened in place. This checks UI wiring, not a full crafting job.

For a negative control, run the published beta.1 JAR in a separate directory
with JEI and MixinExtras 0.4.1 (as supplied by other mods in the reported pack);
it must fail with `CraftConfirmScreenRoutingMixin` / `keyPressed`. Without those
dependencies it may fail earlier on the missing runtime or optional JEI API. A test that
also passes the known-broken artifact is not a valid regression check.

## Verified on 2026-09-10

On Ubuntu / Java 17 / Xvfb with official Forge 47.4.20 and AE2 15.4.10:

- Published beta.1 with JEI/MixinExtras reproduced the reported `keyPressed` failure.
- Beta.2 with only AE2/GuideME passed world entry, confirmation-screen rendering,
  popup mouse dismissal, and Escape dismissal.
- The same beta.2 JAR passed those checks with JEI 15.58.0.209.
- The tested JAR SHA-256 was
  `2d35fa8fb3f6c41b5d54c7f62729e9a61782d6151f81f772e6b7f472a33515eb`.

These runs used a copied showcase save, not the reporter's full modpack.
