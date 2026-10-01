package id.walt.trust

import id.walt.trust.model.SourceAcceptancePolicy
import id.walt.trust.model.SourceLoadOptions
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Focused coverage for [DefaultTrustRegistryService]'s certificate-chain candidate narrowing:
 * the exact-pin path in isolation, that a realistic duplicate registration still surfaces
 * `MULTIPLE_MATCHES`, and absent-Authority-Key-Identifier handling. [Wal1186TrustRegistryTest]
 * already covers the common "leaf issued by a registered CA" path and the
 * fallback-when-narrowing-finds-nothing path incidentally.
 */
class CertificateChainNarrowingTest {

    @Test
    fun `trusts a leaf pinned directly with no issuing CA registered`() = runTest {
        val chain = TestCertificates.createChain("Pinned Leaf")
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        // Only the leaf itself is registered - no root/CA entry exists for an Authority/Subject
        // Key Identifier match to find, so this can only resolve via the exact-SHA-256-pin path.
        assertTrue(
            service.loadSourceFromContent(
                "pinned-leaf",
                lote("pinned-leaf" to chain.leaf),
                options = SourceLoadOptions(SourceAcceptancePolicy.ALLOW_UNSIGNED)
            ).success
        )

        val decision = service.resolveCertificateChain(
            certificateChainPemOrDer = listOf(TestCertificates.pem(chain.leaf)),
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
    }

    @Test
    fun `narrowing still reports MULTIPLE_MATCHES when the same anchor is registered under two entities`() = runTest {
        // The same root certificate registered twice (e.g. imported from two overlapping trust
        // lists), under two different entities. Both copies share the real root's Subject Key
        // Identifier, so narrowing's lookup finds both, not just one - the documented
        // narrowed-candidates trade-off (README: "a registry with unrelated cross-signed anchors
        // ... may report a single match via narrowing instead of MULTIPLE_MATCHES") does not
        // apply here, and this is the realistic shape that trade-off's name suggests: the JDK's
        // own PKIX path builder refuses to complete a path to any candidate whose Subject Key
        // Identifier does not match the presented chain's Authority Key Identifier, so the only
        // registry anchors narrowing could even miss are ones that would never independently
        // validate in the first place - the full scan it falls back from could not have found
        // them either.
        val chain = TestCertificates.createChain("Duplicate Registration")
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        assertTrue(
            service.loadSourceFromContent(
                "duplicate-registration",
                lote(
                    "duplicate-registration-a" to chain.root,
                    "duplicate-registration-b" to chain.root,
                ),
                options = SourceLoadOptions(SourceAcceptancePolicy.ALLOW_UNSIGNED)
            ).success
        )

        val decision = service.resolveCertificateChain(
            certificateChainPemOrDer = listOf(TestCertificates.pem(chain.leaf)),
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.MULTIPLE_MATCHES, decision.decision)
    }

    @Test
    fun `a presented certificate with no Authority Key Identifier falls back instead of throwing`() = runTest {
        val chain = TestCertificates.createChain("No AKI")
        val leafWithoutAki = TestCertificates.leafWithoutAuthorityKeyIdentifier(chain)
        val unrelated = TestCertificates.createChain("Unrelated Registry Entry")
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        // The registry has an entry, but not one related to leafWithoutAki at all - narrowing's
        // exact-pin and AKI/SKI lookups both come up empty (no AKI to extract), so this only
        // resolves correctly if the fallback to the exhaustive scan runs without throwing.
        assertTrue(
            service.loadSourceFromContent(
                "unrelated",
                lote("unrelated" to unrelated.root),
                options = SourceLoadOptions(SourceAcceptancePolicy.ALLOW_UNSIGNED)
            ).success
        )

        val decision = service.resolveCertificateChain(
            certificateChainPemOrDer = listOf(TestCertificates.pem(leafWithoutAki)),
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    private fun lote(vararg entities: Pair<String, X509Certificate>): String = buildJsonObject {
        put("LoTE", buildJsonObject {
            put("ListAndSchemeInformation", buildJsonObject {
                put("LoTEVersionIdentifier", JsonPrimitive(1))
                put("LoTESequenceNumber", JsonPrimitive(1))
                put("LoTEType", JsonPrimitive("http://uri.etsi.org/19602/LoTEType/EUPIDProvidersList"))
                put("SchemeOperatorName", buildJsonArray { add(multilingual("Narrowing Test Operator")) })
                put("SchemeTerritory", JsonPrimitive("AT"))
                put("ListIssueDateTime", JsonPrimitive("2026-01-01T00:00:00Z"))
                put("NextUpdate", JsonPrimitive("2099-01-01T00:00:00Z"))
            })
            put("TrustedEntitiesList", buildJsonArray {
                entities.forEach { (uriSuffix, cert) ->
                    add(buildJsonObject {
                        put("TrustedEntityInformation", buildJsonObject {
                            put("TEName", buildJsonArray { add(multilingual("Narrowing Test Entity $uriSuffix")) })
                            put("TEAddress", buildJsonObject {
                                put("TEPostalAddress", buildJsonArray {
                                    add(buildJsonObject {
                                        put("lang", JsonPrimitive("en"))
                                        put("StreetAddress", JsonPrimitive("Example street 1"))
                                        put("Locality", JsonPrimitive("Vienna"))
                                        put("Country", JsonPrimitive("AT"))
                                    })
                                })
                                put("TEElectronicAddress", buildJsonArray {
                                    add(buildJsonObject {
                                        put("lang", JsonPrimitive("en"))
                                        put("uriValue", JsonPrimitive("https://example.org"))
                                    })
                                })
                            })
                            put("TEInformationURI", buildJsonArray {
                                add(buildJsonObject {
                                    put("lang", JsonPrimitive("en"))
                                    put("uriValue", JsonPrimitive("https://example.org/ListOfTrustedEntities/$uriSuffix"))
                                })
                            })
                        })
                        put("TrustedEntityServices", buildJsonArray {
                            add(buildJsonObject {
                                put("ServiceInformation", buildJsonObject {
                                    put("ServiceName", buildJsonArray { add(multilingual("Test CA $uriSuffix")) })
                                    put("ServiceDigitalIdentity", buildJsonObject {
                                        put("X509Certificates", buildJsonArray {
                                            add(buildJsonObject {
                                                put("val", JsonPrimitive(TestCertificates.derBase64(cert)))
                                            })
                                        })
                                    })
                                    put("ServiceTypeIdentifier", JsonPrimitive("http://uri.etsi.org/TrstSvc/Svctype/CA/QC"))
                                    put("ServiceStatus", JsonPrimitive("http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted"))
                                })
                            })
                        })
                    })
                }
            })
        })
    }.toString()

    private fun multilingual(value: String) = buildJsonObject {
        put("lang", JsonPrimitive("en"))
        put("value", JsonPrimitive(value))
    }
}
