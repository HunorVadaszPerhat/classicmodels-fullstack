package com.hunor.classicmodelsbackend.model;

import lombok.Data;

/**
 * Domain model for an office row.
 *
 * <p>{@code lat} / {@code lng} are nullable doubles; null means the
 * office hasn't been geocoded yet (the front-end hides the map for
 * those rows). They're populated by Flyway migration V3 for the seven
 * seed offices.</p>
 */
@Data
public class Office {
    private String officeCode;
    private String city;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String state;
    private String country;
    private String postalCode;
    private String territory;

    /** Latitude in decimal degrees. North is positive. Null if not geocoded. */
    private Double lat;

    /** Longitude in decimal degrees. East is positive. Null if not geocoded. */
    private Double lng;
}
