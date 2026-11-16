package id.walt.trust

import id.walt.trust.model.SourceAcceptancePolicy
import id.walt.trust.model.SourceLoadOptions
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import id.walt.trust.utils.CanonicalSubjectHash
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import javax.security.auth.x500.X500Principal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonicalSubjectHashTest {

    @Test
    fun namesPkixTreatsAsEqualShareAKeyAndOthersDoNot() {
        val reference = X500Principal("CN=Example Root,O=Example Org,C=AT")
        val variant = X500Principal("cn=EXAMPLE   root , o=example org, c=at")
        check(reference == variant)

        assertEquals(CanonicalSubjectHash.of(reference), CanonicalSubjectHash.of(variant))
        assertNotEquals(CanonicalSubjectHash.of(reference), CanonicalSubjectHash.of(X500Principal("CN=Example Root 2,O=Example Org,C=AT")))
    }

    @Test
    fun loadingASourceKeysEachCertificateBySubjectAndIgnoresTheListedName() = runTest {
        val chain = TestCertificates.createChain("Keyed")
        val store = InMemoryTrustStore()
        val loaded = DefaultTrustRegistryService(store).loadSourceFromContent(
            "keyed",
            tsl(TestCertificates.derBase64(chain.root), listedSubjectName = "CN=Not The Certificate Subject"),
            options = SourceLoadOptions(SourceAcceptancePolicy.ALLOW_UNSIGNED),
        )
        assertTrue(loaded.success, loaded.error)

        val certificateIdentity = store.listCertificateIdentities().toList().single()
        assertEquals("CN=Not The Certificate Subject", certificateIdentity.subjectDn)
        assertEquals(CanonicalSubjectHash.of(chain.root.subjectX500Principal), certificateIdentity.canonicalSubjectSha256Hex)
        assertNull(store.findIdentitiesBySkiHex("010203").toList().single().canonicalSubjectSha256Hex)
    }

    /** One TSP service with a certificate identity whose X509SubjectName differs from it, and an SKI-only one. */
    private fun tsl(certificateDerBase64: String, listedSubjectName: String) = """
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
              <ServiceStatus>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted</ServiceStatus>
              <ServiceDigitalIdentity>
                <DigitalId>
                  <X509Certificate>$certificateDerBase64</X509Certificate>
                  <X509SubjectName>$listedSubjectName</X509SubjectName>
                </DigitalId>
                <DigitalId><X509SKI>AQID</X509SKI></DigitalId>
              </ServiceDigitalIdentity>
            </ServiceInformation></TSPService></TSPServices>
          </TrustServiceProvider></TrustServiceProviderList>
        </TrustServiceStatusList>
    """.trimIndent()
}
