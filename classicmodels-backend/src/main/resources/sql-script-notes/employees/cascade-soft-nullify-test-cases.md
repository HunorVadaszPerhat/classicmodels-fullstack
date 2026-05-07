Five scenarios — three core ones (one per strategy) plus two important edge cases (cascade rejection and the empty-dependents path). Each is self-contained with setup, action, verification, and cleanup so you can re-run them.

I'll assume your local DB is the seeded `classicmodels` plus the V2 migration applied. Set this once at the top of every test:

```sql
SET @schema = 'classicmodels';
SET @emp    = 0;          -- the employee under test (set per scenario)
SET @cust_ids = '';       -- snapshot
SET @rep_ids  = '';       -- snapshot
```

## Scenario 1 — Soft delete (the safe default)

**Goal.** Verify SOFT preserves all relationships and just hides the employee from default views.

**Why this employee.** Pick anyone who has at least one customer AND at least one direct report — that way you exercise both FKs in one run. Diane Murphy (1002) is perfect: 2 customers, 2 direct reports per your earlier dialog screenshot.

**Setup snapshot.** Run before any delete so you can confirm "preserved" later.

```sql
SET @emp = 1002;

-- Capture the customer numbers that point at this employee.
SELECT GROUP_CONCAT(customerNumber) AS cust_snapshot
FROM customers WHERE salesRepEmployeeNumber = @emp;

-- Capture the employee numbers that report to this employee.
SELECT GROUP_CONCAT(employeeNumber) AS rep_snapshot
FROM employees WHERE reportsTo = @emp;
```

Note both lists down — for example `cust_snapshot = '124,167'`, `rep_snapshot = '1056,1076'`.

**Action.** In the UI, click Delete on Diane → dialog shows her name and the two reference tables → leave SOFT selected (default) → click Delete.

**Expected outcome.** The employees list refreshes and Diane is gone from it. No error toast.

**Verification.**

```sql
SET @emp = 1002;
SET @cust_ids = '124,167';   -- from snapshot
SET @rep_ids  = '1056,1076'; -- from snapshot

SELECT 'A. row exists, marked terminated' AS check_name,
       CASE WHEN (SELECT COUNT(*) FROM employees
                  WHERE employeeNumber = @emp
                    AND active = 0
                    AND terminatedDate = CURRENT_DATE) = 1
            THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT 'B. employee hidden from active list',
       CASE WHEN (SELECT COUNT(*) FROM employees
                  WHERE employeeNumber = @emp AND active = 1) = 0
            THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'C. snapshot customers still point at employee (UNCHANGED)',
       CASE WHEN (SELECT COUNT(*) FROM customers
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)
                    AND salesRepEmployeeNumber = @emp) =
                 (LENGTH(@cust_ids) - LENGTH(REPLACE(@cust_ids, ',', '')) + 1)
            THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'D. snapshot reports still report to employee (UNCHANGED)',
       CASE WHEN (SELECT COUNT(*) FROM employees
                  WHERE FIND_IN_SET(employeeNumber, @rep_ids)
                    AND reportsTo = @emp) =
                 (LENGTH(@rep_ids) - LENGTH(REPLACE(@rep_ids, ',', '')) + 1)
            THEN 'PASS' ELSE 'FAIL' END;
```

All four should be PASS. Also hit `GET /api/v1/employees/1002` directly in the browser — should return Diane with `active: false` and a `terminatedDate`. The detail endpoint deliberately doesn't filter on active.

**Cleanup (reversal).**

```sql
UPDATE employees
SET active = 1, terminatedDate = NULL
WHERE employeeNumber = 1002;
```

Reactivation is an UPDATE — that's the whole point of soft delete. She reappears in the list immediately.

## Scenario 2 — Nullify (hard delete with safe detachment)

**Goal.** Verify NULLIFY removes the employee row and severs (but does not destroy) child rows.

**Why a different employee.** NULLIFY is destructive. Once you delete and the auto-increment moves on, you can't easily re-test on the same `employeeNumber`. Pick a sales rep you don't mind losing — Gerard Hernandez (1370) is a typical sales rep with customers and no direct reports, so it isolates the customer FK.

**Setup snapshot.**

```sql
SET @emp = 1370;

-- Customer numbers that will be detached.
SELECT GROUP_CONCAT(customerNumber) AS cust_snapshot
FROM customers WHERE salesRepEmployeeNumber = @emp;
-- e.g. '125,239,254'

-- Confirm no direct reports (sales reps usually don't have any).
SELECT COUNT(*) AS reps FROM employees WHERE reportsTo = @emp;
-- expect 0 for Hernandez
```

**Action.** Delete Hernandez in the UI → dialog opens → pick **NULLIFY** → click Delete.

**Expected outcome.** The list refreshes; Hernandez is gone. The customers table still has 3 rows (or whatever the snapshot count was) but their `salesRepEmployeeNumber` is now NULL.

**Verification.**

