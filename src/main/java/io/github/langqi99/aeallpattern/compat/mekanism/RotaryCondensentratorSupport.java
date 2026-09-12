package io.github.langqi99.aeallpattern.compat.mekanism;

import io.github.langqi99.aeallpattern.AeAllPattern;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the operating direction of Mekanism's rotary condensentrator from a live block entity.
 *
 * <p>The machine keeps a single persisted boolean. Mekanism exposes it twice: the public
 * {@code getMode()} returns the raw flag and the package-private {@code isCondensentrating()}
 * returns its inverse. Callers only care about the semantic direction, so this helper normalises
 * both accessors and never reports a direction it could not actually read.</p>
 *
 * <p>Mekanism and add-ons (such as mekmm's large version) register the block under an id whose
 * path ends in {@code rotary_condensentrator}, so matching stays independent of the concrete
 * tile entity class and works without a compile-time Mekanism dependency.</p>
 */
public final class RotaryCondensentratorSupport {
    /** Category path used by {@code mekanism:condensentrating} (chemical in, fluid out). */
    public static final String CONDENSENTRATING = "condensentrating";
    /** Category path used by {@code mekanism:decondensentrating} (fluid in, chemical out). */
    public static final String DECONDENSENTRATING = "decondensentrating";

    private static final String MODE_ACCESSOR = "getMode";
    private static final String SEMANTIC_ACCESSOR = "isCondensentrating";
    private static final String ID_MARKER = "rotary_condensentrator";

    /**
     * Direction confirmed by the server, keyed by machine position. Mekanism's block entity update
     * tag is empty, so a client block entity never receives {@code mode}; the server reply is the
     * only trustworthy client-side source.
     */
    private static final Map<BlockPos, Boolean> SERVER_DIRECTIONS = new ConcurrentHashMap<>();

    /** Records the server-confirmed direction for a machine. */
    public static void rememberDirection(BlockPos pos, boolean condensentrating) {
        SERVER_DIRECTIONS.put(pos.immutable(), condensentrating);
    }

    /** Drops the remembered direction for a machine, e.g. when the server could not read it. */
    public static void forgetDirection(BlockPos pos) {
        SERVER_DIRECTIONS.remove(pos.immutable());
    }

    /** Drops every remembered direction, e.g. when leaving the world. */
    public static void clearDirections() {
        SERVER_DIRECTIONS.clear();
    }

    private RotaryCondensentratorSupport() {
    }

    /** True for the base machine and for addon variants such as {@code mekmm:large_rotary_condensentrator}. */
    public static boolean isRotaryCondensentrator(@Nullable ResourceLocation id) {
        return id != null && id.getPath().contains(ID_MARKER);
    }

    /**
     * Resolves the direction the machine is currently configured for.
     *
     * @return {@code true} when the machine condenses (chemical to fluid), {@code false} when it
     *     decondensentrates (fluid to chemical), or {@code null} when the direction is unknown.
     *     Callers must treat {@code null} as "do not touch the existing catalog".
     */
    @Nullable
    public static Boolean condensentrating(
            @Nullable Level level, @Nullable BlockPos pos, @Nullable ResourceLocation catalystId) {
        if (level == null || pos == null || !isRotaryCondensentrator(catalystId)) {
            return null;
        }
        Boolean known = knownDirection(pos);
        if (known != null) {
            return known;
        }
        return condensentrating(level, pos);
    }

    /** The direction the server last reported for a machine, or null when none is known yet. */
    @Nullable
    static Boolean knownDirection(BlockPos pos) {
        return SERVER_DIRECTIONS.get(pos.immutable());
    }

    /**
     * Reads the direction straight from a block entity.
     *
     * <p>Only authoritative on the server. A client block entity keeps the default because
     * Mekanism's update tag is empty, which is why callers on the client must use the value the
     * server reported through {@link #rememberDirection}.</p>
     *
     * @return {@code true} when the machine condenses (chemical to fluid), {@code false} when it
     *     decondensentrates (fluid to chemical), or {@code null} when it could not be read.
     */
    @Nullable
    public static Boolean condensentrating(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || BlockPos.ZERO.equals(pos)) {
            return null;
        }
        var entity = level.getBlockEntity(pos);
        if (entity == null) {
            return null;
        }
        Boolean mode = booleanProperty(entity, MODE_ACCESSOR, false, false);
        Boolean semantic = booleanProperty(entity, SEMANTIC_ACCESSOR, true, true);
        Boolean resolved = mode != null ? !mode : semantic;
        // Kept at info level: the direction is the one input this compatibility cannot infer from
        // the catalog, so a wrong reading is the first thing to rule out when patterns disagree.
        AeAllPattern.LOGGER.info(
                "Rotary direction at {} ({}): getMode={} isCondensentrating={} -> {}",
                pos, entity.getClass().getName(), mode, semantic, resolved);
        return resolved;
    }

    /**
     * Normalises a block entity into the semantic direction. Package visible so the reflection
     * contract can be unit tested without a running game.
     */
    @Nullable
    static Boolean condensentrating(Object entity) {
        if (entity == null) {
            return null;
        }
        // Prefer the public accessor: mode == true means decondensentrating (evaporation).
        // An enum named after the direction would be ambiguous here, so only accept booleans.
        Boolean mode = booleanProperty(entity, MODE_ACCESSOR, false, false);
        if (mode != null) {
            return !mode;
        }
        // Fallback for builds that only keep the package-private semantic helper.
        return booleanProperty(entity, SEMANTIC_ACCESSOR, true, true);
    }

    @Nullable
    private static Boolean booleanProperty(
            Object entity, String name, boolean declaredOnly, boolean allowEnum) {
        for (Class<?> type = entity.getClass(); type != null && type != Object.class;
                type = type.getSuperclass()) {
            Method method = find(type, name, declaredOnly);
            if (method == null || method.getParameterCount() != 0) {
                continue;
            }
            try {
                if (!method.trySetAccessible()) {
                    continue;
                }
                Object value = method.invoke(entity);
                if (value instanceof Boolean result) {
                    return result;
                }
                // Some Mekanism builds model the semantic accessor as an enum.
                if (allowEnum && value instanceof Enum<?> constant) {
                    Boolean inferred = fromEnumName(constant.name());
                    if (inferred != null) {
                        return inferred;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next declaration in the hierarchy instead of failing the scan.
            }
        }
        return null;
    }

    @Nullable
    private static Method find(Class<?> type, String name, boolean declaredOnly) {
        try {
            return declaredOnly ? type.getDeclaredMethod(name) : type.getMethod(name);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    @Nullable
    private static Boolean fromEnumName(String name) {
        String normalised = name.toUpperCase(Locale.ROOT);
        if (normalised.contains("DECONDENS") || normalised.contains("EVAPOR")) {
            return Boolean.FALSE;
        }
        if (normalised.contains("CONDENS")) {
            return Boolean.TRUE;
        }
        return null;
    }
}
