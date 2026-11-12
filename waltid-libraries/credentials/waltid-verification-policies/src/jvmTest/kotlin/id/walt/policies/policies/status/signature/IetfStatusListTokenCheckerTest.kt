package id.walt.policies.policies.status.signature

import id.walt.cose.CoseHeaders
import id.walt.cose.toCoseSigner
import id.walt.credentials.formats.W3C11
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.policies.policies.status.status.IetfStatusListTokens
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IetfStatusListTokenCheckerTest {
    private val uri = "https://example.com/statuslists/1"

    @Test
    fun `checker rejects a signed jwt and cwt of the wrong type`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val keyId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
        val credential = W3C11(
            credentialData = buildJsonObject { put("iss", did) },
            signature = null,
            signed = null,
            issuer = did,
        )
        val checker = IetfStatusListTokenChecker()

        val jwt = assertFailsWith<StatusVerificationError> {
            checker.check(signedJwt(key, keyId, did), uri, credential, null)
        }
        assertEquals("IETF status-list JWT typ must be statuslist+jwt", jwt.message)

        val cwt = IetfStatusListTokens.signedCwt(
            uri = uri,
            signer = key.toCoseSigner(),
            protectedHeaders = CoseHeaders(algorithm = -8, kid = keyId.encodeToByteArray()),
            index = 0,
            status = 0u,
        )
        val cwtError = assertFailsWith<StatusVerificationError> {
            checker.check(cwt.toHexString(), uri, credential, null)
        }
        assertEquals("IETF status-list CWT type must be application/statuslist+cwt", cwtError.message)
    }

    private suspend fun signedJwt(key: JWKKey, keyId: String, did: String): String {
        val payload = buildJsonObject {
            put("iss", did)
            put("sub", uri)
            put("iat", 1_700_000_000)
            put("exp", 4_000_000_000)
        }
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "JWT")
            put("kid", keyId)
        }
        val signable = "${json.encodeToString(header).encodeToByteArray().encodeToBase64Url()}." +
            json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        return "$signable.${key.signRaw(signable.encodeToByteArray()).encodeToBase64Url()}"
    }

    private companion object {
        val json = Json
    }
}
