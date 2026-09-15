package edu.seu.vcampus.server.bootstrap;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Seed idempotency checks for the application schema initializer segments. */
abstract class ApplicationSchemaInitializerSeeds extends ApplicationSchemaInitializerParsing {
    static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+([A-Za-z0-9_]+)");
    static final Pattern CREATE_INDEX = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+([A-Za-z0-9_]+)\\s+ON\\s+([A-Za-z0-9_]+)");
    static final Pattern ALTER_TABLE = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\s+([A-Za-z0-9_]+)");
    static final Pattern INSERT = Pattern.compile(
            "(?is)^\\s*INSERT\\s+INTO\\s+([A-Za-z0-9_]+)\\s*\\((.*?)\\)\\s*VALUES\\s*\\((.*)\\)\\s*$");
    static final Map<String, List<String>> SEED_KEYS = Map.ofEntries(
            Map.entry("tblrole", List.of("roleCode")),
            Map.entry("tbluser", List.of("userId")),
            Map.entry("tblpermission", List.of("permissionCode")),
            Map.entry("tblrolepermission", List.of("roleCode", "permissionCode")),
            Map.entry("tbldepartment", List.of("departmentId")),
            Map.entry("tblmajor", List.of("majorId")),
            Map.entry("tblclass", List.of("classId")),
            Map.entry("tblnumbersequence", List.of("sequenceKey")),
            Map.entry("tblstudent", List.of("studentId")),
            Map.entry("tblstudentcollegeadministrator", List.of("departmentId", "userId")),
            Map.entry("tblmajortransferbatch", List.of("batchId")),
            Map.entry("tblmajortransferoption", List.of("optionId")),
            Map.entry("tblmajortransferapplication", List.of("applicationId")),
            Map.entry("tblmajortransferattachment", List.of("attachmentId")),
            Map.entry("tblmajortransferreview", List.of("reviewId")),
            Map.entry("tblmajortransferexecution", List.of("executionId")),
            Map.entry("tbltrainingplan", List.of("planId")),
            Map.entry("tbltrainingplancourse", List.of("planCourseId")),
            Map.entry("tblstudentgrade", List.of("gradeId")),
            Map.entry("tblcrosscourseapplication", List.of("applicationId")),
            Map.entry("tbllibrarypolicy", List.of("policyId")),
            Map.entry("tblterm", List.of("termId")),
            Map.entry("tblcourseselectionphase", List.of("phaseId")),
            Map.entry("tblcourse", List.of("courseId")),
            Map.entry("tblcourseoffering", List.of("offeringId")),
            Map.entry("tblcourseschedule", List.of("scheduleId")),
            Map.entry("tblenrollment", List.of("enrollmentId")),
            Map.entry("tblenrollmentadjustment", List.of("adjustmentId")),
            Map.entry("tblcourseattempt", List.of("attemptId")),
            Map.entry("tblcurriculumplan", List.of("planId")),
            Map.entry("tblcurriculumcourse", List.of("planCourseId")),
            Map.entry("tblcurriculumprerequisite", List.of("prerequisiteId")),
            Map.entry("tblcourseretakequota", List.of("offeringId")),
            Map.entry("tblbook", List.of("bookId")),
            Map.entry("tblbookcopy", List.of("copyId")),
            Map.entry("tblbookloan", List.of("loanId")),
            Map.entry("tblsellerapplication", List.of("applicationId")),
            Map.entry("tblshop", List.of("shopId")),
            Map.entry("tblproduct", List.of("productId")),
            Map.entry("tblproductsku", List.of("skuId")),
            Map.entry("tblcart", List.of("cartId")),
            Map.entry("tblcartitem", List.of("cartItemId")),
            Map.entry("tblordergroup", List.of("orderGroupId")),
            Map.entry("tblorder", List.of("orderId")),
            Map.entry("tblorderitem", List.of("orderItemId")),
            Map.entry("tblpayment", List.of("paymentId")),
            Map.entry("tblpaymentattempt", List.of("attemptId")),
            Map.entry("tblinventoryreservation", List.of("reservationId")));

    ApplicationSchemaInitializerSeeds(Path resourceRoot) {
        super(resourceRoot);
    }

    static boolean seedExists(Connection connection, String sql) throws SQLException {
        Matcher insert = INSERT.matcher(sql);
        if (!insert.matches()) throw new SQLException("Unsupported seed statement: " + sql);
        String table = insert.group(1);
        List<String> keys = SEED_KEYS.get(normalize(table));
        if (keys == null) throw new SQLException("No idempotency key configured for seed table: " + table);
        List<String> columns = splitValues(insert.group(2));
        List<String> values = splitValues(insert.group(3));
        StringBuilder query = new StringBuilder("SELECT 1 FROM ").append(table).append(" WHERE ");
        List<String> keyValues = new ArrayList<>();
        for (String key : keys) {
            int column = indexOf(columns, key);
            if (column < 0 || column >= values.size()) {
                throw new SQLException("Seed does not provide key " + key + " for " + table);
            }
            if (!keyValues.isEmpty()) query.append(" AND ");
            query.append(key).append(" = ?");
            keyValues.add(literal(values.get(column)));
        }
        try (PreparedStatement statement = connection.prepareStatement(query.toString())) {
            for (int index = 0; index < keyValues.size(); index++) {
                statement.setString(index + 1, keyValues.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    static int indexOf(List<String> columns, String sought) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).strip().equalsIgnoreCase(sought)) return index;
        }
        return -1;
    }

    static String literal(String value) throws SQLException {
        String stripped = value.strip();
        if (stripped.length() >= 2 && stripped.startsWith("'") && stripped.endsWith("'")) {
            return stripped.substring(1, stripped.length() - 1).replace("''", "'");
        }
        throw new SQLException("Seed key must be a SQL string literal: " + value);
    }

    static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
