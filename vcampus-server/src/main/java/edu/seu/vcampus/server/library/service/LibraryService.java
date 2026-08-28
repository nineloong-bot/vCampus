package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.LoanView;

/** Library borrowing use cases exposed to transport handlers. */
public interface LibraryService {
    LoanView borrow(String sessionToken, BorrowBookCommand command);
}
