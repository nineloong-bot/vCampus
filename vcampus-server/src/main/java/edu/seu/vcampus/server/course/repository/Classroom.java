package edu.seu.vcampus.server.course.repository;

/** Persisted classroom capacity and shared-venue semantics. */
public record Classroom(String classroom, int capacity, boolean active,
                        boolean sharedSportsVenue) {
}
