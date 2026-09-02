package edu.seu.vcampus.server.course.repository;

import java.time.DayOfWeek;

/** One weekly, inclusive week-and-period schedule row for an offering. */
public record Schedule(String scheduleId, String offeringId, DayOfWeek dayOfWeek, int startPeriod,
                       int endPeriod, int startWeek, int endWeek, String classroom) {
}
