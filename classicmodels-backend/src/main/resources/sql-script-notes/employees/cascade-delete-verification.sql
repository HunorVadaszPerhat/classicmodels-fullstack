SET @emp = 9999;   -- a brand-new employee you created for testing
                   -- (cascade will fail on any seeded employee because their
                   -- customers all have orders/payments)

-- BEFORE delete: snapshot
-- SELECT customerNumber FROM customers WHERE salesRepEmployeeNumber = @emp;
-- SELECT employeeNumber FROM employees WHERE reportsTo = @emp;
SET @cust_ids = '500,501';   -- example test customers
SET @rep_ids  = '';          -- empty if there were no direct reports

-- Also capture totals for orders & payments so we can prove they were untouched.
-- Run BEFORE delete and remember the numbers:
-- SELECT COUNT(*) FROM orders;    -- e.g. 326
-- SELECT COUNT(*) FROM payments;  -- e.g. 273
SET @orders_before   = 326;
SET @payments_before = 273;

SELECT
    'A. employee row is gone' AS check_name,
    CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 0
             THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT
    'B. snapshot customers are GONE (cascade actually removed them)',
    CASE WHEN @cust_ids = '' OR
              (SELECT COUNT(*) FROM customers WHERE FIND_IN_SET(customerNumber, @cust_ids)) = 0
             THEN 'PASS'
         ELSE 'FAIL — customers still exist (was this a NULLIFY?)' END
UNION ALL
SELECT
    'C. direct reports STILL EXIST (org chart not cascaded)',
    CASE WHEN @rep_ids = '' OR
              (SELECT COUNT(*) FROM employees WHERE FIND_IN_SET(employeeNumber, @rep_ids))
                  = (LENGTH(@rep_ids) - LENGTH(REPLACE(@rep_ids, ',', '')) + 1)
             THEN 'PASS — reports preserved'
         ELSE 'FAIL — reports were deleted (would be a serious bug)' END
UNION ALL
SELECT
    'D. direct reports have reportsTo = NULL',
    CASE WHEN @rep_ids = '' OR
              (SELECT COUNT(*) FROM employees
               WHERE FIND_IN_SET(employeeNumber, @rep_ids)
                 AND reportsTo IS NOT NULL) = 0
             THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT
    'E. orders table untouched (financial history preserved)',
    CASE WHEN (SELECT COUNT(*) FROM orders) = @orders_before
             THEN 'PASS' ELSE 'FAIL — orders were modified, this is a serious bug' END
UNION ALL
SELECT
    'F. payments table untouched',
    CASE WHEN (SELECT COUNT(*) FROM payments) = @payments_before
             THEN 'PASS' ELSE 'FAIL' END;