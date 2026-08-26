# Virtual Campus Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Maven project skeleton, shared protocol, Socket runtime, Access transaction/concurrency infrastructure, Swing shell seam, configuration, logging, and distributable JARs required by all five business modules and the shared UI design-system plan.

**Architecture:** A three-module Java 21 C/S application separates serializable contracts, Swing client code, and server code. The server routes typed `Message` objects through bounded executors and application locks; all database writes pass through one transaction manager.

**Tech Stack:** JDK 21, Maven, Swing, Java object streams, UCanAccess, JUnit 5, AssertJ, Mockito, SLF4J, Logback, JaCoCo, Maven Shade Plugin.

**Spec:** `docs/superpowers/specs/2026-08-24-vcampus-overall-architecture-design.md` and `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`

## Global Constraints

- Compile and run with JDK 21; do not use preview features.
- Produce `vCampusClient.jar` and `vCampusServer.jar`.
- Use package prefix `edu.seu.vcampus` and UTF-8 source encoding.
- Keep each Java file at or below 200 lines; every public type and method requires JavaDoc.
- `vcampus-common` must not depend on Swing, JDBC, or server implementation classes.
- Never perform network or database waits on the Swing EDT.
- All write requests require `requestId`, idempotency, a database transaction, and declared resource locks.
- The client must never open or access `vCampus.accdb`.
- Use `requestId` alone as the global idempotency key; retain `clientInstanceId` only for tracing and diagnostics.
- Preserve each module's published multi-resource lock order; sort by `resourceType + resourceId` only when no module-specific order exists.
- Complete `docs/superpowers/plans/2026-08-26-vcampus-ui-design-system.md` after this plan and before any business-module Swing page task.

## File Structure

```text
pom.xml
vcampus-common/pom.xml
vcampus-common/src/main/java/edu/seu/vcampus/common/{protocol,error,paging}/
vcampus-client/pom.xml
vcampus-client/src/main/java/edu/seu/vcampus/client/core/{network,session,navigation,ui}/
vcampus-server/pom.xml
vcampus-server/src/main/java/edu/seu/vcampus/server/{bootstrap,network,routing,session,persistence,concurrency,config}/
vcampus-database/{schema,seed}/
vcampus-distribution/{config,scripts}/
```

---

### Task 1: Maven Reactor and Shared Protocol

**Files:**
- Create: `pom.xml`
- Create: `vcampus-common/pom.xml`
- Create: `vcampus-client/pom.xml`
- Create: `vcampus-server/pom.xml`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/protocol/Message.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/protocol/MessageType.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/protocol/ResponseBody.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/protocol/EmptyRequest.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/protocol/EmptyResponse.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/error/ErrorDetail.java`
- Create: `vcampus-common/src/main/java/edu/seu/vcampus/common/paging/PageResult.java`
- Create: `vcampus-common/src/test/java/edu/seu/vcampus/common/protocol/MessageSerializationTest.java`

**Interfaces:**
- Consumes: no project interfaces.
- Produces: `Message`, `MessageType`, `ResponseBody<T extends Serializable>`, `ErrorDetail` exactly as defined by the overall spec.

- [ ] **Step 1: Write the failing serialization test**

```java
@Test
void roundTripsMessageAndTypedResponse() throws Exception {
    ErrorDetail error = new ErrorDetail("COMMON_FORBIDDEN", "无权限",
            Map.of(), "trace-1", false);
    ResponseBody<String> body = ResponseBody.failure("COMMON_FORBIDDEN",
            "无权限", error);
    Message source = new Message("req-1", MessageType.RESPONSE,
            "TEST", "token", body, 1L);
    byte[] bytes;
    try (var buffer = new ByteArrayOutputStream();
         var out = new ObjectOutputStream(buffer)) {
        out.writeObject(source);
        bytes = buffer.toByteArray();
    }
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
        assertThat(in.readObject()).isEqualTo(source);
    }
}
```

- [ ] **Step 2: Run the test and verify the missing types fail compilation**

Run: `mvn -pl vcampus-common -Dtest=MessageSerializationTest test`

Expected: FAIL because `Message`, `ResponseBody`, and `ErrorDetail` do not exist.

- [ ] **Step 3: Implement the records and parent/module POMs**

```java
public record Message(String requestId, MessageType type, String command,
        String sessionToken, Serializable body, long timestamp)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}

