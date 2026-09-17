package io.github.langqi99.aeallpattern.compat.masterful;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("recipe-viewer")
class MasterfulMachineryBridgeTest {
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath("pack_a", "smelter");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath("pack_b", "smelter");
    public record Structure(ResourceLocation id) {}
    public record Recipe(ResourceLocation structureId) {}
    public record Category(Structure structure) {
        public Structure getStructureModel() { return structure; }
    }

    @Test
    void collectsAllRelatedStructuresButNotSamePathInAnotherNamespace() {
        assertTrue(MasterfulMachineryBridge.categoryMatches(new Category(new Structure(A)), Set.of(A, B)));
        assertTrue(MasterfulMachineryBridge.categoryMatches(new Category(new Structure(B)), Set.of(A, B)));
        assertFalse(MasterfulMachineryBridge.categoryMatches(new Category(new Structure(B)), Set.of(A)));
    }

    @Test
    void unsplitCategoryStillFiltersRecipesByStructure() {
        assertTrue(MasterfulMachineryBridge.categoryMatches(new Category(null), Set.of(A)));
        assertTrue(MasterfulMachineryBridge.recipeMatches(new Recipe(A), Set.of(A)));
        assertFalse(MasterfulMachineryBridge.recipeMatches(new Recipe(B), Set.of(A)));
    }

    @Test
    void incompatibleApiAndUnrelatedObjectsFailClosed() {
        assertFalse(MasterfulMachineryBridge.categoryMatches(new Object(), Set.of(A)));
        assertFalse(MasterfulMachineryBridge.recipeMatches(new Object(), Set.of(A)));
        assertFalse(MasterfulMachineryBridge.isProcessingCategory(new Category(null)));
        assertFalse(MasterfulMachineryBridge.categoryMatches(new Category(null), Set.of()));
    }
}
