package edu.seu.vcampus.common.student;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileContractTest {
    @Test
    void attendanceModesExposeExactlyTheFourApprovedLabels() {
        assertThat(Arrays.stream(AttendanceMode.values()).map(AttendanceMode::displayName))
                .containsExactly("走读", "住校", "借宿", "其他");
        assertThat(AttendanceMode.fromDisplayName("住校"))
                .isEqualTo(AttendanceMode.RESIDENT);
    }

    @Test
    void workspaceRoundTripsAcrossTheSocketSerializationBoundary() throws Exception {
        StudentProfileWorkspace expected = StudentProfileFixtures.workspace();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(expected);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(input.readObject()).isEqualTo(expected);
        }
    }

    private static final class StudentProfileFixtures {
        private static StudentProfileWorkspace workspace() {
            StudentView core = new StudentView("student-1", "user-1", "213230001",
                    "09023101", StudentType.UNDERGRADUATE, "测试学生", "男",
                    "student@seu.edu.cn", "13800000000", "major-1", "class-1",
                    java.time.LocalDate.of(2023, 9, 1), StudentStatus.ACTIVE, 2,
                    "计算机科学与工程学院", "软件工程", "090231班");
            StudentPersonalProfile personal = new StudentPersonalProfile(
                    "CESHI XUESHENG", null, "共青团员", "汉族", "未婚",
                    "居民身份证", "320101200501010011", null,
                    java.time.LocalDate.of(2005, 1, 1), "江苏省", "中国", "江苏省南京市",
                    "江苏省南京市", "非农业家庭户口", "江苏省南京市",
                    "江苏省南京市", "否", "无宗教信仰", true,
                    java.time.LocalDate.of(2020, 12, 12), false, null,
                    "健康或良好", "A", 58, 172, "魔方", "乒乓球", false,
                    "student@seu.edu.cn", "13800000000");
            StudentAcademicProfile academic = new StudentAcademicProfile(
                    "本科生", true, true, "正常", "九龙湖校区", "2023",
                    "计算机科学与工程学院", "计算机科学与技术", "计算机科学与技术2301班",
                    "本科", "非定向", 4, AttendanceMode.RESIDENT, null, null,
                    java.time.LocalDate.of(2027, 7, 30), null, null, null,
                    "张航", null);
            StudentProfileData formal = new StudentProfileData(core, personal, academic);
            return new StudentProfileWorkspace(formal, null);
        }
    }
}
