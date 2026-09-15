package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable parent id query data.
 * @param parentId the parent identifier
 */
public record ParentIdQuery(String parentId) implements Serializable { }
