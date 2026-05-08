# Feature C12 — Customer merge / duplicate detection

## What we built

A two-phase merge flow at `/customers/:id/merge`. Phase 1 fuzzy-
matches every other active customer against the source on name,
contact name, and phone — using Levenshtein distance over normalised
strings — and ranks the top candidates by a weighted similarity
score. Phase 2 opens a side-by-side compare for the chosen pair,
with a per-field radio that lets the user pick the winner's value
or the loser's for each conflicting field. On Merge, the backend
runs one transaction:

1. Apply the per-field overrides to the winner row.
2. Reassign every order from the loser to the winner.
3. Reassign every payment from the loser to the winner.
4. Delete the loser.

If anything fails — a duplicate-key violation on payment
reassignment, a network blip mid-statement — the whole transaction
rolls back and nothing changes. On success, the surviving customer's
detail page is reached via routing.

This is the most pedagogically dense feature in Section B. None of
the patterns it teaches — fuzzy string matching, conflict-resolution
UX, transactional FK reassignment — appeared anywhere in the
employee marathon.

Files touched:

- `classicmodels-backend/src/main/java/.../service/StringSimilarity.java` (new)
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerMergeCandidateDTO.java` (new)
- `classicmodels-backend/src/main/java/.../dto/customer/CustomerMergeRequestDTO.java` (new)
- `classicmodels-backend/src/main/java/.../service/CustomerMergeService.java` (new)
- `classicmodels-backend/src/main/java/.../controller/CustomerController.java` — `/merge-candidates`, `/merge`
- `classicmodels-ui/src/app/customers/customer.service.ts` — types + `getMergeCandidates`, `merge`
- `classicmodels-ui/src/app/customers/customer-merge.component.ts` (new)
- `classicmodels-ui/src/app/customers/customer.routes.ts` — `:id/merge` route
- `classicmodels-ui/src/app/customers/customer-detail.component.html` — "Find duplicates" quick-action

## Why this is worth learning

Three concepts converge.

**Fuzzy string matching.** Levenshtein distance is the canonical
"how different are these two strings?" measure — counting the
minimum number of single-character edits to turn one into the other.
The cost is that the implementation is a 12-line dynamic-programming
algorithm, not a one-liner — but once you've written it once, you
never forget the shape.

**Conflict-resolution UX.** Merging two records is an inherently
interactive decision: the system can't know whether the winner's
phone or the loser's phone is the "right" one. The side-by-side
compare with a per-field radio is the canonical answer — git's
merge tools, GitHub's PR conflict resolver, every customer-data-
hub product you've heard of all surface this same shape.

**Transactional FK reassignment.** Three SQL statements that have
to happen atomically: reassign orders, reassign payments, delete
the loser. If the order reassignment succeeds but the payment
reassignment fails (e.g., a duplicate-key violation on the composite
PK), rolling back means *nothing* changed. The choreography is the
same shape as the F8 reassign-and-delete employee feature, but
applied to a different FK pattern.

The new lesson, specific to Customer, is that **merge is the
inverse of the form**. The C3 form lets you fill in one customer's
fields; C12 lets you collapse two. Same field set, opposite
direction. That symmetry tells you the field list belongs in one
place — though we keep them duplicated for now because the abstraction
isn't yet earning its keep at two callers.

## Background

### Levenshtein distance

The minimum number of single-character edits (insertions, deletions,
substitutions) needed to turn one string into another. Classic DP:

```
    ""  a  t  e  l  i  e  r
""   0  1  2  3  4  5  6  7
 a   1  0  1  2  3  4  5  6
 t   2  1  0  1  2  3  4  5
 e   3  2  1  0  1  2  3  4
 l   4  3  2  1  0  1  2  3
 i   5  4  3  2  1  0  1  2
