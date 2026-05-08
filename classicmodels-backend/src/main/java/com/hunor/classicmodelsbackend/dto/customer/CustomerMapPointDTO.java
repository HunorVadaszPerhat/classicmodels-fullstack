package com.hunor.classicmodelsbackend.dto.customer;

import java.math.BigDecimal;

/**
 * Lightweight projection of a customer for map rendering.
 *
 * <p>The full {@code CustomerResponseDTO} carries 19 fields including
 * audit timestamps and credit limits — way more than a marker on a
 * world map needs. This DTO ships only what the map actually consumes:
 * an id (for routing back to the detail page), enough text for the
 * popup, the coordinates, and one status flag (currently
 * {@code hasSalesRep}) used to colour the marker.</p>
 *
 * <p>Trimming the payload matters at scale — 122 customers × 19 fields
 * is fine, but if this list ever grows to 10k+ rows the difference
 * between a 50-field row and an 8-field row starts to dominate the
 * response size and the parse cost on the client.</p>
 *
 * <p>The endpoint that returns these only includes:
 * <ul>
 *   <li>active customers (the same {@code WHERE active = 1} filter
 *       all default lists use)</li>
 *   <li>customers with non-null {@code lat} / {@code lng} (un-geocoded
 *       customers can't be plotted)</li>
 * </ul>
 * The frontend separately reports "X of Y geocoded" so users
 * understand why some customers aren't on the map.</p>
 */
public record CustomerMapPointDTO(
        int customerNumber,
        String customerName,
        String city,
        String country,
        BigDecimal lat,
        BigDecimal lng,
        /**
         * True when the customer has a sales rep assigned. Used by the
         * map to colour-code "assigned" (default) vs. "unassigned"
         * (highlighted) markers, so sales managers can spot territory
         * gaps at a glance.
         */
        boolean hasSalesRep
) {}
