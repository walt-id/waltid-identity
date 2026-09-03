package id.walt.openid4vci.handlers.credential

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.x509.CertificateDer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalSerializationApi::class)
class MdocCredentialHandlerValidUntilAuthorityTest {

    @Test
    fun `holder requestForm validUntil does not override configured expiry`() = runTest {
        val now = Clock.System.now()
        val configuredValidUntil = now.plus(365.days)
        val holderValidUntil = now.plus(3650.days)

        val result = MdocCredentialHandler().sign(
            request = DefaultCredentialRequest(
                client = DefaultClient(
                    id = "test-client",
                    redirectUris = emptyList(),
                    grantTypes = emptySet(),
                    responseTypes = emptySet(),
                ),
                credentialIdentifier = null,
                credentialConfigurationId = DOC_TYPE,
                proofs = null,
                credentialResponseEncryption = null,
                requestForm = mapOf(
                    "validUntil" to listOf(holderValidUntil.toEpochMilliseconds().toString()),
                ),
            ),
            configuration = CredentialConfiguration(
                format = CredentialFormat.MSO_MDOC,
                doctype = DOC_TYPE,
            ),
            issuerKey = issuerKey(),
            issuerId = "https://issuer.example",
            credentialData = buildJsonObject {
                putJsonObject(DOC_TYPE) {
                    put("given_name", "Jane")
                }
            },
            dataMapping = null,
            selectiveDisclosure = null,
            x5Chain = listOf(issuerCertificate()),
            display = null,
            w3cVersion = null,
            mDocNameSpacesDataMappingConfig = null,
            credentialStatus = null,
            validFrom = now,
            validUntil = configuredValidUntil,
            expectedUpdate = null,
            verifiedProofs = listOf(verifiedProof()),
        )

        val success = assertIs<CredentialResponseResult.Success>(result)
        val credential = requireNotNull(success.response.credentials).single().credential.jsonPrimitive.content
        val validity = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.base64UrlDecode())
            .decodeMobileSecurityObject()
            .validityInfo

        assertEquals(configuredValidUntil.epochSeconds, validity.validUntil.epochSeconds)
    }

    private suspend fun verifiedProof() = VerifiedCredentialProof(
        proofType = "jwt",
        jwt = "",
        algorithm = "ES256",
        header = buildJsonObject { },
        payload = buildJsonObject { },
        holderKey = JWKKey.generate(KeyType.secp256r1),
        holderKid = null,
        holderDid = null,
        nonce = null,
    )

    private suspend fun issuerKey() = KeyManager.resolveSerializedKey(
        """
        {
          "type": "jwk",
          "jwk": {
            "kty": "EC",
            "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
            "crv": "P-256",
            "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
            "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
            "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
          }
        }
        """.trimIndent()
    )

    private fun issuerCertificate() = CertificateDer(
        "MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M"
            .decodeFromBase64()
    )

    private companion object {
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
    }
}
