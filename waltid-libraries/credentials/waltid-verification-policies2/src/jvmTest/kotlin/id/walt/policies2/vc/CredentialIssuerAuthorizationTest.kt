package id.walt.policies2.vc

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.DidResolver
import id.walt.policies2.vc.policies.AllowedIssuerPolicy
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CredentialIssuerAuthorizationTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val did = "did:example:issuer-authorization"

    @Test
    fun `allowlist checks outer issuer with or without inner issuer`() = runTest {
        val key = key("assertion")
        withDocument(document(key)) {
            for (innerIssuer in listOf(false, true)) {
                val credential = credential(key, innerIssuer = innerIssuer)
                assertEquals(did, credential.issuer)
                assertTrue(CredentialSignaturePolicy().verify(credential).isSuccess)
                assertTrue(AllowedIssuerPolicy(JsonPrimitive(did)).verify(credential).isSuccess)
                assertTrue(AllowedIssuerPolicy(JsonPrimitive("did:example:other")).verify(credential).isFailure)
            }
            val missingIssuer = CredentialParser.parseOnly("""{
                "@context":["https://www.w3.org/2018/credentials/v1"],
                "type":["VerifiableCredential"],"credentialSubject":{"id":"did:example:holder"}
            }""")
            assertTrue(AllowedIssuerPolicy(JsonPrimitive(did)).verify(missingIssuer).isFailure)
        }
    }

    @Test
    fun `signature policy requires assertion authorization for JWT and SD JWT credentials`() = runTest {
        val assertion = key("assertion")
        val authentication = key("authentication")
        for (embedded in listOf(false, true)) {
            val document = document(assertion, authentication, embedded)
            withDocument(document) {
                for (sdJwt in listOf(false, true)) {
                    assertTrue(CredentialSignaturePolicy().verify(credential(assertion, sdJwt = sdJwt)).isSuccess)
                    assertTrue(CredentialSignaturePolicy().verify(credential(authentication, sdJwt = sdJwt)).isFailure)
                    assertTrue(CredentialSignaturePolicy().verify(credential(assertion, kid = "$did#missing", sdJwt = sdJwt)).isFailure)
                }
                // Generic DID resolution still exposes authentication keys to other consumers.
                assertEquals(if (embedded) 1 else 2, DidService.resolveToCrypto2Keys(did).getOrThrow().size)
            }
        }
        withDocument(JsonObject(document(assertion, authentication).filterKeys { it != "assertionMethod" })) {
            assertTrue(CredentialSignaturePolicy().verify(credential(assertion)).isFailure)
        }
    }

    private suspend fun key(id: String): Key = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        id = KeyId("$did#$id"), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun method(key: Key) = buildJsonObject {
        put("id", key.id.value.substringAfter(did))
        put("type", "JsonWebKey2020")
        put("controller", did)
        val publicKey = key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec)
        put("publicKeyJwk", Json.parseToJsonElement(publicKey.data.toByteArray().decodeToString()))
    }

    private suspend fun document(assertion: Key, authentication: Key? = null, embedded: Boolean = false): JsonObject {
        val assertionMethod = method(assertion)
        val authenticationMethod = authentication?.let { method(it) }
        return buildJsonObject {
            put("id", did)
            put("verificationMethod", buildJsonArray {
                if (!embedded) add(assertionMethod)
                authenticationMethod?.let { add(it) }
            })
            put("assertionMethod", buildJsonArray {
                add(if (embedded) assertionMethod else JsonPrimitive("#assertion"))
            })
            authentication?.let { put("authentication", buildJsonArray { add(JsonPrimitive(it.id.value)) }) }
        }
    }

    private suspend fun credential(
        key: Key, innerIssuer: Boolean = false, kid: String = key.id.value, sdJwt: Boolean = false,
    ): DigitalCredential {
        val payload = buildJsonObject {
            put("iss", did)
            if (sdJwt) {
                put("vct", "https://issuer.example/identity")
            } else put("vc", buildJsonObject {
                put("@context", buildJsonArray { add(JsonPrimitive("https://www.w3.org/2018/credentials/v1")) })
                put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")) })
                put("credentialSubject", buildJsonObject { put("id", "did:example:holder") })
                if (innerIssuer) put("issuer", did)
            })
        }
        val signed = CompactJws.sign(
            Json.encodeToString(payload).encodeToByteArray(), key, JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject { put("kid", kid); put("typ", if (sdJwt) "dc+sd-jwt" else "JWT") },
        )
        return CredentialParser.parseOnly(if (sdJwt) "$signed~" else signed)
    }

    private suspend fun withDocument(document: JsonObject, block: suspend () -> Unit) {
        val previous = DidService.resolverMethods["example"]
        DidService.registerResolverForMethod("example", object : DidResolver {
            override val name = "issuer-authorization-test"
            override suspend fun getSupportedMethods() = Result.success(setOf("example"))
            override suspend fun resolve(did: String) = Result.success(document)
            @Suppress("OVERRIDE_DEPRECATION")
            override suspend fun resolveToKey(did: String): Result<LegacyKey> = error("Legacy resolution must not be used")
            @Suppress("OVERRIDE_DEPRECATION")
            override suspend fun resolveToKeys(did: String): Result<Set<LegacyKey>> = error("Legacy resolution must not be used")
        })
        try { block() } finally {
            if (previous == null) DidService.resolverMethods.remove("example")
            else DidService.registerResolverForMethod("example", previous)
        }
    }
}