```

`dp[i][j]` is the distance between the first `i` chars of `a` and
the first `j` chars of `b`. The bottom-right cell is the answer.
Each cell is the min of three predecessors:

- `dp[i-1][j] + 1` — delete the i-th char of `a`.
- `dp[i][j-1] + 1` — insert b's j-th char into `a`.
- `dp[i-1][j-1] + (chars match ? 0 : 1)` — substitute.

Time: O(n × m). Space: O(n × m). For very long strings, a rolling
two-row implementation reduces space to O(min(n, m)) but for
customer-name-sized inputs the simple table is fine.

### Why normalise first

Without normalisation, "Atelier Graphique" and "atelier graphique"
score 18 (lots of case-sensitive substitutions). Useless.

```java
static String normalize(String s) {
    return s.toLowerCase().replaceAll("[^a-z0-9]", "");
}
```

After normalisation, both reduce to `ateliergraphique` and score
0 (identical). Stripping non-alphanumerics also handles "Atelier-
Graphique" vs "Atelier Graphique" and similar punctuation variants.

You can go further: stem ("Inc" → "Incorporated"), remove common
business suffixes ("Inc.", "Ltd.", "GmbH", "SARL"), apply Soundex
for English-language pronunciation similarity, run through `iconv`
to fold accented characters. Every additional transformation is
domain knowledge baked into the matcher. Start simple and add
complexity when you see the matcher missing real duplicates in
your data.

References:

- [Wikipedia — Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
- [Wikipedia — Approximate string matching](https://en.wikipedia.org/wiki/Approximate_string_matching)
- [PostgreSQL — pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html) — trigram-based similarity, the production-grade alternative for large datasets
- [Apache Commons Text — Similarity algorithms](https://commons.apache.org/proper/commons-text/userguide.html#Similarity) — Levenshtein, Jaro-Winkler, Cosine, etc.

### Weighted multi-field score

A single Levenshtein score on one field is too crude — "Bon Café"
and "Bon Auto" share half their characters but are clearly not
duplicates. Real duplicate detection scores multiple fields and
combines them.

```java
double combined = 0.50 * nameScore     // most discriminating
                + 0.30 * contactScore   // useful but contacts move
                + 0.20 * phoneScore;    // useful but numbers change
```

The weights encode domain judgment about which fields are most
predictive of duplicates. Two customers with the same business
name are very likely the same business; two with the same phone
number could be co-tenants in the same building. The weights I
picked are a reasonable starting set; tune from real false-positive
/ false-negative rates if you ever see them.

### `MATCHED_FIELD_THRESHOLD` for explainability

The candidate DTO carries a `matchedFields: List<String>` so the UI
can explain *why* this candidate showed up:

> "Atelier Graphique Inc — name + phone matched, contact differed"

The user reads that and immediately understands "yes, these are
probably the same customer with a typo in the name" or "no, these
are unrelated entities that happen to share a phone exchange."
Without this hint, ranking by score alone leaves the user staring
at a percentage and guessing.

This is the same principle that makes a good search-result page
underline the matched terms: showing the user *why* a result is
ranked where it is is at least as important as the ranking itself.

### Why per-customer matching, not all-vs-all

The endpoint scores one source against the other 121 active
customers. O(n) Levenshtein calls per source-customer click, where
each call is O(m × m) on names that average ~20 chars — a few hundred
microseconds of CPU per pair. Total: tens of milliseconds. Fine
for an interactive feature.

The naive all-vs-all approach — find every duplicate pair across
the entire dataset — is O(n²). At n=10k customers that's 100M
comparisons, which crosses the line into "needs an index" territory
(blocking on first letters, locality-sensitive hashing, or trigram
indexes). For our case the per-customer "find duplicates of THIS"
flow keeps things tractable.

References:

- [Hashing for similar item search — LSH](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) — when O(n²) gets too expensive
- [String similarity at scale](https://www.elastic.co/blog/found-similarity-search) — Elasticsearch's tradeoffs

### Hard delete vs. soft delete on merge

Two reasonable defaults:

**Hard delete the loser** (what we picked). After FK reassignment
the loser has no children, no orders, no payments. The row becomes
data without referential meaning. Hard-deleting keeps the merge
intent crisp: <em>one customer, not two with one hidden</em>.

**Soft delete the loser**. Keeps an audit row "this customer used
to exist." Useful for compliance ("were any merges in the last 30
days?") but creates a zombie record — un-deleting the loser would
restore an orphaned row with no orders. Defensible if you also
add a `mergedInto` FK column to the customers table so the
zombie state is at least labelled.

If you genuinely need an audit trail of merges, the right answer
is a `customer_merges` log table written to inside the same
transaction:

```sql
INSERT INTO customer_merges
  (winner_id, loser_id, merged_at, merged_by, reason)
VALUES (?, ?, NOW(), ?, ?)
```

This captures the event without keeping a half-customer around.
We didn't add the log table for C12 to keep the scope contained;
it's a clean follow-up.

### Transactional FK reassignment — the choreography

```
BEGIN;
  UPDATE customers SET ... WHERE customerNumber = winner;  -- field overrides
  UPDATE orders   SET customerNumber = winner WHERE customerNumber = loser;
  UPDATE payments SET customerNumber = winner WHERE customerNumber = loser;
  DELETE FROM customers WHERE customerNumber = loser;
