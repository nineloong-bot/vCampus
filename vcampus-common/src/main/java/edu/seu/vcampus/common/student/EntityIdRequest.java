package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable entity id request data.
 * @param entityId the entity identifier
 */
public record EntityIdRequest(String entityId) implements Serializable { }
