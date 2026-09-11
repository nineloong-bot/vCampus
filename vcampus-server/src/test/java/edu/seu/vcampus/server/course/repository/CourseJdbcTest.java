package edu.seu.vcampus.server.course.repository;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CourseJdbcTest {
    @Test
    void normalizesGeneratedTimestampsToAccessPrecision() {
        Instant value = Instant.parse("2026-09-11T06:27:49.054356900Z");

        assertThat(CourseJdbc.persistencePrecision(value))
                .isEqualTo(Instant.parse("2026-09-11T06:27:49.054356Z"));
    }
}
