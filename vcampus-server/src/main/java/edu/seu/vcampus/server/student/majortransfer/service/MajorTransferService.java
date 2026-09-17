package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.common.student.majortransfer.*;

import java.math.BigDecimal;
import java.util.List;

/** Service interface for the major-transfer workflow. */
public interface MajorTransferService {

    // ── Student operations ──

    MajorTransferWorkspace getStudentWorkspace(String userId);

    MajorTransferApplicationView saveDraft(String userId, SaveMajorTransferDraftCommand command);

    MajorTransferApplicationView uploadAttachment(String userId, UploadMajorTransferAttachmentCommand command);

    MajorTransferApplicationView deleteAttachment(String userId, DeleteMajorTransferAttachmentCommand command);

    MajorTransferApplicationView submit(String userId, SubmitMajorTransferCommand command);

    MajorTransferApplicationView withdraw(String userId, WithdrawMajorTransferCommand command);

    // ── Admin: batch and option configuration ──

    MajorTransferBatchView saveBatch(String adminUserId, SaveMajorTransferBatchCommand command);

    MajorTransferOptionView saveOption(String adminUserId, SaveMajorTransferOptionCommand command);

    /** Saves an option after rechecking that its target belongs to the trusted college. */
    default MajorTransferOptionView saveOption(String adminUserId,
            SaveMajorTransferOptionCommand command, String trustedDepartmentId) {
        return saveOption(adminUserId, command);
    }

    List<MajorTransferBatchView> listBatches();

    List<MajorTransferOptionView> listOptions(String batchId);

    /** Lists only options owned by the trusted college. */
    default List<MajorTransferOptionView> listOptionsForCollege(
            String batchId, String trustedDepartmentId) {
        return listOptions(batchId).stream()
                .filter(option -> trustedDepartmentId.equals(option.targetDepartmentId()))
                .toList();
    }

    // ── Admin: review workflow ──

    List<MajorTransferApplicationView> listApplications(MajorTransferApplicationQuery query);

    List<MajorTransferApplicationView> listApplicationsForCollege(
            MajorTransferApplicationQuery query, String departmentId);

    MajorTransferApplicationView getApplicationDetail(String applicationId);

    MajorTransferAttachmentDocument getAttachment(String attachmentId);

    MajorTransferApplicationView reviewSource(String adminUserId, ReviewMajorTransferSourceCommand command);

    /** Reviews a source stage after transaction-time college validation. */
    default MajorTransferApplicationView reviewSource(String adminUserId,
            ReviewMajorTransferSourceCommand command, String trustedDepartmentId) {
        return reviewSource(adminUserId, command);
    }

    MajorTransferApplicationView reviewQualification(String adminUserId, ReviewMajorTransferQualificationCommand command);

    /** Reviews qualification after transaction-time target-college validation. */
    default MajorTransferApplicationView reviewQualification(String adminUserId,
            ReviewMajorTransferQualificationCommand command, String trustedDepartmentId) {
        return reviewQualification(adminUserId, command);
    }

    MajorTransferApplicationView cancel(String adminUserId, CancelMajorTransferCommand command);

    /** Cancels an application after transaction-time target-college validation. */
    default MajorTransferApplicationView cancel(String adminUserId,
            CancelMajorTransferCommand command, String trustedDepartmentId) {
        return cancel(adminUserId, command);
    }

    // ── Admin: assessment ──

    MajorTransferApplicationView recordScore(String adminUserId, RecordMajorTransferScoreCommand command);

    /** Records a score after transaction-time target-college validation. */
    default MajorTransferApplicationView recordScore(String adminUserId,
            RecordMajorTransferScoreCommand command, String trustedDepartmentId) {
        return recordScore(adminUserId, command);
    }

    MajorTransferImportResult importScores(String adminUserId, ImportMajorTransferScoresCommand command);

    /** Imports scores after transaction-time target-college validation. */
    default MajorTransferImportResult importScores(String adminUserId,
            ImportMajorTransferScoresCommand command, String trustedDepartmentId) {
        return importScores(adminUserId, command);
    }

    /**
     * Exports a CSV score import template populated with qualified applicants for a major transfer option.
     *
     * @param adminUserId the requesting admin's user ID
     * @param optionId the major transfer option ID
     * @param trustedDepartmentId the verified department ID of the college administrator
     * @return the generated CSV template document
     */
    MajorTransferScoreTemplateDocument exportScoreTemplate(
            String adminUserId, String optionId, String trustedDepartmentId);

    /** Returns final-review readiness for one option owned by the trusted college. */
    MajorTransferOptionReadinessView getOptionReadiness(
            String optionId, String trustedDepartmentId);

    /** Gives final approval to assessed applications for one target-major option. */
    MajorTransferOptionReviewResult finalizeOption(String adminUserId,
            FinalizeMajorTransferOptionCommand command, String trustedDepartmentId);

    /** Applies a previously reviewed target-major option exactly once. */
    MajorTransferOptionEffectResult effectiveOption(String adminUserId,
            EffectiveMajorTransferOptionCommand command, String trustedDepartmentId);

    /** Rolls back one target-major option before effectuation. */
    MajorTransferOptionRollbackResult rollbackOption(String adminUserId,
            RollbackMajorTransferOptionCommand command, String trustedDepartmentId);

    /** Returns whether every formal application is ready for atomic finalization. */
    MajorTransferCollegeReadinessView getBatchReadiness(String batchId, String trustedDepartmentId);

    /** Gives final approval to every assessed application without changing enrollment. */
    MajorTransferBatchReviewResult finalizeBatch(String adminUserId,
            FinalizeMajorTransferBatchCommand command, String trustedDepartmentId);

    /** Applies a previously approved batch exactly once. */
    MajorTransferBatchEffectResult effectiveBatch(String adminUserId,
            EffectiveMajorTransferBatchCommand command, String trustedDepartmentId);

    /** Rolls back final approval before effectuation. */
    MajorTransferBatchRollbackResult rollbackBatch(String adminUserId,
            RollbackMajorTransferBatchCommand command, String trustedDepartmentId);

    // ── Admin: final approval and execution ──

    MajorTransferApplicationView finalizeApproval(String adminUserId, FinalizeMajorTransferCommand command);

    /** Finalizes an application after transaction-time target-college validation. */
    default MajorTransferApplicationView finalizeApproval(String adminUserId,
            FinalizeMajorTransferCommand command, String trustedDepartmentId) {
        return finalizeApproval(adminUserId, command);
    }

    MajorTransferApplicationView execute(String adminUserId, ExecuteMajorTransferCommand command);

    /** Executes or retries after transaction-time target-college validation. */
    default MajorTransferApplicationView execute(String adminUserId,
            ExecuteMajorTransferCommand command, String trustedDepartmentId) {
        return execute(adminUserId, command);
    }
}
