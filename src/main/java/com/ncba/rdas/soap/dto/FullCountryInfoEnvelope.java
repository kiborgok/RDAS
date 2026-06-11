package com.ncba.rdas.soap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

/**
 * Maps the SOAP response of {@code FullCountryInfoAllCountries}. Element names are
 * matched by local name, so SOAP/namespace prefixes ({@code soap:}, {@code m:}) are
 * ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "Envelope")
public class FullCountryInfoEnvelope {

    @JacksonXmlProperty(localName = "Body")
    public Body body;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JacksonXmlProperty(localName = "FullCountryInfoAllCountriesResponse")
        public Response response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        @JacksonXmlElementWrapper(localName = "FullCountryInfoAllCountriesResult")
        @JacksonXmlProperty(localName = "tCountryInfo")
        public List<SoapCountryInfo> countries;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SoapCountryInfo {
        @JacksonXmlProperty(localName = "sISOCode")
        public String isoCode;
        @JacksonXmlProperty(localName = "sName")
        public String name;
        @JacksonXmlProperty(localName = "sCapitalCity")
        public String capitalCity;
        @JacksonXmlProperty(localName = "sPhoneCode")
        public String phoneCode;
        @JacksonXmlProperty(localName = "sContinentCode")
        public String continentCode;
        @JacksonXmlProperty(localName = "sCurrencyISOCode")
        public String currencyIsoCode;
        @JacksonXmlProperty(localName = "sCountryFlag")
        public String countryFlag;

        @JacksonXmlElementWrapper(localName = "Languages")
        @JacksonXmlProperty(localName = "tLanguage")
        public List<SoapLanguage> languages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SoapLanguage {
        @JacksonXmlProperty(localName = "sISOCode")
        public String isoCode;
        @JacksonXmlProperty(localName = "sName")
        public String name;
    }
}
