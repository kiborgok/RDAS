package com.ncba.rdas.exception;

/** Thrown when a requested country code does not exist. Maps to HTTP 404. */
public class CountryNotFoundException extends RuntimeException {
    public CountryNotFoundException(String code) {
        super("No country found for code '" + code + "'");
    }
}
