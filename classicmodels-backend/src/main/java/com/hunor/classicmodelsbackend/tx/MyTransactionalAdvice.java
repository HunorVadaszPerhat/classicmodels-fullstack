package com.hunor.classicmodelsbackend.tx;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Around-advice that opens a JDBC transaction, runs the rest of the
 * chain inside it, and either commits on success or rolls back on
 * exception.
 *
 * <p>Identical behaviour to the previous {@code MyTransactionInterceptor}
 * but expressed as a {@link MyMethodInterceptor} so it can compose with
 * other advices ({@code @MyTimed}, future {@code @MyRetry}, …) on the
 * same method without us having to nest proxies. The proxy creator
 * wires this advice into the chain whenever a method is annotated with
 * {@link MyTransactional}.</p>
 *
 * <h3>Compose order</h3>
 *
 * <p>This advice expects to be inside any "outer" advice like
 * {@code MyTimedAdvice}: the timer measures the full transaction
 * (including commit/rollback time), not just the body. The
 * {@link MyAroundBeanPostProcessor} controls that order when it
 * builds the chain.</p>
 */
@Slf4j
public class MyTransactionalAdvice implements MyMethodInterceptor {

    private final DataSource dataSource;

    public MyTransactionalAdvice(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Object invoke(MyMethodInvocation invocation) throws Throwable {
        // Already inside a transaction on this thread? Don't open a new
        // one — let the outer transactional caller own commit/rollback.
        // This is the equivalent of Spring's REQUIRED propagation.
        if (MyDataSourceUtils.currentBound() != null) {
            log.debug("Joining existing transaction for {}.{}",
                    invocation.getTargetClass().getSimpleName(),
                    invocation.getMethod().getName());
            return invocation.proceed();
        }

        log.debug("Starting transaction for {}.{}",
                invocation.getTargetClass().getSimpleName(),
                invocation.getMethod().getName());

        Connection conn = dataSource.getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            MyDataSourceUtils.bind(conn);

            Object result = invocation.proceed();

            conn.commit();
            log.debug("Committed transaction for {}.{}",
                    invocation.getTargetClass().getSimpleName(),
                    invocation.getMethod().getName());
            return result;

        } catch (Throwable t) {
            log.warn("Rolling back transaction for {}.{} due to {}: {}",
                    invocation.getTargetClass().getSimpleName(),
                    invocation.getMethod().getName(),
                    t.getClass().getSimpleName(), t.getMessage());
            try {
                conn.rollback();
            } catch (Throwable rollbackError) {
                t.addSuppressed(rollbackError);
            }
            throw t;
        } finally {
            // Order matters: unbind first, then restore autoCommit, then close.
            MyDataSourceUtils.unbind();
            try { conn.setAutoCommit(previousAutoCommit); } catch (Throwable ignore) { /* */ }
            try { conn.close(); }                          catch (Throwable ignore) { /* */ }
        }
    }
}
