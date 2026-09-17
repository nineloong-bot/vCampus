package edu.seu.vcampus.server.course.repository;

/** Persisted classroom capacity used by offering scheduling. */
public record Classroom(String classroom, int capacity, boolean active) {
}
