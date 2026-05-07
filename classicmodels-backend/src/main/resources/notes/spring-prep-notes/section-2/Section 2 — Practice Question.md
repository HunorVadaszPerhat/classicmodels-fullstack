Section 2 — Practice Questions

Question 1 (Single answer)
A developer writes the following repository method:
javaList<Order> findByCustomerNameAndStatusOrderByTotalDesc(String name, String status);
At what point does Spring Data validate that customerName, status, and total are valid properties on the Order entity?
A) At the time the method is first called at runtime
B) At application startup when the repository is initialised
C) At compile time via annotation processing
D) Only when the query returns results — validation is lazy

Question 2 (Single answer)
A service method is annotated with @Transactional. It calls a repository method annotated with @Transactional(propagation = Propagation.REQUIRES_NEW). The service method then throws a RuntimeException. What happens to the data written by the repository method?
A) It rolls back because the outer service transaction rolls back
B) It commits because REQUIRES_NEW runs in an independent transaction
C) It rolls back because any RuntimeException triggers a full rollback
D) Spring throws IllegalTransactionStateException because nested transactions are not allowed

Question 3 (Multiple answer — Choose 2)
Which two statements correctly describe the difference between RowMapper and ResultSetExtractor?
A) RowMapper gives you control over iterating the ResultSet with rs.next()
B) ResultSetExtractor gives you control over iterating the ResultSet with rs.next()
C) RowMapper is called once per row and returns one object per row
D) ResultSetExtractor is called once per row and returns one object per row
E) Both interfaces require you to call rs.next() manually

Question 4 (Single answer)
A @Transactional service method calls another @Transactional(propagation = Propagation.REQUIRED) method in the same class. The inner method throws a RuntimeException which is caught by the outer method. The outer method then attempts to commit normally. What happens?
A) The transaction commits successfully because the exception was caught
B) Spring throws UnexpectedRollbackException because the transaction was marked rollbackOnly
C) The inner method's work rolls back but the outer method's work commits
D) Spring throws IllegalTransactionStateException at the point the inner method throws

Question 5 (Multiple answer — Choose 3)
Which three of the following statements about @DataJpaTest are correct?
A) It loads the full Spring application context including @Service beans
B) It uses an in-memory H2 database by default, replacing any configured DataSource
C) Each test method is wrapped in a transaction that rolls back automatically
D) @Sql scripts run within the same test transaction by default
E) It requires @Transactional to be explicitly added to each test method
F) It loads only the JPA layer — entities, repositories, and JPA configuration