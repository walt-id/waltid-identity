package id.waltid.openid4vp.wallet.presentation

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseAlgorithm
import id.walt.cose.toCoseSigner
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.isocred.DeviceAuth
import id.walt.isocred.DeviceAuthentication
import id.walt.isocred.DeviceNameSpaces
import id.walt.isocred.DeviceResponse
import id.walt.isocred.DeviceSigned
import id.walt.isocred.Document
import id.walt.isocred.IssuerSigned
import id.walt.isocred.IssuerSignedList
import id.walt.isocred.SessionTranscript
import id.walt.isocred.handover.OpenID4VPHandover
import id.walt.isocred.handover.OpenID4VPHandoverInfo
import id.walt.isocred.sha256
import id.walt.isocred.wrapInCborTag
import id.walt.mdoc.utils.ByteStringWrapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Contextual
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.component1
import kotlin.collections.component2

object MdocPresenter {

    val log = KotlinLogging.logger {}

    fun buildSessionTranscript(
        authorizationRequest: id.walt.verifier.openid.models.authorization.AuthorizationRequest,
        responseUri: String
    ): SessionTranscript {
        val handoverInfo = OpenID4VPHandoverInfo(
            clientId = authorizationRequest.clientId,
            nonce = authorizationRequest.nonce!!,
            jwkThumbprint = null, // Not using JWE in DIRECT_POST
            responseUri = responseUri
        )
        val handoverInfoHash = coseCompliantCbor.encodeToByteArray(handoverInfo).sha256()
        val handover = OpenID4VPHandover(infoHash = handoverInfoHash)
        val sessionTranscript = SessionTranscript.forOpenId(handover)

        return sessionTranscript
    }

    suspend fun buildDeviceAuth(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Key
    ): DeviceAuth {
        // Create the DeviceAuthentication structure to be signed
        val deviceAuthentication = DeviceAuthentication(
            type = "DeviceAuthentication",
            sessionTranscript = sessionTranscript,
            docType = credential.docType,
            namespaces = ByteStringWrapper(disclosedDeviceNamespaces)
        )

        // Sign using the new detached payload function
        val detachedPayload = coseCompliantCbor.encodeToByteArray(deviceAuthentication).wrapInCborTag(24)
        val deviceSignature = CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = holderKey.keyType.toCoseAlgorithm()),
            detachedPayload = detachedPayload,
            signer = holderKey.toCoseSigner()
        )
        val deviceAuth = DeviceAuth(deviceSignature = deviceSignature)

        return deviceAuth
    }

    fun x() {
        /*val selectedIssuerSignedItems = dcqlQueryClaims
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
            }*/
    }

    /*fun presentMdoc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key
    ): JsonPrimitive {
        // Construct DeviceResponse CBOR, then base64url encode it.

        log.debug { "Handling mso_mdoc credential for query $queryId" }

        val mdocsCredential = digitalCredential as MdocsCredential
        val responseUri = authorizationRequest.responseUri
            ?: throw IllegalArgumentException("response_uri is required for mso_mdoc presentation")

        val document: id.walt.isocred.Document = mdocsCredential.parseToDocument()
        val issuerSigned: id.walt.isocred.IssuerSigned = document.issuerSigned

        // Build OpenID4VPHandover (OID4VP Appendix B.2.6.1) without ISO-specific wallet nonce
        val sessionTranscript = buildSessionTranscript(authorizationRequest, responseUri)

        // Determine which namespaces and elements to disclose based on the DCQL match
        val disclosedDeviceNamespaces = DeviceNameSpaces(emptyMap()) // Assuming selective disclosure for device data

        val deviceAuth = buildDeviceAuth(
            sessionTranscript = sessionTranscript,
            credential = mdocsCredential,
            disclosedDeviceNamespaces = disclosedDeviceNamespaces
        )

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
        //log.trace {  }


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
    }*/

}
