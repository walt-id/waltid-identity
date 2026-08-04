package id.walt.trust.parser

import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustListFormat
import id.walt.trust.model.TrustStatus
import id.walt.trust.parser.tsl.TslParseConfig
import id.walt.trust.parser.tsl.TslXmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TslInteroperabilityTest {

    private val unsigned = TslParseConfig(validateSignature = false, requireSignature = false)

    @Test
    fun `normalizes ETSI recognised status, SKI, and Other identities`() {
        val parsed = TslXmlParser.parse(nationalTsl, "national", config = unsigned)

        assertEquals(TrustListFormat.ETSI_TS_119_612_TRUST_LIST_XML, parsed.source.format)
        assertEquals(TrustStatus.RECOGNIZED, parsed.services.single().status)
        assertEquals("010203", parsed.identities.first { it.subjectKeyIdentifierHex != null }.subjectKeyIdentifierHex)
        assertEquals("urn:example:remote-service", parsed.identities.first { "otherId" in it.metadata }.metadata["otherId"])
    }

    @Test
    fun `identifies a LoTL and reports pointer count without treating pointers as providers`() {
        val parsed = TslXmlParser.parse(lotl, "eu-lotl", config = unsigned)

        assertEquals(TrustListFormat.ETSI_TS_119_612_LIST_OF_TRUST_LISTS_XML, parsed.source.format)
        assertEquals("2", parsed.source.metadata["pointerCount"])
        assertEquals(FreshnessState.EXPIRED, parsed.source.freshnessState)
        assertTrue(parsed.entities.isEmpty())
        assertTrue(parsed.services.isEmpty())
    }

    private val nationalTsl = """
        <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#" TSLTag="http://uri.etsi.org/19612/TSLTag">
          <SchemeInformation>
            <TSLVersionIdentifier>6</TSLVersionIdentifier><TSLSequenceNumber>1</TSLSequenceNumber>
            <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</TSLType>
            <SchemeTerritory>AT</SchemeTerritory><ListIssueDateTime>2026-01-01T00:00:00Z</ListIssueDateTime>
            <NextUpdate><dateTime>2099-01-01T00:00:00Z</dateTime></NextUpdate>
          </SchemeInformation>
          <TrustServiceProviderList><TrustServiceProvider>
            <TSPInformation><TSPName><Name xml:lang="en">Example TSP</Name></TSPName></TSPInformation>
            <TSPServices><TSPService><ServiceInformation>
              <ServiceTypeIdentifier>http://uri.etsi.org/TrstSvc/Svctype/CA/QC</ServiceTypeIdentifier>
              <ServiceStatus>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/recognisedatnationallevel</ServiceStatus>
              <ServiceDigitalIdentity>
                <DigitalId><X509SKI>AQID</X509SKI></DigitalId>
                <DigitalId><Other>urn:example:remote-service</Other></DigitalId>
              </ServiceDigitalIdentity>
            </ServiceInformation></TSPService></TSPServices>
          </TrustServiceProvider></TrustServiceProviderList>
        </TrustServiceStatusList>
    """.trimIndent()

    private val lotl = """
        <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#" TSLTag="http://uri.etsi.org/19612/TSLTag">
          <SchemeInformation>
            <TSLVersionIdentifier>6</TSLVersionIdentifier><TSLSequenceNumber>1</TSLSequenceNumber>
            <TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists</TSLType>
            <SchemeTerritory>EU</SchemeTerritory><ListIssueDateTime>2025-01-01T00:00:00Z</ListIssueDateTime>
            <NextUpdate><dateTime>2025-08-01T00:00:00Z</dateTime></NextUpdate>
            <PointersToOtherTSL>
              <OtherTSLPointer><TSLLocation>https://example.org/at.xml</TSLLocation></OtherTSLPointer>
              <OtherTSLPointer><TSLLocation>https://example.org/it.xml</TSLLocation></OtherTSLPointer>
            </PointersToOtherTSL>
          </SchemeInformation>
        </TrustServiceStatusList>
    """.trimIndent()
}
