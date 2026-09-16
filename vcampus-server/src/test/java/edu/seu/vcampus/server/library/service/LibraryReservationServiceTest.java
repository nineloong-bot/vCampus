package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryReservationServiceTest {
    private LibraryServiceFixture fixture;
    private LibraryService service;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LibraryServiceFixture();
        fixture.seedCopies(1);
        fixture.seedUser("user-1", "213240001", "STUDENT");
        fixture.seedUser("user-2", "213240002", "STUDENT");
        fixture.seedUser("user-3", "213240003", "STUDENT");
        fixture.addIdentity("student-1", "user-1", "STUDENT");
        fixture.addIdentity("student-2", "user-2", "STUDENT");
        fixture.addIdentity("student-3", "user-3", "STUDENT");
        service = fixture.service();
    }

    private void returnCopyOne() {
        LoanView loan = service.getCurrentLoans("student-1").get(0);
        service.returnBook("student-1", new ReturnBookCommand(loan.loanId(), loan.rowVersion()));
    }

    @Test
    void reservedCopyIsHeldForTheFirstReaderAfterReturn() {
        service.borrow("student-1", new BorrowBookCommand("copy-1"));
        BookReservationView reservation = service.reserve("student-2", new ReserveBookCommand("copy-1"));
        assertThat(reservation.status()).isEqualTo(ReservationStatus.WAITING);
        assertThat(reservation.queuePosition()).isEqualTo(1);

        returnCopyOne();

        assertThat(service.getBook("book-1").copies()).singleElement().satisfies(copy -> {
            assertThat(copy.status()).isEqualTo(CopyStatus.RESERVED);
            assertThat(copy.reservedForLoginId()).isEqualTo("213240002");
        });
    }

    @Test
    void onlyTheHolderCanBorrowAReservedCopy() {
        service.borrow("student-1", new BorrowBookCommand("copy-1"));
        service.reserve("student-2", new ReserveBookCommand("copy-1"));
        returnCopyOne();

        assertThatThrownBy(() -> service.borrow("student-1", new BorrowBookCommand("copy-1")))
                .isInstanceOf(CopyUnavailableException.class);
        assertThat(service.borrow("student-2", new BorrowBookCommand("copy-1")).borrowerUserId())
                .isEqualTo("user-2");
        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.status()).isEqualTo(CopyStatus.BORROWED);
    }

    @Test
    void queueIsServedInReservationOrder() {
        service.borrow("student-1", new BorrowBookCommand("copy-1"));
        var first = service.reserve("student-2", new ReserveBookCommand("copy-1"));
        var second = service.reserve("student-3", new ReserveBookCommand("copy-1"));
        assertThat(first.queuePosition()).isEqualTo(1);
        assertThat(second.queuePosition()).isEqualTo(2);

        returnCopyOne();
        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.reservedForLoginId()).isEqualTo("213240002");

        BookReservationView held = service.getMyReservations("student-2").get(0);
        service.cancelReservation("student-2",
                new CancelReservationCommand(held.reservationId(), held.rowVersion()));

        assertThat(service.getBook("book-1").copies()).singleElement().satisfies(copy -> {
            assertThat(copy.status()).isEqualTo(CopyStatus.RESERVED);
            assertThat(copy.reservedForLoginId()).isEqualTo("213240003");
        });
    }

    @Test
    void expiredHoldIsReleasedToTheNextReader() throws Exception {
        service.borrow("student-1", new BorrowBookCommand("copy-1"));
        service.reserve("student-2", new ReserveBookCommand("copy-1"));
        service.reserve("student-3", new ReserveBookCommand("copy-1"));
        returnCopyOne();
        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.reservedForLoginId()).isEqualTo("213240002");

        fixture.expireReservations();
        assertThat(service.getBook("book-1").copies()).singleElement().satisfies(copy -> {
            assertThat(copy.status()).isEqualTo(CopyStatus.RESERVED);
            assertThat(copy.reservedForLoginId()).isEqualTo("213240003");
        });

        fixture.expireReservations();
        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.status()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void duplicateReservationIsRejected() {
        service.borrow("student-1", new BorrowBookCommand("copy-1"));
        service.reserve("student-2", new ReserveBookCommand("copy-1"));
        assertThatThrownBy(() -> service.reserve("student-2", new ReserveBookCommand("copy-1")))
                .isInstanceOf(DuplicateReservationException.class);
    }

    @Test
    void availableCopyCannotBeReserved() {
        assertThatThrownBy(() -> service.reserve("student-2", new ReserveBookCommand("copy-1")))
                .isInstanceOf(ReservationNotAllowedException.class);
    }

    @Test
    void administratorSeesAndCancelsReservations() {
        service.borrow("student-1", new BorrowBookCommand("copy-1"));
        service.reserve("student-2", new ReserveBookCommand("copy-1"));
        var page = service.searchReservations(new AdminReservationSearchQuery("213240002", null, 1, 20));
        assertThat(page.items()).hasSize(1);
        BookReservationView view = page.items().get(0);
        service.adminCancelReservation(
                new AdminCancelReservationCommand(view.reservationId(), view.rowVersion()));
        assertThat(service.searchReservations(
                new AdminReservationSearchQuery(null, ReservationStatus.WAITING, 1, 20)).items()).isEmpty();
    }
}