public record ResponseBody<T extends Serializable>(boolean success,
        String code, String message, T data, ErrorDetail error)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    public static <T extends Serializable> ResponseBody<T> failure(
            String code, String message, ErrorDetail error) {
        return new ResponseBody<>(false, code, message, null, error);
    }
    public static <T extends Serializable> ResponseBody<T> success(T data) {
        return new ResponseBody<>(true, "SUCCESS", "成功", data, null);
    }
}

public enum EmptyRequest implements Serializable { INSTANCE }
public enum EmptyResponse implements Serializable { INSTANCE }
public record PageResult<T extends Serializable>(List<T> items,
        int page, int pageSize, long total) implements Serializable {}
```

Set parent properties to `maven.compiler.release=21` and `project.build.sourceEncoding=UTF-8`; pin all dependency and plugin versions in `<dependencyManagement>` and `<pluginManagement>`.

- [ ] **Step 4: Run the shared module verification**

Run: `mvn -pl vcampus-common clean verify`

Expected: PASS; JaCoCo report exists under `vcampus-common/target/site/jacoco`.

- [ ] **Step 5: Commit the protocol contract**

```bash
git add pom.xml vcampus-common vcampus-client/pom.xml vcampus-server/pom.xml
git commit -m "build: establish vcampus reactor and protocol"
```

### Task 2: Server Routing and Connection Runtime

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/routing/MessageHandler.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/routing/MessageRouter.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/routing/ClientContext.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/routing/CommandNotFoundException.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/network/ClientConnection.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/network/SocketServer.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/routing/MessageRouterTest.java`
- Create: `vcampus-server/src/test/java/edu/seu/vcampus/server/network/ClientConnectionTest.java`

**Interfaces:**
- Consumes: `Message` and `ResponseBody` from Task 1.
- Produces: `MessageHandler.handle(Message, ClientContext)`, `MessageRouter.route(Message, ClientContext)`, and synchronized `ClientConnection.send(Message)`.

- [ ] **Step 1: Write router failure and dispatch tests**

```java
@Test
void routesRegisteredCommandAndRejectsUnknownCommand() {
    MessageHandler handler = (message, context) -> ResponseBody.success("ok");
    MessageRouter router = new MessageRouter(Map.of("PING", handler));
    assertThat(router.route(request("PING"), context()).data()).isEqualTo("ok");
    assertThatThrownBy(() -> router.route(request("MISSING"), context()))
            .isInstanceOf(CommandNotFoundException.class)
            .hasMessageContaining("MISSING");
}
```

- [ ] **Step 2: Run the focused router test**

Run: `mvn -pl vcampus-server -am -Dtest=MessageRouterTest test`

Expected: FAIL because routing classes do not exist.

- [ ] **Step 3: Implement typed routing and synchronized writes**

```java
public final class MessageRouter {
    private final Map<String, MessageHandler> handlers;
    public ResponseBody<? extends Serializable> route(
            Message message, ClientContext context) {
        MessageHandler handler = handlers.get(message.command());
        if (handler == null) throw new CommandNotFoundException(message.command());
        return handler.handle(message, context);
    }
}

public record ClientContext(String connectionId, String clientAddress) {}

public void send(Message message) throws IOException {
    writeLock.lock();
    try { output.writeObject(message); output.flush(); }
    finally { writeLock.unlock(); }
}
```

Create output streams before input streams and flush the header. `SocketServer` uses one acceptor and a bounded executor; rejection returns a clear server-busy response before closing.

- [ ] **Step 4: Run routing and connection tests**

Run: `mvn -pl vcampus-server -am -Dtest=MessageRouterTest,ClientConnectionTest test`

Expected: PASS, including two concurrent sends that deserialize as two intact messages.

- [ ] **Step 5: Commit the Socket runtime**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/{routing,network} vcampus-server/src/test/java/edu/seu/vcampus/server/{routing,network}
git commit -m "feat: add socket routing runtime"
```

### Task 3: Access Transactions, Declared-Order Locks, and Request Idempotency

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/persistence/ConnectionProvider.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/persistence/TransactionManager.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/persistence/SqlWork.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/concurrency/ResourceKey.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/concurrency/ResourceLockManager.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/concurrency/StripedResourceLockManager.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/routing/RequestDeduplicator.java`
- Create: `vcampus-database/schema/001_common.sql`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/concurrency/StripedResourceLockManagerTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/persistence/TransactionManagerTest.java`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/routing/RequestDeduplicatorTest.java`

