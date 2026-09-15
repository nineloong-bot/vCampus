package edu.seu.vcampus.common.wallet;

import java.io.Serializable;
import java.io.Serial;

/** One-based wallet history query, limited to 100 rows per page. */
public record WalletHistoryQuery(int page, int pageSize) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
