package id.walt.openid4vci.clientauth.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.x509.CertificateDer
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateBundle
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import id.walt.x509.X509KeyUsage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock

class X509ChainClientAttestationVerifierTest {

    @Test
    fun `authenticates client attestation backed by trusted x509 chain`() = runTest {
        val clientId = "wallet-client"
        val issuer = "https://issuer.example/openid4vci"
        val trustChain = createAttesterTrustChain()
        val material = createClientAttestationMaterial(
            trustChain = trustChain,
            clientId = clientId,
            audience = issuer,
        )
        val method = AttestationBasedClientAuthenticationMethod(
            attestationVerifier = X509ChainClientAttestationVerifier(
                trustedRootCertificatesPem = listOf(trustChain.rootCertificate.certificateDer.toPEMEncodedString()),
            ),
        )

        val result = method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(material.attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(material.popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )

        val authenticated = assertIs<ClientAuthenticationResult.Authenticated>(result)
        assertEquals(clientId, authenticated.client.id)
    }

    @Test
    fun `rejects client attestation backed by untrusted x509 chain`() = runTest {
        val clientId = "wallet-client"
        val issuer = "https://issuer.example/openid4vci"
        val trustChain = createAttesterTrustChain()
        val wrongTrustChain = createAttesterTrustChain(rootCommonName = "Untrusted Attestation Root")
        val material = createClientAttestationMaterial(
            trustChain = trustChain,
            clientId = clientId,
            audience = issuer,
        )
        val method = AttestationBasedClientAuthenticationMethod(
            attestationVerifier = X509ChainClientAttestationVerifier(
                trustedRootCertificatesPem = listOf(wrongTrustChain.rootCertificate.certificateDer.toPEMEncodedString()),
            ),
        )

        val result = method.authenticate(
            endpoint = ClientAuthenticationEndpoint.TOKEN,
            parameters = emptyMap(),
            headers = mapOf(
                ClientAttestationHeaders.CLIENT_ATTESTATION to listOf(material.attestationJwt),
                ClientAttestationHeaders.CLIENT_ATTESTATION_POP to listOf(material.popJwt),
            ),
            context = ClientAuthenticationContext(authorizationServerIssuer = issuer),
        )

        val failure = assertIs<ClientAuthenticationResult.Failure>(result)
        assertEquals("invalid_client", failure.error.error)
        assertEquals("Client attestation x5c chain is not trusted", failure.error.description)
    }

    private suspend fun createAttesterTrustChain(
        rootCommonName: String = "Trusted Attestation Root",
    ): AttesterTrustChain {
        val certificateBuilder = GenericX509CertificateBuilder()
        val rootKey = JWKKey.generate(KeyType.secp256r1)
        val rootCertificate = certificateBuilder.build(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = rootCommonName),
                isCertificateAuthority = true,
                keyUsage = setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign),
            ),
            subjectPublicKey = rootKey.getPublicKey(),
            signingKey = rootKey,
        )

        val attesterKey = JWKKey.generate(KeyType.secp256r1)
        val attesterCertificate = certificateBuilder.build(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "Client Attester"),
                issuerName = rootCertificate.decodedCertificate.subjectName,
                isCertificateAuthority = false,
                keyUsage = setOf(X509KeyUsage.DigitalSignature),
            ),
            subjectPublicKey = attesterKey.getPublicKey(),
            signingKey = rootKey,
        )

        return AttesterTrustChain(
            attesterSigningKey = attesterKey,
            rootCertificate = rootCertificate,
            certificateChain = listOf(attesterCertificate.certificateDer, rootCertificate.certificateDer),
        )
    }

    private suspend fun createClientAttestationMaterial(
        trustChain: AttesterTrustChain,
        clientId: String,
        audience: String,
    ): ClientAttestationMaterial {
        val clientInstanceKey = JWKKey.generate(KeyType.secp256r1)
        val now = Clock.System.now().epochSeconds

        val attestationJwt = trustChain.attesterSigningKey.signJws(
            buildJsonObject {
                put("sub", clientId)
                put("iat", now)
                put("exp", now + 300)
                put("cnf", buildJsonObject {
                    put("jwk", clientInstanceKey.getPublicKey().exportJWKObject())
                })
            }.toString().encodeToByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION),
                "x5c" to JsonArray(
                    trustChain.certificateChain.map { certificate ->
                        JsonPrimitive(certificate.toX5cValue())
                    },
                ),
            ),
        )

        val popJwt = clientInstanceKey.signJws(
            buildJsonObject {
                put("aud", audience)
                put("iat", now)
                put("jti", "proof-$now")
            }.toString().encodeToByteArray(),
            headers = mapOf(
                "typ" to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP),
            ),
        )

        return ClientAttestationMaterial(
            attestationJwt = attestationJwt,
            popJwt = popJwt,
        )
    }

    private fun CertificateDer.toX5cValue(): String =
        bytes.toByteArray().encodeToBase64()

    private data class AttesterTrustChain(
        val attesterSigningKey: Key,
        val rootCertificate: GenericX509CertificateBundle,
        val certificateChain: List<CertificateDer>,
    )

    private data class ClientAttestationMaterial(
        val attestationJwt: String,
        val popJwt: String,
    )
}
