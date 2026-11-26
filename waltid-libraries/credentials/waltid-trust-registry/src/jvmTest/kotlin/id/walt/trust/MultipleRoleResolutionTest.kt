package id.walt.trust

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.ServiceIdentity
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.SourceFamily
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustSource
import id.walt.trust.model.TrustStatus
import id.walt.trust.model.TrustedEntity
import id.walt.trust.model.TrustedEntityType
import id.walt.trust.model.TrustedService
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import id.walt.trust.utils.CanonicalSubjectHash
import id.walt.trust.utils.HashUtils
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A certificate listed for several entities is ambiguous only within the role the caller asks for. Every case runs
 * through the certificate chain, certificate SHA-256 and public-key lookups, which share that rule.
 */
class MultipleRoleResolutionTest {

    private val now = Clock.System.now()
    private val chain = TestCertificates.createChain("Dual Listed")

    @Test
    fun expectedRelyingPartyResolvesToTheRelyingPartyListing() = runTest {
        registry(pidListing, rpListing).assertEachLookup(TrustedEntityType.RELYING_PARTY_PROVIDER) { _, decision ->
            assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
            assertEquals(RP_ENTITY, decision.matchedEntity?.entityId)
            assertEquals(RP_SOURCE, decision.matchedSource?.sourceId)
        }
    }

    @Test
    fun expectedPidProviderResolvesToThePidListing() = runTest {
        registry(pidListing, rpListing).assertEachLookup(TrustedEntityType.PID_PROVIDER) { _, decision ->
            assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
            assertEquals(PID_ENTITY, decision.matchedEntity?.entityId)
            assertEquals(PID_SOURCE, decision.matchedSource?.sourceId)
        }
    }

    @Test
    fun withoutAnExpectedTypeBothListingsStayAmbiguous() = runTest {
        registry(pidListing, rpListing).assertEachLookup(expectedEntityType = null) { _, decision ->
            assertEquals(TrustDecisionCode.MULTIPLE_MATCHES, decision.decision)
        }
    }

    @Test
    fun aRoleNoListingHasIsATypeMismatchNotAnAmbiguity() = runTest {
        registry(pidListing, rpListing).assertEachLookup(TrustedEntityType.WALLET_PROVIDER) { lookup, decision ->
            assertDenied(lookup, decision, "TYPE_MISMATCH")
        }
    }

    @Test
    fun anExpiredListingInTheRequestedRoleIsStaleEvenWhenTheOtherRoleIsFresh() = runTest {
        val expiredRp = rpListing.copy(nextUpdate = now - 1.days)

        registry(pidListing, expiredRp).assertEachLookup(TrustedEntityType.RELYING_PARTY_PROVIDER) { _, decision ->
            assertEquals(TrustDecisionCode.STALE_SOURCE, decision.decision)
            assertEquals(RP_SOURCE, decision.matchedSource?.sourceId)
        }
    }

    @Test
    fun anUnacceptedListingInTheRequestedRoleIsNotReplacedByTheOtherRole() = runTest {
        val unacceptedRp = rpListing.copy(accepted = false)

        registry(pidListing, unacceptedRp).assertEachLookup(TrustedEntityType.RELYING_PARTY_PROVIDER) { lookup, decision ->
            assertDenied(lookup, decision, "SOURCE_NOT_ACCEPTED")
        }
    }

    @Test
    fun aRoleNoListingHasIsATypeMismatchEvenWhenAListingIsNotAccepted() = runTest {
        registry(pidListing.copy(accepted = false), rpListing)
            .assertEachLookup(TrustedEntityType.WALLET_PROVIDER) { lookup, decision ->
                assertDenied(lookup, decision, "TYPE_MISMATCH")
            }
    }

    @Test
    fun twoListingsOfTheRequestedRoleStayAmbiguous() = runTest {
        registry(rpListing, secondRpListing).assertEachLookup(TrustedEntityType.RELYING_PARTY_PROVIDER) { _, decision ->
            assertEquals(TrustDecisionCode.MULTIPLE_MATCHES, decision.decision)
            val evidence = decision.evidence.single { it.type == "MULTIPLE_ENTITIES" }.value
            assertTrue("entity type RELYING_PARTY_PROVIDER" in evidence, evidence)
        }
    }

