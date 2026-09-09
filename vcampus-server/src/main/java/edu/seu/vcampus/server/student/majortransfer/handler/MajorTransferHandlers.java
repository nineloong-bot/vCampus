package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.EntityIdRequest;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.handler.StudentAuthorizationPort;
import edu.seu.vcampus.server.student.handler.StudentPrincipal;
import edu.seu.vcampus.server.student.handler.StudentWriteExecutor;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferException;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferService;

import java.io.Serializable;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers all major-transfer commands and enforces authorization. */
public final class MajorTransferHandlers {
    public static final List<String> STUDENT_COMMANDS = List.of(
            "MAJOR_TRANSFER_GET_WORKSPACE",
            "MAJOR_TRANSFER_SAVE_DRAFT",
            "MAJOR_TRANSFER_UPLOAD_ATTACHMENT",
            "MAJOR_TRANSFER_DELETE_ATTACHMENT",
            "MAJOR_TRANSFER_SUBMIT",
            "MAJOR_TRANSFER_WITHDRAW");

    public static final List<String> ADMIN_COMMANDS = List.of(
            "MAJOR_TRANSFER_SAVE_BATCH",
            "MAJOR_TRANSFER_SAVE_OPTION",
            "MAJOR_TRANSFER_LIST_BATCHES",
            "MAJOR_TRANSFER_LIST_OPTIONS",
            "MAJOR_TRANSFER_LIST_APPLICATIONS",
            "MAJOR_TRANSFER_GET_APPLICATION",
            "MAJOR_TRANSFER_GET_ATTACHMENT",
            "MAJOR_TRANSFER_REVIEW_SOURCE",
            "MAJOR_TRANSFER_REVIEW_QUALIFICATION",
            "MAJOR_TRANSFER_RECORD_SCORE",
            "MAJOR_TRANSFER_GENERATE_PROPOSAL",
            "MAJOR_TRANSFER_FINALIZE",
            "MAJOR_TRANSFER_EXECUTE",
            "MAJOR_TRANSFER_CANCEL");

    private final MajorTransferService service;
    private final StudentAuthorizationPort authorization;
    private final StudentWriteExecutor writes;

    public MajorTransferHandlers(MajorTransferService service,
                                  StudentAuthorizationPort authorization,
                                  StudentWriteExecutor writes) {
        this.service = Objects.requireNonNull(service);
        this.authorization = Objects.requireNonNull(authorization);
        this.writes = Objects.requireNonNull(writes);
    }

