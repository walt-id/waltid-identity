package id.walt.trust.parser

import id.walt.trust.model.TrustedEntityType
import id.walt.trust.parser.lote.LoteJsonParser
import id.walt.trust.parser.lote.LoteXmlParseConfig
import id.walt.trust.parser.lote.LoteXmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EtsiLoteConformanceTest {

    @Test
    fun `accepts normative TS 119 602 JSON and normalizes identities`() {
        val parsed = LoteJsonParser.parse(normativeJson(), "normative-json")

        assertEquals("ETSI_TS_119_602_V1_1_1_JSON", parsed.source.metadata["syntax"])
        assertEquals(TrustedEntityType.WALLET_PROVIDER, parsed.entities.single().entityType)
        assertEquals("Example wallet provider", parsed.entities.single().legalName)
        assertEquals("CN=Wallet Provider,O=Example,C=AT", parsed.identities.single().subjectDn)
    }

    @Test
    fun `rejects JSON properties forbidden by the normative schema`() {
        val invalid = normativeJson().replace(
            "\"LoTEVersionIdentifier\": 1,",
            "\"LoTEVersionIdentifier\": 1, \"notInTheStandard\": true,"
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            LoteJsonParser.parse(invalid, "invalid-json")
        }
        assertTrue(failure.message.orEmpty().contains("validation failed"))
    }

    @Test
    fun `accepts normative TS 119 602 XML after XSD validation`() {
        val parsed = LoteXmlParser.parse(
            normativeXml(), "normative-xml",
            config = LoteXmlParseConfig(validateSignature = false, requireSignature = false)
        )

        assertEquals("ETSI_TS_119_602_V1_1_1_XML", parsed.source.metadata["syntax"])
        assertEquals(TrustedEntityType.WALLET_PROVIDER, parsed.entities.single().entityType)
        assertEquals("CN=Wallet Provider,O=Example,C=AT", parsed.identities.single().subjectDn)
    }

    @Test
    fun `rejects XML that violates the normative XSD`() {
        val invalid = normativeXml().replace("<LoTESequenceNumber>1</LoTESequenceNumber>", "")
        val failure = assertFailsWith<IllegalArgumentException> {
            LoteXmlParser.parse(
                invalid, "invalid-xml",
                config = LoteXmlParseConfig(validateSignature = false, requireSignature = false)
            )
        }
        assertTrue(failure.message.orEmpty().contains("validation failed"))
    }

    private fun normativeJson(): String = """
        {
          "LoTE": {
            "ListAndSchemeInformation": {
              "LoTEVersionIdentifier": 1,
              "LoTESequenceNumber": 1,
              "LoTEType": "http://uri.etsi.org/19602/LoTEType/EUWalletProvidersList",
              "SchemeOperatorName": [{"lang":"en","value":"Example operator"}],
              "SchemeTerritory": "AT",
              "ListIssueDateTime": "2026-07-21T00:00:00Z",
              "NextUpdate": "2026-08-21T00:00:00Z"
            },
            "TrustedEntitiesList": [{
              "TrustedEntityInformation": {
                "TEName": [{"lang":"en","value":"Example wallet provider"}],
                "TEAddress": {
                  "TEPostalAddress": [{
                    "lang":"en", "StreetAddress":"Example street 1",
                    "Locality":"Vienna", "Country":"AT"
                  }],
                  "TEElectronicAddress": [{"lang":"en","uriValue":"https://example.org"}]
                },
                "TEInformationURI": [{
                  "lang":"en",
                  "uriValue":"https://example.org/ListOfTrustedEntities/WalletProvider/AT"
                }]
              },
              "TrustedEntityServices": [{
                "ServiceInformation": {
                  "ServiceName": [{"lang":"en","value":"Wallet solution"}],
                  "ServiceDigitalIdentity": {
                    "X509SubjectNames": ["CN=Wallet Provider,O=Example,C=AT"]
                  },
                  "ServiceTypeIdentifier": "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance",
                  "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                  "StatusStartingTime": "2026-07-21T00:00:00Z"
                }
              }]
            }]
          }
        }
    """.trimIndent()

    private fun normativeXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ListOfTrustedEntities xmlns="http://uri.etsi.org/019602/v1#"
          xmlns:ds="http://www.w3.org/2000/09/xmldsig#" LOTETag="urn:example:lote">
          <ListAndSchemeInformation>
            <LoTEVersionIdentifier>1</LoTEVersionIdentifier>
            <LoTESequenceNumber>1</LoTESequenceNumber>
            <LoTEType>http://uri.etsi.org/19602/LoTEType/EUWalletProvidersList</LoTEType>
            <SchemeOperatorName><Name xml:lang="en">Example operator</Name></SchemeOperatorName>
            <SchemeOperatorAddress>
              <PostalAddresses><PostalAddress xml:lang="en">
                <StreetAddress>Example street 1</StreetAddress><Locality>Vienna</Locality>
                <CountryName>AT</CountryName>
              </PostalAddress></PostalAddresses>
              <ElectronicAddress><URI xml:lang="en">https://example.org</URI></ElectronicAddress>
            </SchemeOperatorAddress>
            <SchemeName><Name xml:lang="en">Example wallet providers</Name></SchemeName>
            <SchemeInformationURI><URI xml:lang="en">https://example.org/scheme</URI></SchemeInformationURI>
            <StatusDeterminationApproach>https://example.org/status</StatusDeterminationApproach>
            <SchemeTerritory>AT</SchemeTerritory>
            <ListIssueDateTime>2026-07-21T00:00:00Z</ListIssueDateTime>
            <NextUpdate><dateTime>2026-08-21T00:00:00Z</dateTime></NextUpdate>
          </ListAndSchemeInformation>
          <TrustedEntitiesList>
            <TrustedEntity>
              <TrustedEntityInformation>
                <TEName><Name xml:lang="en">Example wallet provider</Name></TEName>
                <TEAddress>
                  <PostalAddresses><PostalAddress xml:lang="en">
                    <StreetAddress>Example street 1</StreetAddress><Locality>Vienna</Locality>
                    <CountryName>AT</CountryName>
                  </PostalAddress></PostalAddresses>
                  <ElectronicAddress><URI xml:lang="en">https://example.org</URI></ElectronicAddress>
                </TEAddress>
                <TEInformationURI><URI xml:lang="en">https://example.org/ListOfTrustedEntities/WalletProvider/AT</URI></TEInformationURI>
              </TrustedEntityInformation>
              <TrustedEntityServices>
                <TrustedEntityService><ServiceInformation>
                  <ServiceTypeIdentifier>http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance</ServiceTypeIdentifier>
                  <ServiceName><Name xml:lang="en">Wallet solution</Name></ServiceName>
                  <ServiceDigitalIdentity><DigitalId>
                    <X509SubjectName>CN=Wallet Provider,O=Example,C=AT</X509SubjectName>
                  </DigitalId></ServiceDigitalIdentity>
                  <ServiceStatus>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted</ServiceStatus>
                  <StatusStartingTime>2026-07-21T00:00:00Z</StatusStartingTime>
                </ServiceInformation></TrustedEntityService>
              </TrustedEntityServices>
            </TrustedEntity>
          </TrustedEntitiesList>
        </ListOfTrustedEntities>
    """.trimIndent()
}
