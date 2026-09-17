package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.EntityIdRequest;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.routing.MessageRouter;

import java.io.Serializable;
import java.util.ArrayList;

import static edu.seu.vcampus.server.student.majortransfer.handler.MajorTransferHandlerSupport.typed;

final class MajorTransferApplicationHandlers {
    private final MajorTransferHandlerSupport support;

    MajorTransferApplicationHandlers(MajorTransferHandlerSupport support) {
        this.support = support;
    }

    void register(MessageRouter router) {
        registerReads(router);
        registerReviews(router);
        registerTargetProcessing(router);
    }

    private void registerReads(MessageRouter router) {
        router.register("MAJOR_TRANSFER_LIST_APPLICATIONS",
                typed(MajorTransferApplicationQuery.class, this::listApplications));
        router.register("MAJOR_TRANSFER_GET_APPLICATION", typed(EntityIdRequest.class,
                (message, body) -> support.collegeRead(message,
                        () -> support.scope.requireCanRead(
                                support.principal(message).userId(), body.entityId()),
                        () -> support.service.getApplicationDetail(body.entityId()))));
        router.register("MAJOR_TRANSFER_GET_ATTACHMENT", typed(EntityIdRequest.class,
                (message, body) -> support.collegeRead(message,
                        () -> support.scope.requireCanReadAttachment(
                                support.principal(message).userId(), body.entityId()),
                        () -> support.service.getAttachment(body.entityId()))));
    }

    private void registerReviews(MessageRouter router) {
        router.register("MAJOR_TRANSFER_REVIEW_SOURCE",
                typed(ReviewMajorTransferSourceCommand.class,
                        (message, body) -> support.collegeWrite(message,
                                () -> support.scope.requireSourceApproval(
                                        support.principal(message).userId(), body.applicationId()),
                                departmentId -> support.service.reviewSource(
                                        support.principal(message).userId(), body, departmentId))));
        router.register("MAJOR_TRANSFER_REVIEW_QUALIFICATION",
                typed(ReviewMajorTransferQualificationCommand.class,
                        (message, body) -> targetWrite(message, body.applicationId(),
                                departmentId -> support.service.reviewQualification(
                                        support.principal(message).userId(), body, departmentId))));
    }

    private void registerTargetProcessing(MessageRouter router) {
        router.register("MAJOR_TRANSFER_RECORD_SCORE",
                typed(RecordMajorTransferScoreCommand.class,
                        (message, body) -> targetWrite(message, body.applicationId(),
                                departmentId -> support.service.recordScore(
                                        support.principal(message).userId(), body, departmentId))));
        router.register("MAJOR_TRANSFER_IMPORT_SCORES",
                typed(ImportMajorTransferScoresCommand.class,
                        (message, body) -> support.collegeWrite(message,
                                () -> support.scope.requireTargetApprovalForOption(
                                        support.principal(message).userId(), body.optionId()),
                                departmentId -> support.service.importScores(
                                        support.principal(message).userId(), body, departmentId))));
        router.register("MAJOR_TRANSFER_EXPORT_SCORE_TEMPLATE", typed(EntityIdRequest.class,
                (message, body) -> support.collegeRead(message,
                        () -> support.scope.requireTargetApprovalForOption(
                                support.principal(message).userId(), body.entityId()),
                        () -> support.service.exportScoreTemplate(
                                support.principal(message).userId(), body.entityId(),
                                support.scope.findActiveDepartmentId(support.principal(message).userId())))));
        router.register("MAJOR_TRANSFER_GET_BATCH_READINESS", typed(EntityIdRequest.class,
                (message, body) -> batchRead(message, body.entityId())));
        router.register("MAJOR_TRANSFER_FINALIZE_BATCH", typed(FinalizeMajorTransferBatchCommand.class,
                (message, body) -> support.collegeWrite(message,
                        () -> support.scope.requireTargetApprovalForBatch(
                                support.principal(message).userId(), body.batchId()),
                        departmentId -> support.service.finalizeBatch(
                                support.principal(message).userId(), body, departmentId))));
        router.register("MAJOR_TRANSFER_CANCEL", typed(CancelMajorTransferCommand.class,
                (message, body) -> targetWrite(message, body.applicationId(),
                        departmentId -> support.service.cancel(
                                support.principal(message).userId(), body, departmentId))));
    }

    private ResponseBody<? extends Serializable> batchRead(Message message, String batchId) {
        return support.collegeRead(message,
                () -> support.scope.requireTargetApprovalForBatch(
                        support.principal(message).userId(), batchId),
                () -> support.service.getBatchReadiness(batchId,
                        support.scope.findActiveDepartmentId(support.principal(message).userId())));
    }

    private ResponseBody<? extends Serializable> targetWrite(Message message,
            String applicationId,
            java.util.function.Function<String, ? extends Serializable> action) {
        return support.collegeWrite(message,
                () -> support.scope.requireTargetApproval(
                        support.principal(message).userId(), applicationId), action);
    }

    private ResponseBody<? extends Serializable> listApplications(
            Message message, MajorTransferApplicationQuery query) {
        var principal = support.principal(message);
        if (!principal.hasRole("COLLEGE_ADMIN")) {
            return MajorTransferHandlerSupport.forbidden();
        }
        try {
            String departmentId = support.scope.findActiveDepartmentId(principal.userId());
            return MajorTransferHandlerSupport.success(new ArrayList<>(
                    support.service.listApplicationsForCollege(query, departmentId)));
        } catch (IllegalArgumentException error) {
            if ("COMMON_FORBIDDEN".equals(error.getMessage())) {
                return MajorTransferHandlerSupport.forbidden();
            }
            throw error;
        }
    }
}
