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
    PageResult<BookSummary> searchBooks(BookSearchQuery query);

    BookDetail getBook(String bookId);

    LoanView borrow(String sessionToken, BorrowBookCommand command);

    LoanView returnBook(String sessionToken, ReturnBookCommand command);

    LoanView renew(String sessionToken, RenewLoanCommand command);

    List<LoanView> getCurrentLoans(String sessionToken);

    PageResult<LoanView> getLoanHistory(String sessionToken, LoanHistoryQuery query);

    BookView createBook(CreateBookCommand command);

    BookView updateBook(UpdateBookCommand command);

    BookCopyView addCopy(AddBookCopyCommand command);

    BookCopyView changeCopyStatus(ChangeCopyStatusCommand command);

    LoanView resolveLoan(AdminResolveLoanCommand command);

    PageResult<LoanView> searchAllLoans(AdminLoanSearchQuery query);

    LibraryPolicyView updatePolicy(UpdateLibraryPolicyCommand command);
}
