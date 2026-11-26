package id.walt.wallet2.mobile.proximity

import kotlin.io.encoding.Base64

/**
 * One candidate made available to an application-profile adapter.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property docType Credential mdoc document type.
 * @property label Display-safe credential label when available.
 */
public data class ProximityApplicationCredential(
    public val credentialId: String,
    public val docType: String,
    public val label: String?,
) {
    init {
        require(credentialId.isNotBlank() && docType.isNotBlank())
    }
}

/**
 * Exact request and compatible candidates supplied to a versioned application profile.
 *
 * @property credentials Compatible metadata-only credential candidates.
 * @property requestedDocuments Dependency-free parsed document request facts.
 * @property readerAuthentication Display-safe authentication and trust facts for the request.
 */
public data class ProximityApplicationProfileInput(
    /** Exact DeviceRequest bytes, encoded as unpadded Base64URL. */
    public val deviceRequestBase64Url: String,
    public val credentials: List<ProximityApplicationCredential>,
    public val requestedDocuments: List<ProximityApplicationDocumentRequest>,
    public val readerAuthentication: List<ProximityReaderAuthentication>,
) {
    init {
        require(deviceRequestBase64Url.isNotBlank())
        require(credentials.distinctBy(ProximityApplicationCredential::credentialId).size == credentials.size)
        require(requestedDocuments.isNotEmpty())
        require(requestedDocuments.distinctBy { it.requestIndex }.size == requestedDocuments.size)
        val requestIndices = requestedDocuments.map { it.requestIndex }.toSet()
        require(readerAuthentication.all { authentication ->
            authentication.scope.documentRequestIndex == null || authentication.scope.documentRequestIndex in requestIndices
        }) { "Reader authentication refers to a document outside this request" }
        require(readerAuthentication.distinctBy { it.scope to it.authenticationIndex }.size ==
            readerAuthentication.size) { "Reader-authentication scopes must be unique" }
    }
}

/**
 * Dependency-free parsed request facts supplied to application-profile adapters.
 *
 * @property requestIndex Zero-based document request index.
 * @property docType Requested mdoc document type.
 * @property requestedElements Requested issuer-signed elements and retention intent.
 */
public data class ProximityApplicationDocumentRequest(
    public val requestIndex: Int,
    public val docType: String,
    public val requestedElements: List<ProximityRequestedElement>,
) {
    init {
        require(requestIndex >= 0 && docType.isNotBlank() && requestedElements.isNotEmpty())
        require(requestedElements.distinctBy { it.namespace to it.elementIdentifier }.size == requestedElements.size)
    }
}

/**
 * One locally validated, display-safe application authorization value.
 *
 * @property id Stable detail identifier within the application profile.
 * @property label Display-safe detail label.
 * @property value Display-safe detail value.
 */
public data class ProximityApplicationAuthorizationDetail(
    public val id: String,
    public val label: String,
    public val value: String,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank() && value.isNotBlank())
    }
}

/**
 * Generic device-signed value proposed by a recognized application profile.
 *
 * @property credentialId Credential to which the device-signed value is bound.
 * @property namespace Device namespace containing the value.
 * @property elementIdentifier Element identifier within [namespace].
 */
public data class ProximityDeviceSignedElement(
    public val credentialId: String,
    public val namespace: String,
    public val elementIdentifier: String,
    /** Exact encoded CBOR value as unpadded Base64URL. */
    public val valueCborBase64Url: String,
) {
    init {
        require(credentialId.isNotBlank())
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank())
        require(valueCborBase64Url.isNonEmptyBase64Url()) {
            "A device-signed value must be non-empty unpadded Base64URL"
        }
    }
}

/**
 * Validated output of one recognized, versioned wallet application profile.
 *
 * @property profileId Identifier of the profile that produced the authorization.
 * @property displayTitle Display-safe title for holder review.
 * @property details Display-safe authorization details.
 * @property compatibleCredentialIds Credentials for which this authorization remains valid.
 * @property deviceSignedElements Profile-proposed device-signed values, bound to compatible credentials.
 */
public data class ProximityApplicationAuthorization(
    public val profileId: String,
    public val displayTitle: String,
    public val details: List<ProximityApplicationAuthorizationDetail>,
    public val compatibleCredentialIds: Set<String>,
    /** Profile-owned exact result digest, encoded as unpadded Base64URL SHA-256 bytes. */
    public val resultBindingDigestBase64Url: String,
    public val deviceSignedElements: List<ProximityDeviceSignedElement> = emptyList(),
) {
    init {
        require(profileId.isNotBlank() && displayTitle.isNotBlank())
        require(details.isNotEmpty() && details.distinctBy { it.id }.size == details.size)
        require(compatibleCredentialIds.isNotEmpty() && compatibleCredentialIds.none(String::isBlank))
        require(resultBindingDigestBase64Url.isSha256Base64Url()) {
            "Application-profile binding must be an unpadded Base64URL SHA-256 digest"
        }
        require(deviceSignedElements.all { it.credentialId in compatibleCredentialIds })
        require(
            deviceSignedElements.distinctBy { Triple(it.credentialId, it.namespace, it.elementIdentifier) }.size ==
                deviceSignedElements.size
        )
    }
}

/** Result of asking one application profile to recognize and validate the exact request. */
public sealed interface ProximityApplicationProfileResult {
    /** This profile does not recognize the request. */
    public data object NotRecognized : ProximityApplicationProfileResult

    /** This profile recognized the request and produced a locally validated result. */
    public data class Recognized(
        /** Validated authorization produced by the profile. */
        public val authorization: ProximityApplicationAuthorization,
    ) : ProximityApplicationProfileResult

    /**
     * This profile recognized the request but rejected invalid or unsupported application data.
     * [reason] must be safe to expose to the wallet UI.
     */
    public data class Rejected(
        /** Display-safe rejection reason. */
        public val reason: String,
    ) : ProximityApplicationProfileResult {
        init { require(reason.isNotBlank()) }
    }
}

/** Versioned wallet-owned interpreter for application-specific request data. */
public interface ProximityApplicationProfile {
    /** Stable, versioned profile identifier. */
    public val id: String

    /** Recognizes and validates application semantics in the exact [input]. */
    public suspend fun evaluate(
        input: ProximityApplicationProfileInput,
    ): ProximityApplicationProfileResult
}

/** Ordered registry requiring at most one profile to recognize a request. */
public class ProximityApplicationProfileRegistry(
    profiles: List<ProximityApplicationProfile>,
) {
    internal val profiles: List<ProximityApplicationProfile> = profiles.map { profile ->
        val capturedId = profile.id
        object : ProximityApplicationProfile {
            override val id: String = capturedId
            override suspend fun evaluate(input: ProximityApplicationProfileInput):
                ProximityApplicationProfileResult = profile.evaluate(input)
        }
    }

    init {
        require(this.profiles.none { it.id.isBlank() })
        require(this.profiles.distinctBy { it.id }.size == this.profiles.size) {
            "Application profile identifiers must be unique"
        }
    }

    /** Standard registry instances. */
    public companion object {
        /** Registry that recognizes no application-specific request semantics. */
        public val Empty: ProximityApplicationProfileRegistry =
            ProximityApplicationProfileRegistry(emptyList())
    }
}

internal fun String.isNonEmptyBase64Url(): Boolean =
    isNotBlank() && !contains('=') && runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this).isNotEmpty()
    }.getOrDefault(false)

private fun String.isSha256Base64Url(): Boolean =
    isNotBlank() && !contains('=') && runCatching {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this).size == 32
    }.getOrDefault(false)
