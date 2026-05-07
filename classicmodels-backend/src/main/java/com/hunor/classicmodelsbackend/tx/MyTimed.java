package com.hunor.classicmodelsbackend.tx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a method to have its execution time measured and logged.
 *
 * <p>Behaviour-free marker. Like {@link MyTransactional}, the actual
 * work happens in a {@link MyMethodInterceptor} ({@link MyTimedAdvice})
 * that the {@link MyAroundBeanPostProcessor} wires into the proxy
 * chain when it sees this annotation on a method.</p>
 *
 * <h3>Composes with @MyTransactional</h3>
 *
 * <p>If a method has both {@code @MyTimed} and {@code @MyTransactional},
 * the timing measurement encloses the transaction — meaning the elapsed
 * time you log includes the {@code commit()} or {@code rollback()}
 * round-trip, not just the body. That ordering is fixed in the
 * post-processor when it builds the chain.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyTimed {
}