**Interfaces:**
- Consumes: JDBC `Connection`, `Message.requestId()`.
- Produces: `TransactionManager.inTransaction(SqlWork<T>)`, `ResourceLockManager.withLocks(List<ResourceKey> orderedKeys, Supplier<T>)`, request-ID-only `RequestDeduplicator.executeOnce(...)`, and transaction-aware `replayCompleted(requestId)`, `claim(TransactionContext, Message)`, and `complete(TransactionContext, Message)` for cross-module coordinators.

- [ ] **Step 1: Write transaction rollback and lock ordering tests**

```java
@Test
void rollsBackWhenWorkThrows() {
    assertThatThrownBy(() -> manager.inTransaction(connection -> {
        insertMarker(connection, "before-error");
        throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(countMarkers()).isZero();
}

@Test
void serializesTwoActionsForSameResourceAndPreservesDeclaredOrder() throws Exception {
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger maximum = new AtomicInteger();
    runConcurrently(20, () -> locks.withLock("SKU", "sku-1", () -> {
        maximum.accumulateAndGet(inside.incrementAndGet(), Math::max);
        inside.decrementAndGet();
        return null;
    }));
    assertThat(maximum).hasValue(1);
    assertThat(recordingLocks.acquiredKeys()).containsExactly(
            key("NUMBER_SEQUENCE", "CAMPUS_CARD_GLOBAL"),
            key("NUMBER_SEQUENCE", "STUDENT_NUMBER:090:24:1"),
            key("LOGIN_ID", "213242478"));
}

@Test
void deduplicatesPreLoginRequestsByRequestIdOnly() {
    Message first = writeMessage("8e7c1a21-9d44-4c82-978b-df34326a0341", "client-a");
    Message replay = writeMessage(first.requestId(), "client-b");
    assertThat(deduplicator.executeOnce(first, action)).isEqualTo(success);
    assertThat(deduplicator.executeOnce(replay, action)).isEqualTo(success);
    verify(action, times(1)).get();
}
```

- [ ] **Step 2: Run the focused persistence/concurrency tests**

Run: `mvn -pl vcampus-server -am -Dtest=TransactionManagerTest,StripedResourceLockManagerTest test`

Expected: FAIL because transaction and lock implementations are absent.

- [ ] **Step 3: Implement transaction, declared-order striped locks, and request-ID idempotency**

```java
public <T> T inTransaction(SqlWork<T> work) {
    try (Connection connection = provider.open()) {
        connection.setAutoCommit(false);
        try { T result = work.apply(connection); connection.commit(); return result; }
        catch (Exception error) { connection.rollback(); throw translate(error); }
    } catch (SQLException error) { throw translate(error); }
}

@FunctionalInterface
public interface SqlWork<T> {
    T apply(Connection connection) throws Exception;
}
```

Implement 256 fair `ReentrantLock` stripes and acquire the caller's immutable key list in its declared order. Module services must pass their published fixed order; callers with no published order sort a copy by `resourceType + resourceId` before calling. Create `tblRequestDedup` exactly as defined in the overall spec, make `requestId` its only uniqueness key, and store `clientInstanceId` only as diagnostic data. The standard facade stores `PROCESSING` before business execution and `COMPLETED` plus the serialized response afterward; the transaction-aware API lets a cross-module coordinator claim and complete the same row on its existing `TransactionContext`, so the business result and replay snapshot commit or roll back together.

- [ ] **Step 4: Run Access and 20-thread verification**

Run: `mvn -pl vcampus-server -am -Dtest=TransactionManagerTest,StripedResourceLockManagerTest,RequestDeduplicatorTest test`

Expected: PASS; rollback leaves zero rows and same-key maximum concurrency is one.

- [ ] **Step 5: Commit persistence infrastructure**

```bash
git add vcampus-server/src/main/java/edu/seu/vcampus/server/{persistence,concurrency,routing} vcampus-server/src/test vcampus-database/schema/001_common.sql
git commit -m "feat: add access transactions and concurrency guards"
```

### Task 4: Async Client Connection and Swing Shell Seam

