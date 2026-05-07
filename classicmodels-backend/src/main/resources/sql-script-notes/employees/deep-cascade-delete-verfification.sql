SET @emp = 1370;

-- Snapshot BEFORE running deep cascade.
SELECT GROUP_CONCAT(customerNumber) AS cust_snapshot
FROM customers WHERE salesRepEmployeeNumber = @emp;
-- e.g. '125,239,254'

-- Order numbers attached to those customers — these will all be wiped.
SELECT o.orderNumber
FROM orders o
         JOIN customers c ON c.customerNumber = o.customerNumber
WHERE c.salesRepEmployeeNumber = @emp;

-- Total orders / payments BEFORE.
SELECT COUNT(*) FROM orders;     -- e.g. 326
SELECT COUNT(*) FROM payments;   -- e.g. 273

SET @emp = 1370;
SET @cust_ids   = '125,239,254';   -- from snapshot
SET @orders_before   = 326;
SET @payments_before = 273;

SELECT 'A. employee gone' AS check_name,
       CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 0
                THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT 'B. snapshot customers gone',
       CASE WHEN (SELECT COUNT(*) FROM customers
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)) = 0
                THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'C. orders for those customers gone',
       CASE WHEN (SELECT COUNT(*) FROM orders
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)) = 0
                THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'D. payments for those customers gone',
       CASE WHEN (SELECT COUNT(*) FROM payments
                  WHERE FIND_IN_SET(customerNumber, @cust_ids)) = 0
                THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT 'E. orders count dropped by exactly the cascaded amount',
       -- This is informational; harder to assert without exact counts.
       CONCAT('orders_before=', @orders_before,
              ', orders_now=', (SELECT COUNT(*) FROM orders))
UNION ALL
SELECT 'F. payments count dropped',
       CONCAT('payments_before=', @payments_before,
              ', payments_now=', (SELECT COUNT(*) FROM payments))
UNION ALL
SELECT 'G. orderdetails for those orders gone (no orphans)',
       CASE WHEN (SELECT COUNT(*) FROM orderdetails od
                                           LEFT JOIN orders o ON o.orderNumber = od.orderNumber
                  WHERE o.orderNumber IS NULL) = 0
                THEN 'PASS' ELSE 'FAIL — orphaned orderdetails left behind' END;