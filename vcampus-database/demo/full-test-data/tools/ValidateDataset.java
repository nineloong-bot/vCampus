import java.nio.file.*;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;

/** 只读核对测试库的数量、关联、金额、库存和全部账号密码。 */
class ValidateDataset {
    static int checks;
    static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
        checks++;
    }
    static long count(Connection c, String sql) throws Exception {
        try (var s=c.createStatement();var r=s.executeQuery(sql)) {r.next();return r.getLong(1);}
    }
    static boolean tableExists(Connection c,String table) throws Exception {
        try(var r=c.getMetaData().getTables(null,null,table,new String[]{"TABLE"})) {
            return r.next();
        }
    }
    static Map<String,BigDecimal> totals(Connection c,String table,String key,String value) throws Exception {
        Map<String,BigDecimal> result=new HashMap<>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT "+key+","+value+" FROM "+table)) {
            while(r.next()) result.merge(r.getString(1),r.getBigDecimal(2),BigDecimal::add);
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        try(var c=DriverManager.getConnection("jdbc:ucanaccess://"+args[0]+";immediatelyReleaseResources=true")) {
            c.setReadOnly(true);
            for(String table:List.of("tblCurriculumPlan","tblCurriculumCourse",
                    "tblCurriculumPrerequisite"))
                require(!tableExists(c,table),"Legacy curriculum table remains "+table);
            for(String line:Files.readAllLines(Path.of(args[1]))) {
                String[] parts=line.split("\\t");
                if(parts[0].equals("tblNumberSequence")) continue;
                String key;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+parts[0]+" WHERE 1=0")) {
                    key=r.getMetaData().getColumnName(1);
                }
                long actual=count(c,"SELECT COUNT(*) FROM "+parts[0]+" WHERE "+key+" LIKE 'bulk-%'");
                require(actual==Long.parseLong(parts[1]),parts[0]+" count "+actual+" expected "+parts[1]);
                System.out.println(parts[0]+"="+actual);
            }
            for(String table:List.of("tblUser","tblStudent","tblDepartment","tblMajor","tblClass",
                    "tblTerm","tblCourse","tblTrainingPlan","tblTrainingPlanCourse",
                    "tblTrainingPlanPrerequisite","tblCourseOffering","tblEnrollment")) {
                System.out.println("TOTAL "+table+"="+count(c,"SELECT COUNT(*) FROM "+table));
            }
            String[][] links={
                {"tblStudent","userId","tblUser","userId"},
                {"tblCourseOffering","teacherUserId","tblUser","userId"},
                {"tblEnrollment","studentId","tblStudent","studentId"},
                {"tblCourseAttempt","studentId","tblStudent","studentId"},
                {"tblBookCopy","bookId","tblBook","bookId"},
                {"tblBookLoan","copyId","tblBookCopy","copyId"},
                {"tblBookLoan","borrowerUserId","tblUser","userId"},
                {"tblOrderItem","skuId","tblProductSku","skuId"}};
            for(var a:links) require(count(c,"SELECT COUNT(*) FROM "+a[0]+" a LEFT JOIN "+a[2]
                    +" b ON a."+a[1]+"=b."+a[3]+" WHERE b."+a[3]+" IS NULL")==0,"Orphans "+a[0]);
            require(count(c,"SELECT COUNT(*) FROM (SELECT enrollmentYear FROM tblClass c "
                    +"INNER JOIN tblStudent s ON c.classId=s.classId WHERE s.studentId LIKE 'bulk-%' "
                    +"GROUP BY enrollmentYear)")==4,"Cohort coverage");
            for(int cohort=2023;cohort<=2026;cohort++) {
                require(count(c,"SELECT COUNT(*) FROM tblStudent s INNER JOIN tblClass c "
                        +"ON s.classId=c.classId WHERE s.studentId LIKE 'bulk-%' AND c.enrollmentYear="
                        +cohort)==250,"Student cohort "+cohort);
                int semester=(2026-cohort)*3+2;
                require(count(c,"SELECT COUNT(*) FROM ((tblTrainingPlan p INNER JOIN "
                        +"tblTrainingPlanCourse pc ON p.planId=pc.planId) INNER JOIN tblCourse x "
                        +"ON pc.courseCode=x.courseCode) INNER JOIN tblCourseOffering o "
                        +"ON x.courseId=o.courseId WHERE p.planId LIKE 'bulk-%' AND p.enrollmentYear="
                        +cohort+" AND pc.semester="+semester+" AND o.termId='bulk-term-current'")>0,
                        "Selectable curriculum for cohort "+cohort);
            }
            require(count(c,"SELECT COUNT(*) FROM tblTrainingPlanCourse pc LEFT JOIN tblCourse x "
                    +"ON pc.courseCode=x.courseCode WHERE pc.planCourseId LIKE 'bulk-%' "
                    +"AND x.courseId IS NULL")==0,"Training plan catalog alignment");
            Map<String,Integer> planSemesters=new HashMap<>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT p.majorId,p.enrollmentYear,"+
                    "x.courseId,pc.semester FROM (tblTrainingPlan p INNER JOIN tblTrainingPlanCourse pc "+
                    "ON p.planId=pc.planId) INNER JOIN tblCourse x ON pc.courseCode=x.courseCode "+
                    "WHERE p.planId LIKE 'bulk-%' AND p.isActive=TRUE AND pc.isActive=TRUE")) {
                while(r.next()) planSemesters.put(r.getString(1)+":"+r.getInt(2)+":"+r.getString(3),r.getInt(4));
            }
            Set<String> failedAttempts=new HashSet<>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT studentId,courseId FROM tblCourseAttempt "+
                    "WHERE attemptId LIKE 'bulk-%' AND outcome='FAILED'")) {
                while(r.next()) failedAttempts.add(r.getString(1)+":"+r.getString(2));
            }
            String enrollmentSql="SELECT e.studentId,e.enrollmentType,o.courseId,c.majorId,"+
                    "c.enrollmentYear,t.academicYearStart,t.season FROM (((tblEnrollment e INNER JOIN "+
                    "tblStudent s ON e.studentId=s.studentId) INNER JOIN tblClass c ON s.classId=c.classId) "+
                    "INNER JOIN tblCourseOffering o ON e.offeringId=o.offeringId) INNER JOIN tblTerm t "+
                    "ON o.termId=t.termId WHERE e.enrollmentId LIKE 'bulk-%' AND e.enrollmentStatus='ACTIVE'";
            try(var s=c.createStatement();var r=s.executeQuery(enrollmentSql)) {
                while(r.next()) {
                    int season=switch(r.getString(7)){case "SUMMER"->1;case "AUTUMN"->2;default->3;};
                    int current=(r.getInt(6)-r.getInt(5))*3+season;
                    Integer planned=planSemesters.get(r.getString(4)+":"+r.getInt(5)+":"+r.getString(3));
                    if("NORMAL".equals(r.getString(2))) require(Objects.equals(planned,current),
                            "Normal enrollment outside current plan "+r.getString(1)+":"+r.getString(3));
                    else require(planned!=null&&planned<current&&failedAttempts.contains(
                            r.getString(1)+":"+r.getString(3)),"Invalid retake "+r.getString(1)+":"+r.getString(3));
                }
            }
            require(count(c,"SELECT COUNT(*) FROM (SELECT season FROM tblTerm "
                    +"WHERE termId LIKE 'bulk-%' GROUP BY season)")==3,"Academic season coverage");
            Map<String,Integer> enrolled=new HashMap<>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT offeringId FROM tblEnrollment WHERE enrollmentStatus='ACTIVE'")) {
                while(r.next()) enrolled.merge(r.getString(1),1,Integer::sum);
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT offeringId,enrolledCount,capacity FROM tblCourseOffering WHERE offeringId LIKE 'bulk-%'")) {
                while(r.next()) {
                    require(r.getInt(2)==enrolled.getOrDefault(r.getString(1),0),"Enrollment count "+r.getString(1));
                    require(r.getInt(2)<=r.getInt(3),"Capacity "+r.getString(1));
                }
            }
            require(count(c,"SELECT COUNT(*) FROM tblCourseSelectionPhase WHERE phaseStatus IN ('OPEN','PREVIEW')")==1,"Global selection phase count");
            require(count(c,"SELECT COUNT(*) FROM tblBookLoan l INNER JOIN tblBookCopy p ON l.copyId=p.copyId "
                    +"WHERE l.loanId LIKE 'bulk-%' AND l.loanStatus IN ('ACTIVE','OVERDUE') AND p.copyStatus<>'BORROWED'")==0,"Borrowed copy mismatch");
            require(count(c,"SELECT COUNT(*) FROM tblBookLoan WHERE loanId LIKE 'bulk-%' AND loanStatus='RETURNED' AND returnedAt IS NULL")==0,"Return timestamp missing");
            var lineTotals=totals(c,"tblOrderItem","orderId","lineAmount");
            var orderTotals=totals(c,"tblOrder","orderGroupId","orderAmount");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT orderId,orderAmount FROM tblOrder WHERE orderId LIKE 'bulk-%'")) {
                while(r.next()) require(r.getBigDecimal(2).compareTo(lineTotals.get(r.getString(1)))==0,"Order amount "+r.getString(1));
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT orderGroupId,totalAmount FROM tblOrderGroup WHERE orderGroupId LIKE 'bulk-%'")) {
                while(r.next()) require(r.getBigDecimal(2).compareTo(orderTotals.get(r.getString(1)))==0,"Group amount "+r.getString(1));
            }
            require(count(c,"SELECT COUNT(*) FROM tblOrderItem WHERE orderItemId LIKE 'bulk-%' AND lineAmount<>unitPrice*quantity")==0,"Line multiplication");
            require(count(c,"SELECT COUNT(*) FROM tblPayment p INNER JOIN tblOrderGroup g ON p.orderGroupId=g.orderGroupId "
                    +"WHERE p.paymentId LIKE 'bulk-%' AND p.amount<>g.totalAmount")==0,"Payment amount");
            Map<String,Integer> reserved=new HashMap<>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT skuId,quantity FROM tblInventoryReservation WHERE reservationStatus='ACTIVE'")) {
                while(r.next()) reserved.merge(r.getString(1),r.getInt(2),Integer::sum);
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT skuId,stockQuantity,reservedQuantity FROM tblProductSku WHERE skuId LIKE 'bulk-%'")) {
                while(r.next()) {
                    require(r.getInt(3)==reserved.getOrDefault(r.getString(1),0),"Reservation mismatch "+r.getString(1));
                    require(r.getInt(2)>=r.getInt(3)&&r.getInt(3)>=0,"Stock bounds");
                }
            }
            int accounts=0;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT loginId,passwordHash,passwordSalt,passwordIterations FROM tblUser")) {
                while(r.next()) {
                    var spec=new PBEKeySpec("Test12345".toCharArray(),Base64.getDecoder().decode(r.getString(3)),r.getInt(4),256);
                    byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
                    spec.clearPassword();
                    require(MessageDigest.isEqual(hash,Base64.getDecoder().decode(r.getString(2))),"Password mismatch "+r.getString(1));
                    accounts++;
                }
            }
            System.out.println("Password verified accounts="+accounts);
            System.out.println("PASS checks="+checks);
        }
    }
}
