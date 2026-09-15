package edu.seu.vcampus.server.shop.governance;

import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import java.io.Serializable;

/** Authenticated Socket routes with server-derived identity and safe failure messages. */
public final class GovernanceHandlers {
    /** Registers governance routes using the existing restricted-session-aware user adapter. */
    public GovernanceHandlers(MessageRouter router,GovernanceService service,ShopUserPort users) {
        for(String command:new String[]{"SELF","APPLY","APPLICATIONS","REVIEW_APPLICATION","SHOP","SETTINGS",
                "QUALIFICATIONS","SUBMIT_QUALIFICATION","REVIEW_QUALIFICATION","CASES","SUBMIT_CASE","ACTION","REVIEW_CASE","AUDIT"}) {
            router.register("SHOP2_GOV_"+command,(message,context)->{
                try {
                    var user=users.requireUser(message.sessionToken());
                    if(message.type()!=MessageType.REQUEST) throw new IllegalArgumentException("Request required");
                    Serializable result=service.execute(user,command,message.body(),message.requestId());
                    return ResponseBody.success(result);
                } catch(ShopAccessException e) { return failure(e.code()); }
                catch(SecurityException e) { return failure("AUTH_FORBIDDEN"); }
                catch(IllegalArgumentException|NullPointerException e) { return failure("SHOP_GOV_INVALID_REQUEST"); }
                catch(RuntimeException e) { return failure("SHOP_GOV_RETRY_REQUIRED"); }
            });
        }
    }
    private static ResponseBody<EmptyResponse> failure(String code) {
        return ResponseBody.failure(code,"Shop governance request could not be completed",null);
    }
}
