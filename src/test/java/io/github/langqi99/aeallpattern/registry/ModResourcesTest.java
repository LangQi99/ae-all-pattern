package io.github.langqi99.aeallpattern.registry;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ModResourcesTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void linkerHasTheCompleteBlockResourceChain() {
        for (String relative : List.of(
                "assets/aeallpattern/blockstates/pattern_linker.json",
                "assets/aeallpattern/models/block/pattern_linker.json",
                "assets/aeallpattern/models/item/pattern_linker.json",
                "data/aeallpattern/loot_tables/blocks/pattern_linker.json",
                "data/aeallpattern/tags/blocks/mineable/pickaxe.json",
                "data/aeallpattern/recipes/pattern_linker.json")) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(relative)), () -> "missing resource: " + relative);
        }
    }

    @Test
    void aggregatePatternItemsHaveModelsAndGeneratorRecipe() {
        for (String relative : List.of(
                "assets/aeallpattern/models/item/all_pattern_generator.json",
                "assets/aeallpattern/models/item/aggregate_pattern.json",
                "data/aeallpattern/recipes/all_pattern_generator.json",
                "aeallpattern.mixins.json")) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(relative)), () -> "missing resource: " + relative);
        }
    }

    @Test
    void tianshuSelectorHasTheCompleteBlockResourceChain() throws IOException {
        for (String relative : List.of(
                "assets/aeallpattern/blockstates/tianshu_pattern_selector.json",
                "assets/aeallpattern/models/block/tianshu_pattern_selector.json",
                "assets/aeallpattern/models/block/tianshu_pattern_selector_active.json",
                "assets/aeallpattern/models/item/tianshu_pattern_selector.json",
                "data/aeallpattern/loot_tables/blocks/tianshu_pattern_selector.json",
                "data/aeallpattern/recipes/tianshu_pattern_selector.json")) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(relative)), () -> "missing resource: " + relative);
        }
        for (String relative : List.of(
                "assets/aeallpattern/textures/block/tianshu/tianshu_pattern_selector.png",
                "assets/aeallpattern/textures/block/tianshu/tianshu_pattern_selector_active.png")) {
            BufferedImage image = ImageIO.read(RESOURCES.resolve(relative).toFile());
            assertEquals(64, image.getWidth(), () -> "unexpected texture width: " + relative);
            assertEquals(64, image.getHeight(), () -> "unexpected texture height: " + relative);
            assertTrue(image.getColorModel().hasAlpha(), () -> "texture must retain alpha: " + relative);
        }

        Path activeModel = RESOURCES.resolve("assets/aeallpattern/models/block/tianshu_pattern_selector_active.json");
        try (var reader = Files.newBufferedReader(activeModel)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var elements = root.getAsJsonArray("elements");
            var baseFaces = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
            assertEquals(
                    Set.of("north", "east", "south", "west", "up", "down"),
                    baseFaces.keySet(),
                    "active Tianshu model must render every outer base face");
            var activeScreen = elements.get(5)
                    .getAsJsonObject()
                    .getAsJsonObject("faces")
                    .getAsJsonObject("north");
            assertEquals("#active", activeScreen.get("texture").getAsString());
        }
    }

    @Test
    void allOwnedJsonResourcesParse() throws IOException {
        try (var paths = Files.walk(RESOURCES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(path)) {
                    JsonParser.parseReader(reader);
                }
            }
        }
    }

    @Test
    void ownedPixelArtTexturesAreValidMinecraftSprites() throws IOException {
        for (String relative : List.of(
                "assets/aeallpattern/textures/block/pattern_linker.png",
                "assets/aeallpattern/textures/item/pattern_binder.png")) {
            Path path = RESOURCES.resolve(relative);
            assertTrue(Files.isRegularFile(path), () -> "missing texture: " + relative);
            BufferedImage image = ImageIO.read(path.toFile());
            assertEquals(16, image.getWidth(), () -> "unexpected texture width: " + relative);
            assertEquals(16, image.getHeight(), () -> "unexpected texture height: " + relative);
            assertTrue(image.getColorModel().hasAlpha(), () -> "texture must retain an alpha channel: " + relative);
        }
    }

    @Test
    void modIconIsBundledAtDisplayResolution() throws IOException {
        BufferedImage icon = ImageIO.read(RESOURCES.resolve("icon.png").toFile());
        assertEquals(128, icon.getWidth());
        assertEquals(128, icon.getHeight());
        assertTrue(icon.getColorModel().hasAlpha());
    }
}
