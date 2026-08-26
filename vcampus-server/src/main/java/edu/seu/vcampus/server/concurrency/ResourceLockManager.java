package edu.seu.vcampus.server.concurrency;

import java.util.List;
import java.util.function.Supplier;

/** Serializes application actions by ordered resource keys. */
public interface ResourceLockManager {
    /** Acquires keys in their declared order, executes the action, then releases in reverse. */
    <T> T withLocks(List<ResourceKey> orderedKeys, Supplier<T> action);
}