    @Test
    fun anIdentityWhoseEntityIsMissingIsNotAnAmbiguity() = runTest {
        registry(rpListing, secondRpListing.copy(entityStored = false)).assertEachLookup(expectedEntityType = null) { _, decision ->
            assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
            assertEquals(RP_ENTITY, decision.matchedEntity?.entityId)
        }
    }

    @Test
    fun aListingOfTheRequestedRoleFromAnUnacceptedSourceIsNotAnAmbiguity() = runTest {
        registry(rpListing, secondRpListing.copy(accepted = false))
            .assertEachLookup(TrustedEntityType.RELYING_PARTY_PROVIDER) { _, decision ->
                assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
                assertEquals(RP_ENTITY, decision.matchedEntity?.entityId)
            }
    }

    /** Without filters too, a source that is not accepted never decides trust, so it cannot make a listing ambiguous. */
    @Test
    fun withoutAnExpectedTypeAListingFromAnUnacceptedSourceIsNotAnAmbiguity() = runTest {
        registry(pidListing.copy(accepted = false), rpListing).assertEachLookup(expectedEntityType = null) { _, decision ->
            assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
            assertEquals(RP_ENTITY, decision.matchedEntity?.entityId)
        }
    }

    @Test
    fun aListingWithoutAServiceDoesNotHaveTheExpectedServiceType() = runTest {
        val revocation = pidListing.copy(
            sourceId = "pid-revocation-list", entityId = "pid-revocation-provider", serviceType = PID_REVOCATION_SERVICE
        )

        registry(pidListing.copy(serviceType = null), revocation)
            .assertEachLookup(TrustedEntityType.PID_PROVIDER, PID_REVOCATION_SERVICE) { _, decision ->
                assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
                assertEquals("pid-revocation-provider", decision.matchedEntity?.entityId)
            }
    }

    @Test
    fun expectedServiceTypeSelectsBetweenListingsOfTheSameEntityType() = runTest {
        val revocation = pidListing.copy(
            sourceId = "pid-revocation-list", entityId = "pid-revocation-provider", serviceType = PID_REVOCATION_SERVICE
        )

        registry(pidListing, revocation).assertEachLookup(TrustedEntityType.PID_PROVIDER, PID_REVOCATION_SERVICE) { _, decision ->
            assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
            assertEquals("pid-revocation-provider", decision.matchedEntity?.entityId)
            assertEquals(PID_REVOCATION_SERVICE, decision.matchedService?.serviceType)
        }
    }

    /** The quickstart verifier: its leaf is listed as a relying party, and its IACA as a PID provider. */
    @Test
    fun aChainWhoseLeafAndRootHaveDifferentRolesResolvesEachRole() = runTest {
        val registry = registry(pidListing, rpListing.copy(certificate = chain.leaf))
        val presented = listOf(chain.leaf, chain.root).map(TestCertificates::derBase64)

        val asRelyingParty = registry.resolveCertificateChain(presented, now, TrustedEntityType.RELYING_PARTY_PROVIDER, null)
        val asPidProvider = registry.resolveCertificateChain(presented, now, TrustedEntityType.PID_PROVIDER, null)

        assertEquals(TrustDecisionCode.TRUSTED, asRelyingParty.decision, asRelyingParty.toString())
        assertEquals(RP_ENTITY, asRelyingParty.matchedEntity?.entityId)
        assertEquals(TrustDecisionCode.TRUSTED, asPidProvider.decision, asPidProvider.toString())
        assertEquals(PID_ENTITY, asPidProvider.matchedEntity?.entityId)
    }

    private enum class Lookup { CERTIFICATE_CHAIN, CERTIFICATE_SHA256, PUBLIC_KEY }

    /**
     * One accepted source per listing, each listing the certificate for one entity with one granted service, or with
     * no service when [serviceType] is `null`. Without [entityStored], the identity points to an entity the store
     * does not have.
     */
    private data class Listing(
        val sourceId: String,
        val entityId: String,
        val entityType: TrustedEntityType,
        val serviceType: String?,
        val certificate: X509Certificate,
        val nextUpdate: Instant,
        val accepted: Boolean = true,
        val entityStored: Boolean = true,
    )

    private val pidListing = Listing(
        PID_SOURCE, PID_ENTITY, TrustedEntityType.PID_PROVIDER, PID_ISSUANCE_SERVICE, chain.root, now + 30.days
    )
    private val rpListing = Listing(
        RP_SOURCE, RP_ENTITY, TrustedEntityType.RELYING_PARTY_PROVIDER, RP_SERVICE, chain.root, now + 30.days
    )
    private val secondRpListing = rpListing.copy(sourceId = "second-rp-list", entityId = "second-relying-party")

