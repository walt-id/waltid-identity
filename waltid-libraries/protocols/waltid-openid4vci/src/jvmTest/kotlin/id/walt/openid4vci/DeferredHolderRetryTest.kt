package id.walt.openid4vci

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.deferred.*
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.requests.credential.*
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class DeferredHolderRetryTest {
    @Test fun canonicalKeysMatchWhileHolderIdentityAndBatchAssignmentsRemainBound() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1).getPublicKey().exportJWKObject()
        val another = JWKKey.generate(KeyType.secp256r1).getPublicKey().exportJWKObject()
        var snapshot = DeferredSessionSnapshot("offer", Clock.System.now() + 1.days, DeferredIssuanceState())
        val store = object : DeferredIssuanceStore {
            override suspend fun get(id: String) = snapshot
            override suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean {
                if (snapshot != expected) return false
                snapshot = snapshot.copy(state = replacement)
                return true
            }
        }
        val original = AcceptedCredentialRequest(
            DefaultCredentialRequest(
                client = DefaultClient("wallet", emptyList(), emptySet(), emptySet()),
                credentialIdentifier = "credential", credentialConfigurationId = null, proofs = null,
                credentialResponseEncryption = null, accessTokenClientId = "wallet",
            ), "credential", "config", CredentialConfiguration(CredentialFormat.SD_JWT_VC, vct = "https://example.com/type"),
            listOf(AcceptedHolderBinding(key, "did:example:holder#key", "did:example:holder", setOf(0)), AcceptedHolderBinding(another, proofIndexes = setOf(1))),
        )
        val service = DeferredIssuance(store)
        val transaction = service.accept("offer", original)
        val encryption = CredentialResponseEncryptionParameters(JsonObject(another + mapOf("alg" to JsonPrimitive("ECDH-ES"), "use" to JsonPrimitive("enc"))), "A128GCM")
        val retry = original.copy(
            request = original.request.copy(id = "new-request", requestedAt = Clock.System.now(), credentialResponseEncryption = encryption,
                requestForm = mapOf("credential_response_encryption" to listOf(Json.encodeToString(encryption)))),
            bindings = original.bindings.map { it.copy(publicJwk = JsonObject(it.publicJwk.entries.reversed().associate { it.toPair() } + ("kid" to JsonPrimitive("new-jwk-label")))) },
        )
        assertEquals(transaction, DeferredIssuance(store).accept("offer", retry))
        val saved = snapshot
        val first = original.bindings.first()
        val conflicts = listOf(
            original.copy(bindings = listOf(first.copy(publicJwk = another), original.bindings.last())),
            original.copy(bindings = listOf(first.copy(holderKid = "did:example:holder#other"), original.bindings.last())),
            original.copy(bindings = listOf(first.copy(holderDid = "did:example:other"), original.bindings.last())),
            original.copy(bindings = listOf(first.copy(proofIndexes = setOf(1)), original.bindings.last())),
            original.copy(bindings = original.bindings.reversed()),
            original.copy(bindings = listOf(first)),
        )
        for (conflict in conflicts) {
            assertEquals("invalid_credential_request", assertFailsWith<DeferredProtocolException> { service.accept("offer", conflict) }.error.error)
            assertEquals(saved, snapshot)
        }
    }
}
