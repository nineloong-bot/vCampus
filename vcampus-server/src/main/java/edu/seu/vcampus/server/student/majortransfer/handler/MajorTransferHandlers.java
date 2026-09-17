package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.handler.StudentAuthorizationPort;
import edu.seu.vcampus.server.student.handler.StudentWriteExecutor;
import edu.seu.vcampus.server.student.majortransfer.security.MajorTransferCollegeAuthorizationService;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferService;

import java.util.List;
import java.util.Objects;

/** Registers major-transfer commands with role and college-scope enforcement. */
public final class MajorTransferHandlers {
    public static final List<String> STUDENT_COMMANDS = List.of(
            "MAJOR_TRANSFER_GET_WORKSPACE", "MAJOR_TRANSFER_SAVE_DRAFT",
            "MAJOR_TRANSFER_UPLOAD_ATTACHMENT", "MAJOR_TRANSFER_DELETE_ATTACHMENT",
            "MAJOR_TRANSFER_SUBMIT", "MAJOR_TRANSFER_WITHDRAW");

    public static final List<String> ADMIN_COMMANDS = List.of(
            "MAJOR_TRANSFER_SAVE_BATCH", "MAJOR_TRANSFER_SAVE_OPTION",
            "MAJOR_TRANSFER_LIST_BATCHES", "MAJOR_TRANSFER_LIST_OPTIONS",
            "MAJOR_TRANSFER_LIST_APPLICATIONS", "MAJOR_TRANSFER_GET_APPLICATION",
            "MAJOR_TRANSFER_GET_ATTACHMENT", "MAJOR_TRANSFER_REVIEW_SOURCE",
            "MAJOR_TRANSFER_REVIEW_QUALIFICATION", "MAJOR_TRANSFER_RECORD_SCORE",
            "MAJOR_TRANSFER_IMPORT_SCORES", "MAJOR_TRANSFER_GET_BATCH_READINESS",
            "MAJOR_TRANSFER_FINALIZE_BATCH", "MAJOR_TRANSFER_EFFECTIVE_BATCH",
            "MAJOR_TRANSFER_ROLLBACK_BATCH", "MAJOR_TRANSFER_CANCEL");

    private final MajorTransferStudentHandlers students;
    private final MajorTransferConfigurationHandlers configuration;
    private final MajorTransferApplicationHandlers applications;

    /** Creates a complete major-transfer command registrar. */
    public MajorTransferHandlers(MajorTransferService service,
            StudentAuthorizationPort authorization, StudentWriteExecutor writes,
            MajorTransferCollegeAuthorizationService collegeAuthorization) {
        var support = new MajorTransferHandlerSupport(Objects.requireNonNull(service),
                Objects.requireNonNull(authorization), Objects.requireNonNull(writes),
                Objects.requireNonNull(collegeAuthorization));
        students = new MajorTransferStudentHandlers(support);
        configuration = new MajorTransferConfigurationHandlers(support);
        applications = new MajorTransferApplicationHandlers(support);
    }

    /** Registers every student and administrator command. */
    public void register(MessageRouter router) {
        students.register(router);
        configuration.register(router);
        applications.register(router);
    }
}
