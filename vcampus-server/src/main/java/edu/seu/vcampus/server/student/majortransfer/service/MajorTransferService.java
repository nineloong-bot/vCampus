package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.*;

import java.math.BigDecimal;
import java.util.List;

/** Service interface for the major-transfer workflow. */
public interface MajorTransferService {

    // ── Student operations ──

    /**
     * Performs the get student workspace operation.
     * @param userId the user identifier
     * @return the operation result
     */
    MajorTransferWorkspace getStudentWorkspace(String userId);

    /**
     * Performs the save draft operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView saveDraft(String userId, SaveMajorTransferDraftCommand command);

    /**
     * Performs the upload attachment operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView uploadAttachment(String userId, UploadMajorTransferAttachmentCommand command);

    /**
     * Performs the delete attachment operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView deleteAttachment(String userId, DeleteMajorTransferAttachmentCommand command);

    /**
     * Performs the submit operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView submit(String userId, SubmitMajorTransferCommand command);

    /**
     * Performs the withdraw operation.
     * @param userId the user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView withdraw(String userId, WithdrawMajorTransferCommand command);

    // ── Admin: batch and option configuration ──

    /**
     * Performs the save batch operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferBatchView saveBatch(String adminUserId, SaveMajorTransferBatchCommand command);

    /**
     * Performs the save option operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferOptionView saveOption(String adminUserId, SaveMajorTransferOptionCommand command);

    /** Saves an option after rechecking that its target belongs to the trusted college. */
    default MajorTransferOptionView saveOption(String adminUserId,
            SaveMajorTransferOptionCommand command, String trustedDepartmentId) {
        return saveOption(adminUserId, command);
    }

    /**
     * Performs the list batches operation.
     * @return the operation result
     */
    List<MajorTransferBatchView> listBatches();

    /**
     * Performs the list options operation.
     * @param batchId the batch identifier
     * @return the operation result
     */
    List<MajorTransferOptionView> listOptions(String batchId);

    /** Lists only options owned by the trusted college. */
    default List<MajorTransferOptionView> listOptionsForCollege(
            String batchId, String trustedDepartmentId) {
        return listOptions(batchId).stream()
                .filter(option -> trustedDepartmentId.equals(option.targetDepartmentId()))
                .toList();
    }

    // ── Admin: review workflow ──

    /**
     * Performs the list applications operation.
     * @param query the query
     * @return the operation result
     */
    List<MajorTransferApplicationView> listApplications(MajorTransferApplicationQuery query);

    /**
     * Performs the list applications for college operation.
     * @param query the query
     * @param departmentId the department identifier
     * @return the operation result
     */
    List<MajorTransferApplicationView> listApplicationsForCollege(
            MajorTransferApplicationQuery query, String departmentId);

    /**
     * Performs the get application detail operation.
     * @param applicationId the application identifier
     * @return the operation result
     */
    MajorTransferApplicationView getApplicationDetail(String applicationId);

    /**
     * Performs the get attachment operation.
     * @param attachmentId the attachment identifier
     * @return the operation result
     */
    MajorTransferAttachmentDocument getAttachment(String attachmentId);

    /**
     * Performs the review source operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView reviewSource(String adminUserId, ReviewMajorTransferSourceCommand command);

    /** Reviews a source stage after transaction-time college validation. */
    default MajorTransferApplicationView reviewSource(String adminUserId,
            ReviewMajorTransferSourceCommand command, String trustedDepartmentId) {
        return reviewSource(adminUserId, command);
    }

    /**
     * Performs the review qualification operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView reviewQualification(String adminUserId, ReviewMajorTransferQualificationCommand command);

    /** Reviews qualification after transaction-time target-college validation. */
    default MajorTransferApplicationView reviewQualification(String adminUserId,
            ReviewMajorTransferQualificationCommand command, String trustedDepartmentId) {
        return reviewQualification(adminUserId, command);
    }

    /**
     * Performs the cancel operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView cancel(String adminUserId, CancelMajorTransferCommand command);

    /** Cancels an application after transaction-time target-college validation. */
    default MajorTransferApplicationView cancel(String adminUserId,
            CancelMajorTransferCommand command, String trustedDepartmentId) {
        return cancel(adminUserId, command);
    }

    // ── Admin: assessment ──

    /**
     * Performs the record score operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView recordScore(String adminUserId, RecordMajorTransferScoreCommand command);

    /** Records a score after transaction-time target-college validation. */
    default MajorTransferApplicationView recordScore(String adminUserId,
            RecordMajorTransferScoreCommand command, String trustedDepartmentId) {
        return recordScore(adminUserId, command);
    }

    /**
     * Performs the import scores operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferImportResult importScores(String adminUserId, ImportMajorTransferScoresCommand command);

    /** Imports scores after transaction-time target-college validation. */
    default MajorTransferImportResult importScores(String adminUserId,
            ImportMajorTransferScoresCommand command, String trustedDepartmentId) {
        return importScores(adminUserId, command);
    }

    // ── Admin: final approval and execution ──

    /**
     * Performs the finalize approval operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView finalizeApproval(String adminUserId, FinalizeMajorTransferCommand command);

    /** Finalizes an application after transaction-time target-college validation. */
    default MajorTransferApplicationView finalizeApproval(String adminUserId,
            FinalizeMajorTransferCommand command, String trustedDepartmentId) {
        return finalizeApproval(adminUserId, command);
    }

    /**
     * Performs the execute operation.
     * @param adminUserId the admin user identifier
     * @param command the command
     * @return the operation result
     */
    MajorTransferApplicationView execute(String adminUserId, ExecuteMajorTransferCommand command);

    /** Executes or retries after transaction-time target-college validation. */
    default MajorTransferApplicationView execute(String adminUserId,
            ExecuteMajorTransferCommand command, String trustedDepartmentId) {
        return execute(adminUserId, command);
    }
}
