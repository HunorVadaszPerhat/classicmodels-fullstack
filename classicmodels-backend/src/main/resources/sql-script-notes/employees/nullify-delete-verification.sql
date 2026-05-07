SET @emp = 1370;   -- whoever you nullified

-- BEFORE delete: capture the customers and direct reports that pointed at them.
-- Run this BEFORE pressing Delete, save the numbers, and plug them into @cust_ids
-- and @rep_ids below. Hardcoding is fine — these are one-shot verification queries.
-- SELECT customerNumber FROM customers WHERE salesRepEmployeeNumber = @emp;
-- SELECT employeeNumber FROM employees WHERE reportsTo = @emp;

-- Example snapshot result for emp 1370:
-- customers: 121, 124, 161
-- reports:   (none — Hernandez has no reports)
SET @cust_ids = '121,124,161';

SELECT
    'A. employee row is gone' AS check_name,
    CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 0
             THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT
    'B. no customer still references this employee',
    CASE WHEN (SELECT COUNT(*) FROM customers WHERE salesRepEmployeeNumber = @emp) = 0
             THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT
    'C. snapshot customers STILL EXIST (only their FK was nulled)',
    CASE WHEN (SELECT COUNT(*) FROM customers
               WHERE FIND_IN_SET(customerNumber, @cust_ids))
        = (LENGTH(@cust_ids) - LENGTH(REPLACE(@cust_ids, ',', '')) + 1)
             THEN 'PASS — all snapshot customers preserved'
         ELSE 'FAIL — some customers are missing (was this a CASCADE?)' END
UNION ALL
SELECT
    'D. snapshot customers all have salesRepEmployeeNumber = NULL',
    CASE WHEN (SELECT COUNT(*) FROM customers
               WHERE FIND_IN_SET(customerNumber, @cust_ids)
                 AND salesRepEmployeeNumber IS NOT NULL) = 0
             THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT
    'E. no employee still reports to this employee',
    CASE WHEN (SELECT COUNT(*) FROM employees WHERE reportsTo = @emp) = 0
             THEN 'PASS' ELSE 'FAIL' END;