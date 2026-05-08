package com.hunor.classicmodelsbackend.realtime;

/**
 * The shape we broadcast on the {@code /topic/customers} STOMP
 * destination whenever a customer row is created, updated, or
 * deleted (any strategy — soft or deep cascade).
 *
 * <p>Deliberately tiny. We don't push the changed row's full state
 * because clients always have to do their own GET to handle
 * pagination / filtering / search-debouncing correctly anyway —
 * the event just says "something changed for customer N, refresh
 * if you care." Keeps the event payload schema-stable across
 * backend changes too.</p>
 *
 * @param type            CREATED, UPDATED, or DELETED. SOFT-deletes
 *                        also map to DELETED here — from the client's
 *                        list-page perspective the row disappears
 *                        either way (the {@code WHERE active = 1}
 *                        filter hides soft-deleted rows).
 * @param customerNumber  which customer was affected. For DELETEd
 *                        rows the row may no longer exist (hard
 *                        delete) or may still exist with active=0
 *                        (soft delete) — clients use the id only
 *                        to compare against any local state they're
 *                        showing.
 */
public record CustomerEvent(Type type, int customerNumber) {
    public enum Type { CREATED, UPDATED, DELETED }
}
