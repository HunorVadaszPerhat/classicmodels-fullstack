package com.hunor.classicmodelsbackend.dto.office;

/**
 * What the API returns for a single office.
 *
 * <p>The {@code lat} / {@code lng} fields let the front-end pin the
 * office on a map without doing its own geocoding. Both can be null
 * if a particular office hasn't been geocoded yet — the UI just hides
 * the map in that case.</p>
 */
public record OfficeResponseDTO(
        String officeCode,
        String city,
        String phone,
        String addressLine1,
        String addressLine2,
        String state,
        String country,
        String postalCode,
        String territory,
        Double lat,
        Double lng
) {}
