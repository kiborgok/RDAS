package com.ncba.rdas.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.ncba.rdas.soap.dto.CodeNameListEnvelope;
import com.ncba.rdas.soap.dto.FullCountryInfoEnvelope;
import org.junit.jupiter.api.Test;

/**
 * Confirms the Jackson-XML DTOs deserialise the real SOAP wire format (prefixed,
 * namespaced elements) by matching on local names.
 */
class SoapResponseParsingTest {

    private final XmlMapper xml = new XmlMapper();

    @Test
    void parsesFullCountryInfoWithLanguages() throws Exception {
        String body = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <m:FullCountryInfoAllCountriesResponse xmlns:m="http://www.oorsprong.org/websamples.countryinfo">
                  <m:FullCountryInfoAllCountriesResult>
                    <m:tCountryInfo>
                      <m:sISOCode>KE</m:sISOCode>
                      <m:sName>Kenya</m:sName>
                      <m:sCapitalCity>Nairobi</m:sCapitalCity>
                      <m:sPhoneCode>254</m:sPhoneCode>
                      <m:sContinentCode>AF</m:sContinentCode>
                      <m:sCurrencyISOCode>KES</m:sCurrencyISOCode>
                      <m:sCountryFlag>http://flags/Kenya.jpg</m:sCountryFlag>
                      <m:Languages>
                        <m:tLanguage><m:sISOCode>en</m:sISOCode><m:sName>English</m:sName></m:tLanguage>
                        <m:tLanguage><m:sISOCode>sw</m:sISOCode><m:sName>Swahili</m:sName></m:tLanguage>
                      </m:Languages>
                    </m:tCountryInfo>
                  </m:FullCountryInfoAllCountriesResult>
                </m:FullCountryInfoAllCountriesResponse>
              </soap:Body>
            </soap:Envelope>
            """;

        FullCountryInfoEnvelope env = xml.readValue(body, FullCountryInfoEnvelope.class);

        assertThat(env.body.response.countries).hasSize(1);
        FullCountryInfoEnvelope.SoapCountryInfo c = env.body.response.countries.get(0);
        assertThat(c.isoCode).isEqualTo("KE");
        assertThat(c.name).isEqualTo("Kenya");
        assertThat(c.currencyIsoCode).isEqualTo("KES");
        assertThat(c.languages).hasSize(2);
        assertThat(c.languages.get(0).name).isEqualTo("English");
    }

    @Test
    void parsesContinentList() throws Exception {
        String body = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <m:ListOfContinentsByNameResponse xmlns:m="http://www.oorsprong.org/websamples.countryinfo">
                  <m:ListOfContinentsByNameResult>
                    <m:tContinent><m:sCode>AF</m:sCode><m:sName>Africa</m:sName></m:tContinent>
                    <m:tContinent><m:sCode>EU</m:sCode><m:sName>Europe</m:sName></m:tContinent>
                  </m:ListOfContinentsByNameResult>
                </m:ListOfContinentsByNameResponse>
              </soap:Body>
            </soap:Envelope>
            """;

        CodeNameListEnvelope env = xml.readValue(body, CodeNameListEnvelope.class);

        assertThat(env.body.continentResponse.items).hasSize(2);
        assertThat(env.body.continentResponse.items.get(0).code()).isEqualTo("AF");
        assertThat(env.body.continentResponse.items.get(1).name).isEqualTo("Europe");
    }

    @Test
    void parsesCurrencyList_whichUsesSISOCode() throws Exception {
        // Currencies expose the code as <sISOCode>, unlike continents (<sCode>).
        String body = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <m:ListOfCurrenciesByNameResponse xmlns:m="http://www.oorsprong.org/websamples.countryinfo">
                  <m:ListOfCurrenciesByNameResult>
                    <m:tCurrency><m:sISOCode>EUR</m:sISOCode><m:sName>Euro</m:sName></m:tCurrency>
                    <m:tCurrency><m:sISOCode>KES</m:sISOCode><m:sName>Shilling</m:sName></m:tCurrency>
                  </m:ListOfCurrenciesByNameResult>
                </m:ListOfCurrenciesByNameResponse>
              </soap:Body>
            </soap:Envelope>
            """;

        CodeNameListEnvelope env = xml.readValue(body, CodeNameListEnvelope.class);

        assertThat(env.body.currencyResponse.items).hasSize(2);
        assertThat(env.body.currencyResponse.items.get(0).code()).isEqualTo("EUR");
        assertThat(env.body.currencyResponse.items.get(0).name).isEqualTo("Euro");
    }
}