    public void register(MessageRouter router) {
        // Student commands
        router.register("MAJOR_TRANSFER_GET_WORKSPACE", typed(EmptyRequest.class,
                (message, body) -> student(message,
                        () -> service.getStudentWorkspace(principal(message).userId()))));
        router.register("MAJOR_TRANSFER_SAVE_DRAFT", typed(SaveMajorTransferDraftCommand.class,
                (message, body) -> write(message, () -> student(message,
                        () -> service.saveDraft(principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_UPLOAD_ATTACHMENT",
                typed(UploadMajorTransferAttachmentCommand.class,
                        (message, body) -> write(message, () -> student(message,
                                () -> service.uploadAttachment(
                                        principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_DELETE_ATTACHMENT",
                typed(DeleteMajorTransferAttachmentCommand.class,
                        (message, body) -> write(message, () -> student(message,
                                () -> service.deleteAttachment(
                                        principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_SUBMIT", typed(SubmitMajorTransferCommand.class,
                (message, body) -> write(message, () -> student(message,
                        () -> service.submit(principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_WITHDRAW", typed(WithdrawMajorTransferCommand.class,
                (message, body) -> write(message, () -> student(message,
                        () -> service.withdraw(principal(message).userId(), body)))));

        // Admin commands
        router.register("MAJOR_TRANSFER_GET_ATTACHMENT", typed(EntityIdRequest.class,
                (message, body) -> admin(message, () -> service.getAttachment(body.entityId()))));
        router.register("MAJOR_TRANSFER_SAVE_BATCH", typed(SaveMajorTransferBatchCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> service.saveBatch(principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_SAVE_OPTION", typed(SaveMajorTransferOptionCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> service.saveOption(principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_LIST_BATCHES", typed(EmptyRequest.class,
                (message, body) -> admin(message,
                        () -> new java.util.ArrayList<>(service.listBatches()))));
        router.register("MAJOR_TRANSFER_LIST_OPTIONS", typed(EntityIdRequest.class,
                (message, body) -> admin(message,
                        () -> new java.util.ArrayList<>(service.listOptions(body.entityId())))));
        router.register("MAJOR_TRANSFER_LIST_APPLICATIONS",
                typed(MajorTransferApplicationQuery.class,
                        (message, body) -> admin(message,
                                () -> new java.util.ArrayList<>(service.listApplications(body)))));
        router.register("MAJOR_TRANSFER_GET_APPLICATION", typed(EntityIdRequest.class,
                (message, body) -> admin(message,
                        () -> service.getApplicationDetail(body.entityId()))));
        router.register("MAJOR_TRANSFER_REVIEW_SOURCE",
                typed(ReviewMajorTransferSourceCommand.class,
                        (message, body) -> write(message, () -> admin(message,
                                () -> service.reviewSource(
                                        principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_REVIEW_QUALIFICATION",
                typed(ReviewMajorTransferQualificationCommand.class,
                        (message, body) -> write(message, () -> admin(message,
                                () -> service.reviewQualification(
                                        principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_RECORD_SCORE",
                typed(RecordMajorTransferScoreCommand.class,
                        (message, body) -> write(message, () -> admin(message,
                                () -> service.recordScore(
                                        principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_GENERATE_PROPOSAL",
                typed(GenerateMajorTransferProposalCommand.class,
                        (message, body) -> write(message, () -> admin(message,
                                () -> service.generateProposal(
                                        principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_FINALIZE", typed(FinalizeMajorTransferCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> service.finalizeProposal(
                                principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_EXECUTE", typed(ExecuteMajorTransferCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> service.execute(principal(message).userId(), body)))));
        router.register("MAJOR_TRANSFER_CANCEL", typed(CancelMajorTransferCommand.class,
                (message, body) -> write(message, () -> admin(message,
                        () -> service.cancel(principal(message).userId(), body)))));
    }

    private ResponseBody<? extends Serializable> write(Message message,
            java.util.function.Supplier<ResponseBody<? extends Serializable>> action) {
        StudentPrincipal principal = principal(message);
        if (ADMIN_COMMANDS.contains(message.command()) ? !principal.hasRole("ADMIN") : !principal.hasRole("STUDENT"))
            return forbidden();
        // Scope persisted replay keys to the authenticated actor and command.
        String id = java.util.UUID.nameUUIDFromBytes((principal.userId() + "\n" + message.command() + "\n" + message.requestId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        Message scoped = new Message(id, message.type(), message.command(), message.sessionToken(), message.body(), message.timestamp());
        return writes.execute(scoped, principal, () -> {
            try { return action.get(); }
            catch (ConcurrentModificationException error) {
                return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null);
            } catch (MajorTransferException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (IllegalArgumentException | IllegalStateException error) {
                return ResponseBody.failure("COMMON_INVALID_REQUEST", error.getMessage(), null);
            }
        });
    }

    private ResponseBody<? extends Serializable> admin(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal p = principal(message);
        return p.hasRole("ADMIN") ? success(action.get()) : forbidden();
    }

    private ResponseBody<? extends Serializable> student(Message message,
            java.util.function.Supplier<? extends Serializable> action) {
        StudentPrincipal p = principal(message);
        return p.hasRole("STUDENT") ? success(action.get()) : forbidden();
    }

    private StudentPrincipal principal(Message message) {
        StudentPrincipal p = authorization.authenticate(message.sessionToken());
        if (p == null) throw new IllegalArgumentException("Invalid session");
        return p;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> MessageHandler typed(Class<T> type,
            BiFunction<Message, T, ResponseBody<? extends Serializable>> action) {
        return (message, client) -> {
            if (!type.isInstance(message.body()))
                return ResponseBody.failure("COMMON_INVALID_REQUEST", "请求体类型错误", null);
            try {
                return action.apply(message, type.cast(message.body()));
            } catch (ConcurrentModificationException error) {
                return ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION",
                        "数据已被修改，请刷新", null);
            } catch (MajorTransferException error) {
                return ResponseBody.failure(error.code(), error.getMessage(), null);
            } catch (IllegalArgumentException error) {
                return ResponseBody.failure("COMMON_INVALID_REQUEST",
                        error.getMessage(), null);
            }
        };
    }

    private static <T extends Serializable> ResponseBody<T> success(T value) {
        return ResponseBody.success(value);
    }

    private static ResponseBody<Serializable> forbidden() {
        return ResponseBody.failure("COMMON_FORBIDDEN", "无权访问", null);
    }
}
