package io.github.langqi99.aeallpattern;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Minimal registry bootstrap for plain JUnit without starting Forge networking. */
public final class TestMinecraftBootstrap {
    private TestMinecraftBootstrap() {
    }

    public static void initialize() {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError error) {
            // Forge 1.20.1 initializes NetworkConstants at the very end of bootstrap. Its event
            // listener discovery expects a transformed launch environment, which plain JUnit does
            // not provide. All vanilla registries are initialized before that optional network hook.
            Throwable cause = error;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (!(cause instanceof NoSuchMethodException)
                    || !cause.getMessage().contains("net.minecraftforge.network.NetworkEvent")) {
                throw error;
            }
        }
    }
}
