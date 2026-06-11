package com.ncba.rdas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ncba.rdas.cache.ReferenceDataService;
import com.ncba.rdas.cache.ReferenceDataSnapshot;
import com.ncba.rdas.domain.Country;
import com.ncba.rdas.domain.Language;
import com.ncba.rdas.exception.CountryNotFoundException;
import com.ncba.rdas.web.dto.CountryResponse;
import com.ncba.rdas.web.dto.PageResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CountryQueryServiceTest {

    @Mock
    private ReferenceDataService referenceData;

    private CountryQueryService service;

    @BeforeEach
    void setUp() {
        service = new CountryQueryService(referenceData);
        when(referenceData.snapshot()).thenReturn(fixture());
    }

    @Test
    void searchByName_isCaseInsensitiveAndPartial() {
        PageResponse<CountryResponse> page = service.search(criteria("ken", null, null, null, 0, 20));
        assertThat(page.content()).extracting(CountryResponse::name).containsExactly("Kenya");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void filterByContinent_matchesCodeOrName() {
        assertThat(service.search(criteria(null, "AF", null, null, 0, 20)).totalElements()).isEqualTo(2);
        assertThat(service.search(criteria(null, "europe", null, null, 0, 20)).totalElements()).isEqualTo(1);
    }

    @Test
    void filterByCurrency_matchesCode() {
        PageResponse<CountryResponse> page = service.search(criteria(null, null, "EUR", null, 0, 20));
        assertThat(page.content()).extracting(CountryResponse::name).containsExactly("France");
    }

    @Test
    void filterByLanguage_matchesNameAcrossCountries() {
        PageResponse<CountryResponse> page = service.search(criteria(null, null, null, "english", 0, 20));
        assertThat(page.content()).extracting(CountryResponse::name)
                .containsExactlyInAnyOrder("Kenya", "Uganda");
    }

    @Test
    void sorting_descendingByName() {
        CountrySearchCriteria c = CountrySearchCriteria.normalised(
                null, null, null, null, 0, 20, SortField.NAME, false);
        assertThat(service.search(c).content()).extracting(CountryResponse::name)
                .containsExactly("Uganda", "Kenya", "France");
    }

    @Test
    void pagination_splitsResults() {
        CountrySearchCriteria first = CountrySearchCriteria.normalised(
                null, null, null, null, 0, 2, SortField.NAME, true);
        PageResponse<CountryResponse> p0 = service.search(first);
        assertThat(p0.content()).hasSize(2);
        assertThat(p0.totalElements()).isEqualTo(3);
        assertThat(p0.totalPages()).isEqualTo(2);
        assertThat(p0.first()).isTrue();
        assertThat(p0.last()).isFalse();

        CountrySearchCriteria second = CountrySearchCriteria.normalised(
                null, null, null, null, 1, 2, SortField.NAME, true);
        PageResponse<CountryResponse> p1 = service.search(second);
        assertThat(p1.content()).hasSize(1);
        assertThat(p1.last()).isTrue();
    }

    @Test
    void getByCode_returnsCountry_caseInsensitive() {
        assertThat(service.getByCode("ke").name()).isEqualTo("Kenya");
    }

    @Test
    void getByCode_unknown_throwsNotFound() {
        assertThatThrownBy(() -> service.getByCode("ZZ"))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void countriesByCurrency_returnsSharedCurrency() {
        PageResponse<CountryResponse> page = service.countriesByCurrency(
                "KES", 0, 20, SortField.NAME, true);
        assertThat(page.content()).extracting(CountryResponse::name).containsExactly("Kenya");
    }

    private static CountrySearchCriteria criteria(
            String name, String continent, String currency, String language, int page, int size) {
        return CountrySearchCriteria.normalised(
                name, continent, currency, language, page, size, SortField.NAME, true);
    }

    private static ReferenceDataSnapshot fixture() {
        Country kenya = new Country("KE", "Kenya", "Nairobi", "254", "AF", "Africa",
                "KES", "Shilling", "flag/ke", List.of(new Language("en", "English"), new Language("sw", "Swahili")));
        Country uganda = new Country("UG", "Uganda", "Kampala", "256", "AF", "Africa",
                "UGX", "Shilling", "flag/ug", List.of(new Language("en", "English")));
        Country france = new Country("FR", "France", "Paris", "33", "EU", "Europe",
                "EUR", "Euro", "flag/fr", List.of(new Language("fr", "French")));

        Map<String, Country> byCode = new LinkedHashMap<>();
        byCode.put("KE", kenya);
        byCode.put("UG", uganda);
        byCode.put("FR", france);

        return new ReferenceDataSnapshot(
                List.of(kenya, uganda, france),
                byCode,
                Map.of("AF", "Africa", "EU", "Europe"),
                Map.of("KES", "Shilling", "UGX", "Shilling", "EUR", "Euro"),
                Instant.now());
    }
}
