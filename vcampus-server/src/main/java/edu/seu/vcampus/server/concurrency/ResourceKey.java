package edu.seu.vcampus.server.concurrency;

import java.util.Objects;

/** Stable application-lock identity composed of resource type and resource id. */
public record ResourceKey(String resourceType, String resourceId) {
    /** Validates both key components. */
    public ResourceKey {
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
    }
}
