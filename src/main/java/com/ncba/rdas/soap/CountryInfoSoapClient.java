package com.ncba.rdas.soap;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.ncba.rdas.config.RdasProperties;
import com.ncba.rdas.soap.dto.CodeNameListEnvelope;
import com.ncba.rdas.soap.dto.FullCountryInfoEnvelope;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Thin client over the CountryInfo SOAP service.
 *
 * <p>Builds SOAP 1.2 envelopes by hand (the WSDL is simple and stable), posts them
 * with a timeout-bounded {@link RestTemplate}, and unmarshals the XML body with
 * Jackson. Every call is wrapped by a Resilience4j retry (transient failures) and
 * circuit breaker (sustained outage), referencing the {@code countryInfoSoap}
 * instance configured in {@code application.yml}.
 *
 * <p>This is the <em>only</em> component in RDAS that talks SOAP — every other
 * layer works with the canonical domain model.
 */
@Component
public class CountryInfoSoapClient {

    private static final Logger log = LoggerFactory.getLogger(CountryInfoSoapClient.class);
    private static final String RESILIENCE = "countryInfoSoap";

    private final RestTemplate restTemplate;
    private final RdasProperties props;
    private final XmlMapper xmlMapper = new XmlMapper();

    public CountryInfoSoapClient(RestTemplate soapRestTemplate, RdasProperties props) {
        this.restTemplate = soapRestTemplate;
        this.props = props;
    }

    /** One call that returns every country with full detail and embedded languages. */
    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public List<FullCountryInfoEnvelope.SoapCountryInfo> fetchAllCountries() {
        String body = call("FullCountryInfoAllCountries");
        FullCountryInfoEnvelope env = read(body, FullCountryInfoEnvelope.class);
        if (env.body == null || env.body.response == null || env.body.response.countries == null) {
            throw new SoapClientException("FullCountryInfoAllCountries returned no countries");
        }
        return env.body.response.countries;
    }

    /** Continent code -> name reference list. */
    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public List<CodeNameListEnvelope.CodeName> fetchContinents() {
        String body = call("ListOfContinentsByName");
        CodeNameListEnvelope env = read(body, CodeNameListEnvelope.class);
        if (env.body == null || env.body.continentResponse == null
                || env.body.continentResponse.items == null) {
            throw new SoapClientException("ListOfContinentsByName returned no continents");
        }
        return env.body.continentResponse.items;
    }

    /** Currency code -> name reference list. */
    @Retry(name = RESILIENCE)
    @CircuitBreaker(name = RESILIENCE)
    public List<CodeNameListEnvelope.CodeName> fetchCurrencies() {
        String body = call("ListOfCurrenciesByName");
        CodeNameListEnvelope env = read(body, CodeNameListEnvelope.class);
        if (env.body == null || env.body.currencyResponse == null
                || env.body.currencyResponse.items == null) {
            throw new SoapClientException("ListOfCurrenciesByName returned no currencies");
        }
        return env.body.currencyResponse.items;
    }

    private String call(String operation) {
        String soapEnvelope = buildEnvelope(operation);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/soap+xml; charset=utf-8"));
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    props.getSoap().getEndpoint(),
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(soapEnvelope, headers),
                    String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new SoapClientException(
                        "SOAP " + operation + " failed with status " + response.getStatusCode());
            }
            return response.getBody();
        } catch (RestClientException e) {
            log.warn("SOAP call {} failed: {}", operation, e.getMessage());
            throw new SoapClientException("SOAP call " + operation + " failed", e);
        }
    }

    private <T> T read(String xml, Class<T> type) {
        try {
            return xmlMapper.readValue(xml, type);
        } catch (Exception e) {
            throw new SoapClientException("Unable to parse SOAP response for " + type.getSimpleName(), e);
        }
    }

    private String buildEnvelope(String operation) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<" + operation + " xmlns=\"" + props.getSoap().getNamespace() + "\" />"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }
}
