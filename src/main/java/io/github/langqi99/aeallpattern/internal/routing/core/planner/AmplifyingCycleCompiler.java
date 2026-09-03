package io.github.langqi99.aeallpattern.internal.routing.core.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Compiles a directly self-amplifying recipe into the planner's executable closed-loop form.
 *
 * <p>For example, {@code A + D -> 2 A} becomes {@code returned(A) + D -> net 1 A}. The returned
 * input is the physical startup seed and the reduced output is the amount added by each firing.
 * The original pattern source is retained so the AE adapter still schedules the real recipe.</p>
 *
 * <p>This proof is deliberately narrow. Every input matching the output must be an ordinary,
 * consumed input and the primary output must be strictly larger than their sum. Ambiguous returns,
 * remainders, finite-use inputs and non-growing self loops are left to the existing cycle guard.</p>
 */
public final class AmplifyingCycleCompiler {
    private AmplifyingCycleCompiler() {
    }

    public record Compiled<K>(CraftPattern<K> pattern, long seedAmount) {
    }

    public static <K> Compiled<K> compile(
            CraftPattern<K> original,
            LongFunction<CraftInput<K>> seedFactory) {
        long seedAmount = 0L;
        boolean foundSelfInput = false;
        List<CraftInput<K>> externalInputs = new ArrayList<>(original.inputs().size());
        for (CraftInput<K> input : original.inputs()) {
            if (!original.output().equals(input.key())) {
                externalInputs.add(input);
                continue;
            }
            foundSelfInput = true;
            if (input.returned() || input.remainder() != null
                    || input.reusableStockSource() != null) {
                return null;
            }
            seedAmount = Sat.add(seedAmount, input.amount());
        }
        if (!foundSelfInput || seedAmount <= 0 || original.outputAmount() <= seedAmount) {
            return null;
        }

        CraftInput<K> seed = seedFactory.apply(seedAmount);
        if (seed == null || !original.output().equals(seed.key())
                || !seed.returned() || seed.uses() != CraftInput.INFINITE_USES) {
            throw new IllegalArgumentException("amplifying-cycle seed must be the returned output key");
        }
        externalInputs.add(seed);
        CraftPattern<K> compiled = new CraftPattern<>(
                original.output(),
                original.outputAmount() - seedAmount,
                externalInputs,
                original.byproducts(),
                original.source(),
                original.idleProviderCount(),
                original.providerCount());
        return new Compiled<>(compiled, seedAmount);
    }
}
