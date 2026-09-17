package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MajorTransferScoreImportTest {

    @Test
    void parsesExportedTemplateCsvWithChineseHeadersAndBom(@TempDir Path tempDir) throws Exception {
        Path csvPath = tempDir.resolve("template.csv");
        String content = "\uFEFF申请编号,一卡通号,姓名,原学院,原专业,目标专业,笔试成绩,面试成绩\r\n"
                + "app-101,70125101,钱子涵,数学院,数学与应用数学,计算机科学与技术,88.5,92.0\r\n"
                + "app-102,70125102,孙八,数学院,数学与应用数学,计算机科学与技术,79.0,85.5\r\n";
        Files.writeString(csvPath, content, StandardCharsets.UTF_8);

        List<ImportMajorTransferScoresCommand.ScoreEntry> entries =
                MajorTransferScoreImport.parse(csvPath.toFile());

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).applicationId()).isEqualTo("app-101");
        assertThat(entries.get(0).writtenScore()).isEqualByComparingTo(new BigDecimal("88.5"));
        assertThat(entries.get(0).interviewScore()).isEqualByComparingTo(new BigDecimal("92.0"));

        assertThat(entries.get(1).applicationId()).isEqualTo("app-102");
        assertThat(entries.get(1).writtenScore()).isEqualByComparingTo(new BigDecimal("79.0"));
        assertThat(entries.get(1).interviewScore()).isEqualByComparingTo(new BigDecimal("85.5"));
    }

    @Test
    void parsesLegacyThreeColumnCsv(@TempDir Path tempDir) throws Exception {
        Path csvPath = tempDir.resolve("legacy.csv");
        String content = "app-201,80,90\r\n";
        Files.writeString(csvPath, "id,written,interview\r\n" + content, StandardCharsets.UTF_8);

        List<ImportMajorTransferScoresCommand.ScoreEntry> entries =
                MajorTransferScoreImport.parse(csvPath.toFile());

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).applicationId()).isEqualTo("app-201");
        assertThat(entries.get(0).writtenScore()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(entries.get(0).interviewScore()).isEqualByComparingTo(new BigDecimal("90"));
    }

    @Test
    void collegeActionsRendersDownloadCsvButtonForQualifiedApplications() {
        StudentClientService service = mock(StudentClientService.class);
        JPanel actions = new JPanel();
        JLabel status = new JLabel();
        MajorTransferCollegeActions collegeActions = new MajorTransferCollegeActions(
                actions, service, actions, status, () -> {}, () -> {}, f -> {});

        Instant now = Instant.now();
        MajorTransferApplicationView qualifiedApp = new MajorTransferApplicationView(
                "app-1", "b1", "s1", "钱子涵", MajorTransferApplicationType.ORDINARY,
                MajorTransferStatus.QUALIFIED, "opt-1", "m1", "计科", "d1", "计算机学院",
                "d2", "数学院", "m2", "数应", "c1", "班级", "70125101", "2024", "理由",
                null, null, null, List.of(), List.of(), true, true, 0, now, now, now);

        collegeActions.render(qualifiedApp, true);

        JButton downloadBtn = findButton(actions, "downloadScoreCsvButton");
        assertThat(downloadBtn).isNotNull();
        assertThat(downloadBtn.getText()).isEqualTo("下载成绩CSV");

        JButton importBtn = findButton(actions, "importScoreButton");
        assertThat(importBtn).isNotNull();

        JButton recordBtn = findButton(actions, "recordScoreButton");
        assertThat(recordBtn).isNotNull();

        // When status is SUBMITTED, download button should not appear
        MajorTransferApplicationView submittedApp = new MajorTransferApplicationView(
                "app-2", "b1", "s2", "李四", MajorTransferApplicationType.ORDINARY,
                MajorTransferStatus.SUBMITTED, "opt-1", "m1", "计科", "d1", "计算机学院",
                "d2", "数学院", "m2", "数应", "c1", "班级", "70125102", "2024", "理由",
                null, null, null, List.of(), List.of(), true, false, 0, now, now, now);
        collegeActions.render(submittedApp, true);
        assertThat(findButton(actions, "downloadScoreCsvButton")).isNull();
    }

    private static JButton findButton(JPanel panel, String name) {
        for (java.awt.Component c : panel.getComponents()) {
            if (c instanceof JButton btn && name.equals(btn.getName())) {
                return btn;
            }
        }
        return null;
    }
}
