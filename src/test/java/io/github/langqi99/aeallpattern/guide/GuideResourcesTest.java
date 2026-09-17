package io.github.langqi99.aeallpattern.guide;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import guideme.compiler.PageCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GuideResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/aeallpattern");
    private static final List<String> PAGES = List.of("index.md", "aggregate_patterns.md", "pattern_linker.md",
            "tianshu_router.md", "multiblocks.md");

    @Test
    void bothLanguagesParseWithTheInstalledGuideMeAndHaveValidLinks() throws Exception {
        for (String language : List.of("en_us", "zh_cn")) {
            Path root = ASSETS.resolve("guides/aeallpattern/guide").resolve(language.equals("en_us") ? "" : "_" + language);
            Set<String> itemIds = new HashSet<>();
            for (String name : PAGES) {
                String source = Files.readString(root.resolve(name));
                var page = PageCompiler.parse("aeallpattern", language,
                        new ResourceLocation("aeallpattern", name), source);
                assertNotNull(page.getFrontmatter().navigationEntry(), name);
                assertFalse(page.getAstRoot().children().isEmpty(), name);
                Object ids = page.getFrontmatter().additionalProperties().get("item_ids");
                if (ids instanceof List<?> values) {
                    for (Object value : values) assertTrue(itemIds.add((String) value), "Duplicate item anchor: " + value);
                }
                var links = Pattern.compile("\\]\\(([^)#]+\\.md)(?:#[^)]*)?\\)").matcher(source);
                while (links.find()) assertTrue(Files.isRegularFile(root.resolve(links.group(1))), links.group(1));
            }
            assertEquals(Set.of("aeallpattern:guide", "aeallpattern:all_pattern_generator", "aeallpattern:aggregate_pattern",
                    "aeallpattern:pattern_linker", "aeallpattern:pattern_binder", "aeallpattern:tianshu_pattern_selector"), itemIds);
        }
    }

    @Test
    void dataDrivenGuideAndItemTranslationsArePresent() throws Exception {
        var definition = JsonParser.parseString(Files.readString(ASSETS.resolve("guideme_guides/guide.json"))).getAsJsonObject();
        assertEquals("en_us", definition.get("default_language").getAsString());
        for (String language : List.of("en_us", "zh_cn")) {
            var translations = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/" + language + ".json"))).getAsJsonObject();
            assertTrue(translations.has("item.aeallpattern.guide"));
        }
    }
}