**Files:**
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/network/ClientConnection.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/network/PendingRequests.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/navigation/PageNavigator.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/MainFrame.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/core/ui/ConnectionStatusPanel.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/network/PendingRequestsTest.java`
- Test: `vcampus-client/src/test/java/edu/seu/vcampus/client/core/ui/EdtSafetyTest.java`

**Interfaces:**
- Consumes: shared `Message` protocol.
- Produces: `CompletableFuture<ResponseBody<T>> ClientConnection.send(String command, Serializable body, Duration timeout)`, connection-state events, `PageNavigator.show(String pageId)`, and the minimal `MainFrame` seam extended by the UI design-system plan.

- [ ] **Step 1: Write response-correlation and timeout tests**

```java
@Test
void completesOnlyMatchingRequest() {
    PendingRequests pending = new PendingRequests(clock, scheduler);
    CompletableFuture<Message> first = pending.register("r1", Duration.ofSeconds(1));
    CompletableFuture<Message> second = pending.register("r2", Duration.ofSeconds(1));
    pending.complete(response("r2"));
    assertThat(second).isCompletedWithValue(response("r2"));
    assertThat(first).isNotDone();
}
```

- [ ] **Step 2: Run client core tests**

Run: `mvn -pl vcampus-client -am -Dtest=PendingRequestsTest,EdtSafetyTest test`

Expected: FAIL because the client core does not exist.

- [ ] **Step 3: Implement correlation, CardLayout navigation, and EDT handoff**

```java
connection.send("PING", EmptyRequest.INSTANCE, Duration.ofSeconds(10))
        .whenComplete((response, error) -> SwingUtilities.invokeLater(() -> {
            statusPanel.setBusy(false);
            statusPanel.show(response, error);
        }));
```

`ClientConnection` owns one reader thread and never touches Swing. `MainFrame` supplies layout-managed header/navigation/content/footer extension points and a `CardLayout` registry, but visual tokens, fixed dimensions, templates, shared state panels, navigation order, and final shell composition are implemented and verified by `2026-08-26-vcampus-ui-design-system.md`.

- [ ] **Step 4: Run client tests and a headless Swing smoke test**

Run: `mvn -pl vcampus-client -am test`

Expected: PASS; `EdtSafetyTest` proves response callbacks update components on the EDT.

- [ ] **Step 5: Commit the client shell**

```bash
git add vcampus-client/src/main vcampus-client/src/test
git commit -m "feat: add async swing client shell"
```

### Task 5: Configuration, Logging, Bootstrap, and Distribution

**Files:**
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/config/ServerConfig.java`
- Create: `vcampus-server/src/main/java/edu/seu/vcampus/server/bootstrap/ServerMain.java`
- Create: `vcampus-client/src/main/java/edu/seu/vcampus/client/ClientMain.java`
- Create: `vcampus-distribution/config/client.properties`
- Create: `vcampus-distribution/config/server.properties`
- Create: `vcampus-distribution/config/logback.xml`
- Create: `vcampus-distribution/scripts/start-server.sh`
- Create: `vcampus-distribution/scripts/start-client.sh`
- Create: `vcampus-distribution/scripts/start-server.bat`
- Create: `vcampus-distribution/scripts/start-client.bat`
- Test: `vcampus-server/src/test/java/edu/seu/vcampus/server/config/ServerConfigTest.java`

**Interfaces:**
- Consumes: server runtime, client shell, transaction manager.
- Produces: validated `ServerConfig`, graceful shutdown, executable shaded JARs and launch scripts.

- [ ] **Step 1: Write strict configuration tests**

```java
@Test
void rejectsInvalidPortAndMissingDatabase() {
    Properties properties = validProperties();
    properties.setProperty("server.port", "70000");
    assertThatThrownBy(() -> ServerConfig.from(properties, tempDir))
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("server.port");
}
```

- [ ] **Step 2: Run configuration tests**

Run: `mvn -pl vcampus-server -am -Dtest=ServerConfigTest test`

Expected: FAIL because `ServerConfig` is absent.

- [ ] **Step 3: Implement validated startup and graceful shutdown**

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    socketServer.stopAccepting();
    socketServer.awaitRequests(Duration.ofSeconds(30));
    socketServer.close();
}, "vcampus-shutdown"));
```

Scripts must run `java -version`, reject versions below 21, use relative `config/` and `data/` paths, and never embed credentials. Configure the Shade plugin main classes as `ClientMain` and `ServerMain`.

- [ ] **Step 4: Verify the complete reactor and artifacts**

Run: `mvn clean verify && mvn package && mvn javadoc:aggregate`

Expected: BUILD SUCCESS; artifacts include `vCampusClient.jar`, `vCampusServer.jar`, JaCoCo reports, aggregate JavaDoc, all seven design documents, and `docs/ui-review/manifest.md`. Start the server with a missing database and verify it exits with a readable Chinese error; restore the file and verify a client connects.

- [ ] **Step 5: Commit distribution support**

```bash
git add vcampus-server vcampus-client vcampus-distribution pom.xml
git commit -m "build: package runnable vcampus distribution"
```
