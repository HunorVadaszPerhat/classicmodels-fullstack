package com.hunor.classicmodelsbackend.tx;

import lombok.extern.slf4j.Slf4j;

/**
 * Around-advice that measures the elapsed wall-clock time of a method
 * call and logs it. The simplest possible {@link MyMethodInterceptor}
 * — and a useful demonstration that the chain abstraction lets us
 * stack these like building blocks.
 *
 * <h3>Why nanoTime, not currentTimeMillis</h3>
 *
 * <p>{@link System#nanoTime()} is monotonic — it only ever increases,
 * regardless of clock adjustments (NTP, daylight saving, manual
 * setting). For measuring elapsed time you always want nanoTime.
 * {@link System#currentTimeMillis()} can jump backwards when the
 * wall clock is corrected, producing negative durations. We log in
 * milliseconds because nanoseconds is rarely the unit you care
 * about for method-level timing.</p>
 */
@Slf4j
public class MyTimedAdvice implements MyMethodInterceptor {

    @Override
    public Object invoke(MyMethodInvocation invocation) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            // Duration is captured even when proceed() threw — that's
            // useful (you want to know how long a slow query took
            // before it timed out). We don't catch/rethrow because
            // any handling is the next interceptor's job; finally is
            // the right place for unconditional measurement.
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("⏱  {}.{} took {} ms",
                    invocation.getTargetClass().getSimpleName(),
                    invocation.getMethod().getName(),
                    elapsedMs);
        }
    }
}