```sql
SET @emp = 1370;
SET @cust_ids = '125,239,254';   -- from snapshot

SELECT 'A. employee row is gone' AS check_name,
       CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 0
            THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT 'B. snapshot customers STILL EXIST',
       CASE WHEN (SELECT COUNT(*) FROM customers
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)) =
                 (LENGTH(@cust_ids) - LENGTH(REPLACE(@cust_ids, ',', '')) + 1)
            THEN 'PASS — preserved'
            ELSE 'FAIL — customers were deleted, did you pick CASCADE by mistake?' END
UNION ALL
SELECT 'C. snapshot customers all have salesRepEmployeeNumber = NULL',
       CASE WHEN (SELECT COUNT(*) FROM customers
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)
                    AND salesRepEmployeeNumber IS NOT NULL) = 0
            THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'D. customers still have their orders (financial history intact)',
       CASE WHEN (SELECT COUNT(*) FROM orders
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)) > 0
            THEN 'PASS' ELSE 'INFO — these customers had no orders' END;
```

D is worth running because if it ever flipped to a missing-orders state you'd know something nullified more than `salesRepEmployeeNumber`.

**Cleanup.** This is destructive — there is no undo. To re-run the scenario you have to either:

- Re-insert Hernandez and reattach the orphan customers:

```sql
INSERT INTO employees (employeeNumber, lastName, firstName, extension, email,
                       officeCode, reportsTo, jobTitle)
VALUES (1370, 'Hernandez', 'Gerard', 'x2028',
        'ghernande@classicmodelcars.com', '4', 1102, 'Sales Rep');

UPDATE customers SET salesRepEmployeeNumber = 1370
WHERE customerNumber IN (125, 239, 254);
```

Note we explicitly set `employeeNumber = 1370` to keep FKs predictable. This works because the column is the PK and there's no auto-increment requirement when you supply the value, but in production you would not normally do this.

- Or accept the loss and pick a different employee for the next run.

## Scenario 3a — Cascade success (the rare clean case)

**Goal.** Verify CASCADE deletes both the employee and their customers, leaves direct reports nulled, and never touches orders/payments.

**Why setup is required.** Every seeded customer has orders, so cascading any seeded employee will be rejected by the FK on `orders.customerNumber`. To see the success path you need a clean test employee with a clean test customer.

**Setup.**

```sql
-- 1. Create a throwaway employee (officeCode 1 = San Francisco, exists in seed).
INSERT INTO employees (lastName, firstName, extension, email, officeCode, jobTitle, active)
VALUES ('TestRep', 'Casey', 'x9999', 'casey@test.local', '1', 'Sales Rep', 1);
SET @emp = LAST_INSERT_ID();

-- 2. Create a throwaway customer assigned to them, with no orders/payments.
INSERT INTO customers (customerNumber, customerName, contactLastName, contactFirstName,
                       phone, addressLine1, city, country,
                       salesRepEmployeeNumber, creditLimit)
VALUES (9001, 'TEST Customer Alpha', 'Alpha', 'Test', '555-0001',
        '1 Test St', 'Testville', 'US', @emp, 0);

-- 3. Snapshot the totals you'll later assert haven't moved.
SELECT @emp AS test_emp;
SELECT COUNT(*) AS orders_before   FROM orders;     -- e.g. 326
SELECT COUNT(*) AS payments_before FROM payments;   -- e.g. 273
```

