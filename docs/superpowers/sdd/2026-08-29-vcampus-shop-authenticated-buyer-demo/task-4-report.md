# Task 4 report — bounded buyer navigation

## Changed files

- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRoute.java`
- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigator.java`
- `vcampus-client/src/main/java/edu/seu/vcampus/client/shop/ui/navigation/ShopRouteHost.java`
- `vcampus-client/src/test/java/edu/seu/vcampus/client/shop/ui/navigation/ShopNavigatorTest.java`

## RED/GREEN evidence

- RED: `mvn -pl vcampus-client -am '-Dtest=ShopNavigatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` failed during test compilation because `ShopRoute` and `ShopNavigator` did not exist.
- GREEN: the same focused command passed with 4 tests, 0 failures, and 0 errors.

## Complete verification

- `mvn -pl vcampus-client -am '-Dsurefire.failIfNoSpecifiedTests=false' test` passed: common 1 test, server 45 tests, client 12 tests; 58 total, 0 failures/errors.
- `git diff --check` passed with no output.

## Commit

- Implementation commit: `3937b79ae2cc159eed9ba09e88008127c4b9c719`

## Fix round 1

- Added exact capacity-bound assertions: history is precisely `product-3` through
  `product-22` before overflow recovery, and `back()` restores `product-22`.
- Added complete `ProductSearchQuery` restoration and host-observation assertions
  proving current/history state is updated before `render` is called.
- These new tests initially ran RED because the prior duplicate-render assertion
  incorrectly included the intentional `back()` render. After narrowing that
  assertion to the open sequence, focused navigation tests passed 6/6.
- Focused verification: `mvn -pl vcampus-client -am '-Dtest=ShopNavigatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` — 6 tests passed.
- Complete reactor verification: `mvn -pl vcampus-client -am '-Dsurefire.failIfNoSpecifiedTests=false' test` — common 1, server 45, client 14; 60 tests passed.
- `git diff --check` passed with no output before the fix round commit.
- Fix round commit: `f2e50459eb5e01658abaa72d847b94a7eca67e7c`.

## Deviations and risks

- Initial navigation state is represented by `Optional.empty()` and `back()` is a safe no-op when history is empty.
- Route query/value objects are retained as record components; route constructors reject null values.
- Maven emitted existing Mockito/JDK dynamic-agent warnings during tests; they did not affect results.
