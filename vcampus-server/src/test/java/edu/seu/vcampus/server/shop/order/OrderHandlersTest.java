package edu.seu.vcampus.server.shop.order;

import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.routing.*;
import edu.seu.vcampus.common.user.*;
import edu.seu.vcampus.common.protocol.*;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderHandlersTest {
    @Test void authenticatedProtocolRejectsAdminRestrictionAndSpoofedBodies() throws Exception {
        try(var f=new OrderFixture()) {
            var sessions=new SessionRegistry();
            var user=new UserIdentity("student-1","ignored",UserRole.STUDENT,AccountStatus.ACTIVE);
            String buyer=sessions.create(user),restricted=sessions.create(user,Set.of(),true,"client");
            String admin=sessions.create(new UserIdentity("admin-1","ignored",UserRole.SHOP_ADMIN,AccountStatus.ACTIVE));
            var router=new MessageRouter(Map.of());
            new OrderHandlers(router,f.orders,sessions);
            assertFalse(call(router,"QUOTE","invalid",f.request("k1")).success());
            assertEquals("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED",call(router,"QUOTE",restricted,f.request("k1")).code());
            assertEquals("ORDER_FORBIDDEN",call(router,"CHECKOUT",admin,f.request("k1")).code());
            assertFalse(call(router,"CHECKOUT",buyer,"student-1").success());
            var reply=call(router,"CHECKOUT",buyer,f.request("k1"));
            assertTrue(reply.success());
            assertEquals(reply.data(),call(router,"CHECKOUT",buyer,f.request("k1")).data());
            assertFalse(reply.toString().contains("student-1"));
        }
    }
    private ResponseBody<?> call(MessageRouter router,String kind,String token,Serializable body) {
        return router.route(new Message("same-request",MessageType.REQUEST,"SHOP2_ORDER_"+kind,token,body,0),
                new ClientContext("connection","127.0.0.1"));
    }
}
