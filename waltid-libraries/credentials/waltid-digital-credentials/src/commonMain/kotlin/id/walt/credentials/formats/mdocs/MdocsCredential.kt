package id.walt.credentials.formats

import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.credentials.signatures.CredentialSignature
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.did.dids.resolver.local.DidJwkResolver
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.Document
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("vc-mdocs")
data class MdocsCredential(
    override val credentialData: JsonObject,

    /** raw, base64url-encoded DeviceResponse / Document string */
    override val signed: String?,

    /** The document type, e.g., "org.iso.18013.5.1.mDL". */
    val docType: String,

    // The signature is implicit within the COSE structures of the mdoc.
    override val signature: CredentialSignature? = null,


    @EncodeDefault
    override var issuer: String? = null,
    @EncodeDefault
    override var subject: String? = null,
) : DigitalCredential() {
    override val format: String = "mso_mdoc"

    companion object {
        private val log = KotlinLogging.logger { }
        private val issuerDidResolver = DidJwkResolver()

        suspend fun verifyMdocSignature(document: Document, issuerKey: Key) {
            log.trace { "Verifying issuerAuth signature with issuer key..." }
            val issuerAuthSignatureValid = document.issuerSigned.issuerAuth.verify(issuerKey.toCoseVerifier())

            require(issuerAuthSignatureValid) { "IssuerAuth signature is invalid!" }
        }

    }

    fun parseToDocument(): Document {
        requireNotNull(signed) { "No signed in Mdocs credential" }
        log.trace { "Signed is: $signed" }

        val isHex = signed.matchesHex()
        val isBase64 = signed.matchesBase64Url()

        require(isHex || isBase64) { "Signed is neither hex nor base64" }

        val signedBytes = if (isHex) signed.hexToByteArray() else signed.decodeFromBase64Url()

        val document = runCatching {
            log.trace { "Decoding DeviceResponse from: ${signedBytes.toHexString()}" }
            val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(signedBytes)
            val document =
                deviceResponse.documents?.firstOrNull() ?: throw IllegalArgumentException("Mdoc document not found in DeviceResponse")
            document
        }.recoverCatching {
            log.trace { "Failed deserializing as DeviceResponse, trying as Document: ${it.stackTraceToString()}" }
            val document = coseCompliantCbor.decodeFromByteArray<Document>(signedBytes)
            document
        }.getOrThrow()

        return document
    }

    override suspend fun verify(publicKey: Key): Result<JsonElement> {
        val document = parseToDocument()

        return runCatching { verifyMdocSignature(document, publicKey) }
            .map { credentialData }
    }

    suspend fun verify(): Result<JsonElement> {
        val issuerVirtualDid = issuer ?: throw IllegalArgumentException("Missing virtual issuer DID")
        val issuerKey = issuerDidResolver.resolveToKey(issuerVirtualDid).getOrThrow()
        return verify(issuerKey)
    }
}
