package io.github.langqi99.aeallpattern.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("recipe-viewer")
class ClientRecipeMachineResolverTest {
    @Test
    void mapsEveryPackagedExCrafterToItsExtendedCraftingWorkstation() {
        assertAlias("packagedexcrafting", "basic_crafter", "extendedcrafting", "basic_table");
        assertAlias("packagedexcrafting", "advanced_crafter", "extendedcrafting", "advanced_table");
        assertAlias("packagedexcrafting", "elite_crafter", "extendedcrafting", "elite_table");
        assertAlias("packagedexcrafting", "ultimate_crafter", "extendedcrafting", "ultimate_table");
        assertAlias("packagedexcrafting", "ender_crafter", "extendedcrafting", "ender_crafter");
        assertAlias("packagedexcrafting", "flux_crafter", "extendedcrafting", "flux_crafter");
        assertAlias("packagedexcrafting", "combination_crafter", "extendedcrafting", "crafting_core");
        assertAlias("packagedexcrafting", "marked_pedestal", "extendedcrafting", "pedestal");
    }

    @Test
    void mapsEveryPackagedAvaritiaCrafterToItsReAvaritiaWorkstation() {
        assertAlias("packagedavaritia", "sculk_crafter", "avaritia", "sculk_crafting_table");
        assertAlias("packagedavaritia", "nether_crafter", "avaritia", "nether_crafting_table");
        assertAlias("packagedavaritia", "end_crafter", "avaritia", "end_crafting_table");
        assertAlias("packagedavaritia", "extreme_crafter", "avaritia", "extreme_crafting_table");
    }

    @Test
    void mapsEveryAppliedExtendedCraftingStationToItsExtendedCraftingWorkstation() {
        assertAlias("applied_extended_crafting", "table_basic_pattern_provider", "extendedcrafting", "basic_table");
        assertAlias("applied_extended_crafting", "table_advanced_pattern_provider", "extendedcrafting", "advanced_table");
        assertAlias("applied_extended_crafting", "table_elite_pattern_provider", "extendedcrafting", "elite_table");
        assertAlias("applied_extended_crafting", "table_ultimate_pattern_provider", "extendedcrafting", "ultimate_table");
        assertAlias("applied_extended_crafting", "ender_crafter_pattern_provider", "extendedcrafting", "ender_crafter");
        assertAlias("applied_extended_crafting", "flux_crafter_pattern_provider", "extendedcrafting", "flux_crafter");
        assertAlias("applied_extended_crafting", "crafter_core_pattern_provider", "extendedcrafting", "crafting_core");
    }

    @Test
    void mapsEveryMekanismExtrasFactoryToARegisteredWorkstation() {
        String[] tiers = {"absolute", "supreme", "cosmic", "infinite"};
        String[] mekanismFactories = {
                "combining", "compressing", "crushing", "enriching", "infusing",
                "injecting", "purifying", "sawing", "smelting"
        };
        String[] moreMachineFactories = {
                "centrifuging", "crystallizing", "dissolving", "lathing", "liquifying",
                "oxidizing", "painting", "pigment_extracting", "planting", "pressurised_reacting",
                "recycling", "replicating", "rolling_mill", "stamping", "washing"
        };
        for (String tier : tiers) {
            for (String factory : mekanismFactories) {
                assertAlias("mekanism_extras", tier + "_" + factory + "_factory",
                        "mekanism", singleMekanismMachine(factory));
            }
            for (String factory : moreMachineFactories) {
                assertAlias("mekanism_extras", tier + "_" + factory + "_factory",
                        singleAdvancedMachineNamespace(factory), singleAdvancedMachine(factory));
            }
        }
    }

    @Test
    void mapsMekmmFactoriesToSingleBlockMachines() {
        String[] tiers = {"basic", "advanced", "elite", "ultimate", "dense", "quantum",
                "overclocked", "multiversal", "creative"};
        String[] factories = {
                "centrifuging", "crystallizing", "dissolving", "lathing", "liquifying",
                "oxidizing", "painting", "pigment_extracting", "planting",
                "pressurised_reacting", "recycling", "replicating", "rolling_mill", "stamping", "washing"
        };
        for (String tier : tiers) {
            for (String factory : factories) {
                assertAlias("mekmm", tier + "_" + factory + "_factory",
                        singleAdvancedMachineNamespace(factory), singleAdvancedMachine(factory));
            }
        }
    }

    private static String singleMekanismMachine(String factory) {
        return switch (factory) {
            case "combining" -> "combiner";
            case "compressing" -> "osmium_compressor";
            case "crushing" -> "crusher";
            case "enriching" -> "enrichment_chamber";
            case "infusing" -> "metallurgic_infuser";
            case "injecting" -> "chemical_injection_chamber";
            case "purifying" -> "purification_chamber";
            case "sawing" -> "precision_sawmill";
            case "smelting" -> "energized_smelter";
            default -> throw new AssertionError(factory);
        };
    }

    private static String singleAdvancedMachineNamespace(String factory) {
        return switch (factory) {
            case "lathing", "planting", "recycling", "replicating", "rolling_mill", "stamping" -> "mekmm";
            default -> "mekanism";
        };
    }

    private static String singleAdvancedMachine(String factory) {
        return switch (factory) {
            case "centrifuging" -> "isotopic_centrifuge";
            case "crystallizing" -> "chemical_crystallizer";
            case "dissolving" -> "chemical_dissolution_chamber";
            case "lathing" -> "cnc_lathe";
            case "liquifying" -> "nutritional_liquifier";
            case "oxidizing" -> "chemical_oxidizer";
            case "painting" -> "painting_machine";
            case "pigment_extracting" -> "pigment_extractor";
            case "planting" -> "planting_station";
            case "pressurised_reacting" -> "pressurized_reaction_chamber";
            case "recycling" -> "recycler";
            case "replicating" -> "replicator";
            case "rolling_mill" -> "cnc_rolling_mill";
            case "stamping" -> "cnc_stamper";
            case "washing" -> "chemical_washer";
            default -> throw new AssertionError(factory);
        };
    }

    @Test
    void leavesOrdinaryMachinesUnchanged() {
        ResourceLocation id = id("mekmm", "large_chemical_infuser");
        assertEquals(id, ClientRecipeMachineResolver.catalystAlias(id));
    }

    private static void assertAlias(
            String sourceNamespace, String sourcePath, String targetNamespace, String targetPath) {
        assertEquals(
                id(targetNamespace, targetPath),
                ClientRecipeMachineResolver.catalystAlias(id(sourceNamespace, sourcePath)));
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
