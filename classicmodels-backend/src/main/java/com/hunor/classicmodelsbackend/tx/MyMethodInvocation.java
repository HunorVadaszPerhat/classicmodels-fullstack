package com.hunor.classicmodelsbackend.tx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Represents an in-progress method call, threaded through a chain of
 * {@link MyMethodInterceptor} advices.
 *
 * <p>Each interceptor receives this object and must call
 * {@link #proceed()} to advance to the next interceptor. When proceed()
 * has walked off the end of the chain, it invokes the actual target
 * method via reflection. The chain unwinds naturally on the way back —
 * the outermost interceptor's {@code invoke()} returns last.</p>
 *
 * <h3>One instance per call</h3>
 *
 * <p>An invocation is <em>not</em> thread-safe and not reusable. A new
 * one is constructed for every method call (in
 * {@link MyChainInvocationHandler#invoke}), discarded when the call
 * returns. The mutable {@code currentIndex} field is what tracks
 * progress through the chain.</p>
 */
public class MyMethodInvocation {

    private final Object target;
    private final Method implMethod;
    private final Object[] args;
    private final List<MyMethodInterceptor> chain;

    /**
     * Index of the most recently entered interceptor. Starts at -1 so
     * the first {@code proceed()} bumps it to 0 (the outermost
     * interceptor). When it equals {@code chain.size()} we're past the
     * end and call the target.
     */
    private int currentIndex = -1;

    public MyMethodInvocation(Object target, Method implMethod, Object[] args,
                              List<MyMethodInterceptor> chain) {
        this.target = target;
        this.implMethod = implMethod;
        this.args = args;
        this.chain = chain;
    }

    /**
     * Advance to the next interceptor in the chain. When the chain is
     * exhausted, invokes the target method via reflection.
     *
     * <p>This is the heart of the interceptor pattern. Every advice
     * calls {@code proceed()} to "continue the chain", and the same
     * MyMethodInvocation object passes from advice to advice picking
     * up where the previous one left off. If you've ever used Servlet
     * filters with their {@code FilterChain.doFilter(...)}, this is
     * the same idea.</p>
     */
    public Object proceed() throws Throwable {
        currentIndex++;
        if (currentIndex < chain.size()) {
            return chain.get(currentIndex).invoke(this);
        }
        // End of chain — call the actual target. Unwrap reflection
        // exceptions so callers see the real cause.
        try {
            return implMethod.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    // -- Accessors used by interceptors and for diagnostics --

    public Object getTarget()            { return target; }
    public Class<?> getTargetClass()     { return target.getClass(); }
    public Method getMethod()            { return implMethod; }
    public Object[] getArguments()       { return args; }
}
