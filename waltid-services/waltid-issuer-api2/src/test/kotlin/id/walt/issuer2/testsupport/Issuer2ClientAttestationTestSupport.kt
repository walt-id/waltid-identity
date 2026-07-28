package id.walt.issuer2.testsupport

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.ClientAuthenticationMethodConfig
import id.walt.openid4vci.clientauth.attestation.ClientAttestationJwtTypes
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationMethod
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import id.walt.openid4vci.tokens.jwt.JwtConfirmationClaims
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import kotlinx.serialization.json.*
import kotlin.time.Clock

data class Issuer2ClientAttestationTestMaterial(
    val clientAuthenticationConfig: ClientAuthenticationConfig,
    val attestationAssembler: ClientAttestationAssembler,
)

private val walletCryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

suspend fun generateIssuer2WalletInstanceKey(id: String = "issuer2-wallet-instance") =
    walletCryptoRuntime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

suspend fun createIssuer2ClientAttestationTestMaterial(): Issuer2ClientAttestationTestMaterial {
    val attesterKey = JWKKey.generate(KeyType.secp256r1)
    return Issuer2ClientAttestationTestMaterial(
        clientAuthenticationConfig = ClientAuthenticationConfig(
            supportedMethods = listOf(
                ClientAuthenticationMethodConfig.ClientAttestation(
                    config = ClientAttestationVerifierConfig(
                        verificationMethod = ClientAttestationVerificationMethod.StaticJwk(
                            jwk = Json.parseToJsonElement(attesterKey.getPublicKey().exportJWK()).jsonObject,
                        ),
                    ),
                ),
            ),
        ),
        attestationAssembler = ClientAttestationAssembler(
            LocalWalletAttestationProvider(attesterKey),
        ),
    )
}

private class LocalWalletAttestationProvider(
    private val attesterKey: Key,
) : WalletAttestationProvider {

    override suspend fun getAttestationJwt(instancePublicKeyJwk: EncodedKey.Jwk, clientId: String): String {
        val now = Clock.System.now().epochSeconds
        return attesterKey.signJws(
            buildJsonObject {
                put(JwtPayloadClaims.SUBJECT, clientId)
                put(JwtPayloadClaims.ISSUED_AT, now)
                put(JwtPayloadClaims.EXPIRATION, now + 300)
                put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                    put(JwtConfirmationClaims.JWK, Jwk.parse(instancePublicKeyJwk))
                })
            }.toString().encodeToByteArray(),
            headers = mapOf(
                JwtHeaderParams.TYPE to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION),
            ),
        )
    }

    @Deprecated("Use the EncodedKey.Jwk overload")
    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String =
        getAttestationJwt(
            EncodedKey.Jwk(
                data = BinaryData(
                    Json.encodeToString(instanceKey.getPublicKey().exportJWKObject()).encodeToByteArray()
                ),
                privateMaterial = false,
            ),
            clientId,
        )
}