COMMIT;
```

Order matters — children before parents — but the version-without-
ordering also works because we wrap the whole thing in one
transaction. If the DELETE fires before the UPDATEs, InnoDB rejects
it with an FK violation and the transaction rolls back; no harm
done.

The interesting failure mode is the **payment composite-PK
collision**. Payments has `PRIMARY KEY (customerNumber, checkNumber)`.
If both winner and loser happen to have a payment with the same
check number, the UPDATE fails with a duplicate-key violation
mid-statement. Our transaction handler catches the SQLException,
rolls back, and surfaces the error to the user.

In practice check numbers are nearly always unique across customers
(they're issued sequentially by the customer's bank), so this is
a vanishingly rare edge case. If it ever became a real problem,
the right fix is a smarter UPDATE that detects the collision and
either skips the colliding payment or renames the check number —
both of which are domain decisions, not infrastructure ones.

References:

- [MySQL InnoDB transaction model](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-model.html)
- [Spring docs — Programmatic transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)

### The two-phase UI: Pick → Compare

Phase 1: list of candidates with similarity scores. Phase 2: per-
field compare with radio picker. Why two phases:

- **Picking is a different decision from resolving.** Phase 1 asks
  "which customer is the duplicate?" Phase 2 asks "for each
  conflicting field, which value should survive?" Mixing them
  produces a giant scrolling page where the user has to scroll
  past per-field radios for every candidate.
- **Phase 1 only needs display fields**, so the candidate DTO is
  cheap. Phase 2 needs every mergeable field of both records, so
  we wait until the user has actually picked before fetching the
  full loser.
- **Reversibility.** A "Pick a different candidate" button on
  phase 2 lets the user back out without losing their place in the
  candidate list.

Reference: [GitHub PR conflict resolver](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/addressing-merge-conflicts/resolving-a-merge-conflict-on-github) — same two-phase shape applied to text merges

### Sensible defaults on phase 2 entry

When the user enters phase 2, every conflicting field gets a default
choice:

- **Identical values** → no picker rendered, "no conflict" tag.
- **One side empty, one side populated** → default to the populated
  side. (No reason to keep an empty value when a real one is available.)
- **Both populated and different** → default to **winner**. The
  surviving row stays unchanged unless the user explicitly elects
  the loser's value.

The empty/populated heuristic catches a common case: the loser was
a stub created during a quick "add customer" form fill, and the
winner has the full data filled in (or vice versa). Auto-defaulting
to the populated side means the user clicks Merge without thinking
in the easy case, and only intervenes when there's a genuine
disagreement.

### Why omit "WINNER" entries from the wire payload

The frontend builds a per-field choices map but only sends the
"LOSER" entries:

```ts
fieldOverrides: Object.fromEntries(
  Object.entries(this.overrides()).filter(([, choice]) => choice === 'LOSER')
),
```

The backend's contract is "any field NOT in the map keeps the
winner's value." So sending `{ phone: 'WINNER' }` is noise —
identical effect to omitting the key. Stripping the noise on the
client side keeps the wire payload small and the request body
self-documenting (every entry is a deliberate "use the loser's
value").

### Cache invalidation: every customer cache, both customer ids

The merge mutates the winner row, deletes the loser, and reassigns
orders/payments. That hits five customer caches plus the activity
cache:

```java
@CacheEvict(cacheNames = {
    "customers", "customersAll", "customersPaged",
    "customersMapPoints", "customersActiveCount", "customerActivity"
}, allEntries = true)
```

`allEntries = true` is the simple-but-correct approach: wipe the
whole cache rather than figure out which keys are stale. The
performance cost is one extra DB read per cache after the merge,
which at our scale is invisible.

### Live events for both customers

After a successful merge:

```java
events.convertAndSend("/topic/customers", new CustomerEvent(UPDATED, winnerId));
events.convertAndSend("/topic/customers", new CustomerEvent(DELETED, request.loserId()));
```

Two events, not one. The list page in another tab needs to know
the loser is gone (so it disappears from the table); the detail
page open on the winner needs to know the row changed. Both events
flow through the same `/topic/customers` broker; subscribers
discriminate on the `type` field if they care.

## The code, walked through

### Per-customer scoring loop

```java
for (Customer candidate : all) {
    double nameScore    = StringSimilarity.score(source.getCustomerName(), candidate.getCustomerName());
    double contactScore = StringSimilarity.score(
            contactKey(source.getContactFirstName(), source.getContactLastName()),
            contactKey(candidate.getContactFirstName(), candidate.getContactLastName()));
    double phoneScore   = StringSimilarity.score(
            digitsOnly(source.getPhone()),
            digitsOnly(candidate.getPhone()));

    double combined = WEIGHT_NAME * nameScore
                    + WEIGHT_CONTACT * contactScore
                    + WEIGHT_PHONE * phoneScore;

    if (combined < threshold) continue;

    List<String> matched = new ArrayList<>();
    if (nameScore    >= MATCHED_FIELD_THRESHOLD) matched.add("name");
    if (contactScore >= MATCHED_FIELD_THRESHOLD) matched.add("contact");
    if (phoneScore   >= MATCHED_FIELD_THRESHOLD) matched.add("phone");

    ranked.add(new CustomerMergeCandidateDTO(..., combined, matched));
}
```

Three field-level scores, one combined weighted total, one threshold
filter, one matched-fields list for explainability. Linear scan,
no indexes, no heuristics — sufficient for a few hundred rows.

### Merge transaction with manual `setAutoCommit(false)`

```java
try (Connection conn = dataSource.getConnection()) {
    conn.setAutoCommit(false);
    try {
        // 1. Field overrides on winner (uses repo.update, separate connection)
        if (!overrides.isEmpty()) repo.update(winner);

        // 2. Reassign orders.
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET customerNumber = ? WHERE customerNumber = ?")) {
            ps.setInt(1, winnerId);
            ps.setInt(2, request.loserId());
            ps.executeUpdate();
        }

        // 3. Reassign payments.
        // ...
        // 4. Delete the loser.
        // ...

        conn.commit();
    } catch (...) {
        conn.rollback();
        throw ex;
    } finally {
        conn.setAutoCommit(true);
    }
} catch (SQLException ex) { ... }
```

Same idiom as the `deleteAndDeepCascade` method in
`CustomerRepository`. The customer service tree doesn't yet have
the AOP `@MyTransactional` wiring (that infrastructure was built
for employees), so manual transaction handling is the correct
fallback. When/if customer gets the AOP wiring, the boilerplate
collapses to one annotation.

There's a subtle wart in step 1: `repo.update(winner)` opens its own
connection from the pool, so it doesn't run inside the same
transaction as steps 2–4. If steps 2–4 fail and roll back, the
winner's field overrides have already committed — the winner ends
up with the loser's values but the merge didn't complete. We
mitigate by checking `if (!overrides.isEmpty())` so the side effect
only runs when the user actually elected to copy fields, which
narrows the failure window. The proper fix is `@Transactional`
end-to-end so both updates share the connection — that's a real
improvement worth doing in a transaction-cleanup pass.

### Frontend — discriminator-style two-phase rendering

```ts
phase = signal<'PICK' | 'COMPARE'>('PICK');
```

A single `phase` signal drives the whole page. `@if (phase() === 'PICK')`
and `@if (phase() === 'COMPARE')` blocks branch the template.
Simpler than two separate routes; preserves the back-button
navigation (a sub-route would create a history entry per phase
which is more navigation than the user wants).

### `comparisonRows` computed driving the table

```ts
comparisonRows = computed(() => {
  const w = this.winner();
  const l = this.loserCustomer();
  if (!w || !l) return [];
  return CustomerMergeComponent.FIELDS.map(({ field, label }) => {
    const wVal = w[field];
    const lVal = l[field];
    const winnerValue = wVal == null || wVal === '' ? '' : String(wVal);
    const loserValue  = lVal == null || lVal === '' ? '' : String(lVal);
    const identical = winnerValue === loserValue;
    return { field: field as string, label, winnerValue, loserValue, identical };
  });
});
```

Static FIELDS list × dynamic winner/loser pair. Recomputes only
when the winner or loser signal changes. The template just renders
the rows; the "should this be a picker or a no-conflict tag" check
is one boolean (`row.identical`) rather than a per-cell template
calculation.

## How to test

### Find duplicates that exist in the seed data

The seed dataset doesn't have explicit duplicates, but you can
contrive one quickly:

1. Open Atelier graphique (#103). Note its name and contact.
2. **New Customer** → fill it in with similar values:
   - Name: `Atelier Graphic` (one letter off)
   - Contact: `Schmitt Carine` (same name, swapped order)
   - Phone: `40.32.2555` (identical)
   - Address: anything

3. Save. Open the new customer; click **Find duplicates**.
4. The candidate list should show #103 with a high match score
   (~85–95%) and matched-fields tags including "name", "contact",
   and "phone".

### Two-phase merge flow

1. From the candidate list, click **Choose →** on the high-scoring
   row. The compare table appears.
2. Identical fields show "no conflict" with greyed-out values.
3. For the differing rows, the radio defaults to the winner; flip
   one or two to "loser" to see the changes preview in your head.
4. Click **Merge customers**. After ~100ms you land back on the
   winner's detail page. The winner's name/contact reflect any
   "loser" overrides. The loser is gone (try `/customers/{loserId}`
   directly — should 404).

### Transactional rollback

1. Manually create a payment collision: in the DB, edit a payment
   so winner and loser have a payment with the same `checkNumber`.
2. Try to merge. Should fail with a duplicate-key error visible in
   the dialog.
3. Verify in the DB:
   - The loser still exists.
   - The orders are still on the loser.
   - The payments are still on the loser.
   - The winner's field overrides may or may not have committed —
     see the wart called out above. (Test with no overrides to keep
     the result clean.)

### Direct API tests

```bash
# Find candidates above 70% similarity
curl -s -H "Authorization: Bearer $JWT" \
  'http://localhost:9090/api/v1/customers/103/merge-candidates?threshold=0.7&limit=5' | jq

