package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Paged administrator query for pending profile applications. */
/**
 * Carries immutable student profile review query data.
 * @param page the page
 * @param pageSize the page size
 */
public record StudentProfileReviewQuery(int page, int pageSize) implements Serializable { }
