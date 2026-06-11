package com.ncba.rdas.soap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

/**
 * Maps the SOAP responses of {@code ListOfContinentsByName} and
 * {@code ListOfCurrenciesByName}. Both return a list of {@code sCode}/{@code sName}
 * pairs; the only difference is the item element name ({@code tContinent} vs
 * {@code tCurrency}), so both are mapped as optional fields and the populated one
 * is used.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "Envelope")
public class CodeNameListEnvelope {

    @JacksonXmlProperty(localName = "Body")
    public Body body;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JacksonXmlProperty(localName = "ListOfContinentsByNameResponse")
        public ContinentResponse continentResponse;
        @JacksonXmlProperty(localName = "ListOfCurrenciesByNameResponse")
        public CurrencyResponse currencyResponse;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContinentResponse {
        @JacksonXmlElementWrapper(localName = "ListOfContinentsByNameResult")
        @JacksonXmlProperty(localName = "tContinent")
        public List<CodeName> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrencyResponse {
        @JacksonXmlElementWrapper(localName = "ListOfCurrenciesByNameResult")
        @JacksonXmlProperty(localName = "tCurrency")
        public List<CodeName> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeName {
        // Continents expose the code as <sCode>, currencies as <sISOCode>.
        @JacksonXmlProperty(localName = "sCode")
        public String sCode;
        @JacksonXmlProperty(localName = "sISOCode")
        public String sIsoCode;
        @JacksonXmlProperty(localName = "sName")
        public String name;

        /** @return the code from whichever element the operation used. */
        public String code() {
            return sCode != null ? sCode : sIsoCode;
        }
    }
}
