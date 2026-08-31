package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.SellerReviewDecision;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.repository.ShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerApplicationServiceTest {
    private ShopTestDatabase database;
    private ShopRepository repository;
    private FakeShopUserPort users;
    private SellerApplicationService seller;
    private ShopAdminService admin;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        repository = new AccessShopRepository();
        users = new FakeShopUserPort();
        users.add("student-token", "student-1", ShopUserKind.STUDENT, true);
        users.add("teacher-token", "teacher-1", ShopUserKind.TEACHER, true);
        users.add("inactive-token", "inactive-1", ShopUserKind.STUDENT, false);
        users.add("other-token", "other-1", ShopUserKind.OTHER, true);
        users.add("admin-token", "admin-1", ShopUserKind.ADMINISTRATOR, true);
        var transactions = new TransactionManager(database.connections());
        var locks = new StripedResourceLockManager();
        var clock = Clock.fixed(Instant.parse("2026-08-28T05:00:00Z"), ZoneOffset.UTC);
        seller = new SellerApplicationService(repository, users, transactions, locks, clock);
        admin = new ShopAdminService(repository, users, transactions, locks, clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void activeStudentsAndTeachersCanSaveAndSubmitDrafts() {
        SellerApplicationView student = seller.saveDraft("student-token", draft("晨光文具"));
        SellerApplicationView teacher = seller.saveDraft("teacher-token", draft("教师书屋"));

        assertThat(student.status()).isEqualTo(SellerApplicationStatus.DRAFT);
        assertThat(teacher.status()).isEqualTo(SellerApplicationStatus.DRAFT);
        assertThat(seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(student.applicationId(), student.rowVersion())).status())
                .isEqualTo(SellerApplicationStatus.PENDING);
    }

    @Test
    void inactiveAndIneligibleAccountsAreRejectedBeforeCreatingDrafts() {
        assertThatThrownBy(() -> seller.saveDraft("inactive-token", draft("不可用店铺")))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> seller.saveDraft("other-token", draft("无资格店铺")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void pendingApplicationCannotBeEditedAndRejectedApplicationReturnsToDraft() {
        SellerApplicationView draft = seller.saveDraft("student-token", draft("旧名称"));
        SellerApplicationView pending = seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));

        assertThatThrownBy(() -> seller.saveDraft("student-token",
                edit(pending, "待审核时修改")))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_SELLER_APPLICATION_STATUS_INVALID));

        SellerApplicationView rejected = admin.reviewApplication(new ReviewSellerApplicationCommand(
                pending.applicationId(), SellerReviewDecision.REJECT, "资料不完整", pending.rowVersion()));
        SellerApplicationView revised = seller.saveDraft("student-token", edit(rejected, "新名称"));

        assertThat(revised.status()).isEqualTo(SellerApplicationStatus.DRAFT);
        assertThat(revised.shopName()).isEqualTo("新名称");
        assertThat(seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(revised.applicationId(), revised.rowVersion())).status())
                .isEqualTo(SellerApplicationStatus.PENDING);
    }

    @Test
    void approvingTwiceCreatesExactlyOneShop() throws Exception {
        SellerApplicationView draft = seller.saveDraft("student-token", draft("并发店铺"));
        SellerApplicationView pending = seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        ReviewSellerApplicationCommand command = new ReviewSellerApplicationCommand(
                pending.applicationId(), SellerReviewDecision.APPROVE, null, pending.rowVersion());

        List<Outcome> outcomes = concurrently(() -> admin.reviewApplication(command));

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        long shops = new TransactionManager(database.connections()).inTransaction(
                connection -> repository.countShopsByOwner(connection, "student-1"));
        assertThat(shops).isEqualTo(1);
    }

    @Test
    void aUserWithApprovedShopCannotStartAnotherApplication() {
        SellerApplicationView draft = seller.saveDraft("student-token", draft("唯一店铺"));
        SellerApplicationView pending = seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        admin.reviewApplication(new ReviewSellerApplicationCommand(pending.applicationId(),
                SellerReviewDecision.APPROVE, null, pending.rowVersion()));

        assertThatThrownBy(() -> seller.saveDraft("student-token", draft("第二店铺")))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_SELLER_APPLICATION_EXISTS));
    }

    @Test
    void missingApplicationIsAnEmptyOptionalAndAdministratorCannotApply() {
        assertThat(seller.findMyApplication("student-token")).isEmpty();
        assertThatThrownBy(() -> seller.saveDraft("admin-token", draft("管理员店铺")))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void submissionRequiresStatementAndSupportedCategory() {
        SaveSellerDraftCommand missingStatement = new SaveSellerDraftCommand(null, "无计划店铺",
                "校园服务", "文具", "025-12345678", "  ", 0);
        SellerApplicationView draft = seller.saveDraft("student-token", missingStatement);

        assertThatThrownBy(() -> seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("applicationStatement");
        assertThatThrownBy(() -> seller.saveDraft("teacher-token", new SaveSellerDraftCommand(
                null, "非法类别店铺", "校园服务", "虚构类别", "025-12345678",
                "经营计划", 0)))
                .isInstanceOfSatisfying(ShopException.class,
                        error -> assertThat(error.code()).isEqualTo(ShopErrorCode.SHOP_CATEGORY_INVALID));
    }

    @Test
    void approvedNormalizedShopNameCannotBeSubmittedAgain() {
        SellerApplicationView firstDraft = seller.saveDraft("student-token", draft("Campus Shop"));
        SellerApplicationView firstPending = seller.submitApplication("student-token",
                new SubmitSellerApplicationCommand(firstDraft.applicationId(), firstDraft.rowVersion()));
        admin.reviewApplication(new ReviewSellerApplicationCommand(firstPending.applicationId(),
                SellerReviewDecision.APPROVE, null, firstPending.rowVersion()));
        SellerApplicationView duplicate = seller.saveDraft("teacher-token", draft("  campus shop  "));

        assertThatThrownBy(() -> seller.submitApplication("teacher-token",
                new SubmitSellerApplicationCommand(duplicate.applicationId(), duplicate.rowVersion())))
                .isInstanceOfSatisfying(ShopException.class,
                        error -> assertThat(error.code()).isEqualTo(ShopErrorCode.SHOP_NAME_EXISTS));
    }

    private static SaveSellerDraftCommand draft(String name) {
        return new SaveSellerDraftCommand(null, name, "校园服务", "文具", "025-12345678",
                "诚信经营计划", 0);
    }

    private static SaveSellerDraftCommand edit(SellerApplicationView view, String name) {
        return new SaveSellerDraftCommand(view.applicationId(), name, view.description(),
                view.category(), view.contact(), view.applicationStatement(), view.rowVersion());
    }

    private static List<Outcome> concurrently(Callable<?> action) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Outcome>> calls = List.of(() -> invoke(start, action), () -> invoke(start, action));
            List<Future<Outcome>> futures = calls.stream().map(executor::submit).toList();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (var future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private static Outcome invoke(CountDownLatch start, Callable<?> action) {
        try {
            start.await();
            action.call();
            return new Outcome(true);
        } catch (Exception error) {
            return new Outcome(false);
        }
    }

    private record Outcome(boolean success) { }
}
