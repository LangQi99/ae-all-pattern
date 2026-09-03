package io.github.langqi99.aeallpattern.internal.routing.core.planner;

/**
 * Marker for a reusable seed view backed by the same physical inventory as ordinary stock.
 * It may reserve stock for execution ordering, but must never be added to ordinary stock a second time.
 */
public interface OrdinaryStockAlias {
}
