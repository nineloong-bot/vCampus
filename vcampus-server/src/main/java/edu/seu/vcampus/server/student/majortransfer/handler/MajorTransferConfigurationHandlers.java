package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.EntityIdRequest;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferBatchCommand;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferOptionCommand;
import edu.seu.vcampus.server.routing.MessageRouter;

import java.io.Serializable;
import java.util.ArrayList;

import static edu.seu.vcampus.server.student.majortransfer.handler.MajorTransferHandlerSupport.typed;

final class MajorTransferConfigurationHandlers {
    private final MajorTransferHandlerSupport support;

    MajorTransferConfigurationHandlers(MajorTransferHandlerSupport support) {
        this.support = support;
    }

    void register(MessageRouter router) {
        router.register("MAJOR_TRANSFER_SAVE_BATCH", typed(SaveMajorTransferBatchCommand.class,
                (message, body) -> support.centralWrite(message,
                        () -> support.service.saveBatch(
                                support.principal(message).userId(), body))));
        router.register("MAJOR_TRANSFER_LIST_BATCHES", typed(EmptyRequest.class,
                (message, body) -> support.batchRead(message,
                        () -> new ArrayList<>(support.service.listBatches()))));
        router.register("MAJOR_TRANSFER_SAVE_OPTION", typed(SaveMajorTransferOptionCommand.class,
                (message, body) -> support.collegeWrite(message, null,
                        departmentId -> support.service.saveOption(
                                support.principal(message).userId(), body, departmentId))));
        router.register("MAJOR_TRANSFER_LIST_OPTIONS", typed(EntityIdRequest.class,
                (message, body) -> listOptions(message, body.entityId())));
    }

    private ResponseBody<? extends Serializable> listOptions(Message message, String batchId) {
        var principal = support.principal(message);
        if (!principal.hasRole("COLLEGE_ADMIN")) {
            return MajorTransferHandlerSupport.forbidden();
        }
        try {
            String departmentId = support.scope.findActiveDepartmentId(principal.userId());
            return MajorTransferHandlerSupport.success(new ArrayList<>(
                    support.service.listOptionsForCollege(batchId, departmentId)));
        } catch (IllegalArgumentException error) {
            if ("COMMON_FORBIDDEN".equals(error.getMessage())) {
                return MajorTransferHandlerSupport.forbidden();
            }
            throw error;
        }
    }
}
