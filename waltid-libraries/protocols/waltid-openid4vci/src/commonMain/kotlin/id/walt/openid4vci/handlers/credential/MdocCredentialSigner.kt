package id.walt.openid4vci.handlers.credential

import id.walt.cose.CoseCertificate
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.mdoc.dataelement.DataElement as LegacyMdocDataElement
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.toCborElement
import id.walt.openid4vci.requests.credential.CredentialRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object MdocCredentialSigner {
    @OptIn(ExperimentalSerializationApi::class)
    @Deprecated("Use the Crypto2Key overload")
    suspend fun generateMdocCredential(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerKey: Key,
        issuerCertificate: List<CoseCertificate>,
        docType: String,
        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>? = null,
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement
        ) -> CborElement? = defaultSchemalessMappingFunction,
    ): String = generateMdocCredential(
        credentialRequest = credentialRequest,
        credentialData = credentialData,
        issuerSigningKey = IssuerSigningKey.Legacy(issuerKey),
        issuerCertificate = issuerCertificate,
        docType = docType,
        validFrom = validFrom,
        validUntil = validUntil,
        status = status,
        mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
        valueMappingFunction = valueMappingFunction,
    )

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun generateMdocCredential(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerKey: Crypto2Key,
        signatureAlgorithm: Int,
        issuerCertificate: List<CoseCertificate>,
        docType: String,
        validFrom: Instant? = null,
        validUntil: Instant = Clock.System.now().plus(1.days * 365 * 10),
        status: Status? = null,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>? = null,
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement,
        ) -> CborElement? = defaultSchemalessMappingFunction,
    ): String = generateMdocCredential(
        credentialRequest = credentialRequest,
        credentialData = credentialData,
        issuerSigningKey = IssuerSigningKey.Crypto2(issuerKey, signatureAlgorithm),
        issuerCertificate = issuerCertificate,
        docType = docType,
        validFrom = validFrom,
        validUntil = validUntil,
        status = status,
        mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
        valueMappingFunction = valueMappingFunction,
    )

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun generateMdocCredential(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerSigningKey: IssuerSigningKey,
        issuerCertificate: List<CoseCertificate>,
        docType: String,
        validFrom: Instant?,
        validUntil: Instant,
        status: Status?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement,
        ) -> CborElement?,
    ): String {
        val holderKey = resolveHolderKey(credentialRequest)
        validateIssuerKey(issuerSigningKey)
        val namespaces = credentialData.mapValues { (namespace, namespaceData) ->
            requireNotNull(namespaceData as? JsonObject) {
                "Credential data for namespace $namespace must be a JSON object"
            }
        }

        val effectiveValueMappingFunction =
            { docTypeValue: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement ->
                mDocNameSpacesDataMappingConfig
                    ?.get(namespace)
                    ?.entriesConfigMap
                    ?.get(elementIdentifier)
                    ?.executeMapping(elementValueJson)
                    ?.toKotlinxCborElement()
                    ?: valueMappingFunction(docTypeValue, namespace, elementIdentifier, elementValueJson)
            }

        val issuanceData = MdocIssuer.MdocUniversalIssuanceData(namespaces)
        val issuedCredential = when (issuerSigningKey) {
            is IssuerSigningKey.Legacy -> MdocIssuer.issueUniversal(
                issuerKey = issuerSigningKey.key,
                issuerCertificate = issuerCertificate,
                holderKey = holderKey,
                docType = docType,
                data = issuanceData,
                validFrom = validFrom,
                validUntil = validUntil,
                status = status,
                valueMappingFunction = effectiveValueMappingFunction,
            )

            is IssuerSigningKey.Crypto2 -> MdocIssuer.issueUniversal(
                issuerKey = issuerSigningKey.key,
                signatureAlgorithm = issuerSigningKey.algorithm,
                issuerCertificate = issuerCertificate,
                holderKey = holderKey,
                docType = docType,
                data = issuanceData,
                validFrom = validFrom,
                validUntil = validUntil,
                status = status,
                valueMappingFunction = effectiveValueMappingFunction,
            )
        }

        return coseCompliantCbor.encodeToByteArray(issuedCredential).encodeToBase64Url()
    }

    suspend fun resolveHolderKey(credentialRequest: CredentialRequest): CoseKey {
        val jwtProof = credentialRequest.proofs?.jwt?.firstOrNull()
            ?: throw IllegalArgumentException("Missing JWT proof in proofs")
        return JwtProofUtils.resolveHolderKey(jwtProof)
    }

    private fun validateIssuerKey(issuerKey: IssuerSigningKey) {
        when (issuerKey) {
            is IssuerSigningKey.Legacy -> {
                require(issuerKey.key.keyType == KeyType.secp256r1) { "Issuer key must be EC secp256r1" }
                require(issuerKey.key.hasPrivateKey) { "Issuer key must have private key" }
            }

            is IssuerSigningKey.Crypto2 -> {
                require(issuerKey.key.spec == KeySpec.Ec(EcCurve.P256)) { "Issuer key must be EC P-256" }
                require(issuerKey.key.capabilities.signer != null) { "Issuer key must permit signing" }
            }
        }
    }

    private sealed interface IssuerSigningKey {
        data class Legacy(val key: Key) : IssuerSigningKey
        data class Crypto2(val key: Crypto2Key, val algorithm: Int) : IssuerSigningKey
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val defaultSchemalessMappingFunction: (
        docType: String,
        namespace: String,
        elementIdentifier: String,
        elementValueJson: JsonElement
    ) -> CborElement? = { _, _, _, elementValueJson ->
        elementValueJson.toCborElement()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun LegacyMdocDataElement.toKotlinxCborElement(): CborElement =
        Cbor.decodeFromByteArray(toCBOR())
}
