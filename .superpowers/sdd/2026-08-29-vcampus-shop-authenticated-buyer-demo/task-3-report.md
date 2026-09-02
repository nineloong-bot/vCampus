# Task 3 report — asynchronous buyer client

## Changed files

- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientPort.java`
- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientException.java`
- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/service/ShopClientService.java`
- `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ShopClientFixtures.java`
- `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/service/ShopClientServiceTest.java`

## TDD evidence

The focused test initially failed at test compilation because `ShopClientService` and `ShopClientException` did not exist. After the minimal implementation, the focused suite passed 3 tests.

## Verification

- `mvn -pl vcampus-client -am "-Dtest=ShopClientServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` — BUILD SUCCESS; 3 tests passed.
- `mvn -pl vcampus-client -am test` — BUILD SUCCESS; common 1, server 45, client 7 tests passed.
- `git diff --check` — no whitespace errors.
- Shop client production sources contain no blocking `join`, `get`, or sleep calls; all mapping is on the `CompletableFuture` chain.

## Concern/deviation

No functional deviation. Mockito emits a JDK dynamic-agent warning during tests; it is an existing test-runtime warning and does not affect results.

## Review round 1

Added `rejectsSuccessfulResponseWithNullDataAndPreservesStableCode`, covering a `success=true` response whose data is null. The existing production implementation already handled this case, so the new test was directly GREEN; no production weakening was used to manufacture RED evidence. The test preserves `SHOP_CART_EMPTY` as the `ShopClientException` code/message.

- Focused rerun: `mvn -pl vcampus-client -am "-Dtest=ShopClientServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` — BUILD SUCCESS; 4 tests passed.
- Full client reactor rerun: `mvn -pl vcampus-client -am test` — BUILD SUCCESS; common 1, server 45, client 8 tests passed.
- `git diff --check` — no whitespace errors.
