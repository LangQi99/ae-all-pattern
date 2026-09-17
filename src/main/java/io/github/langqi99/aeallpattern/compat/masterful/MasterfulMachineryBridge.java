package io.github.langqi99.aeallpattern.compat.masterful;

import io.github.langqi99.aeallpattern.AeAllPattern;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Optional MM 3 / MM Upgraded bridge. No MM or recipe-viewer classes load on a server without MM. */
public final class MasterfulMachineryBridge {
    private static final String STRUCTURES = "io.ticticboom.mods.mm.structure.StructureManager";
    private static final String CATEGORY = "io.ticticboom.mods.mm.compat.jei.category.MMRecipeCategory";

    private MasterfulMachineryBridge() {
    }

    public static Set<ResourceLocation> structuresForController(ResourceLocation controllerId) {
        try {
            Class<?> manager = Class.forName(STRUCTURES);
            List<?> structures = (List<?>) manager.getMethod("getStructuresForController", ResourceLocation.class)
                    .invoke(null, controllerId);
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (Object structure : structures) {
                ids.add((ResourceLocation) structure.getClass().getMethod("id").invoke(structure));
            }
            return Set.copyOf(ids);
        } catch (ClassNotFoundException ignored) {
            return Set.of();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            AeAllPattern.LOGGER.debug("Cannot resolve Masterful Machinery controller {}", controllerId, error);
            return Set.of();
        }
    }

    public static boolean isProcessingCategory(Object category) {
        return category != null && category.getClass().getName().equals(CATEGORY);
    }

    /** Shared categories have no structure; recipes are always filtered again by their actual structure ID. */
    public static boolean categoryMatches(Object category, Set<ResourceLocation> structures) {
        if (structures.isEmpty()) return false;
        try {
            Object structure = category.getClass().getMethod("getStructureModel").invoke(category);
            return structure == null || structures.contains(structure.getClass().getMethod("id").invoke(structure));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return false;
        }
    }

    public static boolean recipeMatches(Object recipe, Set<ResourceLocation> structures) {
        try {
            return structures.contains(recipe.getClass().getMethod("structureId").invoke(recipe));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return false;
        }
    }
}
