Question 1 — Answer: B
Spring Data parses derived query method names and validates property references at application startup when the repository proxy is created. If customerName, status, or total don't exist as fields on Order, Spring throws PropertyReferenceException during context initialisation — before any request is ever made.

A — Wrong. Validation is not deferred to first call. Spring fails fast at startup.
C — Wrong. Spring Data uses runtime proxy generation, not compile-time annotation processing. A typo compiles cleanly.
D — Wrong. Validation is not lazy and doesn't depend on results being returned.


Question 2 — Answer: B
REQUIRES_NEW suspends the outer transaction and starts a completely independent new transaction for the repository method. That transaction commits on its own when the repository method returns successfully — before the outer service method throws its RuntimeException. The outer transaction's rollback has no effect on an already-committed independent transaction.

A — Wrong. The outer transaction rolling back cannot reach inside a committed independent transaction.
C — Wrong. The RuntimeException triggers the outer transaction's rollback, not the inner one which already committed.
D — Wrong. REQUIRES_NEW is a valid and supported propagation behaviour. No exception is thrown for using it.


Question 3 — Answers: B and C

B ✅ — ResultSetExtractor hands you the entire ResultSet and you call rs.next() yourself to iterate. You control the loop.
C ✅ — RowMapper is called once per row by JdbcTemplate and returns one object per row. JdbcTemplate handles the iteration and collects results into a List.
A — Wrong. This describes ResultSetExtractor, not RowMapper. With RowMapper you never call rs.next().
D — Wrong. This describes RowMapper, not ResultSetExtractor. ResultSetExtractor returns one result for the entire ResultSet — not one object per row.
E — Wrong. Only ResultSetExtractor requires manual rs.next(). RowMapper and RowCallbackHandler both have iteration handled by JdbcTemplate.


Question 4 — Answer: B
This is the rollbackOnly trap from 2.2.2. The inner method has REQUIRED propagation — it joins the outer transaction rather than creating a new one. When it throws a RuntimeException, Spring marks the shared transaction as rollbackOnly. The outer method catches the exception, which prevents it from propagating — but it's too late. The transaction is already poisoned. When the outer method finishes and Spring attempts to commit, it sees the rollbackOnly flag and throws UnexpectedRollbackException instead of committing.

A — Wrong. Catching the exception does not undo the rollbackOnly mark on the shared transaction.
C — Wrong. Because both methods share one transaction (via REQUIRED), there is no way to partially commit. It's all or nothing.
D — Wrong. Spring doesn't throw at the point the inner method throws — that exception propagates normally. The problem surfaces at commit time.


Question 5 — Answers: B, C, and F

B ✅ — @DataJpaTest replaces any configured DataSource with an embedded H2 database by default. Use @AutoConfigureTestDatabase(replace = Replace.NONE) to override.
C ✅ — @DataJpaTest is @Transactional by default. Each test rolls back automatically without needing @Transactional on individual test methods.
F ✅ — @DataJpaTest is a slice test — it loads only the JPA layer (entities, repositories, JPA infrastructure). @Service and @Controller beans are not loaded.
A — Wrong. Loading the full context is what @SpringBootTest does. @DataJpaTest is deliberately narrow.
D — Wrong in the sense that it's a distractor — @Sql scripts do run within the test transaction by default, making D technically true. However the question asks for three correct answers and B, C, F are the cleaner, more directly testable facts about @DataJpaTest specifically. If you picked D as one of your three, that's defensible — it is true — but A and E are clearly wrong, making B, C, F the intended set.
E — Wrong. @DataJpaTest includes @Transactional at the class level already. You don't need to add it yourself.