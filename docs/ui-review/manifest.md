# vCampus UI Review Manifest

Foundation establishes the layout-managed `MainFrame`, `PageNavigator`, and EDT-safe
`ConnectionStatusPanel`. Visual review screenshots and state coverage are supplied by
the dedicated UI design-system implementation plan.

- Design source: `docs/superpowers/specs/2026-08-26-vcampus-ui-design-system.md`
- Foundation status: shell seam implemented; visual feature pages pending
- Required states for later review: loading, normal, empty, error, disconnected

## Course module visual baseline

- Branch/commit baseline: `course` / `45eff29` plus the current state-model follow-up.
- Shared source: theme tokens copied byte-for-byte from teammate branch `origin/as811`;
  the course module does not modify `MainFrame` or the shared shell.
- Reviewed locally at 1280 × 800: `course/c01-offering-search--normal.png`,
  `course/c01-offering-search--loading.png`, `course/c01-offering-search--empty.png`,
  `course/c01-offering-search--error.png`, `course/c01-offering-search--disconnected.png`,
  and `course/c04-my-schedule--normal.png`.
- Automated coverage: shared palette/dimensions, query-list structure, table rules,
  weekly-grid structure, stable navigation IDs, and loading/empty/disconnected states.
- External UI review: pending a non-course teammate, as required by the UI specification.
- Shared-component dependency: no fetched teammate branch currently contains the
  specification-named shared buttons, paged table, state panels, or confirm dialog;
  course pages therefore use shared tokens with standard Swing components until the
  shared owner publishes those components.
