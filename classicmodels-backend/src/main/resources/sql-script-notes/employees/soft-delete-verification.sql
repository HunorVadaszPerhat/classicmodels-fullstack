SET @emp = 1002;   -- Diane Murphy, or whoever you soft-deleted

SELECT
    'A. employee row still exists' AS check_name,
    CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp) = 1
             THEN 'PASS' ELSE 'FAIL' END AS result
UNION ALL
SELECT
    'B. employee.active = 0',
    CASE WHEN (SELECT active FROM employees WHERE employeeNumber = @emp) = 0
             THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT
    'C. employee.terminatedDate is set to today',
    CASE WHEN (SELECT terminatedDate FROM employees WHERE employeeNumber = @emp) = CURRENT_DATE
             THEN 'PASS' ELSE 'FAIL' END
UNION ALL
SELECT
    'D. customer refs preserved (not nulled, not deleted)',
    CASE WHEN (SELECT COUNT(*) FROM customers WHERE salesRepEmployeeNumber = @emp) > 0
             THEN 'PASS — refs preserved'
         ELSE 'INFO — employee had no customers to begin with' END
UNION ALL
SELECT
    'E. direct reports preserved (still reportsTo this employee)',
    CASE WHEN (SELECT COUNT(*) FROM employees WHERE reportsTo = @emp) > 0
             THEN 'PASS — refs preserved'
         ELSE 'INFO — employee had no direct reports' END
UNION ALL
SELECT
    'F. employee hidden from public list (active=1 filter)',
    CASE WHEN (SELECT COUNT(*) FROM employees WHERE employeeNumber = @emp AND active = 1) = 0
             THEN 'PASS' ELSE 'FAIL' END;