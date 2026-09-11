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

    List<MajorTransferBatchView> listBatches();

    List<MajorTransferOptionView> listOptions(String batchId);

    // ── Admin: review workflow ──

    List<MajorTransferApplicationView> listApplications(MajorTransferApplicationQuery query);

    List<MajorTransferApplicationView> listApplicationsForCollege(
            MajorTransferApplicationQuery query, String departmentId);

    MajorTransferApplicationView getApplicationDetail(String applicationId);

    MajorTransferAttachmentDocument getAttachment(String attachmentId);

    MajorTransferApplicationView reviewSource(String adminUserId, ReviewMajorTransferSourceCommand command);

    MajorTransferApplicationView reviewQualification(String adminUserId, ReviewMajorTransferQualificationCommand command);

    MajorTransferApplicationView cancel(String adminUserId, CancelMajorTransferCommand command);

    // ── Admin: assessment and ranking ──

    MajorTransferApplicationView recordScore(String adminUserId, RecordMajorTransferScoreCommand command);

    MajorTransferRankingView generateProposal(String adminUserId, GenerateMajorTransferProposalCommand command);

    // ── Admin: final approval and execution ──

    MajorTransferApplicationView finalizeProposal(String adminUserId, FinalizeMajorTransferCommand command);

    MajorTransferApplicationView execute(String adminUserId, ExecuteMajorTransferCommand command);
}
