# Task 2 report — Buyer Socket handlers and request idempotency

## Changed files

- `vcampus-server/src/main/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlers.java`
- `vcampus-server/src/test/java/edu/seu/vcampus/server/shop/handler/BuyerShopHandlersTest.java`

The handler registers exactly the 11 buyer commands, authenticates every request
through `ShopUserPort`, and routes write commands through
`RequestDeduplicator.executeOnce`. Shop and authentication failures are converted
to stable protocol codes; validation and unexpected failures are sanitized to
`COMMON_VALIDATION_FAILED` and `COMMON_INTERNAL_ERROR`. Business checkout/payment
events execute inside the first deduplicated supplier, while `commandCompleted`
executes after the wrapper has the final response, including replay responses.

## TDD evidence

RED command:

```text
mvn -pl vcampus-server -am '-Dtest=BuyerShopHandlersTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observed failure: test compilation failed because `BuyerShopHandlers` did not
exist (the initial test also had generic fixture/import compile errors that were
corrected before the implementation cycle continued).

## Verification

Focused Handler test:

```text
mvn -pl vcampus-server -am '-Dtest=BuyerShopHandlersTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: BUILD SUCCESS; 4 tests run, 0 failures, 0 errors.

Focused Handler + logger tests:

```text
mvn -pl vcampus-server -am '-Dtest=BuyerShopHandlersTest,ShopBusinessLoggerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: BUILD SUCCESS; 5 tests run, 0 failures, 0 errors.

Complete server reactor:

```text
mvn -pl vcampus-server -am test
```

Result: BUILD SUCCESS; common and server reactor tests totaled 42 tests, with 0
failures and 0 errors.

```text
git diff --check
```

Result: no output.

## Commit

Commit is created as `feat(shop): add buyer socket handlers`.

## Concerns / deviations

No production files outside the two allowed Shop-scoped paths were changed.
Maven emitted the existing Mockito inline-mock-maker/JDK dynamic-agent warning;
it did not affect test outcomes.

## Review round 1 fixes

Added explicit null-body validation before `Class.cast`, plus real two-route
replay tests for checkout and payment. The replay fixtures cache the first
`ResponseBody` by request ID and return it on the second route, verifying each
business service runs once, each business event logs once, and
`commandCompleted` logs twice.

Focused rerun:

```text
mvn -pl vcampus-server -am '-Dtest=BuyerShopHandlersTest,ShopBusinessLoggerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: BUILD SUCCESS; 8 tests run, 0 failures, 0 errors.

Complete server reactor rerun:

```text
mvn -pl vcampus-server -am test
```

Result: BUILD SUCCESS; 45 tests run, 0 failures, 0 errors.
