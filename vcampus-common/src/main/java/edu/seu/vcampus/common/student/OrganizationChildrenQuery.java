package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Lists children of one organization, optionally including inactive rows. */
/**
 * Carries immutable organization children query data.
 * @param parentId the parent identifier
 * @param activeOnly the active only
 */
public record OrganizationChildrenQuery(String parentId, boolean activeOnly)
        implements Serializable { }
