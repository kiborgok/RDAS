package com.ncba.rdas.domain;

/**
 * A language spoken in a country.
 *
 * @param isoCode ISO language code (e.g. {@code en})
 * @param name    human-readable name (e.g. {@code English})
 */
public record Language(String isoCode, String name) {
}
