package edu.seu.vcampus.common.protocol;
import java.io.*; import java.util.Objects;
/** Typed request for one stable entity identifier. */
/**
 * Carries immutable entity id request data.
 * @param entityId the entity identifier
 */
public record EntityIdRequest(String entityId) implements Serializable {
 @Serial private static final long serialVersionUID=1L;
 /**
 * Validates and creates a entity identifier request.
 * @param entityId the entity identifier
 */
public EntityIdRequest { Objects.requireNonNull(entityId,"entityId"); if(entityId.isBlank()) throw new IllegalArgumentException("entityId"); }
}
