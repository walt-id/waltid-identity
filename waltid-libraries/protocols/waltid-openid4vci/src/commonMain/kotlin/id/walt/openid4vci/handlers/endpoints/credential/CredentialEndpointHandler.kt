package id.walt.openid4vci.handlers.endpoints.credential

import id.walt.crypto.keys.Key
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.SigningAlgId
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.sdjwt.SDMap
import id.walt.mdoc.objects.mso.Status
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * Handler for credential responses per credential format.
 */
fun interface CredentialEndpointHandler {
    suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Key,
        issuerId: String,
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<CertificateDer>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
    ): CredentialResponseResult
}

data class Crypto2CredentialSigningKey(
    val key: Crypto2Key,
    val algorithm: SigningAlgId,
) {
    fun requireJwsAlgorithm(): JwsAlgorithm = (algorithm as? SigningAlgId.Jose)
        ?.let { JwsAlgorithm.parse(it.value) }
        ?: throw IllegalArgumentException("Credential issuance requires a JOSE signing algorithm")

    fun requireCoseAlgorithm(): Int = when (algorithm) {
        is SigningAlgId.CoseValue -> algorithm.value
        is SigningAlgId.CoseName -> algorithm.value.toCoseAlgorithmOrNull()
            ?: throw IllegalArgumentException("Unsupported COSE signing algorithm: ${algorithm.value}")
        else -> throw IllegalArgumentException("Credential issuance requires a COSE signing algorithm")
    }

    companion object {
        fun select(key: Crypto2Key, configuration: CredentialConfiguration): Crypto2CredentialSigningKey {
            val supported = configuration.credentialSigningAlgValuesSupported
                ?: setOf(defaultAlgorithm(key, configuration))
            val algorithm = supported.firstOrNull {
                it.isCompatibleWith(key) &&
                    (configuration.format != CredentialFormat.MSO_MDOC || key.spec == KeySpec.Ec(EcCurve.P256) && it.coseValue() == -7)
            }
                ?: throw IllegalArgumentException(
                    "No credential signing algorithm is compatible with key specification ${key.spec}",
                )
            return Crypto2CredentialSigningKey(key, algorithm)
        }

        private fun defaultAlgorithm(key: Crypto2Key, configuration: CredentialConfiguration): SigningAlgId =
            if (configuration.format == CredentialFormat.MSO_MDOC) {
                SigningAlgId.CoseValue(key.spec.toCoseAlgorithm())
            } else {
                SigningAlgId.Jose(key.preferredJwsAlgorithm().identifier)
            }

        private fun SigningAlgId.isCompatibleWith(key: Crypto2Key): Boolean = when (this) {
            is SigningAlgId.Jose -> runCatching { JwsAlgorithm.parse(value) }.getOrNull()
                ?.takeIf { key.spec.supports(it) }
                ?.let { key.capabilities.supportsSignatureAlgorithm(it.toSignatureAlgorithm()) }
                ?: false

            is SigningAlgId.CoseValue -> key.spec.supportsCose(value)
            is SigningAlgId.CoseName -> value.toCoseAlgorithmOrNull()?.let { key.spec.supportsCose(it) } == true
            is SigningAlgId.LdSuite -> false
        }

        private fun KeySpec.toCoseAlgorithm(): Int = when (this) {
            KeySpec.Ec(EcCurve.P256) -> -7
            KeySpec.Ec(EcCurve.P384) -> -35
            KeySpec.Ec(EcCurve.P521) -> -36
            KeySpec.Ec(EcCurve.SECP256K1) -> -47
            is KeySpec.Edwards -> -8
            is KeySpec.Rsa -> -257
            else -> throw IllegalArgumentException("Key specification $this does not support COSE signing")
        }

        private fun KeySpec.supports(algorithm: JwsAlgorithm): Boolean = when (algorithm) {
            JwsAlgorithm.ES256 -> this == KeySpec.Ec(EcCurve.P256)
            JwsAlgorithm.ES384 -> this == KeySpec.Ec(EcCurve.P384)
            JwsAlgorithm.ES512 -> this == KeySpec.Ec(EcCurve.P521)
            JwsAlgorithm.ES256K -> this == KeySpec.Ec(EcCurve.SECP256K1)
            JwsAlgorithm.ED25519 -> this == KeySpec.Edwards(EdwardsCurve.ED25519)
            JwsAlgorithm.ED448 -> this == KeySpec.Edwards(EdwardsCurve.ED448)
            JwsAlgorithm.EDDSA -> this is KeySpec.Edwards &&
                curve in setOf(EdwardsCurve.ED25519, EdwardsCurve.ED448)
            JwsAlgorithm.RS256,
            JwsAlgorithm.RS384,
            JwsAlgorithm.RS512,
            JwsAlgorithm.PS256,
            JwsAlgorithm.PS384,
            JwsAlgorithm.PS512,
            -> this is KeySpec.Rsa && bits >= 2048
        }

        private fun KeySpec.supportsCose(algorithm: Int): Boolean = when (algorithm) {
            -7 -> this == KeySpec.Ec(EcCurve.P256)
            -35 -> this == KeySpec.Ec(EcCurve.P384)
            -36 -> this == KeySpec.Ec(EcCurve.P521)
            -47 -> this == KeySpec.Ec(EcCurve.SECP256K1)
            -8 -> this is KeySpec.Edwards
            -37, -38, -39, -257, -258, -259 -> this is KeySpec.Rsa && bits >= 2048
            else -> false
        }

        private fun String.toCoseAlgorithmOrNull(): Int? = when (this) {
            "ES256" -> -7
            "ES384" -> -35
            "ES512" -> -36
            "ES256K" -> -47
            "EdDSA" -> -8
            "PS256" -> -37
            "PS384" -> -38
            "PS512" -> -39
            "RS256" -> -257
            "RS384" -> -258
            "RS512" -> -259
            else -> null
        }

        private fun SigningAlgId.coseValue(): Int? = when (this) {
            is SigningAlgId.CoseValue -> value
            is SigningAlgId.CoseName -> value.toCoseAlgorithmOrNull()
            else -> null
        }
    }
}

fun interface Crypto2CredentialEndpointHandler {
    suspend fun sign(
        request: CredentialRequest,
        configuration: CredentialConfiguration,
        issuerKey: Crypto2CredentialSigningKey,
        issuerId: String,
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        selectiveDisclosure: SDMap?,
        x5Chain: List<CertificateDer>?,
        display: List<CredentialDisplay>?,
        w3cVersion: String?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        credentialStatus: Status?,
        validFrom: Instant?,
        validUntil: Instant?,
    ): CredentialResponseResult
}
