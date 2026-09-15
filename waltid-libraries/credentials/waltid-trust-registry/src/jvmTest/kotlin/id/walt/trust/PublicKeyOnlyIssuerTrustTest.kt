package id.walt.trust

import id.walt.trust.model.SourceAcceptancePolicy
import id.walt.trust.model.SourceLoadOptions
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.parser.lote.LoteJsonParser
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import id.walt.trust.utils.HashUtils
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Regression tests covering ETSI TS 119 602 trust list entities that are registered by
 * public key only, with no X.509 certificate — a registration method the standard
 * explicitly allows for `ServiceDigitalIdentity` (`PublicKeyValues`), but which was not
 * resolvable by the trust registry: the LoTE JSON parser never populated
 * `ServiceIdentity.publicKeyJwkThumbprint`, and `resolveByPublicKey` was an unimplemented
 * stub that always returned `UNSUPPORTED_SOURCE`.
 */
class PublicKeyOnlyIssuerTrustTest {

    // Synthetic EC P-256 test key — not tied to any real issuer or deployment.
    private val issuerJwk = """
        {"kty":"EC","crv":"P-256","x":"tR18QswxSXP7FHeSosEv_QrwG0WSav0cCfHFzX4_CGU","y":"OB5i8e_3dDnk6mP6GmSNI4fYfktLRriBBkFLXCCJQJM"}
    """.trimIndent()

    // Independently computed RFC 7638 SHA-256 thumbprint of the JWK above.
    private val expectedThumbprint = "3NXehEmAD9njt2Tu0uuonzVoqrhLPdqtgYUG-D4vr2o"

    @Test
    fun `computes the RFC 7638 thumbprint of a public-key-only trust list entry`() {
        val thumbprint = HashUtils.computeJwkSha256Thumbprint(Json.parseToJsonElement(issuerJwk))
        assertEquals(expectedThumbprint, thumbprint)
    }

    @Test
    fun `LoTE JSON parser populates publicKeyJwkThumbprint for PublicKeyValues identities`() {
        val parsed = LoteJsonParser.parse(lote(), sourceId = "public-key-only-source")
        val identity = parsed.identities.single()
        assertEquals(expectedThumbprint, identity.publicKeyJwkThumbprint)
    }

    @Test
    fun `resolves trust for an issuer registered by public key only`() = runTest {
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        val result = service.loadSourceFromContent(
            sourceId = "public-key-only-source",
            content = lote(),
            options = SourceLoadOptions(SourceAcceptancePolicy.ALLOW_UNSIGNED)
        )
        assertTrue(result.success, result.error)

        val decision = service.resolveByPublicKey(
            jwk = issuerJwk,
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals("Example Credential Issuer", decision.matchedEntity?.legalName)
    }

    @Test
    fun `rejects a public key that is not registered in the trust list`() = runTest {
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        val result = service.loadSourceFromContent(
            sourceId = "public-key-only-source",
            content = lote(),
            options = SourceLoadOptions(SourceAcceptancePolicy.ALLOW_UNSIGNED)
        )
        assertTrue(result.success, result.error)

        val unrelatedJwk = """{"kty":"EC","crv":"P-256","x":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","y":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}"""
        val decision = service.resolveByPublicKey(
            jwk = unrelatedJwk,
            instant = Clock.System.now()
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    private fun lote(): String = """
        {
          "LoTE": {
            "ListAndSchemeInformation": {
              "LoTEVersionIdentifier": 1,
              "LoTESequenceNumber": 1,
              "LoTEType": "https://trust.example.org/ns/lote-type/issuer-list",
              "SchemeOperatorName": [{"lang": "en", "value": "Example Trust Scheme Operator"}],
              "SchemeTerritory": "US",
              "ListIssueDateTime": "2026-01-01T00:00:00Z",
              "NextUpdate": "2099-01-01T00:00:00Z"
            },
            "TrustedEntitiesList": [
              {
                "TrustedEntityInformation": {
                  "TEName": [{"lang": "en", "value": "Example Credential Issuer"}],
                  "TEAddress": {
                    "TEPostalAddress": [{"lang": "en", "StreetAddress": "1 Example Street", "Locality": "Example City", "Country": "US"}],
                    "TEElectronicAddress": [{"lang": "en", "uriValue": "mailto:trust@example.org"}]
                  },
                  "TEInformationURI": [{"lang": "en", "uriValue": "https://trust.example.org/ListOfTrustedEntities/example-issuer"}]
                },
                "TrustedEntityServices": [
                  {
                    "ServiceInformation": {
                      "ServiceName": [{"lang": "en", "value": "SD-JWT Credential Issuance"}],
                      "ServiceDigitalIdentity": {
                        "PublicKeyValues": [$issuerJwk]
                      },
                      "ServiceTypeIdentifier": "https://trust.example.org/ns/svc-type/issuer/credential-issuance",
                      "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                      "StatusStartingTime": "2026-01-01T00:00:00Z"
                    }
                  }
                ]
              }
            ]
          }
        }
    """.trimIndent()
}
