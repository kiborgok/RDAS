package com.ncba.rdas.web;

import com.ncba.rdas.config.RdasProperties;
import com.ncba.rdas.service.CountryQueryService;
import com.ncba.rdas.service.CountrySearchCriteria;
import com.ncba.rdas.service.SortField;
import com.ncba.rdas.web.dto.CountryResponse;
import com.ncba.rdas.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Country search and detail endpoints — the single API that satisfies the business
 * requirement (search by name; filter by continent/currency/language; details;
 * countries sharing a currency; pagination; sorting).
 */
@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Countries", description = "Search and retrieve country reference data")
public class CountryController {

    private final CountryQueryService queryService;
    private final RdasProperties props;

    public CountryController(CountryQueryService queryService, RdasProperties props) {
        this.queryService = queryService;
        this.props = props;
    }

    @Operation(summary = "Search/list countries",
            description = "Filter by name, continent, currency and language; sort and paginate. "
                    + "Filters are case-insensitive; continent/currency accept either code or name.")
    @GetMapping("/countries")
    public PageResponse<CountryResponse> searchCountries(
            @Parameter(description = "Partial, case-insensitive country name")
            @RequestParam(required = false) String name,
            @Parameter(description = "Continent code (e.g. AF) or name (e.g. Africa)")
            @RequestParam(required = false) String continent,
            @Parameter(description = "Currency ISO code (e.g. KES) or name")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Language ISO code (e.g. en) or name (e.g. English)")
            @RequestParam(required = false) String language,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @Parameter(description = "Page size")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort as field[,asc|desc]. Fields: name, code, capital, continent, currency, phoneCode")
            @RequestParam(defaultValue = "name,asc") String sort) {

        int effectiveSize = resolveSize(size);
        SortSpec spec = SortSpec.parse(sort);
        CountrySearchCriteria criteria = CountrySearchCriteria.normalised(
                name, continent, currency, language, page, effectiveSize, spec.field(), spec.ascending());
        return queryService.search(criteria);
    }

    @Operation(summary = "Get a country by ISO code")
    @GetMapping("/countries/{code}")
    public CountryResponse getCountry(
            @Parameter(description = "ISO country code, e.g. KE") @PathVariable String code) {
        return queryService.getByCode(code);
    }

    @Operation(summary = "List countries sharing a currency",
            description = "All countries that use the given currency (ISO code or name).")
    @GetMapping("/currencies/{code}/countries")
    public PageResponse<CountryResponse> countriesByCurrency(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be >= 0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        int effectiveSize = resolveSize(size);
        SortSpec spec = SortSpec.parse(sort);
        return queryService.countriesByCurrency(code, page, effectiveSize, spec.field(), spec.ascending());
    }

    /** Clamp/validate the requested page size against the configured maximum. */
    private int resolveSize(Integer size) {
        int max = props.getQuery().getMaxPageSize();
        if (size == null) {
            return props.getQuery().getDefaultPageSize();
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        if (size > max) {
            throw new IllegalArgumentException("size must be <= " + max);
        }
        return size;
    }

    /** Parsed representation of the {@code sort} query parameter. */
    private record SortSpec(SortField field, boolean ascending) {
        static SortSpec parse(String sort) {
            String[] parts = sort.split(",", 2);
            SortField field = SortField.fromApiName(parts[0].trim());
            boolean asc = true;
            if (parts.length > 1) {
                String dir = parts[1].trim();
                if (dir.equalsIgnoreCase("desc")) {
                    asc = false;
                } else if (!dir.equalsIgnoreCase("asc")) {
                    throw new IllegalArgumentException(
                            "Sort direction must be 'asc' or 'desc', got '" + dir + "'");
                }
            }
            return new SortSpec(field, asc);
        }
    }
}