    private suspend fun registry(vararg listings: Listing): DefaultTrustRegistryService {
        val store = InMemoryTrustStore()
        listings.forEach { listing ->
            val der = TestCertificates.derBase64(listing.certificate)
            store.replaceSourceData(
                TrustSource(
                    listing.sourceId, SourceFamily.LOTE, listing.sourceId, nextUpdate = listing.nextUpdate,
                    assurance = SourceAssurance(
                        authenticityState = AuthenticityState.AUTHENTICATED, accepted = listing.accepted
                    ),
                ),
                listOfNotNull(
                    TrustedEntity(listing.entityId, listing.sourceId, listing.entityType, listing.entityId)
                        .takeIf { listing.entityStored }
                ),
                listOfNotNull(
                    listing.serviceType?.let {
                        TrustedService(
                            "${listing.entityId}-service", listing.sourceId, listing.entityId, it, TrustStatus.GRANTED
                        )
                    }
                ),
                listOf(
                    ServiceIdentity(
                        "${listing.entityId}-identity", listing.sourceId, listing.entityId,
                        listing.serviceType?.let { "${listing.entityId}-service" },
                        certificateDerBase64 = der,
                        certificateSha256Hex = HashUtils.computeCertificateSha256(der),
                        publicKeyJwkThumbprint = HashUtils.computeJwkSha256Thumbprint(
                            Json.parseToJsonElement(jwk(listing.certificate))
                        ),
                        canonicalSubjectSha256Hex = CanonicalSubjectHash.ofCertificate(der),
                    )
                ),
            )
        }
        return DefaultTrustRegistryService(store)
    }

    /** Resolves the root through each lookup; the chain lookup presents the leaf the root issued. */
    private suspend fun DefaultTrustRegistryService.assertEachLookup(
        expectedEntityType: TrustedEntityType?,
        expectedServiceType: String? = null,
        assertion: (Lookup, TrustDecision) -> Unit,
    ) {
        for (lookup in Lookup.entries) {
            val decision = when (lookup) {
                Lookup.CERTIFICATE_CHAIN -> resolveCertificateChain(
                    listOf(TestCertificates.derBase64(chain.leaf)), now, expectedEntityType, expectedServiceType
                )
                Lookup.CERTIFICATE_SHA256 -> resolveByCertificateSha256(
                    checkNotNull(HashUtils.computeCertificateSha256(TestCertificates.derBase64(chain.root))),
                    now, expectedEntityType, expectedServiceType,
                )
                Lookup.PUBLIC_KEY -> resolveByPublicKey(jwk(chain.root), now, expectedEntityType, expectedServiceType)
            }
            try {
                assertion(lookup, decision)
            } catch (failure: AssertionError) {
                throw AssertionError("$lookup lookup: ${failure.message}\n$decision", failure)
            }
        }
    }

    /**
     * The chain lookup reports only that no eligible path was found, so the denial reason is checked on the
     * certificate and public-key lookups.
     */
    private fun assertDenied(lookup: Lookup, decision: TrustDecision, evidenceType: String) {
        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
        if (lookup != Lookup.CERTIFICATE_CHAIN) {
            assertTrue(decision.evidence.any { it.type == evidenceType }, "expected $evidenceType evidence")
        }
    }

    private fun jwk(certificate: X509Certificate): String {
        val point = (certificate.publicKey as ECPublicKey).w
        fun coordinate(value: BigInteger): String {
            val bytes = value.toByteArray().takeLast(32).toByteArray()
            return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32 - bytes.size) + bytes)
        }
        return """{"kty":"EC","crv":"P-256","x":"${coordinate(point.affineX)}","y":"${coordinate(point.affineY)}"}"""
    }

    private companion object {
        const val PID_SOURCE = "pid-list"
        const val PID_ENTITY = "pid-provider"
        const val PID_ISSUANCE_SERVICE = "pid-issuance"
        const val PID_REVOCATION_SERVICE = "pid-revocation"
        const val RP_SOURCE = "rp-list"
        const val RP_ENTITY = "relying-party"
        const val RP_SERVICE = "relying-party-access"
    }
}
