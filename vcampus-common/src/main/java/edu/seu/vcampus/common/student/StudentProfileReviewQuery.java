package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Paged administrator query for pending profile applications. */
public record StudentProfileReviewQuery(int page, int pageSize) implements Serializable { }
