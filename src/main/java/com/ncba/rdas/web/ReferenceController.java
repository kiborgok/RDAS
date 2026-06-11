package com.ncba.rdas.web;

import com.ncba.rdas.cache.ReferenceDataService;
import com.ncba.rdas.cache.ReferenceDataSnapshot;
import com.ncba.rdas.web.dto.CodeNameResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lookup endpoints for the reference dimensions, useful for populating filter
 * drop-downs in consuming UIs.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference", description = "Continent, currency and language lookups")
public class ReferenceController {

    private final ReferenceDataService referenceData;

    public ReferenceController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    @Operation(summary = "List continents")
    @GetMapping("/continents")
    public List<CodeNameResponse> continents() {
        return toSortedList(referenceData.snapshot().continents());
    }

    @Operation(summary = "List currencies")
    @GetMapping("/currencies")
    public List<CodeNameResponse> currencies() {
        return toSortedList(referenceData.snapshot().currencies());
    }

    @Operation(summary = "List languages", description = "Distinct languages across all countries.")
    @GetMapping("/languages")
    public List<CodeNameResponse> languages() {
        ReferenceDataSnapshot snapshot = referenceData.snapshot();
        Map<String, String> distinct = new TreeMap<>();
        snapshot.countries().forEach(c ->
                c.languages().forEach(l -> {
                    if (l.isoCode() != null) {
                        distinct.putIfAbsent(l.isoCode(), l.name());
                    }
                }));
        return toSortedList(distinct);
    }

    private static List<CodeNameResponse> toSortedList(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> new CodeNameResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CodeNameResponse::name,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }
}
