package id.walt.openid4vci.handlers.credential

import id.walt.cose.CoseCertificate
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.mdoc.dataelement.DataElement as LegacyMdocDataElement
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.toCborElement
import id.walt.openid4vci.proofs.VerifiedCredentialProof
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

    /**
     * The data elements our own [id.waltid.openid4vp.wallet.presentation.MdocPresenter] device-signs
     * for transaction data. OpenID4VP 1.0 Appendix B.2.1 deliberately defines no element names: each
     * transaction data type defines the (NameSpace, DataElementIdentifier, DataElementValue) it
     * contributes. `transaction_data_hash_alg` is emitted only when the request carries
     * `transaction_data_hashes_alg`, so both are authorized to cover either case.
     */
    private val TRANSACTION_DATA_HASH_ELEMENTS = listOf("transaction_data_hash", "transaction_data_hash_alg")

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
        expectedUpdate: Instant? = null,
        status: Status? = null,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>? = null,
        verifiedProof: VerifiedCredentialProof? = null,
        authorizedTransactionDataTypes: List<String>? = null,
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
        expectedUpdate = expectedUpdate,
        status = status,
        mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
        verifiedProof = verifiedProof,
        authorizedTransactionDataTypes = authorizedTransactionDataTypes,
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
        expectedUpdate: Instant? = null,
        status: Status? = null,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>? = null,
        verifiedProof: VerifiedCredentialProof? = null,
        authorizedTransactionDataTypes: List<String>? = null,
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
        expectedUpdate = expectedUpdate,
        status = status,
        mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
        verifiedProof = verifiedProof,
        authorizedTransactionDataTypes = authorizedTransactionDataTypes,
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
        expectedUpdate: Instant?,
        status: Status?,
        mDocNameSpacesDataMappingConfig: Map<String, LegacyMdocJsonObjectToCborMappingConfig>?,
        verifiedProof: VerifiedCredentialProof?,
        authorizedTransactionDataTypes: List<String>?,
        valueMappingFunction: (
            docType: String,
            namespace: String,
            elementIdentifier: String,
            elementValueJson: JsonElement,
        ) -> CborElement?,
    ): String {
        // A proof verified upfront already carries the holder key, so it is not resolved twice.
        val holderKey = verifiedProof?.toCosePublicKey() ?: resolveHolderKey(credentialRequest)
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
        val keyAuthorizations = authorizedTransactionDataTypes.toKeyAuthorizations()
        val issuedCredential = when (issuerSigningKey) {
            is IssuerSigningKey.Legacy -> MdocIssuer.issueUniversal(
                issuerKey = issuerSigningKey.key,
                issuerCertificate = issuerCertificate,
                holderKey = holderKey,
                docType = docType,
                data = issuanceData,
                validFrom = validFrom,
                validUntil = validUntil,
                expectedUpdate = expectedUpdate,
                status = status,
                keyAuthorizations = keyAuthorizations,
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
                expectedUpdate = expectedUpdate,
                status = status,
                keyAuthorizations = keyAuthorizations,
                valueMappingFunction = effectiveValueMappingFunction,
            )
        }

        return coseCompliantCbor.encodeToByteArray(issuedCredential).encodeToBase64Url()
    }

    /**
     * Authorizes the device key to sign transaction data of the given types. Without this the holder
     * cannot sign transaction data at all, because presentation requires the type to appear in the
     * MSO's KeyAuthorizations.
     *
     * OpenID4VP Appendix B.2.1 leaves the concrete namespace and data element mapping to the
     * transaction data type. Using the type itself as the response namespace is the walt.id
     * convention, matching what [id.waltid.openid4vp.wallet.presentation.MdocPresenter] emits, so that
     * is what gets authorized here.
     *
     * The grant is granular rather than a blanket `nameSpaces` entry, because our presenter only ever
     * device-signs [TRANSACTION_DATA_HASH_ELEMENTS] under that namespace. Authorizing the whole
     * namespace would also permit arbitrary future device-signed elements.
     */
    private fun List<String>?.toKeyAuthorizations(): KeyAuthorization? =
        this?.filter { it.isNotBlank() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?.let { types -> KeyAuthorization(dataElements = types.associateWith { TRANSACTION_DATA_HASH_ELEMENTS }) }

    suspend fun resolveHolderKey(credentialRequest: CredentialRequest): CoseKey {
        val jwtProof = credentialRequest.proofs?.jwt?.firstOrNull()
            ?: throw IllegalArgumentException("Missing JWT proof in proofs")
        return JwtProofUtils.resolveHolderKey(jwtProof)
    }

    private suspend fun VerifiedCredentialProof.toCosePublicKey(): CoseKey =
        holderKey.exportPublicJwk().toCoseKey()

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
