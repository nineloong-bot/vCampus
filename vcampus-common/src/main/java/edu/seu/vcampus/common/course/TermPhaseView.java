package edu.seu.vcampus.common.course; import java.io.*; import java.time.Instant;
/** Server-time phase snapshot used by adjustment pages. */ /**
 * Carries immutable term phase view data.
 * @param termId the term identifier
 * @param termStatus the term status
 * @param phase the phase
 * @param serverTime the server time
 * @param enrollmentStartAt the enrollment start at
 * @param enrollmentEndAt the enrollment end at
 * @param adjustmentStartAt the adjustment start at
 * @param adjustmentEndAt the adjustment end at
 */
public record TermPhaseView(String termId,String termStatus,String phase,Instant serverTime,Instant enrollmentStartAt,Instant enrollmentEndAt,Instant adjustmentStartAt,Instant adjustmentEndAt)implements Serializable{@Serial private static final long serialVersionUID=1L;}
