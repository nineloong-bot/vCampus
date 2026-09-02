package edu.seu.vcampus.server.student.pdf;

import edu.seu.vcampus.common.student.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfilePdfServiceTest {
    @Test
    void pdfContainsApprovedChineseProfileAndNeverDraftValues() throws Exception {
        var service = new StudentProfilePdfService();

        PdfDocument pdf = service.generate(formalProfile(),
                Instant.parse("2026-08-31T08:00:00Z"));

        assertThat(pdf.filename()).isEqualTo("学籍基本信息_09023101_测试学生.pdf");
        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = Normalizer.normalize(new PDFTextStripper().getText(document), Normalizer.Form.NFKC);
            assertThat(text).contains("学生基本信息表", "测试学生", "住校", "数据以系统正式档案为准");
            assertThat(text).doesNotContain("走读");
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
        Path qa = Path.of("target", "pdf-qa", "student-profile.pdf");
        Files.createDirectories(qa.getParent());
        Files.write(qa, pdf.content());
    }

    private static StudentProfileData formalProfile() {
        StudentView core = new StudentView("student-1", "user-1", "213230001",
                "09023101", StudentType.UNDERGRADUATE, "测试学生", "男",
                "student@seu.edu.cn", "13800000000", "major-1", "class-1",
                LocalDate.of(2023, 9, 1), StudentStatus.ACTIVE, 2,
                "计算机科学与工程学院", "软件工程", "090231班");
        StudentPersonalProfile personal = new StudentPersonalProfile(
                "CESHI XUESHENG", null, "共青团员", "汉族", "未婚", "居民身份证",
                "320101200501010011", null, LocalDate.of(2005, 1, 1), "江苏省", "中国",
                "江苏省南京市", "江苏省南京市", "非农业家庭户口", "江苏省南京市",
                "江苏省南京市", "否", "无宗教信仰", true, LocalDate.of(2020, 12, 12),
                false, null, "健康或良好", "A", 58, 172, "魔方", "乒乓球", false,
                "student@seu.edu.cn", "13800000000");
        StudentAcademicProfile academic = new StudentAcademicProfile("本科生", true, true,
                "正常", "九龙湖校区", "2023", "计算机科学与工程学院", "计算机科学与技术",
                "计算机科学与技术2301班", "本科", "非定向", 4,
                AttendanceMode.RESIDENT, null, null, LocalDate.of(2027, 7, 30), null,
                null, null, "张航", null);
        return new StudentProfileData(core, personal, academic);
    }
}
