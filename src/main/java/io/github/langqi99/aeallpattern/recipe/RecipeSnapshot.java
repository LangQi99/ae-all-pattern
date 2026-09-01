package io.github.langqi99.aeallpattern.recipe;

import java.util.Objects;
import java.util.List;
import io.github.langqi99.aeallpattern.aggregate.AggregateInputSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Immutable, concrete single-input recipe exposed to AE2. */
public final class RecipeSnapshot {
    private final ResourceLocation recipeId;
    private final List<ItemStack> inputs;
    private final List<List<ItemStack>> inputAlternatives;
    private final ItemStack output;
    private final RecipeFingerprint fingerprint;
    private final int processingTicks;

    public RecipeSnapshot(
            ResourceLocation recipeId,
            ItemStack input,
            ItemStack output,
            RecipeFingerprint fingerprint,
            int processingTicks) {
        this(recipeId, List.of(input), output, fingerprint, processingTicks);
    }

    public RecipeSnapshot(
            ResourceLocation recipeId,
            List<ItemStack> inputs,
            ItemStack output,
            RecipeFingerprint fingerprint,
            int processingTicks) {
        this(recipeId, inputs.stream().map(List::of).toList(), output, fingerprint, processingTicks, true);
    }

    public static RecipeSnapshot withAlternatives(
            ResourceLocation recipeId,
            List<List<ItemStack>> inputAlternatives,
            ItemStack output,
            RecipeFingerprint fingerprint,
            int processingTicks) {
        return new RecipeSnapshot(recipeId, inputAlternatives, output, fingerprint, processingTicks, true);
    }

    private RecipeSnapshot(
            ResourceLocation recipeId,
            List<List<ItemStack>> inputAlternatives,
            ItemStack output,
            RecipeFingerprint fingerprint,
            int processingTicks,
            boolean ignored) {
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(inputAlternatives, "inputAlternatives");
        if (inputAlternatives.isEmpty() || inputAlternatives.size() > 81) {
            throw new IllegalArgumentException("recipe must have between 1 and 81 inputs");
        }
        this.inputAlternatives = inputAlternatives.stream()
                .map(alternatives -> {
                    if (alternatives == null || alternatives.isEmpty()
                            || alternatives.size() > AggregateInputSlot.configuredAlternativeLimit()) {
                        throw new IllegalArgumentException("input has too many alternatives");
                    }
                    return alternatives.stream().map(stack -> requireStack(stack, "input")).toList();
                })
                .toList();
        this.inputs = this.inputAlternatives.stream().map(List::getFirst).toList();
        this.output = requireStack(output, "output");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.processingTicks = Math.max(1, processingTicks);
    }

    public ResourceLocation recipeId() {
        return recipeId;
    }

    public ItemStack input() {
        return inputs.getFirst().copy();
    }

    public List<ItemStack> inputs() {
        return inputs.stream().map(ItemStack::copy).toList();
    }

    public List<List<ItemStack>> inputAlternatives() {
        return inputAlternatives.stream()
                .map(alternatives -> alternatives.stream().map(ItemStack::copy).toList())
                .toList();
    }

    public ItemStack output() {
        return output.copy();
    }

    public RecipeFingerprint fingerprint() {
        return fingerprint;
    }

    public int processingTicks() {
        return processingTicks;
    }

    private static ItemStack requireStack(ItemStack stack, String name) {
        Objects.requireNonNull(stack, name);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return stack.copy();
    }
}
