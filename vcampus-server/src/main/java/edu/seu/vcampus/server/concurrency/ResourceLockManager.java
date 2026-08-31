package edu.seu.vcampus.server.concurrency;

import java.util.List;
import java.util.function.Supplier;

/** Serializes application actions by ordered resource keys. */
public interface ResourceLockManager {
    /** Acquires the resources in a deadlock-safe order, executes the action, then releases them. */
    <T> T withLocks(List<ResourceKey> orderedKeys, Supplier<T> action);
}
