package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.wallet.service.WalletException;
import java.io.Serializable;

/** Versioned authenticated Socket order endpoints deriving actor identity from the session. */
public final class OrderHandlers {
    private final OrderService service;
    private final SessionRegistry sessions;
    /** Registers all order commands on the server message router. */
    public OrderHandlers(MessageRouter router,OrderService service,SessionRegistry sessions) {
        this.service=service;this.sessions=sessions;
        for(String command:new String[]{"QUOTE","CHECKOUT","BUYER_LIST","SELLER_LIST","VALIDATE","PAY","CANCEL",
                "REFUND_REQUEST","REFUND_APPROVE","REFUND_REJECT","SHIP","RECEIVE"})
            router.register("SHOP2_ORDER_"+command,(message,context)->handle(message));
    }
    private ResponseBody<? extends Serializable> handle(Message message) {
        try {
            var session=sessions.requireSnapshot(message.sessionToken());
            if(session.restricted())return failure("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
            if(session.identity().accountStatus()!=AccountStatus.ACTIVE)return failure("AUTH_FORBIDDEN");
            if(session.identity().role()!=edu.seu.vcampus.common.user.UserRole.STUDENT
                    && session.identity().role()!=edu.seu.vcampus.common.user.UserRole.TEACHER)
                return failure("ORDER_FORBIDDEN");
            if(message.type()!=MessageType.REQUEST)return failure("ORDER_INVALID_REQUEST");
            String actor=session.identity().userId(),kind=message.command().substring("SHOP2_ORDER_".length());
            Serializable result=switch(kind) {
                case "QUOTE" -> service.quote(actor,body(message,CheckoutRequest.class));
                case "CHECKOUT" -> service.checkout(actor,message.requestId(),body(message,CheckoutRequest.class));
                case "BUYER_LIST" -> service.list(actor,false,body(message,OrderQuery.class));
                case "SELLER_LIST" -> service.list(actor,true,body(message,OrderQuery.class));
                case "VALIDATE" -> service.validate(actor,body(message,OrderAction.class));
                default -> service.act(actor,message.requestId(),kind,body(message,OrderAction.class));
            };
            return ResponseBody.success(result);
        } catch(OrderException | WalletException | SessionExpiredException error){return failure(error.getMessage());}
        catch(IllegalArgumentException | NullPointerException error){return failure("ORDER_INVALID_REQUEST");}
        catch(RuntimeException error){return failure("ORDER_RETRY_REQUIRED");}
    }
    private static <T> T body(Message message,Class<T> type) {
        if(!type.isInstance(message.body()))throw new OrderException("ORDER_INVALID_REQUEST");
        return type.cast(message.body());
    }
    private static ResponseBody<EmptyResponse> failure(String code) {
        return ResponseBody.failure(code,"订单操作未完成，请刷新状态后重试",null);
    }
}
