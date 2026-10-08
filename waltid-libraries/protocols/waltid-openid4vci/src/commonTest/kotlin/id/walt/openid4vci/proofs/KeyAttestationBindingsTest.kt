package id.walt.openid4vci.proofs

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.DidResolver
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.attestation.VerifiedKeyAttestation
import id.walt.openid4vci.proofs.attestation.resolveBindings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class KeyAttestationBindingsTest {
    private val method = "attestationtest"
    private val did = "did:$method:holder"
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val configuration = CredentialConfiguration(
        CredentialFormat.JWT_VC_JSON,
        cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Did(method)),
        proofTypesSupported = mapOf(ProofType.ATTESTATION.value to ProofTypeMetadata(setOf("ES256"))),
    )

    @Test
    fun `same DID resolves once per call and retains each verification method binding`() = runTest {
        val first = key("first")
        val second = key("second")
        val attestation = attestation(first to "$did#first", second to "$did#second")
        withResolver({ document("$did#first" to first, "#second" to second) }) { calls ->
            repeat(2) {
                val bindings = attestation.resolveBindings(configuration)
                assertEquals(listOf("$did#first", "$did#second"), bindings.map { it.holderKid })
                assertEquals(listOf(did, did), bindings.map { it.holderDid })
                assertEquals(listOf(first, second), bindings.map { it.holderKey })
                assertEquals(List(it + 1) { did }, calls)
            }
        }
    }

    @Test
    fun `distinct DIDs resolve independently`() = runTest {
        val first = key("first")
        val second = key("second")
        val otherDid = "did:$method:other"
        withResolver({ resolvedDid ->
            document("$resolvedDid#key" to if (resolvedDid == did) first else second)
        }) { calls ->
            val bindings = attestation(first to "$did#key", second to "$otherDid#key")
                .resolveBindings(configuration)
            assertEquals(listOf(did, otherDid), bindings.map { it.holderDid })
            assertEquals(listOf(did, otherDid), calls)
        }
    }

    @Test
    fun `reused resolution still rejects missing verification methods and mismatched keys`() = runTest {
        val first = key("first")
        val second = key("second")
        for ((kid, expectedMessage) in listOf(
            "$did#missing" to "Attested JWK kid must identify one DID verification method",
            "$did#second" to "Attested JWK does not match its DID verification method",
        )) {
            withResolver({ document("$did#first" to first, "$did#second" to first) }) { calls ->
                val error = assertFailsWith<CredentialProofValidationException> {
                    attestation(first to "$did#first", second to kid).resolveBindings(configuration)
                }
                assertEquals(expectedMessage, error.message)
                assertEquals(listOf(did), calls)
            }
        }
    }

    @Test
    fun `resolution errors remain invalid proofs`() = runTest {
        val holder = key("holder")
        withResolver({ error("DID unavailable") }) { calls ->
            val error = assertFailsWith<CredentialProofValidationException> {
                attestation(holder to "$did#key").resolveBindings(configuration)
            }
            assertEquals("Could not resolve attested key DID", error.message)
            assertEquals(listOf(did), calls)
        }
    }

    @Test
    fun `resolution cancellation propagates`() = runTest {
        val holder = key("holder")
        val cancellation = CancellationException("Resolution cancelled")
        withResolver({ throw cancellation }) { calls ->
            assertSame(cancellation, assertFailsWith<CancellationException> {
                attestation(holder to "$did#key").resolveBindings(configuration)
            })
            assertEquals(listOf(did), calls)
        }
    }

    private suspend fun key(id: String) = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun attestation(vararg holders: Pair<Key, String>) = VerifiedKeyAttestation(
        jwt = "unused", header = buildJsonObject {}, attesterKey = holders.first().first,
        payload = buildJsonObject {
            put("attested_keys", JsonArray(holders.map { (key, kid) ->
                JsonObject(key.exportPublicJwkObject() + ("kid" to JsonPrimitive(kid)))
            }))
        },
        attestedKeys = holders.map { it.first },
    )

    private suspend fun document(vararg methods: Pair<String, Key>) = buildJsonObject {
        put("verificationMethod", JsonArray(methods.map { (id, key) ->
            buildJsonObject {
                put("id", id)
                put("publicKeyJwk", key.exportPublicJwkObject())
            }
        }))
    }

    private suspend fun withResolver(
        resolveDocument: suspend (String) -> JsonObject,
        block: suspend (List<String>) -> Unit,
    ) {
        val calls = mutableListOf<String>()
        val resolver = object : DidResolver {
            override val name = "attestation binding test"
            override suspend fun getSupportedMethods() = Result.success(setOf(method))
            override suspend fun resolve(did: String): Result<JsonObject> {
                calls.add(did)
                return Result.success(resolveDocument(did))
            }
            @Deprecated("Unused legacy resolver API")
            override suspend fun resolveToKey(did: String): Result<id.walt.crypto.keys.Key> = error("Unused")
            @Deprecated("Unused legacy resolver API")
            override suspend fun resolveToKeys(did: String): Result<Set<id.walt.crypto.keys.Key>> = error("Unused")
        }
        val previous = DidService.registerResolverForMethod(method, resolver)
        try {
            block(calls)
        } finally {
            if (previous == null) DidService.resolverMethods.remove(method)
            else DidService.registerResolverForMethod(method, previous)
        }
    }
}
