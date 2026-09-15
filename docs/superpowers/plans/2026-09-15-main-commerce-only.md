# Main commerce module integration

Base: origin/main a5fe91d. Source: integration/steven44-commerce-release HEAD 34be9de (committed source, not its unfinished merge).

Accepted scope: retain main's application shell, login, permissions, student/course/library code and non-commerce database records. Integrate the new commerce UI, protocol, catalog/order/governance services and independent wallet. Use main as database base, transplant only commerce-owned records after explicit account-reference validation. Keep existing working copies and unresolved merge untouched. Do not push automatically in this implementation task.

1. Root: import focused module directories, their tests, SQL and preset assets; patch main's shared registration/transport hooks minimally. Preserve main UI outside commerce; do not import library fine UI changes. Retain wallet fine port as a callable dependency for later library integration.
2. Database task: snapshot committed main and commerce databases; create repeatable migration and verification on copies. Verify all non-commerce rows unchanged, ownership references, wallet conservation and history consistency. Never replace live databases. Deliver validated copy in this new worktree only and a manifest of retained/migrated row counts and hashes.
3. Root: run failing registration regression before hook edits, focused tests, full build and documentation checks; inspect actual packaged startup on copies. Verify diff whitelist against main. Resolve compatibility defects without overwriting unrelated modules.
4. Independent review: inspect module boundary and migration preservation, fix material findings; document startup path, baseline, tests and data scope for review.

No hidden reset, no whole-branch merge, no replacing main's user/student/course/library datasets. Shared file edits are limited to module registration and request identity support. New Java <=200 lines. Maven runs serialized; agents coordinate before executing them.

Completed 2026-09-15: selective modules and hooks, migrated database installed only here, full test reports reviewed and failing commerce assertion corrected, package/JavaDoc passed, two copied-database startup passes, preservation and wallet checks passed, independent review resolved session handoff. Ready for local branch review; no remote push.
