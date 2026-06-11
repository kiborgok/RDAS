package com.ncba.rdas.exception;

/**
 * Thrown when a query arrives but the reference-data snapshot has never been
 * successfully loaded (e.g. the SOAP service was already down at startup). Maps to
 * HTTP 503 with a Retry-After hint.
 */
public class ReferenceDataUnavailableException extends RuntimeException {
    public ReferenceDataUnavailableException(String message) {
        super(message);
    }
}
