package edu.seu.vcampus.server.governance;

import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ModuleAdministrationConcurrencyTest {
    @Test void simultaneousMovesToDifferentModulesCannotEmptyTheirSourceModule() throws Exception {
        // A controlled persistence boundary allows both reads to see the same count
        // unless the production service serializes changes to the source module.
        var accounts = new ConcurrentHashMap<String, UserRole>();
        accounts.put("first", UserRole.USER_ADMIN);
        accounts.put("second", UserRole.USER_ADMIN);
        var repository = mock(AccessModuleAdministrationRepository.class);
        when(repository.requireAccount(any(), anyString(), anyLong())).thenAnswer(call -> {
            String id = call.getArgument(1);
            return new ModuleAdministrationRepository.AdministratorAccount(id, id,
                    accounts.get(id), AccountStatus.ACTIVE, 0);
        });
        var bothCounts = new CyclicBarrier(2);
        when(repository.countActive(any(), any())).thenAnswer(call -> {
            UserRole role = call.getArgument(1);
            long count = accounts.values().stream().filter(role::equals).count();
            try { bothCounts.await(500, TimeUnit.MILLISECONDS); }
            catch (TimeoutException | BrokenBarrierException ignored) { }
            return count;
        });
        doAnswer(call -> {
            accounts.put(call.getArgument(1), call.getArgument(2));
            return null;
        }).when(repository).updateRoleAndStatus(any(), anyString(), any(), any(), anyLong());
        var service = new ModuleAdministrationService(
                new TransactionManager(() -> mock(Connection.class)),
                new StripedResourceLockManager(), repository,
                mock(AuditRepository.class), new SessionRegistry());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> {
                start.await(); return move(service, "first", "COURSE");
            });
            Future<String> second = executor.submit(() -> {
                start.await(); return move(service, "second", "LIBRARY");
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED");
            assertThat(accounts.values().stream().filter(UserRole.USER_ADMIN::equals).count())
                    .isEqualTo(1);
        }
    }

    private static String move(ModuleAdministrationService service, String id, String module) {
        try {
            service.assign("super", new AssignModuleAdministratorCommand(module, id, 0));
            return "SUCCESS";
        } catch (IllegalStateException failure) { return failure.getMessage(); }
    }
}
