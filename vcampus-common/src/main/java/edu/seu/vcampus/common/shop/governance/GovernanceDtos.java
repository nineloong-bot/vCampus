package edu.seu.vcampus.common.shop.governance;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Typed requests and privacy-safe records for simulated shop governance. */
public final class GovernanceDtos {
    private GovernanceDtos() { }
    /** Text-only application; accepting the platform rules is mandatory. */
    public record Apply(String shopName, String subjectName, String licenseNumber,
            boolean rulesAccepted) implements Serializable { }
    /** Review decision for one pending record. */
    public record Review(String id, boolean approved, String reason) implements Serializable { }
    /** Simulated special license application. */
    public record Qualification(String type, String number, LocalDate expiresOn) implements Serializable { }
    /** REPORT_PRODUCT, REPORT_SHOP, REOPEN or REMEDIATION request. */
    public record SubmitCase(String kind, String objectId, String reason,
            String description) implements Serializable { }
    /** WARN, SUSPEND, EMERGENCY or NO_VIOLATION, optionally resolving a report. */
    public record Action(String objectId, String action, String reason,
            List<String> productIds, String reportId) implements Serializable {
        /** Copies associated product identifiers. */
        public Action { productIds = productIds == null ? List.of() : List.copyOf(productIds); }
    }
    /** The editable store introduction. */
    public record Settings(String description) implements Serializable { }
    /** SELF, ADMIN or SHOP list scope and optional object identifier. */
    public record Query(String scope, String objectId) implements Serializable { }
    /** Public business record: contains no reporter identity. */
    public record View(String id, String kind, String objectId, String state, String title,
            String description, String reason, String subjectName, String licenseNumber,
            LocalDate expiresOn) implements Serializable { }
    /** Immutable response collection. */
    public record Views(List<View> items) implements Serializable {
        /** Defensively copies the response records. */
        public Views { items = List.copyOf(items); }
    }
    /** Audit identity is available to administrators only. */
    public record Audit(String id, String actorId, String objectId, String action, String reason,
            String occurredAt, String beforeState, String afterState, String linkedId) implements Serializable { }
    /** Administrator audit response. */
    public record Audits(List<Audit> items) implements Serializable {
        /** Defensively copies the audit records. */
        public Audits { items = List.copyOf(items); }
    }
}
