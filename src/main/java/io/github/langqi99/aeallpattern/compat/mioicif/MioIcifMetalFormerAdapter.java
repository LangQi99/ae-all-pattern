package io.github.langqi99.aeallpattern.compat.mioicif;

import appeng.api.stacks.AEItemKey;
import io.github.langqi99.aeallpattern.AeAllPattern;
import io.github.langqi99.aeallpattern.binding.BindingRecord;
import io.github.langqi99.aeallpattern.machine.ItemHandlerTransfer;
import io.github.langqi99.aeallpattern.machine.MachineAdapter;
import io.github.langqi99.aeallpattern.recipe.RecipeCatalog;
import io.github.langqi99.aeallpattern.recipe.RecipeFingerprint;
import io.github.langqi99.aeallpattern.recipe.RecipeSnapshot;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

/**
 * mio_icif 金属成型机 (Metal Former / Advanced Metal Former) adapter.
 *
 * <p>The machine has three internal modes — ROLLING, CUTTING and EXTRUDING — each backed by its own
 * recipe type ({@code mio_icif:metal_former_rolling/_cutting/_extruding}). Every recipe has a single
 * ingredient, a fixed ingredient count and a per-recipe processing time. Only the recipes of the
 * machine's <em>currently selected</em> mode are discoverable, so the aggregate captures the mode at
 * generation time; switching the machine's mode afterwards requires regenerating the pattern.
 *
 * <p>mio_icif has no published Maven artifact and its jar is not committed, so — unlike the Mekanism
 * adapter — every mio_icif member is read reflectively. This keeps the adapter compilable with no
 * dependency and consistent with how the project integrates other non-Maven optional mods. Only the
 * mod-specific members are reflective; ingredient, stack and inventory handling use direct APIs.
 */
final class MioIcifMetalFormerAdapter implements MachineAdapter {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("mio_icif", "metal_former");
    private static final Set<ResourceLocation> BLOCKS = Set.of(
            ResourceLocation.fromNamespaceAndPath("mio_icif", "producer/block_metal_former"),
            ResourceLocation.fromNamespaceAndPath("mio_icif", "producer/block_metal_former_advanced"));
    private static final int MAX_INGREDIENT_VARIANTS = 64;
    private static final int MAX_PATTERNS = 4096;
    private static final MethodCache METHODS = new MethodCache();

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    /** Package-visible for the unit test: matches the two metal former block ids. */
    static boolean matchesBlock(ResourceLocation blockId) {
        return BLOCKS.contains(blockId);
    }

    /** Pure mapping used by discovery; kept static so it can be unit tested without a server. */
    static String recipeTypePath(String modeName) {
        if (modeName == null) {
            return null;
        }
        return switch (modeName) {
            case "ROLLING" -> "metal_former_rolling";
            case "CUTTING" -> "metal_former_cutting";
            case "EXTRUDING" -> "metal_former_extruding";
            default -> null;
        };
    }

    @Override
    public boolean supports(ServerLevel level, BlockEntity target) {
        return matchesBlock(BuiltInRegistries.BLOCK.getKey(target.getBlockState().getBlock()));
    }

    @Override
    public RecipeCatalog discoverRecipes(ServerLevel level, BlockEntity target, long generation) {
        String path = recipeTypePath(readModeName(target));
        if (path == null) {
            AeAllPattern.LOGGER.warn("mio_icif metal former has no recognized mode at {}; "
                    + "no recipes discovered", target.getBlockPos());
            return new RecipeCatalog(generation, List.of(), 0);
        }
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("mio_icif", path));
        if (type == null) {
            AeAllPattern.LOGGER.warn("mio_icif recipe type {} is not registered", path);
            return new RecipeCatalog(generation, List.of(), 0);
        }

        List<RecipeHolder<?>> holders = new ArrayList<>(allRecipes(level, type));
        holders.sort(Comparator.comparing(holder -> holder.id().toString()));
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        int filtered = 0;

        for (RecipeHolder<?> holder : holders) {
            Object recipe = holder.value();
            Ingredient ingredient = as(Ingredient.class, invoke(recipe, "getIngredient"), holder);
            ItemStack result = as(ItemStack.class, invoke(recipe, "getResult"), holder);
            Integer count = as(Integer.class, invoke(recipe, "getIngredientCount"), holder);
            Integer ticks = as(Integer.class, invoke(recipe, "getProcessingTime"), holder);
            if (ingredient == null || result == null || result.isEmpty()
                    || count == null || count < 1 || ticks == null) {
                filtered++;
                continue;
            }
            ItemStack[] variants = ingredient.getItems();
            if (variants.length == 0 || variants.length > MAX_INGREDIENT_VARIANTS) {
                filtered++;
                continue;
            }
            Arrays.sort(variants, Comparator.comparing(MioIcifMetalFormerAdapter::normalize));
            for (ItemStack variant : variants) {
                if (snapshots.size() >= MAX_PATTERNS) {
                    filtered++;
                    break;
                }
                ItemStack input = variant.copyWithCount(count);
                if (input.getCount() > input.getMaxStackSize()) {
                    filtered++;
                    continue;
                }
                ItemStack output = result.copy();
                String normalizedInput = normalize(input);
                String normalizedOutput = normalize(output);
                if (Objects.equals(AEItemKey.of(input), AEItemKey.of(output))
                        || !seen.add(List.of(normalizedInput, normalizedOutput))) {
                    filtered++;
                    continue;
                }
                RecipeFingerprint fingerprint = new RecipeFingerprint(
                        ID.toString(), holder.id().toString(), normalizedInput, normalizedOutput, schemaVersion());
                snapshots.add(new RecipeSnapshot(holder.id(), input, output, fingerprint, ticks));
            }
        }

