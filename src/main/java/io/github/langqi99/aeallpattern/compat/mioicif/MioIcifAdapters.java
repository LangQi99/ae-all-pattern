package io.github.langqi99.aeallpattern.compat.mioicif;

import io.github.langqi99.aeallpattern.machine.MachineAdapterRegistry;

/**
 * Loaded only when mio_icif is present. The adapter resolves every mio_icif member reflectively,
 * so nothing here references the mod's classes and the class can be touched unconditionally.
 */
public final class MioIcifAdapters {
    private MioIcifAdapters() {
    }

    public static void registerAll() {
        MachineAdapterRegistry.register(new MioIcifMetalFormerAdapter());
    }
}
