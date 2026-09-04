package io.github.langqi99.aeallpattern.aggregate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2-terminal-style search over the full recipe list of an aggregate pattern.
 *
 * <p>Space-separated tokens are ANDed. Per-token prefixes select the matched field and a
 * leading {@code !} negates the token: {@code @} matches the mod id or display name,
 * {@code $} matches any item/fluid tag, {@code =} matches the registry id, {@code #}
 * matches the registry id too, and a bare token matches the display name. Matching is
 * case-insensitive. Used on the server against the complete recipe list so the client
 * picker can search every stored pattern, not just the initially synced subset.
 */
public final class AggregatePatternSearch {
    private AggregatePatternSearch() {
    }

    /** Filters the full recipe list; a blank query returns the leading recipes. */
    public static List<AggregatePatternSelectionMenu.Entry> filter(
            List<AggregateRecipe> recipes, String searchText, boolean searchOutputs, int limit) {
        if (searchText == null || searchText.isBlank()) {
            return AggregatePatternSelectionMenu.entriesFromRecipes(recipes);
        }
        List<AggregateRecipe> matched = new ArrayList<>(Math.min(limit, recipes.size()));
        for (AggregateRecipe recipe : recipes) {
            if (matched.size() >= limit) {
                break;
            }
            if (matchesRecipe(recipe, searchText, searchOutputs)) {
                matched.add(recipe);
            }
        }
        return AggregatePatternSelectionMenu.entriesFromRecipes(matched);
    }

    /** Filters by inputs and outputs together, matching AE terminal search behavior. */
    public static List<AggregatePatternSelectionMenu.Entry> filterAny(
            List<AggregateRecipe> recipes, String searchText, int limit) {
        return AggregatePatternSelectionMenu.entriesFromRecipes(
                filterRecipesAny(recipes, searchText, limit));
    }

    /** Returns matching recipes without converting them, allowing server-side UI paging. */
    public static List<AggregateRecipe> filterRecipesAny(
            List<AggregateRecipe> recipes, String searchText, int limit) {
        int safeLimit = Math.max(0, limit);
        if (searchText == null || searchText.isBlank()) {
            return recipes.subList(0, Math.min(safeLimit, recipes.size()));
        }
        List<AggregateRecipe> matched = new ArrayList<>(Math.min(safeLimit, recipes.size()));
        for (AggregateRecipe recipe : recipes) {
            if (matched.size() >= safeLimit) {
                break;
            }
            if (matchesRecipe(recipe, searchText, false) || matchesRecipe(recipe, searchText, true)) {
                matched.add(recipe);
            }
        }
        return List.copyOf(matched);
    }

    /** True when any input (or output, per mode) stack of the recipe matches the query. */
    public static boolean matchesRecipe(AggregateRecipe recipe, String searchText, boolean searchOutputs) {
        List<GenericStack> stacks = searchOutputs ? recipe.outputs() : recipe.inputs();
        if (stacks.isEmpty()) {
            return false;
        }
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null && matchesStack(stack.what(), searchText)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesStack(AEKey key, String query) {
        String[] tokens = query.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            boolean negate = token.charAt(0) == '!';
            String term = negate ? token.substring(1) : token;
            boolean matched;
            if (term.startsWith("@")) {
                matched = modMatches(key, term.substring(1));
            } else if (term.startsWith("$")) {
                matched = tagMatches(key, term.substring(1));
            } else if (term.startsWith("=") || term.startsWith("#")) {
                matched = registryId(key).toString().contains(term.substring(1));
            } else {
                matched = key.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(term);
            }
            if (negate == matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean modMatches(AEKey key, String term) {
        String modId = registryId(key).getNamespace();
        if (modId.contains(term)) {
            return true;
        }
        return net.minecraftforge.fml.ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName().toLowerCase(Locale.ROOT).contains(term))
                .orElse(false);
    }

    @SuppressWarnings("deprecation")
    private static boolean tagMatches(AEKey key, String term) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.getItem().builtInRegistryHolder().tags()
                    .anyMatch(tag -> tag.location().toString().contains(term));
        }
        if (key instanceof AEFluidKey fluidKey) {
            return fluidKey.getFluid().builtInRegistryHolder().tags()
                    .anyMatch(tag -> tag.location().toString().contains(term));
        }
        return false;
    }

    private static ResourceLocation registryId(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return BuiltInRegistries.ITEM.getKey(itemKey.getItem());
        }
        if (key instanceof AEFluidKey fluidKey) {
            return BuiltInRegistries.FLUID.getKey(fluidKey.getFluid());
        }
        return key.getId();
    }
}
