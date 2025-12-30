@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.cose.*
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.DcqlMatcher
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.handover.OpenID4VPHandover
import id.walt.mdoc.objects.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive

object MdocPresenter {

    val log = KotlinLogging.logger {}

    fun buildSessionTranscript(
        authorizationRequest: AuthorizationRequest,
        responseUri: String
    ): SessionTranscript {
        requireNotNull(authorizationRequest.clientId) { "Missing client id in authorization request - is this a DC API flow?" }
        val handoverInfo = OpenID4VPHandoverInfo(
            clientId = authorizationRequest.clientId!!,
            nonce = authorizationRequest.nonce!!,
            jwkThumbprint = null, // Not using JWE in DIRECT_POST
            responseUri = responseUri
        )
        log.trace { "Client side session transaction openid4vp handover info: $handoverInfo" }

        val handoverInfoCbor = coseCompliantCbor.encodeToByteArray(handoverInfo)
        log.trace { "Client side Handover info cbor: ${handoverInfoCbor.toHexString()}" }

        val handoverInfoHash = handoverInfoCbor.sha256()
        log.trace { "Client side Handover info hash: ${handoverInfoHash.toHexString()}" }

        val handover = OpenID4VPHandover(
            identifier = "OpenID4VPHandover",
            infoHash = handoverInfoHash
        )
        val sessionTranscript = SessionTranscript.forOpenId(handover)
        log.trace { "Client side Session transcript: $sessionTranscript" }

        return sessionTranscript
    }

    suspend fun buildDeviceAuth(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Key
    ): DeviceAuth {
        // Create the DeviceAuthentication structure to be signed
        val deviceAuthBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            sessionTranscript, credential.docType, ByteStringWrapper(disclosedDeviceNamespaces)
        )

        // Sign using the detached payload function
        val deviceSignature = CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = holderKey.keyType.toCoseAlgorithm()),
            detachedPayload = deviceAuthBytes,
            signer = holderKey.toCoseSigner()
        )
        val deviceAuth = DeviceAuth(deviceSignature = deviceSignature)

        return deviceAuth
    }

    suspend fun presentMdoc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key
    ): JsonPrimitive {
        log.debug { "Handling mso_mdoc credential" }

        val mdocsCredential = digitalCredential as MdocsCredential
        val responseUri = authorizationRequest.responseUri
            ?: throw IllegalArgumentException("response_uri is required for mso_mdoc presentation")

        val document: Document = mdocsCredential.document
        val issuerSigned: IssuerSigned = document.issuerSigned

        // Build OpenID4VPHandover (OID4VP Appendix B.2.6.1) without ISO-specific wallet nonce
        val sessionTranscript = buildSessionTranscript(authorizationRequest, responseUri)

        // Determine which namespaces and elements to disclose based on the DCQL match
        val disclosedDeviceNamespaces = DeviceNameSpaces(emptyMap()) // Assuming selective disclosure for device data

        val deviceAuth = buildDeviceAuth(
            sessionTranscript = sessionTranscript,
            credential = mdocsCredential,
            disclosedDeviceNamespaces = disclosedDeviceNamespaces,
            holderKey = holderKey
        )
        log.trace { "Wallet-created device auth: $deviceAuth" }

        val dcqlQueryClaims = matchResult.originalQuery.claims
        requireNotNull(dcqlQueryClaims) { "Missing claims for DCQL credential query: ${matchResult.originalQuery}" }

        //--- SD START
        val selectedIssuerSignedItems = dcqlQueryClaims
            .groupBy { it.path.first() }
            .mapValues { (sdNamespace2, claimQueries) ->
                claimQueries.map { claimsQuery ->
                    val path = claimsQuery.path
                    require(path.size == 2) { "Invalid state: Expected DCQL claim path two have only two elements (namespace + elementIdentifier)?" }

                    val (sdNamespace, sdElementIdentifier) = path
                    check(sdNamespace == sdNamespace2) { "??? $sdNamespace != $sdNamespace2" }

                    val issuerSignedNamespaces: Map<String, IssuerSignedList>? = issuerSigned.namespaces
                    // todo: in theory, all items could be device-provided data too
                    requireNotNull(issuerSignedNamespaces) { "No issuer-signed namespaces to choose from for DCQL query claims!" }

                    val selectedNamespace: IssuerSignedList = issuerSignedNamespaces[sdNamespace]
                        ?: throw IllegalArgumentException("Namespace does not exist in issuer-signed namespaces for DCQL query claim: $sdNamespace")

                    val matchedIssuerSignedItem =
                        selectedNamespace.entries.find { it.value.elementIdentifier == sdElementIdentifier }?.value
                            ?: throw IllegalArgumentException("Could not find item for DCQL query: namespace = $sdNamespace, element = $sdElementIdentifier")

                    log.trace { "Mapped sd claim $claimsQuery to $matchedIssuerSignedItem of namespace $sdNamespace" }

                    matchedIssuerSignedItem
                }
            }
        log.trace { "Selected disc:" + matchResult.selectedDisclosures }
        log.trace { "DCQL claims:  " + authorizationRequest.dcqlQuery!!.credentials.first().claims!! }

        val issuerSignedWithSelectedNamespaceItems =
            IssuerSigned.fromIssuerSignedItems(
                namespacedItems = selectedIssuerSignedItems,
                issuerAuth = issuerSigned.issuerAuth
            )

        //--- SD END

        // 5. Assemble the final DeviceResponse
        val deviceResponse = DeviceResponse(
            version = "1.0",
            documents = arrayOf(
                Document(
                    docType = mdocsCredential.docType,
                    issuerSigned = issuerSignedWithSelectedNamespaceItems,
                    //issuerSigned = issuerSigned,
                    deviceSigned = DeviceSigned(ByteStringWrapper(disclosedDeviceNamespaces), deviceAuth)
                )
            ),
            status = 0u
        )

        // 6. CBOR-encode and base64url-encode the response string
        val deviceResponseBytes = coseCompliantCbor.encodeToByteArray(deviceResponse)
        return JsonPrimitive(deviceResponseBytes.encodeToBase64Url())
    }

}
