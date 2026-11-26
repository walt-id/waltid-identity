package id.walt.wallet2.mobile.digitalcredentials

import id.walt.mdoc.readertrust.ReaderTrustDecision
import id.walt.wallet2.mobile.presentation.PresentationCredentialOption
import id.walt.wallet2.mobile.presentation.PresentationCredentialSelection

/**
 * Parsed request shape Apple exposes before the user grants access to the raw Annex C request.
 *
 * @property documents Requested mdoc documents.
 */
public data class AnnexCParsedRequest(
    public val documents: List<AnnexCDocumentRequest>,
)

/**
 * Requested mdoc document and namespace elements.
 *
 * @property docType Requested mdoc document type.
 * @property namespaces Requested namespace elements keyed by namespace.
 */
public data class AnnexCDocumentRequest(
    public val docType: String,
    public val namespaces: Map<String, List<String>>,
)

/**
 * Two-phase Annex C input. Android supplies raw fields immediately; Apple may defer them.
 *
 * @property parsedRequest Parsed request supplied before consent.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property selectedRegistryEntryIds Entries selected by the platform matcher.
 * @property deviceRequestBase64Url Raw DeviceRequest, when the platform exposes it.
 * @property encryptionInfoBase64Url Raw Annex C encryption information, when available.
 */
public data class AnnexCRequest(
    public val parsedRequest: AnnexCParsedRequest,
    public val verifiedOrigin: String,
    public val selectedRegistryEntryIds: List<String> = emptyList(),
    public val deviceRequestBase64Url: String? = null,
    public val encryptionInfoBase64Url: String? = null,
)

/**
 * Consent preview for an ISO 18013-7 Annex C presentation.
 *
 * @property requestId Opaque identifier binding submission to this preview.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property parsedRequest Parsed mdoc request.
 * @property credentialOptions Matching wallet credentials.
 * @property readerAuthentication Whether and how the reader authenticated the request.
 * @property readerTrust Trust policy decision for the authenticated reader; present only when
 * [readerAuthentication] is [DigitalCredentialReaderAuthentication.VERIFIED].
 */
public data class AnnexCPreview(
    public val requestId: String,
    public val verifiedOrigin: String,
    public val parsedRequest: AnnexCParsedRequest,
    public val credentialOptions: List<PresentationCredentialOption>,
    public val readerAuthentication: DigitalCredentialReaderAuthentication,
    public val readerTrust: ReaderTrustDecision?,
)

/**
 * Raw post-consent Annex C data required to sign and encrypt a response.
 *
 * @property requestId Opaque identifier returned by the preview.
 * @property verifiedOrigin Authenticated requesting origin.
 * @property deviceRequestBase64Url Raw DeviceRequest obtained after consent.
 * @property encryptionInfoBase64Url Raw Annex C encryption information obtained after consent.
 * @property selectedCredentialOptions One selected credential per requested document.
 */
public data class AnnexCSubmission(
    public val requestId: String,
    public val verifiedOrigin: String,
    public val deviceRequestBase64Url: String,
    public val encryptionInfoBase64Url: String,
    public val selectedCredentialOptions: List<PresentationCredentialSelection>,
)
