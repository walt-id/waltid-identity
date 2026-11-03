package id.walt.policies2.vc.status.signature

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseSigner
import id.walt.credentials.formats.W3C11
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.bit.BitValueReaderFactory
import id.walt.policies2.vc.policies.status.content.CwtParser
import id.walt.policies2.vc.policies.status.content.JwtParser
import id.walt.policies2.vc.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.model.IETFStatusContent
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.reader.IETFCwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.IETFJwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.format.CwtFormatMatcher
import id.walt.policies2.vc.policies.status.reader.format.JwtFormatMatcher
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.validator.IETFStatusValidator
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.model.IndexedStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IetfStatusListTokenValidationTest {
    private val uri = "https://example.com/statuslists/1"
    private lateinit var did: String
    private lateinit var key: JWKKey
    private lateinit var keyId: String
    private lateinit var validator: IETFStatusValidator
    private val fetcher = mockk<CredentialFetcher>()

    @BeforeTest
    fun setup() = runTest {
        DidService.minimalInit()
        key = JWKKey.generate(KeyType.Ed25519)
        did = DidService.registerByKey("key", key).did
        keyId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
        validator = IETFStatusValidator(
            fetcher = fetcher,
            bitValueReaderFactory = BitValueReaderFactory(),
            expansionAlgorithm = TokenStatusListExpansionAlgorithm(),
            IETFJwtStatusValueReader(JwtFormatMatcher(), JwtParser()),
            IETFCwtStatusValueReader(CwtFormatMatcher(), CwtParser()),
            signatureVerifier = StatusListSignatureVerifier(),
        )
    }

    @Test
    fun `signed jwt and cwt status lists are accepted when subject and expiry match`() = runTest {
        assertTrue(validate(StatusListContent.Text(signedJwt(sub = uri, exp = FAR_FUTURE))).isSuccess)
        assertTrue(validate(StatusListContent.Binary(signedCwt(sub = uri, exp = FAR_FUTURE))).isSuccess)
    }

    @Test
    fun `forged cwt subject is rejected after the signature verifies`() = runTest {
        val result = validate(StatusListContent.Binary(signedCwt(sub = "https://evil.example/list", exp = FAR_FUTURE)))
        assertTrue(result.isFailure)
        assertEquals(
            "IETF status-list token subject does not match referenced status-list URI",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `expired jwt and cwt status lists are rejected`() = runTest {
        val jwt = validate(StatusListContent.Text(signedJwt(sub = uri, exp = EXPIRED)))
        val cwt = validate(StatusListContent.Binary(signedCwt(sub = uri, exp = EXPIRED)))
        assertEquals("IETF status-list token is expired", jwt.exceptionOrNull()?.message)
        assertEquals("IETF status-list token is expired", cwt.exceptionOrNull()?.message)
    }

    @Test
    fun `unsigned status list text is rejected`() = runTest {
        val result = validate(StatusListContent.Text("not-a-status-list"))
        assertTrue(result.isFailure)
        assertEquals(
            "Unsupported unsigned status-list text format",
            result.exceptionOrNull()?.message,
        )
    }

    private suspend fun validate(content: StatusListContent) = run {
        coEvery { fetcher.fetch(uri) } returns Result.success(content)
        validator.validate(
            entry = IETFEntry(IETFEntry.StatusListField(index = 0u, uri = uri)),
            attribute = IETFStatusPolicyAttribute(value = 0u),
            referencedCredential = W3C11(
                credentialData = buildJsonObject { put("iss", did) },
                signature = null,
                signed = null,
                issuer = did,
            ),
            signerAuthorizer = null,
        )
    }

    private suspend fun signedJwt(sub: String, exp: Long): String {
        val payload = buildJsonObject {
            put("iss", did)
            put("sub", sub)
            put("iat", 1_700_000_000)
            put("exp", exp)
            put("status_list", buildJsonObject {
                put("bits", 1)
                put("lst", validList())
            })
        }
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "statuslist+jwt")
            put("kid", keyId)
        }
        val signable = "${json.encodeToString(header).encodeToByteArray().encodeToBase64Url()}." +
            json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
        val signature = key.signRaw(signable.encodeToByteArray()).encodeToBase64Url()
        return "$signable.$signature"
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun signedCwt(sub: String, exp: Long): ByteArray {
        val payload = coseCompliantCbor.encodeToByteArray(
            CwtToken(
                sub = sub,
                exp = exp,
                iat = 1_700_000_000,
                statusList = IETFStatusContent(size = 1, list = validList()),
            )
        )
        return CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = -8, kid = keyId.encodeToByteArray()),
            payload = payload,
            signer = key.toCoseSigner(),
        ).toTagged()
    }

    private fun validList(): String = TokenStatusListCodec.encode(
        statuses = listOf(IndexedStatus(index = 0, value = 0u)),
        statusSize = 1,
        listSizeBytes = 16,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class CwtToken(
        @CborLabel(2) val sub: String,
        @CborLabel(4) val exp: Long,
        @CborLabel(6) val iat: Long,
        @CborLabel(65533) val statusList: IETFStatusContent,
    )

    private companion object {
        const val FAR_FUTURE = 4_000_000_000L
        const val EXPIRED = 1_700_000_000L
        val json = Json
    }
}
