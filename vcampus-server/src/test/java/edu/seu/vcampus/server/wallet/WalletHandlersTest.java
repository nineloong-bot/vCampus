package edu.seu.vcampus.server.wallet;

import edu.seu.vcampus.server.wallet.handler.WalletHandlers;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.routing.*;
import edu.seu.vcampus.common.user.*;
import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.common.wallet.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class WalletHandlersTest {
    @Test void usesSessionIdentityRejectsInvalidSessionsAndNeverExposesPostingCommands() throws Exception {
        try (var db = new WalletTestDatabase()) {
            var sessions = new SessionRegistry();
            var identity = new UserIdentity("buyer", "buyer", UserRole.STUDENT, AccountStatus.ACTIVE);
            String token = sessions.create(identity);
            String restricted = sessions.create(identity, Set.of(), true, "client");
            var router = new MessageRouter(Map.of());
            new WalletHandlers(router, WalletServiceTest.service(db), sessions);
            assertThat(call(router, "WALLET_GET_BALANCE", "invalid", EmptyRequest.INSTANCE).success()).isFalse();
            assertThat(call(router, "WALLET_RECHARGE", restricted, new RechargeCommand(BigDecimal.TEN)).code())
                    .isEqualTo("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
            assertThat(call(router, "WALLET_GET_BALANCE", token, "other").success()).isFalse();
            var reply = call(router, "WALLET_RECHARGE", token, new RechargeCommand(BigDecimal.TEN));
            assertThat(reply.success()).isTrue();
            assertThat(call(router, "WALLET_RECHARGE", token, new RechargeCommand(BigDecimal.TEN)).data()).isEqualTo(reply.data());
            assertThat(WalletServiceTest.service(db).getBalance("other").balanceCents()).isZero();
            assertThat(call(router, "WALLET_GET_BALANCE", token, EmptyRequest.INSTANCE).data())
                    .isEqualTo(new WalletBalance(1000, 0, 1));
            assertThat(call(router, "WALLET_GET_HISTORY", token, new WalletHistoryQuery(0, 10)).success()).isFalse();
            assertThat(router.isRegistered("WALLET_DEBIT")).isFalse();
            assertThat(router.isRegistered("WALLET_REFUND")).isFalse();
        }
    }
    private ResponseBody<?> call(MessageRouter router, String command, String token, Serializable body) {
        return router.route(new Message("request", MessageType.REQUEST, command, token, body, 0),
                new ClientContext("connection", "127.0.0.1"));
    }
}
