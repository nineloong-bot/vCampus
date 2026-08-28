package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/** Resumes a suspended shop. */
public record ResumeShopCommand(String shopId,
        long expectedVersion) implements Serializable { }
