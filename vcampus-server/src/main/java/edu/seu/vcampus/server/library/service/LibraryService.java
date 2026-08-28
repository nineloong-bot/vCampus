package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;

/** Library borrowing use cases exposed to transport handlers. */
public interface LibraryService {
    LoanView borrow(String sessionToken, BorrowBookCommand command);

    LoanView returnBook(String sessionToken, ReturnBookCommand command);

    LoanView renew(String sessionToken, RenewLoanCommand command);
}
