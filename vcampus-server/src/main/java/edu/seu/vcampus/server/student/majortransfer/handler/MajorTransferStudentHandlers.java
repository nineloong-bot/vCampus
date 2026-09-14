package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.student.majortransfer.DeleteMajorTransferAttachmentCommand;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferDraftCommand;
import edu.seu.vcampus.common.student.majortransfer.SubmitMajorTransferCommand;
import edu.seu.vcampus.common.student.majortransfer.UploadMajorTransferAttachmentCommand;
import edu.seu.vcampus.common.student.majortransfer.WithdrawMajorTransferCommand;
import edu.seu.vcampus.server.routing.MessageRouter;

import static edu.seu.vcampus.server.student.majortransfer.handler.MajorTransferHandlerSupport.typed;

final class MajorTransferStudentHandlers {
    private final MajorTransferHandlerSupport support;

    MajorTransferStudentHandlers(MajorTransferHandlerSupport support) {
        this.support = support;
    }

    void register(MessageRouter router) {
        router.register("MAJOR_TRANSFER_GET_WORKSPACE", typed(EmptyRequest.class,
                (message, body) -> support.studentRead(message,
                        () -> support.service.getStudentWorkspace(
                                support.principal(message).userId()))));
        router.register("MAJOR_TRANSFER_SAVE_DRAFT", typed(SaveMajorTransferDraftCommand.class,
                (message, body) -> support.studentWrite(message,
                        () -> support.service.saveDraft(
                                support.principal(message).userId(), body))));
        router.register("MAJOR_TRANSFER_UPLOAD_ATTACHMENT",
                typed(UploadMajorTransferAttachmentCommand.class,
                        (message, body) -> support.studentWrite(message,
                                () -> support.service.uploadAttachment(
                                        support.principal(message).userId(), body))));
        router.register("MAJOR_TRANSFER_DELETE_ATTACHMENT",
                typed(DeleteMajorTransferAttachmentCommand.class,
                        (message, body) -> support.studentWrite(message,
                                () -> support.service.deleteAttachment(
                                        support.principal(message).userId(), body))));
        router.register("MAJOR_TRANSFER_SUBMIT", typed(SubmitMajorTransferCommand.class,
                (message, body) -> support.studentWrite(message,
                        () -> support.service.submit(
                                support.principal(message).userId(), body))));
        router.register("MAJOR_TRANSFER_WITHDRAW", typed(WithdrawMajorTransferCommand.class,
                (message, body) -> support.studentWrite(message,
                        () -> support.service.withdraw(
                                support.principal(message).userId(), body))));
    }
}
