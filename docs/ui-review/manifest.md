# vCampus UI Review Manifest

Foundation establishes the layout-managed `MainFrame`, `PageNavigator`, and EDT-safe
`ConnectionStatusPanel`. Visual review screenshots and state coverage are supplied by
the dedicated UI design-system implementation plan.

- Design source: `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`
- Foundation status: shell seam implemented; visual feature pages pending
- Required states for later review: loading, normal, empty, error, disconnected

## Course module visual baseline

- Branch baseline: `course`; screenshots are regenerated from the current tested branch state.
- Shared source: theme tokens copied byte-for-byte from teammate branch `origin/as811`;
  the course module does not modify `MainFrame` or the shared shell.
- Reviewed locally at 1280 × 800: C-01 normal/loading/empty/error/disconnected,
  C-03 through C-11 normal student/management surfaces. The 720/680/640 px dialog review set
  includes C-02 atomic-change confirmation, C-07 term create, C-08 course create, and
  C-09 offering edit (`course/c02-*`, `course/c07-term-editor-*`,
  `course/c08-course-editor-*`, `course/c09-offering-editor-*`).
- Automated coverage: shared palette/dimensions, query-list structure, table rules,
  weekly-grid structure, stable navigation IDs, loading/empty/disconnected states,
  complete term windows, optimistic versions, and aggregate offering schedules.
- External UI review: pending a non-course teammate, as required by the UI specification.
- Shared-component dependency: no fetched teammate branch currently contains the
  specification-named shared buttons, paged table, state panels, or confirm dialog;
  course pages therefore use shared tokens with standard Swing components until the
  shared owner publishes those components.
