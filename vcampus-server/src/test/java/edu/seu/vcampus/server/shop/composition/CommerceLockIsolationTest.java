package edu.seu.vcampus.server.shop.composition;

import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.Map;
import static org.mockito.Mockito.*;

class CommerceLockIsolationTest {
    @Test void maintenanceDoesNotAcquireStudentAndAccountResourceStripes() {
        var shared=mock(ResourceLockManager.class);
        var transactions=mock(TransactionManager.class);
        try(var runtime=new CommerceRuntime(new MessageRouter(Map.of()),transactions,shared,new SessionRegistry(),Clock.systemUTC())) {
            runtime.governance().maintain();
            verifyNoInteractions(shared);
        }
    }
}
