package io.github.langqi99.aeallpattern.internal.routing.ae2.crafting;

/**
 * Per-order recipe routing preferences consumed by the fast crafting planner.
 *
 * <p>The policy is deliberately compact so host mods can attach it to one
 * calculation without mutating global recipe state.</p>
 */
public record CraftingRoutePolicy(
        int aggregatePriority,
        boolean requireFeasible,
        int pathPreference,
        int stockSurplusPreference,
        int yieldPreference,
        boolean preferFast,
        int preferenceOrder,
        boolean allowByproductOrders,
        boolean allowAmplifyingCycles) {
    public static final int CRITERION_PATH = 0;
    public static final int CRITERION_STOCK_SURPLUS = 1;
    public static final int CRITERION_HIGH_YIELD = 2;
    public static final int CRITERION_FAST = 3;
    public static final int CRITERION_COUNT = 4;
    /** Stock surplus -> waiting time -> short path -> single-run output. */
    public static final int DEFAULT_PREFERENCE_ORDER = 0x2031;

    public static final CraftingRoutePolicy DEFAULT =
            new CraftingRoutePolicy(-1, true, -1, 1, 1, true, DEFAULT_PREFERENCE_ORDER, false, true);

    public static final int MIN_PRIORITY = -32;
    public static final int MAX_PRIORITY = 32;

    public CraftingRoutePolicy {
        aggregatePriority = Math.clamp(aggregatePriority, MIN_PRIORITY, MAX_PRIORITY);
        pathPreference = Math.clamp(pathPreference, -1, 1);
        stockSurplusPreference = Math.clamp(stockSurplusPreference, -1, 1);
        yieldPreference = Math.clamp(yieldPreference, -1, 1);
        preferenceOrder = normalizeOrder(preferenceOrder);
    }

    /** Compatibility constructor for integrations built against the original on/off preferences. */
    public CraftingRoutePolicy(
            int aggregatePriority,
            boolean requireFeasible,
            int pathPreference,
            boolean preferStockSurplus,
            boolean preferHighYield,
            boolean preferFast,
            int preferenceOrder) {
        this(
                aggregatePriority,
                requireFeasible,
                pathPreference,
                preferStockSurplus ? 1 : 0,
                preferHighYield ? 1 : 0,
                preferFast,
                preferenceOrder,
                false,
                true);
    }

    /** Compatibility constructor for policies created before byproduct ordering was configurable. */
    public CraftingRoutePolicy(
            int aggregatePriority,
            boolean requireFeasible,
            int pathPreference,
            int stockSurplusPreference,
            int yieldPreference,
            boolean preferFast,
            int preferenceOrder) {
        this(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference,
                yieldPreference, preferFast, preferenceOrder, false, true);
    }

    /** Compatibility constructor for policies created before amplifying cycles were configurable. */
    public CraftingRoutePolicy(
            int aggregatePriority,
            boolean requireFeasible,
            int pathPreference,
            int stockSurplusPreference,
            int yieldPreference,
            boolean preferFast,
            int preferenceOrder,
            boolean allowByproductOrders) {
        this(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference,
                yieldPreference, preferFast, preferenceOrder, allowByproductOrders, true);
    }

    public CraftingRoutePolicy withAggregatePriority(int value) {
        return new CraftingRoutePolicy(
                value, requireFeasible, pathPreference, stockSurplusPreference, yieldPreference, preferFast,
                preferenceOrder, allowByproductOrders, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withPathPreference(int value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, value, stockSurplusPreference, yieldPreference, preferFast,
                preferenceOrder, allowByproductOrders, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withStockSurplus(boolean value) {
        return withStockSurplusPreference(value ? 1 : 0);
    }

    public CraftingRoutePolicy withStockSurplusPreference(int value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, pathPreference, value, yieldPreference, preferFast,
                preferenceOrder, allowByproductOrders, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withHighYield(boolean value) {
        return withYieldPreference(value ? 1 : 0);
    }

    public CraftingRoutePolicy withYieldPreference(int value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference, value, preferFast,
                preferenceOrder, allowByproductOrders, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withFast(boolean value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference, yieldPreference, value,
                preferenceOrder, allowByproductOrders, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withPreferenceOrder(int value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference, yieldPreference, preferFast,
                value, allowByproductOrders, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withByproductOrders(boolean value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference, yieldPreference, preferFast,
                preferenceOrder, value, allowAmplifyingCycles);
    }

    public CraftingRoutePolicy withAmplifyingCycles(boolean value) {
        return new CraftingRoutePolicy(
                aggregatePriority, requireFeasible, pathPreference, stockSurplusPreference, yieldPreference, preferFast,
                preferenceOrder, allowByproductOrders, value);
    }

    /** True only for the positive (more) direction; retained for source compatibility. */
    public boolean preferStockSurplus() {
        return stockSurplusPreference > 0;
    }

    /** True only for the positive (more) direction; retained for source compatibility. */
    public boolean preferHighYield() {
        return yieldPreference > 0;
    }

    public int criterionAt(int position) {
        if (position < 0 || position >= CRITERION_COUNT) {
            throw new IndexOutOfBoundsException(position);
        }
        return (preferenceOrder >>> (position * 4)) & 0xF;
    }

    public CraftingRoutePolicy moveCriterion(int from, int to) {
        if (from < 0 || from >= CRITERION_COUNT || to < 0 || to >= CRITERION_COUNT || from == to) {
            return this;
        }
        int[] order = new int[CRITERION_COUNT];
        for (int i = 0; i < CRITERION_COUNT; i++) {
            order[i] = criterionAt(i);
        }
        int moved = order[from];
        if (from < to) {
            System.arraycopy(order, from + 1, order, from, to - from);
        } else {
            System.arraycopy(order, to, order, to + 1, from - to);
        }
        order[to] = moved;
        return withPreferenceOrder(packOrder(order));
    }

    public String serialize() {
        return aggregatePriority + "," + (requireFeasible ? 1 : 0) + "," + pathPreference + ","
                + stockSurplusPreference + "," + yieldPreference + ","
                + (preferFast ? 1 : 0) + "," + preferenceOrder + "," + (allowByproductOrders ? 1 : 0)
                + "," + (allowAmplifyingCycles ? 1 : 0);
    }

    public static CraftingRoutePolicy deserialize(String serialized) {
        if (serialized == null) {
            return DEFAULT;
        }
        String[] values = serialized.split(",", -1);
        if (values.length < 6 || values.length > 9) {
            return DEFAULT;
        }
        try {
            return new CraftingRoutePolicy(
                    Integer.parseInt(values[0]),
                    !values[1].equals("0"),
                    Integer.parseInt(values[2]),
                    Integer.parseInt(values[3]),
                    Integer.parseInt(values[4]),
                    values[5].equals("1"),
                    values.length >= 7 ? Integer.parseInt(values[6]) : DEFAULT_PREFERENCE_ORDER,
                    values.length >= 8 && values[7].equals("1"),
                    values.length < 9 || values[8].equals("1"));
        } catch (NumberFormatException ignored) {
            return DEFAULT;
        }
    }

    private static int normalizeOrder(int packed) {
        boolean[] seen = new boolean[CRITERION_COUNT];
        int[] order = new int[CRITERION_COUNT];
        for (int i = 0; i < CRITERION_COUNT; i++) {
            int criterion = (packed >>> (i * 4)) & 0xF;
            if (criterion >= CRITERION_COUNT || seen[criterion]) {
                return DEFAULT_PREFERENCE_ORDER;
            }
            seen[criterion] = true;
            order[i] = criterion;
        }
        return packOrder(order);
    }

    private static int packOrder(int[] order) {
        int packed = 0;
        for (int i = 0; i < CRITERION_COUNT; i++) {
            packed |= (order[i] & 0xF) << (i * 4);
        }
        return packed;
    }
}
