package com.hunor.classicmodelsbackend.tx;

/**
 * The "around advice" interface.
 *
 * <p>An interceptor wraps a method call: it can observe the call,
 * modify arguments before delegating, run code on entry/exit, decide
 * not to delegate at all, etc. Crucially, it doesn't call the target
 * method itself — instead it calls {@link MyMethodInvocation#proceed()},
 * which advances to the next interceptor in the chain (or the actual
 * target if this is the last).</p>
 *
 * <p>This is the same shape as the AOP Alliance
 * {@code org.aopalliance.intercept.MethodInterceptor}, which is the
 * interface Spring's transaction, security, caching, and async
 * infrastructure all build on. By copying the abstraction we get the
 * same composability: add a new annotation and a new
 * {@code MyMethodInterceptor} implementation, and it slots into the
 * chain alongside everything else.</p>
 *
 * <h3>Design contract</h3>
 *
 * <p>Implementations <em>must</em> call {@code invocation.proceed()}
 * exactly once on the success path. Calling it zero times turns the
 * interceptor into a "short-circuit" advice (think: caching — return
 * the cached value, don't hit the target). Calling it more than once
 * is reserved for retry/replay scenarios. Throwing without calling
 * proceed() suppresses the target entirely.</p>
 */
@FunctionalInterface
public interface MyMethodInterceptor {

    /**
     * Run this advice. Implementations typically:
     * <ol>
     *   <li>do something on entry,</li>
     *   <li>call {@code invocation.proceed()} to delegate to the next
     *       interceptor (or the target method),</li>
     *   <li>do something with the result or in a finally block,</li>
     *   <li>return whatever the proceed() call returned.</li>
     * </ol>
     */
    Object invoke(MyMethodInvocation invocation) throws Throwable;
}
