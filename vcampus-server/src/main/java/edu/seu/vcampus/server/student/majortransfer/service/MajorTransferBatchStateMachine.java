package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;

/** Enforces irreversible lifecycle changes for school-wide transfer batches. */
final class MajorTransferBatchStateMachine {
    private MajorTransferBatchStateMachine() { }

    static void requireUpdate(MajorTransferBatchStatus current, MajorTransferBatchStatus next) {
        boolean allowed = switch (current) {
            case DRAFT -> next == MajorTransferBatchStatus.DRAFT || next == MajorTransferBatchStatus.OPEN;
            case OPEN -> next == MajorTransferBatchStatus.OPEN || next == MajorTransferBatchStatus.CLOSED;
            case CLOSED, EFFECTIVE -> false;
        };
        if (!allowed) {
            String code = current == MajorTransferBatchStatus.CLOSED
                    || current == MajorTransferBatchStatus.EFFECTIVE
                    ? "TRANSFER_BATCH_CLOSED" : "TRANSFER_STATE_INVALID";
            throw new MajorTransferException(code, "批次已关闭，不能重新开放或修改");
        }
    }

    static void requireCreatable(MajorTransferBatchStatus status) {
        if (status != MajorTransferBatchStatus.DRAFT && status != MajorTransferBatchStatus.OPEN) {
            throw new MajorTransferException("TRANSFER_STATE_INVALID", "新批次只能为草稿或开放状态");
        }
    }
}
