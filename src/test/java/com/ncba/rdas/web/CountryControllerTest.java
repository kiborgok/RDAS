package com.ncba.rdas.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ncba.rdas.config.RdasProperties;
import com.ncba.rdas.exception.CountryNotFoundException;
import com.ncba.rdas.service.CountryQueryService;
import com.ncba.rdas.web.dto.CountryResponse;
import com.ncba.rdas.web.dto.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CountryController.class)
@Import(RdasProperties.class)
class CountryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CountryQueryService queryService;

    @Test
    void search_returnsOkAndPageBody() throws Exception {
        CountryResponse kenya = new CountryResponse("KE", "Kenya", "Nairobi", "254",
                "AF", "Africa", "KES", "Shilling", "flag/ke", List.of());
        when(queryService.search(any()))
                .thenReturn(PageResponse.of(List.of(kenya), 0, 20, 1, "name,asc"));

        mvc.perform(get("/api/v1/countries").param("name", "ken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("KE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.sort").value("name,asc"));
    }

    @Test
    void search_rejectsPageSizeAboveMax() throws Exception {
        mvc.perform(get("/api/v1/countries").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void search_rejectsUnknownSortField() throws Exception {
        mvc.perform(get("/api/v1/countries").param("sort", "bogus,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_rejectsNegativePage() throws Exception {
        mvc.perform(get("/api/v1/countries").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCountry_unknown_returns404() throws Exception {
        when(queryService.getByCode(eq("ZZ"))).thenThrow(new CountryNotFoundException("ZZ"));
        mvc.perform(get("/api/v1/countries/ZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
