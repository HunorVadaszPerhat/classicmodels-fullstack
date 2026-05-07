package com.hunor.classicmodelsbackend.realtime;

/**
 * The shape we broadcast on the {@code /topic/employees} STOMP
 * destination whenever an employee row is created, updated, or deleted.
 *
 * <p>Deliberately tiny. We don't push the changed row's full state
 * because clients always have to do their own GET to handle pagination
 * / filtering correctly anyway — the event just says "something
 * changed for employee N, refresh if you care." Keeps the event
 * payload schema-stable across backend changes too.</p>
 *
 * @param type            CREATED, UPDATED, or DELETED
 * @param employeeNumber  which employee was affected. For DELETEd rows
 *                        the row no longer exists — clients use the
 *                        id only to compare against any local state
 *                        they're showing.
 */
public record EmployeeEvent(Type type, int employeeNumber) {
    public enum Type { CREATED, UPDATED, DELETED }
}
