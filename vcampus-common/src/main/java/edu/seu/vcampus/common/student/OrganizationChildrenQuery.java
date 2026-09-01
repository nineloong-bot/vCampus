package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Lists children of one organization, optionally including inactive rows. */
public record OrganizationChildrenQuery(String parentId, boolean activeOnly)
        implements Serializable { }
