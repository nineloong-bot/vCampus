package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Command to create or update a major-transfer batch. */
public record SaveMajorTransferBatchCommand(
        String batchId,
        String batchName,
        MajorTransferBatchStatus status,
        Instant applicationStart,
        Instant applicationEnd,
        Instant publicityStart,
        Instant publicityEnd,
        Instant effectiveDate,
        long expectedVersion
) implements Serializable {
    public SaveMajorTransferBatchCommand {
        Objects.requireNonNull(batchName, "batchName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(applicationStart, "applicationStart");
        Objects.requireNonNull(applicationEnd, "applicationEnd");
        if (batchName.isBlank() || batchName.length() > 128) throw new IllegalArgumentException("批次名称必填且不超过128字");
        if ((publicityStart == null) != (publicityEnd == null)) throw new IllegalArgumentException("公示起止时间须同时填写");
        if (publicityStart != null && (publicityStart.isBefore(applicationEnd) || publicityEnd.isBefore(publicityStart)))
            throw new IllegalArgumentException("公示时间必须在报名结束后，结束时间不能早于开始时间");
        if (effectiveDate != null && effectiveDate.isBefore(publicityEnd != null ? publicityEnd : applicationEnd))
            throw new IllegalArgumentException("生效日期不能早于报名或公示结束时间");
        if (applicationStart.isAfter(applicationEnd)) {
            throw new IllegalArgumentException("报名开始时间不能晚于结束时间");
        }
    }
}
