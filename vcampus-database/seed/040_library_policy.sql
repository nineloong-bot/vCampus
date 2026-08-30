INSERT INTO tblLibraryPolicy
    (policyId, roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, rowVersion)
VALUES ('library-policy-student', 'STUDENT', 5, 30, 1, 15, 0);

INSERT INTO tblLibraryPolicy
    (policyId, roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, rowVersion)
VALUES ('library-policy-teacher', 'TEACHER', 10, 60, 2, 30, 0);
