package edu.seu.vcampus.server.bootstrap;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

class VerifyDistributionDataTest {
    @Test
    void verifyData() throws Exception {
        for (String dbPath : List.of("../vcampus-distribution/data/vCampus.accdb", "../vCampus-release/data/vCampus.accdb")) {
            String url = "jdbc:ucanaccess://" + dbPath + ";immediatelyReleaseResources=true";
            try (var conn = DriverManager.getConnection(url);
                 var stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                        UPDATE tblStudent
                        SET enrolled = TRUE, onCampus = TRUE, campus = '九龙湖校区',
                            educationLevel = '本科生', trainingMode = '普通全日制',
                            programLengthYears = 4, attendanceMode = '全日制',
                            expectedGraduationDate = #2027-06-30#, counselorName = '周辅导'
                        WHERE studentNumber = '09023999'
                        """);
                try (var rs = stmt.executeQuery("SELECT enrolled, onCampus FROM tblStudent WHERE studentNumber='09023999'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("enrolled")).isTrue();
                    assertThat(rs.getBoolean("onCampus")).isTrue();
                    System.out.println(dbPath + " 09023999 enrolled=" + rs.getBoolean("enrolled") + ", onCampus=" + rs.getBoolean("onCampus"));
                }
                try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM tblMajorTransferApplication")) {
                    rs.next();
                    System.out.println(dbPath + " TOTAL APPLICATIONS: " + rs.getInt(1));
                }
            }
        }
    }
}
