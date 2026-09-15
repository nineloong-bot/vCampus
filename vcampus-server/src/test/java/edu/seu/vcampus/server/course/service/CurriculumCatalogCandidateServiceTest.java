package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.server.course.repository.CurriculumCatalogCandidateRepository.Definition;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CurriculumCatalogCandidateServiceTest {
    @Test void deduplicatesIdenticalDefinitionsAndMarksMetadataConflicts() {
        var rows = List.of(
                definition("pc-1", "CS101", "程序设计", "3", 48, "REQUIRED", "d1"),
                definition("pc-2", "cs101", "程序设计", "3.0", 48, "REQUIRED", "d1"),
                definition("pc-3", "CS102", "数据结构", "3", 48, "REQUIRED", "d1"),
                definition("pc-4", "CS102", "数据结构", "4", 64, "REQUIRED", "d1"),
                definition("pc-5", "CS103", "数据库", "3", 48, "REQUIRED", "d1"));

        var result = CurriculumCatalogCandidateService.aggregate(rows, List.of("CS103"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).courseCode()).isEqualTo("CS101");
        assertThat(result.get(0).conflicted()).isFalse();
        assertThat(result.get(1).courseCode()).isEqualTo("CS102");
        assertThat(result.get(1).conflicted()).isTrue();
    }

    private static Definition definition(String id, String code, String name, String credits,
                                         int hours, String nature, String department) {
        return new Definition(id, code, name, new BigDecimal(credits), hours, nature,
                department, "计算机学院");
    }
}