Write down the printed `test_emp` value (it'll be ~1703 depending on prior inserts), the orders count, and the payments count.

**Action.** In the UI go to the employees list → find "TestRep, Casey" → click Delete → pick **CASCADE** → type `TestRep` to enable the button → click Delete.

**Expected outcome.** Employee disappears. Customer 9001 disappears. Orders/payments untouched.

**Verification.**

```sql
SET @emp = 1703;             -- the value from setup
SET @cust_ids = '9001';
SET @orders_before   = 326;
SET @payments_before = 273;

SELECT 'A. employee gone' AS check_name,
       CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 0
            THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT 'B. snapshot customers GONE (cascade actually removed them)',
       CASE WHEN (SELECT COUNT(*) FROM customers
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)) = 0
            THEN 'PASS' ELSE 'FAIL — customers still exist (was this NULLIFY?)' END
UNION ALL
SELECT 'C. orders count unchanged',
       CASE WHEN (SELECT COUNT(*) FROM orders) = @orders_before
            THEN 'PASS' ELSE 'CRITICAL FAIL — orders were modified' END
UNION ALL
SELECT 'D. payments count unchanged',
       CASE WHEN (SELECT COUNT(*) FROM payments) = @payments_before
            THEN 'PASS' ELSE 'CRITICAL FAIL — payments were modified' END;
```

C and D are the safety-rail checks. If either ever fails you have a bug worth investigating immediately.

**Cleanup.** Nothing to do — the test data is gone, which is the whole point of the test.

## Scenario 3b — Cascade rejection (the safety net)

**Goal.** Verify CASCADE refuses to run when a customer has orders/payments, and that the failed transaction rolls back cleanly.

**Why this matters.** This is the scenario that protects financial history. If the rollback ever broke, the cascade would silently start destroying orders. So testing the rejection path is *more* important than testing success.

**Setup snapshot.** Pick any seeded sales rep with customers — Gerard Bondur (1102) and Anthony Bow (1143) work, but Hernandez (1370) is the simplest.

```sql
SET @emp = 1370;

-- Capture everything we expect to remain identical after the failed cascade.
SELECT @emp AS test_emp;
SELECT COUNT(*) AS employees_before FROM employees;
SELECT COUNT(*) AS customers_before FROM customers;
SELECT COUNT(*) AS orders_before    FROM orders;
SELECT COUNT(*) AS payments_before  FROM payments;

-- Also remember the customer numbers; they must all still exist after.
SELECT GROUP_CONCAT(customerNumber) AS cust_snapshot
FROM customers WHERE salesRepEmployeeNumber = @emp;
```

**Action.** Delete Hernandez → pick **CASCADE** → type `Hernandez` → click Delete.

**Expected outcome.** The dialog closes. An error appears in the list view ("Delete (CASCADE) failed: …Cannot delete or update a parent row…"). Hernandez is **still in the list** — nothing changed. This is the rollback in action.

**Verification — the assertion is "nothing moved".**

```sql
SET @emp = 1370;
SET @cust_ids = '125,239,254';     -- from snapshot
SET @employees_before = 23;
SET @customers_before = 122;
SET @orders_before    = 326;
SET @payments_before  = 273;

SELECT 'A. employee row STILL EXISTS' AS check_name,
       CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 1
            THEN 'PASS' ELSE 'FAIL — rollback broke, this is a serious bug' END AS result
UNION ALL
SELECT 'B. employees count unchanged',
       CASE WHEN (SELECT COUNT(*) FROM employees) = @employees_before
            THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'C. customers count unchanged',
       CASE WHEN (SELECT COUNT(*) FROM customers) = @customers_before
            THEN 'PASS' ELSE 'FAIL — partial cascade leaked through rollback' END
UNION ALL
SELECT 'D. snapshot customers STILL EXIST and still point at employee',
       CASE WHEN (SELECT COUNT(*) FROM customers
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)
                    AND salesRepEmployeeNumber = @emp) =
                 (LENGTH(@cust_ids) - LENGTH(REPLACE(@cust_ids, ',', '')) + 1)
            THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'E. orders count unchanged',
       CASE WHEN (SELECT COUNT(*) FROM orders) = @orders_before
            THEN 'PASS' ELSE 'CRITICAL — orders modified despite rejection' END
UNION ALL
SELECT 'F. payments count unchanged',
       CASE WHEN (SELECT COUNT(*) FROM payments) = @payments_before
            THEN 'PASS' ELSE 'CRITICAL' END;
```

Every line should be PASS. The error message in the UI is the *only* visible effect of this test.

**Cleanup.** Nothing — by design.

## Scenario 4 — No dependents (the empty path)

**Goal.** Verify the dialog renders sensibly for an employee no one references, and that all three strategies still complete without error.

**Setup.**

```sql
INSERT INTO employees (lastName, firstName, extension, email, officeCode, jobTitle, active)
VALUES ('Lonely', 'Lee', 'x0000', 'lee@test.local', '1', 'Intern', 1);
SET @emp = LAST_INSERT_ID();
SELECT @emp;
```

This employee has no customers and no direct reports.

**Action.** Click Delete in the UI on "Lonely, Lee".

**Expected outcome.** The dialog opens with "No other tables reference this employee. Any delete strategy is safe." All three radio options are still available; SOFT is selected by default. Whatever you pick, the delete completes cleanly.

**Verification.** Pick each strategy in three separate runs (re-creating Lee each time):

| Strategy | After delete |
|----------|--------------|
| SOFT     | Lee row exists with `active=0`, `terminatedDate=today` |
| NULLIFY  | Lee row gone; nothing else changed (no FKs to nullify) |
| CASCADE  | Lee row gone; nothing else changed (no customers to cascade) |

The cascade variant is interesting: with no customers to delete, the `DELETE FROM customers WHERE salesRepEmployeeNumber = ?` affects 0 rows, the `UPDATE employees SET reportsTo = NULL` affects 0 rows, and only the final DELETE has any effect. The cascade succeeds because there's nothing to fail on.

## Suggested run order

For a complete sweep in one sitting:

1. Scenario 4 (sanity-check the empty path with all three strategies).
2. Scenario 1 (soft on Diane), then reverse her.
3. Scenario 3b (cascade rejection on Hernandez) — *no cleanup needed*.
4. Scenario 2 (nullify on Hernandez) — destructive, run after 3b.
5. Scenario 3a (cascade success with the throwaway pair).

This order leaves your DB closer to its starting state, and each scenario builds confidence that the next one's outcome can't be confused with leftover state from the previous one.

## A useful debugging tip

Whenever a verification line says FAIL, run the row-level FK query from earlier (`UNION ALL` over `customers` and `employees`) to see exactly which rows survived or moved unexpectedly. The PASS/FAIL summary tells you *that* something is off; the row-level query tells you *what*.