        snapshots.sort(Comparator.comparing(snapshot -> snapshot.fingerprint().stableKey()));
        return new RecipeCatalog(generation, snapshots, filtered);
    }

    /** The machine's mode is part of the catalog cache key and the aggregate library variant. */
    @Override
    public String catalogVariant(ServerLevel level, BlockEntity target) {
        String mode = readModeName(target);
        return mode == null ? "" : mode;
    }

    @Override
    public boolean insert(ServerLevel level, BindingRecord binding, ItemStack stack) {
        for (Direction side : preferredFirst(binding.clickedSide())) {
            if (ItemHandlerTransfer.insertFully(capability(level, binding, side), stack)) {
                return true;
            }
        }
        if (ItemHandlerTransfer.insertFully(
                level.getCapability(Capabilities.ItemHandler.BLOCK, binding.target().pos(), null), stack)) {
            return true;
        }
        // Fallback for machines that expose slots only through their vanilla container.
        if (level.getBlockEntity(binding.target().pos()) instanceof WorldlyContainer container) {
            for (Direction side : preferredFirst(binding.clickedSide())) {
                if (ItemHandlerTransfer.insertFully(new SidedInvWrapper(container, side), stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isInputBlocked(ServerLevel level, BindingRecord binding) {
        boolean foundCapability = false;
        for (Direction side : withNull(preferredFirst(binding.clickedSide()))) {
            var handler = capability(level, binding, side);
            if (handler == null) {
                continue;
            }
            foundCapability = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && handler.isItemValid(slot, stack)) {
                    return true;
                }
            }
        }
        if (!foundCapability
                && level.getBlockEntity(binding.target().pos()) instanceof WorldlyContainer container) {
            for (Direction side : preferredFirst(binding.clickedSide())) {
                var wrapper = new SidedInvWrapper(container, side);
                for (int slot = 0; slot < wrapper.getSlots(); slot++) {
                    ItemStack stack = wrapper.getStackInSlot(slot);
                    if (!stack.isEmpty() && wrapper.isItemValid(slot, stack)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public ItemStack extractAnyOutput(ServerLevel level, BindingRecord binding, boolean simulate) {
        for (Direction side : preferredFirst(binding.clickedSide())) {
            ItemStack extracted = ItemHandlerTransfer.extractAny(capability(level, binding, side), simulate);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        ItemStack unsided = ItemHandlerTransfer.extractAny(
                level.getCapability(Capabilities.ItemHandler.BLOCK, binding.target().pos(), null), simulate);
        if (!unsided.isEmpty()) {
            return unsided;
        }
        if (level.getBlockEntity(binding.target().pos()) instanceof WorldlyContainer container) {
            for (Direction side : preferredFirst(binding.clickedSide())) {
                ItemStack extracted = ItemHandlerTransfer.extractAny(
                        new SidedInvWrapper(container, side), simulate);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static net.neoforged.neoforge.items.IItemHandler capability(
            ServerLevel level, BindingRecord binding, Direction side) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, binding.target().pos(), side);
    }

    private static String readModeName(BlockEntity target) {
        Object mode = invoke(target, "getMode");
        return mode instanceof Enum<?> value ? value.name() : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> allRecipes(ServerLevel level, RecipeType<?> type) {
        return (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
    }

    private static <T> T as(Class<T> type, Object value, RecipeHolder<?> holder) {
        if (value == null) {
            AeAllPattern.LOGGER.warn("mio_icif recipe {} is missing an expected accessor", holder.id());
            return null;
        }
        if (!type.isInstance(value)) {
            AeAllPattern.LOGGER.warn("mio_icif recipe {} returned unexpected {} for {}",
                    holder.id(), value.getClass().getName(), type.getSimpleName());
            return null;
        }
        return type.cast(value);
    }

    private static Object invoke(Object target, String name) {
        if (target == null) {
            return null;
        }
        Optional<Method> method = METHODS.find(target.getClass(), name);
        if (method.isEmpty()) {
            return null;
        }
        try {
            return method.get().invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            AeAllPattern.LOGGER.warn("mio_icif reflective call {}() failed", name, e);
            return null;
        }
    }

    private static List<Direction> preferredFirst(Direction preferred) {
        List<Direction> directions = new ArrayList<>(Direction.values().length);
        directions.add(preferred);
        for (Direction side : Direction.values()) {
            if (side != preferred) {
                directions.add(side);
            }
        }
        return directions;
    }

    private static List<Direction> withNull(List<Direction> directions) {
        return Stream.concat(directions.stream(), Stream.of((Direction) null)).toList();
    }

    private static String normalize(ItemStack stack) {
        return AEItemKey.of(stack) + "*" + stack.getCount();
    }

    /** Caches reflective {@code getMethod} lookups (including misses) for the lifetime of the JVM. */
    private static final class MethodCache {
        private final ConcurrentHashMap<String, Optional<Method>> cache = new ConcurrentHashMap<>();

        Optional<Method> find(Class<?> type, String name) {
            return cache.computeIfAbsent(type.getName() + "#" + name, key -> {
                try {
                    Method method = type.getMethod(name);
                    method.setAccessible(true);
                    return Optional.of(method);
                } catch (NoSuchMethodException e) {
                    return Optional.empty();
                }
            });
        }
    }
}
