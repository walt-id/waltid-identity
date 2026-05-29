package id.walt.openid4vci.handlers.credential

import id.walt.cose.CoseCertificate
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
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
    ): String {
        val holderKey = resolveHolderKey(credentialRequest)
        validateIssuerKey(issuerKey)
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

        val issuedCredential = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            issuerCertificate = issuerCertificate,
            holderKey = holderKey,
            docType = docType,
            data = MdocIssuer.MdocUniversalIssuanceData(namespaces),
            validFrom = validFrom,
            validUntil = validUntil,
            status = status,
            valueMappingFunction = effectiveValueMappingFunction,
        )

        return coseCompliantCbor.encodeToByteArray(issuedCredential).encodeToBase64Url()
    }

    suspend fun resolveHolderKey(credentialRequest: CredentialRequest): CoseKey {
        val jwtProof = credentialRequest.proofs?.jwt?.firstOrNull()
            ?: throw IllegalArgumentException("Missing JWT proof in proofs")
        return JwtProofUtils.resolveHolderKey(jwtProof)
    }

    private fun validateIssuerKey(issuerKey: Key) {
        require(issuerKey.keyType == KeyType.secp256r1) {
            "Issuer key must be EC secp256r1"
        }
        require(issuerKey.hasPrivateKey) {
            "Issuer key must have private key"
        }
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
