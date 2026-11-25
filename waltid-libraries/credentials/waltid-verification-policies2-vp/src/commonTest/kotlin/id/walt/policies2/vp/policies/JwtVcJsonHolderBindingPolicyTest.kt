package id.walt.policies2.vp.policies

import id.walt.credentials.CredentialHolderBinding
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.W3C11
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JwtVcJsonHolderBindingPolicyTest {
    private fun credential(subject: String?, data: JsonObject = JsonObject(emptyMap())) = W3C11(
        credentialData = data, subject = subject, signature = null, signed = null,
    )

    @Test
    fun holderBindingIsRegisteredAndIncludedInDefaults() {
        assertTrue(VPVerificationPolicyManager.defaultJwtVcJsonPolicies.any { it is HolderBindingJwtVcJsonVPPolicy })
        val policies = Json.decodeFromString<VPPolicyList>("""{"jwt_vc_json":["jwt_vc_json/holder-binding"]}""")
        assertTrue(policies.jwtVcJson.single() is HolderBindingJwtVcJsonVPPolicy)
        val restored = Json.decodeFromString<VPPolicyList>(Json.encodeToString(policies))
        assertTrue(restored.jwtVcJson.single() is HolderBindingJwtVcJsonVPPolicy)
    }

    @Test
    fun authenticatedHolderCanPresentOwnCredential() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val kid = DidService.resolveAuthenticationMethodId(did, key.getKeyId())
        val payload = JsonObject(mapOf("iss" to JsonPrimitive(did)))
        val jwt = key.signJws(payload.toString().encodeToByteArray(), mapOf("kid" to JsonPrimitive(kid)))
        val presentation = JwtVcJsonPresentation(jwt, payload, did, null, null, null, listOf(credential(did)))
        assertTrue(HolderBindingJwtVcJsonVPPolicy().runPolicy(presentation, null).success)
    }

    @Test
    fun subjectFallbackUsesAuthenticatedSignerInsteadOfParsedIssuer() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val kid = DidService.resolveAuthenticationMethodId(did, key.getKeyId())
        val payload = JsonObject(mapOf("iss" to JsonPrimitive(did)))
        val jwt = key.signJws(payload.toString().encodeToByteArray(), mapOf("kid" to JsonPrimitive(kid)))
        val unverifiedIssuer = "did:example:unverified"
        val presentation = JwtVcJsonPresentation(jwt, payload, unverifiedIssuer, null, null, null,
            listOf(credential(unverifiedIssuer)))
        assertTrue(!HolderBindingJwtVcJsonVPPolicy().runPolicy(presentation, null).success)
        assertFailsWith<IllegalArgumentException> {
            CredentialHolderBinding.requireBoundToPresenter(credential(did), null, "unused")
        }
    }

    @Test
    fun missingOrDifferentSubjectCannotSatisfyBinding() = runTest {
        for (subject in listOf(null, "did:example:other")) {
            assertFailsWith<IllegalArgumentException> {
                CredentialHolderBinding.requireBoundToPresenter(credential(subject), "did:example:holder", "unused")
            }
        }
    }

    @Test
    fun unresolvedConfirmationCannotFallBackToSubject() = runTest {
        val did = "did:example:holder"
        val data = JsonObject(mapOf("cnf" to JsonObject(emptyMap())))
        assertFailsWith<IllegalArgumentException> {
            CredentialHolderBinding.requireBoundToPresenter(credential(did, data), did, "unused")
        }
    }

    @Test
    fun matchingKeyConfirmationBindsWithoutSubjectDid() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519).getPublicKey()
        val publicJwk = key.exportJWKObject()
        val data = JsonObject(mapOf("cnf" to JsonObject(mapOf("jwk" to publicJwk))))
        CredentialHolderBinding.requireBoundToPresenter(credential(null, data), "did:example:presenter", key.getThumbprint())
    }

    @Test
    fun wrappedCredentialUsesOuterConfirmationKey() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val payload = JsonObject(mapOf(
            "iss" to JsonPrimitive("did:example:issuer"),
            "cnf" to JsonObject(mapOf("jwk" to key.getPublicKey().exportJWKObject())),
            "vc" to JsonObject(mapOf(
                "@context" to JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))),
                "type" to JsonArray(listOf(JsonPrimitive("VerifiableCredential"))),
                "credentialSubject" to JsonObject(mapOf("id" to JsonPrimitive("urn:example:subject"))),
            )),
        ))
        val jwt = key.signJws(payload.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive("JWT")))
        val parsed = CredentialParser.detectAndParse(jwt).second
        CredentialHolderBinding.requireBoundToPresenter(parsed, "did:example:holder", key.getThumbprint())
    }

    @Test
    fun confirmationSupportsDifferentPublicKeyTypesAndIgnoresJwkMetadata() = runTest {
        // Public fixtures keep this key-comparison test independent of the iOS signing keychain.
        // Expected RFC 7638 values were independently calculated from the required JWK members.
        val publicKeys = listOf(
            """{"kty":"OKP","crv":"Ed25519","x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiYSkbL9b5BAsbk"}""" to "OWXv714j_6Ww9PZNLsMLS4KJP3hn1wi27euYltkBOeM",
            """{"kty":"EC","crv":"P-256","x":"uAMG3ffORZJAxox6jd9WqKVCyf-nzebhG64f4uGkw80","y":"PBd-jzVV8ZM01_6Pra2o2uG05-6yhRCw-6hGCJcOtKc"}""" to "ZDx9KDTstLgs6IbEYXN6JZIsOxGt8ZdvID1tSxJl1NM",
            """{"kty":"RSA","n":"uCXibTXC_yQ3LQiakyjGRoft03otjLmHFWSK7GMZvdpaL5uqt5OPUvfo7wlW8hywjcBxnJMLN4WoL1pjdS74bWYF2Fte4xHAoZsCQRffvv9aviK5QnW3qQGg95hk03ECowA0B1gb2GEQ5aoT1MpHZdBIBVmxcbHiYOtsoM85e7P7jJy6Z4QM6tQy26BImSlCuA6Zn4c4aK_-nzH3h0SNsChMJkx0TNvQkvKogwgOVw8NIGKkQstDPHudbIiLI0tPmD6co01rdhXo1kH-bD5cwbR9T66bLLsWtyq4VWj2CtkAyMtjb3AGhFNLK5kth20OUVIOe-AXtUm-6irMVFoIBQ","e":"AQAB"}""" to "MXRPLvpPIerzwiKjZeiGcyjeCbw6k2sYUQlNLXJ2rQc"
        )
        for ((publicKey, expectedThumbprint) in publicKeys) {
            val jwk = JsonObject(Json.parseToJsonElement(publicKey).jsonObject + ("kid" to JsonPrimitive("local-test-key")))
            val data = JsonObject(mapOf("cnf" to JsonObject(mapOf("jwk" to jwk))))
            try {
                CredentialHolderBinding.requireBoundToPresenter(credential(null, data), null, expectedThumbprint)
            } catch (cause: Exception) {
                throw AssertionError("Holder-key comparison failed for ${jwk["kty"]}/${jwk["crv"]}", cause)
            }
        }
    }

    @Test
    fun explicitConfirmationTakesPrecedenceOverMatchingSubject() = runTest {
        val did = "did:example:holder"
        val key = JWKKey.generate(KeyType.Ed25519).getPublicKey()
        val data = JsonObject(mapOf("cnf" to JsonObject(mapOf("jwk" to key.exportJWKObject()))))
        assertFailsWith<IllegalArgumentException> {
            CredentialHolderBinding.requireBoundToPresenter(credential(did, data), did, "different-thumbprint")
        }
    }

    @Test
    fun malformedAndUnsupportedConfirmationsFailClosed() = runTest {
        val did = "did:example:holder"
        for (confirmation in listOf(
            JsonPrimitive("not-an-object"),
            JsonObject(mapOf("jwk" to JsonPrimitive("not-a-key"))),
            JsonObject(mapOf("jkt" to JsonPrimitive("unsupported-reference"))),
            JsonObject(mapOf("kid" to JsonPrimitive(""))),
        )) {
            val data = JsonObject(mapOf("cnf" to confirmation))
            assertFailsWith<IllegalArgumentException> {
                CredentialHolderBinding.requireBoundToPresenter(credential(did, data), did, "unused")
            }
        }
    }

    @Test
    fun privateConfirmationMaterialIsRejected() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val data = JsonObject(mapOf("cnf" to JsonObject(mapOf("jwk" to key.exportJWKObject()))))
        assertFailsWith<IllegalArgumentException> {
            CredentialHolderBinding.requireBoundToPresenter(credential(null, data), "did:example:holder", key.getThumbprint())
        }
    }

    @Test
    fun conflictingSubjectRepresentationsAreRejected() = runTest {
        val did = "did:example:holder"
        val data = JsonObject(mapOf("credentialSubject" to JsonObject(mapOf("id" to JsonPrimitive("did:example:other")))))
        assertFailsWith<IllegalArgumentException> {
            CredentialHolderBinding.requireBoundToPresenter(credential(did, data), did, "unused")
        }
    }

    @Test
    fun nonDidSubjectCannotUseAnUnauthenticatedIssuerNameAsBinding() = runTest {
        val subject = "https://holder.example"
        assertFailsWith<IllegalArgumentException> {
            CredentialHolderBinding.requireBoundToPresenter(credential(subject), subject, "unused")
        }
    }

}
