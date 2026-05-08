package com.hunor.classicmodelsbackend.dto.customer;

import java.util.List;

/**
 * One potential duplicate of a customer, returned by
 * {@code GET /customers/{id}/merge-candidates}.
 *
 * <p>The frontend uses this in two ways: the list view ranks
 * candidates by {@link #score} and shows the score alongside each
 * card, and the {@link #matchedFields} list explains <em>why</em>
 * this candidate was matched ("name + phone matched, contact name
 * differed") so the user can sanity-check before opening the
 * detail compare.</p>
 *
 * @param customerNumber  loser candidate's id
 * @param customerName    full name (used for the ranked list display)
 * @param contactFirstName for display
 * @param contactLastName  for display
 * @param phone            for display
 * @param city             for display ("...in Paris")
 * @param country          for display
 * @param score            similarity score 0.0–1.0; weighted across name,
 *                         contact name, and phone fields
 * @param matchedFields    fields whose individual similarity exceeded
 *                         the threshold; "name", "contact", "phone"
 */
public record CustomerMergeCandidateDTO(
        int customerNumber,
        String customerName,
        String contactFirstName,
        String contactLastName,
        String phone,
        String city,
        String country,
        double score,
        List<String> matchedFields
) {}
