package id.walt.issuer2.testsupport

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

data class Issuer2ClientAttestationTestMaterial(
    val clientAuthenticationConfig: ClientAuthenticationConfig,
    val attestationAssembler: ClientAttestationAssembler,
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

    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
        val now = Clock.System.now().epochSeconds
        return attesterKey.signJws(
            buildJsonObject {
                put(JwtPayloadClaims.SUBJECT, clientId)
                put(JwtPayloadClaims.ISSUED_AT, now)
                put(JwtPayloadClaims.EXPIRATION, now + 300)
                put(JwtPayloadClaims.CONFIRMATION, buildJsonObject {
                    put(JwtConfirmationClaims.JWK, instanceKey.getPublicKey().exportJWKObject())
                })
            }.toString().encodeToByteArray(),
            headers = mapOf(
                JwtHeaderParams.TYPE to JsonPrimitive(ClientAttestationJwtTypes.CLIENT_ATTESTATION),
            ),
        )
    }
}
