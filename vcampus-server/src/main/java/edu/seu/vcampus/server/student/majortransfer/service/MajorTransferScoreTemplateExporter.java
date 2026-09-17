package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferScoreTemplateDocument;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.student.majortransfer.repository.MajorTransferRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Generates downloadable CSV score entry templates pre-filled with qualified applicants.
 */
public final class MajorTransferScoreTemplateExporter {
    private final TransactionManager transactions;
    private final MajorTransferRepository repository;

    /**
     * Creates a new score template exporter.
     *
     * @param transactions transaction manager
     * @param repository   major transfer repository
     */
    public MajorTransferScoreTemplateExporter(
            TransactionManager transactions,
            MajorTransferRepository repository) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Exports a CSV score import template populated with qualified applicants for a major transfer option.
     *
     * @param adminUserId         the requesting administrator user ID
     * @param optionId            the target option ID
     * @param trustedDepartmentId the verified administrator department ID
     * @return generated CSV template document
     */
    public MajorTransferScoreTemplateDocument exportScoreTemplate(
            String adminUserId, String optionId, String trustedDepartmentId) {
        Objects.requireNonNull(optionId, "optionId");
        return transactions.inTransaction(connection -> {
            var option = repository.findOption(connection, optionId)
                    .orElseThrow(() -> new IllegalArgumentException("TRANSFER_OPTION_NOT_FOUND"));
            if (trustedDepartmentId != null && !trustedDepartmentId.equals(option.targetDepartmentId())) {
                throw new IllegalArgumentException("COMMON_FORBIDDEN");
            }
            List<MajorTransferRepository.ApplicationRow> applicants =
                    repository.listApplicationsByBatch(connection, option.batchId()).stream()
                            .filter(a -> optionId.equals(a.optionId())
                                    && a.status() == MajorTransferStatus.QUALIFIED)
                            .toList();
            StringBuilder csv = new StringBuilder("\uFEFF");
            csv.append("申请编号,一卡通号,姓名,原学院,原专业,目标专业,笔试成绩,面试成绩\r\n");
            for (var row : applicants) {
                String studentNum = row.fromStudentNumber() != null && !row.fromStudentNumber().isBlank()
                        ? row.fromStudentNumber() : row.studentId();
                csv.append(escape(row.applicationId())).append(",")
                        .append(escape(studentNum)).append(",")
                        .append(escape(row.studentName())).append(",")
                        .append(escape(row.fromDepartmentName())).append(",")
                        .append(escape(row.fromMajorName())).append(",")
                        .append(escape(option.targetMajorName())).append(",")
                        .append(row.writtenScore() != null ? row.writtenScore().toString() : "").append(",")
                        .append(row.interviewScore() != null ? row.interviewScore().toString() : "")
                        .append("\r\n");
            }
            String majorName = option.targetMajorName() == null ? "专业"
                    : option.targetMajorName().replaceAll("[\\\\/:*?\"<>|\\s]", "_");
            String fileName = "转专业成绩导入模板_" + majorName + "_" + option.optionId() + ".csv";
            return new MajorTransferScoreTemplateDocument(fileName,
                    csv.toString().getBytes(StandardCharsets.UTF_8));
        });
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
