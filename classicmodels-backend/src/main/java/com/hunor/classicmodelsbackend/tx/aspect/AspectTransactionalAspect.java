package com.hunor.classicmodelsbackend.tx.aspect;

import com.hunor.classicmodelsbackend.tx.MyDataSourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Spring AOP equivalent of the hand-rolled @MyTransactional infrastructure.
 *
 * <p>Behaviour is the same: open a JDBC transaction, run the target,
 * commit on success, roll back on exception. The difference is
 * <em>everything around it</em>:</p>
 *
 * <ul>
 *   <li>No bean post-processor — Spring's
 *       {@code AnnotationAwareAspectJAutoProxyCreator} discovers this
 *       {@code @Aspect} bean on startup and registers its advices.</li>
 *   <li>No InvocationHandler — Spring builds the proxy and routes calls
 *       to the advice automatically.</li>
 *   <li>No chain handler — Spring orders multiple aspects via
 *       {@code @Order} and runs them in sequence.</li>
 *   <li>Pointcut expression — {@code "@annotation(...)"} is AspectJ's
 *       DSL for "match any join point whose target method has this
 *       annotation present." No manual reflection in our code.</li>
 * </ul>
 *
 * <p>We re-use {@link MyDataSourceUtils} for the connection binding so
 * any repository written against it (which is all of them, after the
 * earlier refactor) works under either advice without modification.
 * That's a real-world benefit: the connection-binding contract is
 * stable, only the advice framework changes.</p>
 *
 * <h3>Composition</h3>
 *
 * <p>This advice can coexist with the hand-rolled chain on the same
 * application: methods marked {@link AspectTransactional} go through
 * Spring AOP, methods marked {@code @MyTransactional} go through our
 * own chain. They don't interact, because Spring AOP and our
 * post-processor look for different annotations.</p>
 */
@Aspect
@Component
@Slf4j
public class AspectTransactionalAspect {

    private final DataSource dataSource;

    public AspectTransactionalAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Around-advice fired whenever a method annotated with
     * {@link AspectTransactional} is called via a Spring-managed bean.
     *
     * <p>The pointcut {@code @annotation(...)} reads "match join points
     * where the target method has the given annotation." Spring evaluates
     * this once per proxy creation and caches the result.</p>
     *
     * <p>Compare this body to {@code MyTransactionalAdvice.invoke(...)}.
     * The transaction management itself is identical. The differences:</p>
     * <ul>
     *   <li>{@code ProceedingJoinPoint pjp} replaces our {@code MyMethodInvocation}
     *       — same role, AspectJ's vocabulary.</li>
     *   <li>{@code pjp.proceed()} replaces our {@code invocation.proceed()}.</li>
     *   <li>{@code pjp.getSignature()} gives reflective access to the
     *       method, target, etc., for logging.</li>
     * </ul>
     */
    @Around("@annotation(com.hunor.classicmodelsbackend.tx.aspect.AspectTransactional)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {

        if (MyDataSourceUtils.currentBound() != null) {
            log.debug("[aspect] joining existing transaction for {}",
                    pjp.getSignature().toShortString());
            return pjp.proceed();
        }

        log.debug("[aspect] starting transaction for {}",
                pjp.getSignature().toShortString());

        Connection conn = dataSource.getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            MyDataSourceUtils.bind(conn);

            Object result = pjp.proceed();

            conn.commit();
            log.debug("[aspect] committed transaction for {}",
                    pjp.getSignature().toShortString());
            return result;

        } catch (Throwable t) {
            log.warn("[aspect] rolling back transaction for {} due to {}: {}",
                    pjp.getSignature().toShortString(),
                    t.getClass().getSimpleName(), t.getMessage());
            try { conn.rollback(); } catch (Throwable rb) { t.addSuppressed(rb); }
            throw t;
        } finally {
            MyDataSourceUtils.unbind();
            try { conn.setAutoCommit(previousAutoCommit); } catch (Throwable ignore) { /* */ }
            try { conn.close(); }                          catch (Throwable ignore) { /* */ }
        }
    }
}
