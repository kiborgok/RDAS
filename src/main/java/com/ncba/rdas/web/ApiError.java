package com.ncba.rdas.web;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error body returned by every failed request (RFC-7807-style).
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param message   human-readable summary
 * @param path      request path
 * @param details   field-level messages (e.g. validation errors), may be empty
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {
}
