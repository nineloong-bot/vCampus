# 虚拟校园图书馆模块设计

## 1. 目标与范围

本模块管理书目、实体馆藏副本、借阅、归还、续借、逾期和借阅策略。模块不处理罚款支付；逾期只触发提示和停止新增借阅。

## 2. 权限

学生和教师可检索、借阅、归还、续借及查看本人记录。管理员可维护书目和副本、查询全部借阅、标记遗失/损坏并配置借阅策略。

## 3. 默认策略

| 角色 | 最大在借 | 借期 | 最大续借 | 每次续借 |
|---|---:|---:|---:|---:|
| 学生 | 5 | 30 天 | 1 | 15 天 |
| 教师 | 10 | 60 天 | 2 | 30 天 |

策略由 `tblLibraryPolicy` 读取，不写死在服务代码中。

## 4. 业务规则

- 一个副本同一时间只能有一条有效借阅。
- 存在逾期借阅的用户不能新增借阅。
- 达到在借数量上限时拒绝借阅。
- 续借仅限未逾期的有效记录且不能超过次数。
- 归还将副本恢复为 `AVAILABLE`；遗失/损坏由管理员改为对应状态。
- 所有借阅历史永久保留，不物理删除。

## 5. Swing 页面

`L-01 BookSearchPanel`、`L-02 BookDetailPanel`、`L-03 CurrentLoansPanel`、`L-04 LoanHistoryPanel`、`L-05 LoanActionDialog`、`L-06 BookManagementPanel`、`L-07 CopyManagementPanel`、`L-08 LoanAdminPanel`、`L-09 LibraryPolicyPanel`。

## 6. DTO 与接口

```java
enum CopyStatus { AVAILABLE, BORROWED, LOST, DAMAGED }
enum LoanStatus { ACTIVE, RETURNED, OVERDUE, LOST }
record BorrowBookCommand(String copyId) implements Serializable {}
record ReturnBookCommand(String loanId, long expectedVersion)
        implements Serializable {}
record RenewLoanCommand(String loanId, long expectedVersion)
        implements Serializable {}
record BookSearchQuery(String keyword, String category,
        Boolean availableOnly, int page, int pageSize)
        implements Serializable {}

public interface LibraryService {
    PageResult<BookSummary> searchBooks(BookSearchQuery query);
    BookDetail getBook(String bookId);
    LoanView borrow(String sessionToken, BorrowBookCommand command);
    LoanView returnBook(String sessionToken, ReturnBookCommand command);
    LoanView renew(String sessionToken, RenewLoanCommand command);
    List<LoanView> getCurrentLoans(String sessionToken);
    PageResult<LoanView> getLoanHistory(String sessionToken,
                                       LoanHistoryQuery query);
    BookView createBook(CreateBookCommand command);
    BookView updateBook(UpdateBookCommand command);
    BookCopyView addCopy(AddBookCopyCommand command);
    BookCopyView changeCopyStatus(ChangeCopyStatusCommand command);
    PageResult<LoanView> searchAllLoans(AdminLoanSearchQuery query);
}
```

## 7. 消息合同

`LIBRARY_SEARCH_BOOKS`、`LIBRARY_GET_BOOK`、`LIBRARY_BORROW`、`LIBRARY_RETURN`、`LIBRARY_RENEW`、`LIBRARY_GET_MY_CURRENT_LOANS`、`LIBRARY_GET_MY_LOAN_HISTORY` 面向已登录用户；`LIBRARY_CREATE_BOOK`、`LIBRARY_UPDATE_BOOK`、`LIBRARY_ADD_COPY`、`LIBRARY_CHANGE_COPY_STATUS`、`LIBRARY_SEARCH_ALL_LOANS` 和 `LIBRARY_UPDATE_POLICY` 仅管理员可用。所有写命令幂等。

## 8. 数据库

```text
tblBook(bookId PK, isbn VARCHAR(20) UNIQUE, title, author, publisher,
        publishDate, category, description, isActive, rowVersion)
tblBookCopy(copyId PK, bookId FK, barcode VARCHAR(32) UNIQUE,
            locationCode, copyStatus, rowVersion)
tblBookLoan(loanId PK, copyId FK, borrowerUserId FK,
            borrowedAt, dueAt, returnedAt NULL, renewCount,
            loanStatus, rowVersion)
tblLibraryPolicy(policyId PK, roleCode UNIQUE, maxActiveLoans,
                 loanDays, maxRenewals, renewalDays, rowVersion)
```

`isbn` 标识书目，`barcode` 标识实体副本。有效借阅的查询条件为 `loanStatus IN (ACTIVE, OVERDUE)`。

## 9. 事务与并发

借阅按顺序锁定 `LIBRARY_USER:<userId>` 和 `BOOK_COPY:<copyId>`，事务内检查账户、逾期、上限和副本状态，随后同时创建借阅并修改副本。

归还锁定 `LOAN:<loanId>` 和副本，使用 `rowVersion` 防止重复。续借锁定用户与借阅记录并重新计算到期日。每日维护任务把已过期的 `ACTIVE` 记录改为 `OVERDUE`；查询时也动态判断，避免维护任务延迟绕过限制。

## 10. 错误码

`LIBRARY_BOOK_NOT_FOUND`、`LIBRARY_COPY_UNAVAILABLE`、`LIBRARY_LOAN_LIMIT_REACHED`、`LIBRARY_USER_HAS_OVERDUE_LOANS`、`LIBRARY_LOAN_NOT_ACTIVE`、`LIBRARY_LOAN_ALREADY_RETURNED`、`LIBRARY_RENEWAL_LIMIT_REACHED`、`LIBRARY_LOAN_OVERDUE`、`LIBRARY_COPY_STATUS_INVALID`。

## 11. 测试与验收

- 20 人同时借同一副本只有一人成功。
- 同一用户并发借阅不能绕过上限。
- 存在动态或持久化逾期记录时均不能借书。
- 重复归还相同 `requestId` 返回原结果，不重复修改。
- 不同请求同时归还时一个成功，另一个得到状态错误或版本冲突。
- 续借次数和新的到期日按角色策略计算。
- 副本与有效借阅状态始终一致。

## 12. 文件边界

```text
vcampus-common/.../library/{command,query,view,enum}
vcampus-client/.../library/{ui,service}
vcampus-server/.../library/{handler,service,repository,domain,validation}
vcampus-server/src/test/.../library
```

模块消费 `AuthorizationPort`；不得修改用户表或实现罚款支付。

## 13. 下游实现任务

1. 书目、副本、策略 Repository 与唯一性测试。
2. 借阅规则纯单元测试和策略解析。
3. 并发借阅、借阅上限和逾期限制。
4. 原子归还、续借、遗失/损坏处理。
5. 每日逾期维护任务和恢复测试。
6. 消息 Handler、权限、幂等和序列化测试。
7. 九个 Swing 页面、Access 集成和演示场景。
