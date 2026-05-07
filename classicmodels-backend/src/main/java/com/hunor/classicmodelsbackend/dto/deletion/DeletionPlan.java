package com.hunor.classicmodelsbackend.dto.deletion;

import java.util.List;

/**
 * The output of {@code DeletionPlannerService.plan(...)} — a complete,
 * read-only description of what a deep-cascade deletion of one entity
 * would touch.
 *
 * <p>Discovery walks {@code INFORMATION_SCHEMA} starting at
 * {@link #rootTable}/{@link #rootIdColumn} and follows every foreign key
 * downward. The result is a topologically-sorted list of steps (deepest
 * leaves first, root last) plus aggregate stats — handy for previewing
 * the "blast radius" before exposing any execute path.</p>
 *
 * <p>Nothing in this object has side effects. Generating a plan is
 * idempotent. The numbers age the moment the underlying tables change,
 * so re-plan immediately before any actual execution.</p>
 *
 * @param rootTable         the starting table (the entity whose deletion
 *                          is being planned).
 * @param rootIdColumn      the PK column of {@code rootTable} used to
 *                          identify the row.
 * @param rootId            the id of the row being planned. Object-typed
 *                          so numeric and string PKs both pass through.
 * @param totalSteps        equal to {@code steps.size()}; surfaced
 *                          explicitly because it's the most common stat
 *                          the UI wants to render in a summary line.
 * @param totalAffectedRows total rows that would be affected across all
 *                          steps. Includes DELETE and NULLIFY counts;
 *                          excludes WARNING steps (which would not run).
 * @param steps             the steps in execution order — deepest leaves
 *                          first, root last.
 */
public record DeletionPlan(
        String rootTable,
        String rootIdColumn,
        Object rootId,
        int totalSteps,
        long totalAffectedRows,
        List<DeletionStep> steps
) {}
