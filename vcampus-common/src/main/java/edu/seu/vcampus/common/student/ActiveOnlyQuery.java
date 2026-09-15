package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable active only query data.
 * @param activeOnly the active only
 */
public record ActiveOnlyQuery(boolean activeOnly) implements Serializable { }
