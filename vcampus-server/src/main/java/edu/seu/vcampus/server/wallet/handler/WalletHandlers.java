package edu.seu.vcampus.server.wallet.handler;

import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.common.wallet.*;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.wallet.service.WalletService;
import edu.seu.vcampus.server.wallet.service.WalletException;
import java.io.Serializable;

/** Authenticated self-service wallet commands. Internal debit/refund are not exposed. */
public final class WalletHandlers {
    private final WalletService wallets;
    private final SessionRegistry sessions;
    /** Registers the three public commands with server-derived user identity. */
    public WalletHandlers(MessageRouter router, WalletService wallets, SessionRegistry sessions) {
        this.wallets = wallets; this.sessions = sessions;
        for (String command : new String[]{"WALLET_GET_BALANCE", "WALLET_RECHARGE", "WALLET_GET_HISTORY"}) {
            router.register(command, (message, context) -> handle(message));
        }
    }
    private ResponseBody<? extends Serializable> handle(Message message) {
        try {
            var session = sessions.requireSnapshot(message.sessionToken());
            if (session.restricted()) return failure("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
            if (session.identity().accountStatus() != AccountStatus.ACTIVE) return failure("AUTH_FORBIDDEN");
            if (message.type() != MessageType.REQUEST) return failure("WALLET_INVALID_REQUEST");
            String userId = session.identity().userId();
            Serializable result = switch (message.command()) {
                case "WALLET_GET_BALANCE" -> {
                    requireBody(message, EmptyRequest.class);
                    yield wallets.getBalance(userId);
                }
                case "WALLET_RECHARGE" -> wallets.recharge(userId, message.requestId(),
                        requireBody(message, RechargeCommand.class).amount());
                case "WALLET_GET_HISTORY" -> wallets.history(userId, requireBody(message, WalletHistoryQuery.class));
                default -> throw new WalletException("WALLET_INVALID_REQUEST");
            };
            return ResponseBody.success(result);
        } catch (WalletException error) { return failure(error.getMessage()); }
        catch (SessionExpiredException error) { return failure(error.getMessage()); }
        catch (RuntimeException error) { return failure("WALLET_RETRY_REQUIRED"); }
    }
    private static <T> T requireBody(Message message, Class<T> type) {
        if (!type.isInstance(message.body())) throw new WalletException("WALLET_INVALID_REQUEST");
        return type.cast(message.body());
    }
    private static ResponseBody<EmptyResponse> failure(String code) {
        return ResponseBody.failure(code, "Wallet request was not completed", null);
    }
}
