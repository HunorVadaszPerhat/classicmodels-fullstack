package com.hunor.classicmodelsbackend.tx;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Bridges raw JDBC repository code with the transactional interceptor.
 *
 * <p>The puzzle: when {@link MyTransactionInterceptor} starts a transaction
 * it opens one connection, sets autoCommit=false on it, and runs the target
 * method. But the target method itself calls {@code dataSource.getConnection()}
 * — and a HikariCP pool will hand it a <em>different</em> connection. That
 * different connection has autoCommit=true, so the changes inside the
 * "transactional" method actually commit immediately, and the interceptor's
 * commit() does nothing.</p>
 *
 * <p>The fix: every transactional method must use the same connection the
 * interceptor opened. We do that by storing the active transactional
 * connection in a {@link ThreadLocal}, and routing repository code through
 * this class instead of through {@code dataSource.getConnection()} directly.</p>
 *
 * <pre>
 *   // Before (broken under our new interceptor):
 *   try (Connection c = dataSource.getConnection()) { ... }
 *
 *   // After (sees the transactional connection if one is bound):
 *   try (Connection c = MyDataSourceUtils.getConnection(dataSource)) { ... }
 * </pre>
 *
 * <p>This is the same pattern Spring's {@code DataSourceUtils} uses, just
 * stripped down to the essentials. Spring also supports propagation modes,
 * lazy connection acquisition, exception translation, etc. — none of
 * which we need to demonstrate the core idea.</p>
 *
 * <h3>The close() trick</h3>
 *
 * <p>The repository code uses try-with-resources, which calls {@code close()}
 * on the connection at the end of the method. If we returned the raw
 * transactional connection, that close() would return it to the pool —
 * mid-transaction! Instead, when a connection is bound, we return a tiny
 * dynamic proxy whose {@code close()} is a no-op. The interceptor (which
 * holds the real connection) decides when to actually close it.</p>
 */
public final class MyDataSourceUtils {

    private MyDataSourceUtils() { /* static-only */ }

    /**
     * The connection bound to the current thread, if any. Each thread has
     * its own copy — that's the whole point of {@link ThreadLocal}. The
     * Servlet container calls our request handler on a worker thread, so
     * "thread" maps roughly onto "request" in this codebase, which means
     * "transaction" maps onto "request" too.
     */
    private static final ThreadLocal<Connection> BOUND_CONNECTION = new ThreadLocal<>();

    /**
     * Get a connection for use in repository code.
     *
     * <p>If a transaction is currently active on this thread, returns a
     * close-suppressing proxy around the transactional connection. The
     * caller can use try-with-resources normally; close() does nothing,
     * and the interceptor will commit or roll back later.</p>
     *
     * <p>If no transaction is active, simply borrows a connection from
     * the pool. The caller's try-with-resources closes (returns to pool)
     * as expected — same behaviour as before this class existed.</p>
     */
    public static Connection getConnection(DataSource dataSource) throws SQLException {
        Connection bound = BOUND_CONNECTION.get();
        if (bound != null) {
            return wrapWithSuppressedClose(bound);
        }
        return dataSource.getConnection();
    }

    // -----------------------------------------------------------------
    //  Package-private: only the interceptor binds/unbinds connections.
    // -----------------------------------------------------------------

    /**
     * Bind a connection as the transactional connection for the current
     * thread. Called by the interceptor at transaction start. Throws if
     * something is already bound — nested transactions aren't supported
     * in this learning version (Spring would let you decide via
     * propagation modes).
     */
    public static void bind(Connection connection) {
        if (BOUND_CONNECTION.get() != null) {
            throw new IllegalStateException(
                    "A transactional connection is already bound to this thread. " +
                    "Nested @MyTransactional calls are not supported.");
        }
        BOUND_CONNECTION.set(connection);
    }

    /**
     * Remove the transactional binding for the current thread. Called by
     * the interceptor in the finally block. Crucial: forgetting this
     * leaks connections AND poisons subsequent requests on the same
     * worker thread (because {@code ThreadLocal} survives across
     * requests — Tomcat reuses worker threads).
     */
    public static void unbind() {
        BOUND_CONNECTION.remove();
    }

    /**
     * Read-only view used by {@link MyTransactionInterceptor} to detect
     * "am I already inside a transaction?" without touching the binding.
     */
    public static Connection currentBound() {
        return BOUND_CONNECTION.get();
    }

    // -----------------------------------------------------------------
    //  Close-suppressing wrapper.
    // -----------------------------------------------------------------
    /**
     * Wrap a connection so that {@code close()} becomes a no-op. Every
     * other method delegates straight through.
     *
     * <p>We use a JDK dynamic proxy — not because it's the only way (a
     * hand-written {@code Connection} delegate would also work), but
     * because the proxy mechanism is exactly what powers the rest of
     * this hand-rolled @MyTransactional infrastructure. Same hammer.</p>
     */
    private static Connection wrapWithSuppressedClose(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        // Pretend we closed — the interceptor will close
                        // for real after commit/rollback.
                        return null;
                    }
                    if ("isClosed".equals(method.getName())) {
                        // Minor lie: caller might think the connection is
                        // open when in fact the interceptor has already
                        // closed it. In practice repositories check this
                        // rarely; if it ever bites, expand the special-
                        // casing here.
                        return delegate.isClosed();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        // Unwrap so callers see the underlying SQLException
                        // and not a wrapping reflection exception.
                        throw e.getTargetException();
                    }
                });
    }
}
