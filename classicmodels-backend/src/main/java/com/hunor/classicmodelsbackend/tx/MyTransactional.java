package com.hunor.classicmodelsbackend.tx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a method as needing transactional execution.
 *
 * <p>This is a <em>marker</em> annotation: it carries no behaviour by itself.
 * The runtime behaviour comes from {@link MyTransactionInterceptor} (which
 * opens a connection, sets autoCommit=false, commits or rolls back) and
 * {@link MyTransactionalBeanPostProcessor} (which wraps every bean
 * containing such a method in a JDK dynamic proxy). Annotation alone is
 * inert — it's the proxy that does the work.</p>
 *
 * <h3>Why each meta-annotation matters</h3>
 *
 * <p>{@code @Target(ElementType.METHOD)}: this annotation can only be
 * applied to methods. Applying it to a class or field is a compile error.
 * Spring's real {@code @Transactional} accepts {@code TYPE} too (so you
 * can mark a whole class transactional), but for a learning version
 * limiting to methods keeps the lookup logic obvious.</p>
 *
 * <p>{@code @Retention(RetentionPolicy.RUNTIME)}: the annotation must
 * survive into the running JVM so reflection (in the post-processor and
 * the interceptor) can read it. {@code SOURCE} would be discarded by the
 * compiler; {@code CLASS} would be in the .class file but unavailable at
 * runtime. Only {@code RUNTIME} is reflection-visible.</p>
 *
 * <p>No {@code @Inherited}: subclasses do <em>not</em> automatically
 * inherit this annotation from their parent. That matches what Spring
 * does — and avoids a class of confusing bugs where annotating a
 * superclass silently makes every subclass transactional.</p>
 *
 * <h3>Limitations of this hand-rolled version</h3>
 * <ul>
 *   <li>No propagation modes (REQUIRED, REQUIRES_NEW, NESTED, …). We
 *       behave like REQUIRED: if a transaction already exists on this
 *       thread, we use it; otherwise we start a new one.</li>
 *   <li>No isolation levels (we accept whatever the connection's default is).</li>
 *   <li>No rollback rules. We roll back on <em>any</em> Throwable, where
 *       Spring's default is "checked exceptions don't roll back."
 *       For the pedagogical version, "any throwable rolls back" is
 *       both simpler and arguably saner.</li>
 *   <li>Only works on beans that implement at least one interface (JDK
 *       dynamic proxies require an interface — that's why we extracted
 *       the EmployeeRepository interface).</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyTransactional {
}
