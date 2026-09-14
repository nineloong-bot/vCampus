package edu.seu.vcampus.common.wallet;

import java.io.Serializable;
import java.io.Serial;

/** Self-service virtual recharge; user identity is obtained from the session. */
public record RechargeCommand(java.math.BigDecimal amount) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
