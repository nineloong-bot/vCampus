# Main plus current commerce data

Base: committed `origin/main` (`a5fe91d`), `vcampus-distribution/data/vCampus.accdb`.
The committed commerce code reference is `34be9de`. Its database has zero rows in
all 18 new catalog/order/governance/wallet tables. The active database snapshot
from `E:/summer-school/vCampus/vcampus-distribution/data/vCampus.accdb`, taken on
2026-09-15, contains the exercised commerce flows and is the migration source.
The original files and unresolved worktree were never opened through a database
connection or modified. Both committed versions were extracted with `git archive`;
the active version was copied before opening. All three snapshots remain under
`E:/summer-school/tmp/main-commerce-data`.

The candidate is `E:/summer-school/tmp/main-commerce-data/candidate.accdb`.
It has **38 noncommerce tables / 14,431 rows identical to main**, including all
1,173 account rows, academic data, library data, permissions, audit, deduplication,
and number sequences. Source user IDs and login IDs match all 1,173 main accounts;
no mapping or account edits are applied. Other source account field differences
are deliberately resolved by keeping main, including its passwords and roles.

All 30 commerce-owned tables exactly match the active snapshot. For overlapping
keys, source commerce values are authoritative. Every main commerce primary key
is still present; an older source that loses a main key fails before copying.
The full source snapshot remains available, including its noncommerce data.
The migration transfers no general audit/dedup/sequence rows: inventory found
only user/platform actions and academic/card sequence keys in those tables.
Commerce receipts, wallet business keys and governance audits are transferred.

Selected candidate counts:

| Data | Main | Candidate |
| --- | ---: | ---: |
| Shops | 31 | 32 |
| Seller applications | 48 | 49 |
| Products / SKUs | 603 / 1,203 | 603 / 1,203 |
| Orders / order groups | 602 / 602 | 606 / 606 |
| Order items | 602 | 611 |
| Carts / cart items | 301 / 602 | 305 / 616 |
| New order states / line states | 0 / 0 | 4 / 9 |
| Order events / inventory movements | 0 / 0 | 7 / 18 |
| Order receipts / catalog receipts | 0 / 0 | 6 / 21 |
| Wallet accounts / operations / entries / escrows | 0 / 0 / 0 / 0 | 4 / 6 / 12 / 2 |
| Governance applications / audits / receipts | 0 / 0 / 0 | 1 / 2 / 2 |

The wallet ledger reconciles all six operations to zero, all four account balances
to user postings, and both held escrows to escrow postings. Wallet balances are
150,525 cents total; held escrow is 19,475 cents; virtual recharge is 170,000 cents.
References between migrated records and retained accounts are validated.

`vcampus-database/tools/main-commerce-manifest.tsv` records each table's three
counts and SHA-256 hashes, plus byte hashes of all three files. Table hashes use
column metadata and length-prefixed, typed cell values; row order is ignored and
duplicate rows remain significant. Binary cell values are hashed as hex. File
hashes identify exact artifacts; table hashes establish logical preservation.
This is a merged active dataset, separate from the schema/seed generator baseline.
Do not regenerate it from the baseline seeds as a release-preparation step.

Run from the repository root with JDK 21 and the packaged dependency JAR:

```powershell
$classes = 'E:/summer-school/tmp/main-commerce-data/classes'
$main = 'E:/summer-school/tmp/main-commerce-data/main/vcampus-distribution/data/vCampus.accdb'
$source = 'E:/summer-school/tmp/main-commerce-data/active.accdb'
$cp = "$classes;vcampus-distribution/lib/vCampusServer.jar"
javac -encoding UTF-8 -cp vcampus-distribution/lib/vCampusServer.jar -d $classes vcampus-database/tools/*.java
java -cp $cp MainCommerceMigrationTest $main $source
java -cp $cp MainCommerceMigration --source-commerce $main $source 'E:/summer-school/tmp/main-commerce-data/candidate-2.accdb'
java -cp $cp VerifyMainCommerceData $main $source 'E:/summer-school/tmp/main-commerce-data/candidate-2.accdb'
```

The destination must be new: existing files are rejected, and source/main files
are opened read-only. A failed candidate is left for diagnosis and is not a valid
release artifact unless verification succeeds. The script is opt-in and never
installs the candidate. New table column definitions, primary/unique indexes and
relationships are copied from the source; existing main table definitions remain.

Regression verification passed: unmigrated main fails source completeness;
all source commerce and main noncommerce records pass after migration; existing
output cannot be overwritten; a one-cent wallet corruption, changed account
record, and changed login identity are rejected. No Maven process was started by
this data task. The repository's full Maven verification remains separate.

Before release, start the packaged server against another **copy** of the candidate,
stop it, and run this command. It requires exact main noncommerce hashes while
allowing commerce lifecycle maintenance (for example expiration of pending orders):

```powershell
java -cp $cp VerifyMainCommerceData --after-startup $main 'E:/summer-school/tmp/main-commerce-data/startup-copy.accdb'
```

Startup mode still verifies current commerce references and reconciles the wallet.
The full three-file verification is required before startup to establish complete
source migration. The library fine sentinel is accepted only as `peerId=LIBRARY`
on a `LIBRARY_FINE` operation; system recharge is accepted only as `peerId=SYSTEM`
on a `RECHARGE` operation. Actors must always reference actual accounts.
