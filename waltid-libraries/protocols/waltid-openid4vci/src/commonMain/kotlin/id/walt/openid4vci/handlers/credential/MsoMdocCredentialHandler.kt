package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.handlers.endpoints.credential.CredentialEndpointHandler
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig as LegacyMdocJsonObjectToCborMappingConfig
import id.walt.mdoc.objects.mso.Status
import id.walt.sdjwt.SDMap
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant


/**
 * Base handler for ISO mso_mdoc credential issuance in the waltid-openid4vci library.
 *
 * The library does not bundle CBOR/COSE signing — that requires platform-specific
 * dependencies (BouncyCastle, cose-java, etc.). Instead, integrators subclass this
 * handler and implement [issueMdoc] to perform the actual signing.
 *
 * The [credentialData] passed to [sign] is interpreted as a namespace map:
 * each top-level key is a namespace string and its value is a [JsonObject] of
 * data element identifier → value pairs. The [CredentialConfiguration.doctype]
 * provides the document type.
 */
abstract class MsoMdocCredentialHandler : CredentialEndpointHandler {

    override suspend fun sign(
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
    ): CredentialResponseResult {
        return try {
            val docType = configuration.doctype
                ?: return CredentialResponseResult.Failure(
                    OAuthError("invalid_request", "Missing doctype in credential configuration for mso_mdoc")
                )

            val namespaceData = credentialData.entries
                .mapNotNull { (namespace, value) ->
                    (value as? JsonObject)?.let { namespace to it }
                }
                .toMap()

            if (namespaceData.isEmpty()) {
                return CredentialResponseResult.Failure(
                    OAuthError("invalid_request", "credentialData must contain at least one namespace for mso_mdoc")
                )
            }

            val holderKey = extractHolderKey(request)
                ?: return CredentialResponseResult.Failure(
                    OAuthError("invalid_or_missing_proof", "Could not extract holder key from proof")
                )

            val issued = issueMdoc(
                docType = docType,
                namespaceData = namespaceData,
                holderKey = holderKey,
                issuerKey = issuerKey,
                x5Chain = x5Chain,
                validityDays = 365,
            )

            CredentialResponseResult.Success(
                CredentialResponse(
                    credentials = listOf(IssuedCredential(credential = JsonPrimitive(issued))),
                )
            )
        } catch (e: Exception) {
            CredentialResponseResult.Failure(OAuthError("invalid_request", e.message))
        }
    }

    /**
     * Extracts the holder's public key from the credential request proof.
     * Override to extract the key from the proof JWT header `jwk` claim or CWT.
     */
    protected open suspend fun extractHolderKey(request: CredentialRequest): Key? = null

    /**
     * Perform the actual mdoc CBOR/COSE signing.
     *
     * @param docType the document type (e.g. `"org.iso.18013.5.1.mDL"`)
     * @param namespaceData map of namespace → { elementIdentifier: value }
     * @param holderKey the holder's public key for device key binding
     * @param issuerKey the issuer's signing key
     * @param x5Chain optional certificate chain for the issuer key
     * @param validityDays validity period in days
     * @return base64url-encoded IssuerSigned structure (OID4VCI mso_mdoc response format)
     */
    abstract suspend fun issueMdoc(
        docType: String,
        namespaceData: Map<String, JsonObject>,
        holderKey: Key,
        issuerKey: Key,
        x5Chain: List<CertificateDer>?,
        validityDays: Int,
    ): String
}