# Merge customer 555 into customer 103, taking the loser's phone
curl -s -X POST -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"loserId": 555, "fieldOverrides": {"phone": "LOSER"}}' \
  'http://localhost:9090/api/v1/customers/103/merge' | jq
```

### Live updates across tabs

1. Open two tabs on the customer list.
2. In tab A, run a merge (the loser was visible in the list before).
3. Tab B's list should refresh — both events fired (`UPDATED`
   for the winner if its row needs to reflect a name change,
   `DELETED` for the loser to remove its row).

## What you just learned

- **Levenshtein distance** as the canonical fuzzy-string metric,
  including the DP table layout and why O(n × m) is fine for
  customer-name-sized inputs.
- **String normalisation** as a precondition for meaningful fuzzy
  matching — case, punctuation, whitespace folding.
- **Weighted multi-field scoring** for combining several
  field-level scores into one rankable total.
- **Explainability tags** (`matchedFields`) that show the user
  *why* a candidate was ranked where it was, not just *how high*.
- **Transactional FK reassignment** via manual
  `setAutoCommit(false)` / `commit` / `rollback` — the same idiom
  used by the cascade-delete path.
- **Two-phase UI for compound decisions** — "pick a candidate"
  is a different question from "resolve per-field conflicts," and
  splitting them keeps each screen focused.
- **Sensible defaults** that quietly do the right thing in the
  empty/populated case so the user only has to intervene on real
  conflicts.
- **Wire-payload hygiene** — strip "no-op" entries client-side so
  the request body is self-documenting.
- **Hard-delete-on-merge as a deliberate intent statement** —
  one customer, not two with one hidden.

## Study materials

### Approximate string matching

- [Wikipedia — Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
- [Wikipedia — Approximate string matching](https://en.wikipedia.org/wiki/Approximate_string_matching)
- [PostgreSQL — pg_trgm trigram extension](https://www.postgresql.org/docs/current/pgtrgm.html)
- [Apache Commons Text — Similarity algorithms](https://commons.apache.org/proper/commons-text/userguide.html#Similarity)
- [Locality-Sensitive Hashing](https://en.wikipedia.org/wiki/Locality-sensitive_hashing) — sublinear approximate matching at scale

### Duplicate detection in practice

- [Microsoft — Duplicate detection in master data](https://learn.microsoft.com/en-us/biztalk/core/duplicate-detection-in-the-bizunit-pipeline) — production-flavoured discussion
- [Trifacta blog — Fuzzy matching in data prep](https://www.trifacta.com/) — the messier reality of customer-data duplicates
- [Customer Data Platform 101 — Identity resolution](https://www.cdp.com/cdp-institute/) — the wider domain

### Conflict-resolution UX

- [GitHub — Resolving a merge conflict](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/addressing-merge-conflicts/resolving-a-merge-conflict-on-github)
- [Beyond Compare](https://www.scootersoftware.com/) — desktop merge tool reference design
- [Material — MatRadioGroup](https://material.angular.io/components/radio/overview)

### Transactions & FK reassignment

- [MySQL InnoDB transaction model](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-model.html)
- [JDBC tutorial — Using transactions](https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html)
- [Spring docs — Programmatic transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)
