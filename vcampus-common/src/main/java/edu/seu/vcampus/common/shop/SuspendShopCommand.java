package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Suspends an active shop with an auditable reason. */
public record SuspendShopCommand(String shopId, String reason,
        long expectedVersion) implements Serializable { }
