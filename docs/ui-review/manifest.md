# vCampus UI Review Manifest

Foundation establishes the layout-managed `MainFrame`, `PageNavigator`, and EDT-safe
`ConnectionStatusPanel`. Visual review screenshots and state coverage are supplied by
the dedicated UI design-system implementation plan.

- Design source: `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`
- Foundation status: shell and user authentication flow implemented
- Required states for later review: loading, normal, empty, error, disconnected

## Library module

- Review size: 1280 × 800 (minimum supported size: 1024 × 680)
- Shared foundation: `MainFrame`, theme tokens, page inset, table row height and permission navigation
- Reader pages: catalog search/detail/borrow, current loans/return/renew, loan history
- `LIBRARY_ADMIN` pages: book management, copy management, all-loan search, lending policy
- State copy covered: initial, loading, normal, empty, error, submitting and optimistic-lock refresh guidance
- Async checks: socket work stays in `LibraryClientService`; Swing updates return to the EDT; catalog search accepts only the latest response
- Automated review: `LibraryUiTest` and `LibraryHandlersTest`
