package com.ncba.rdas.soap;

/**
 * Raised when the upstream CountryInfo SOAP service cannot be reached or returns
 * an unusable response. Used as the retryable exception for Resilience4j.
 */
public class SoapClientException extends RuntimeException {

    public SoapClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SoapClientException(String message) {
        super(message);
    }
}
