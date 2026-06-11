package com.ncba.rdas.web.dto;

import com.ncba.rdas.domain.Country;
import java.util.List;

/**
 * Full country detail returned by {@code GET /countries/{code}} and list endpoints.
 */
public record CountryResponse(
        String code,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String continent,
        String currencyCode,
        String currency,
        String flagUrl,
        List<LanguageResponse> languages) {

    public static CountryResponse from(Country c) {
        return new CountryResponse(
                c.isoCode(),
                c.name(),
                c.capitalCity(),
                c.phoneCode(),
                c.continentCode(),
                c.continentName(),
                c.currencyCode(),
                c.currencyName(),
                c.flagUrl(),
                c.languages().stream().map(LanguageResponse::from).toList());
    }

    public record LanguageResponse(String code, String name) {
        public static LanguageResponse from(com.ncba.rdas.domain.Language l) {
            return new LanguageResponse(l.isoCode(), l.name());
        }
    }
}
