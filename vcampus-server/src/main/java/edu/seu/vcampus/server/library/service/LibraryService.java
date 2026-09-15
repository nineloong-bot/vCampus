package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.AddBookCopyCommand;
import edu.seu.vcampus.common.library.AdminLoanSearchQuery;
import edu.seu.vcampus.common.library.AdminResolveLoanCommand;
import edu.seu.vcampus.common.library.BookCopyView;
import edu.seu.vcampus.common.library.BookDetail;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.library.BookView;
import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.ChangeCopyStatusCommand;
import edu.seu.vcampus.common.library.CreateBookCommand;
import edu.seu.vcampus.common.library.LibraryPolicyView;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.common.library.UpdateBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import edu.seu.vcampus.common.paging.PageResult;

import java.util.List;

/** Library borrowing use cases exposed to transport handlers. */
public interface LibraryService {
    /**
     * Performs the search books operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<BookSummary> searchBooks(BookSearchQuery query);

    /**
     * Performs the search managed books operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<BookSummary> searchManagedBooks(BookSearchQuery query);

    /**
     * Performs the get book operation.
     * @param bookId the book identifier
     * @return the operation result
     */
    BookDetail getBook(String bookId);

    /**
     * Performs the borrow operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    LoanView borrow(String sessionToken, BorrowBookCommand command);

    /**
     * Performs the return book operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    LoanView returnBook(String sessionToken, ReturnBookCommand command);

    /**
     * Performs the renew operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    LoanView renew(String sessionToken, RenewLoanCommand command);

    /**
     * Performs the get current loans operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    List<LoanView> getCurrentLoans(String sessionToken);

    /**
     * Performs the get loan history operation.
     * @param sessionToken the session token
     * @param query the query
     * @return the operation result
     */
    PageResult<LoanView> getLoanHistory(String sessionToken, LoanHistoryQuery query);

    /**
     * Performs the create book operation.
     * @param command the command
     * @return the operation result
     */
    BookView createBook(CreateBookCommand command);

    /**
     * Performs the update book operation.
     * @param command the command
     * @return the operation result
     */
    BookView updateBook(UpdateBookCommand command);

    /**
     * Performs the add copy operation.
     * @param command the command
     * @return the operation result
     */
    BookCopyView addCopy(AddBookCopyCommand command);

    /**
     * Performs the change copy status operation.
     * @param command the command
     * @return the operation result
     */
    BookCopyView changeCopyStatus(ChangeCopyStatusCommand command);

    /**
     * Performs the resolve loan operation.
     * @param command the command
     * @return the operation result
     */
    LoanView resolveLoan(AdminResolveLoanCommand command);

    /**
     * Performs the search all loans operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<LoanView> searchAllLoans(AdminLoanSearchQuery query);

    /**
     * Performs the get policies operation.
     * @return the operation result
     */
    List<LibraryPolicyView> getPolicies();

    /**
     * Performs the update policy operation.
     * @param command the command
     * @return the operation result
     */
    LibraryPolicyView updatePolicy(UpdateLibraryPolicyCommand command);
}
