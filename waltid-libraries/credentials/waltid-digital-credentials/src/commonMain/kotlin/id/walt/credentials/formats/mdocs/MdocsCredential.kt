package id.walt.credentials.formats

import id.walt.cose.toCoseVerifier
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.CredentialSignature
import id.walt.crypto.keys.Key
import id.walt.did.dids.resolver.local.DidJwkResolver
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.parser.MdocParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal const val MSO_MDOC_FORMAT = "mso_mdoc"
private const val VC_MDOCS_SERIAL_NAME = "vc-mdocs"

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = MdocsCredential.MdocsCredentialSerializer::class)
@SerialName(VC_MDOCS_SERIAL_NAME)
data class MdocsCredential(
    override val credentialData: JsonObject,

    /** raw, base64url-encoded DeviceResponse / Document string */
    override val signed: String?,

    /** The document type, e.g., "org.iso.18013.5.1.mDL". */
    val docType: String,

    // The signature is implicit within the COSE structures of the mdoc.
    override val signature: CredentialSignature? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override var issuer: String? = null,

    @EncodeDefault
    override var subject: String? = null,
) : DigitalCredential() {
    override val format: String = MSO_MDOC_FORMAT

    /**
     * This virtual DID is *virtual*: It does not exist in the mdocs credential.
     * Instead, it is put there by the credential parser (parsing from mdocs issuer auth chain),
     * so that it does not need to be reparsed every time.
     */
    /*override suspend fun getSignerKey(): Key {
        requireNotNull(signature) { "Mdocs credential is not signed" }
        require(signature is CoseCredentialSignature) { "Mdocs is signed with wrong signature" }

        //val issuerVirtualDid = issuer ?: throw IllegalArgumentException("Missing virtual issuer DID")
        //val issuerKey = issuerDidResolver.resolveToKey(issuerVirtualDid).getOrThrow()
        return signature.signerKey
    }*/
    override suspend fun getSignerKey(): Key {
        require(signature is CoseCredentialSignature) { "Invalid signature for Mdocs credential" }
        return signature.signerKey.key
    }

    companion object {
        private val log = KotlinLogging.logger { }
        private val issuerDidResolver = DidJwkResolver()

        suspend fun verifyMdocSignature(document: Document, issuerKey: Key) {
            log.trace { "Verifying issuerAuth signature with issuer key..." }
            val issuerAuthSignatureValid = document.issuerSigned.issuerAuth.verify(issuerKey.toCoseVerifier())

            require(issuerAuthSignatureValid) { "IssuerAuth signature is invalid!" }
        }

        /**
         * hack to make [MdocsCredential] mockable for testing,
         * otherwise would have to inject the document parser into the constructor
         */
        internal var msoExtractionTestHook: ((MdocsCredential) -> MobileSecurityObject?)? = null

    }

    val document by lazy { parseToDocument() }
    val documentMso by lazy { document.issuerSigned.decodeMobileSecurityObject() }

    private fun parseToDocument(): Document {
        requireNotNull(signed) { "No signed in Mdocs credential" }
        log.trace { "Signed is: $signed" }

        return MdocParser.parseToDocument(signed)
    }

    override suspend fun verify(publicKey: Key): Result<JsonElement> {
        return runCatching { verifyMdocSignature(document, publicKey) }
            .map { credentialData }
    }

    suspend fun verify(): Result<JsonElement> {
        val signerKey = getSignerKey()
        return verify(signerKey)
    }

    object MdocsCredentialSerializer : KSerializer<MdocsCredential> {

        @Serializable
        @SerialName(VC_MDOCS_SERIAL_NAME)
        private data class MdocsCredentialDataTransferObject(
            // kotlinx.serialization doesn't support unwrapping,
            // so listing each property, instead of nesting the MdocsCredential object
            val credentialData: JsonObject,
            val signed: String?,
            val docType: String,
            val signature: CredentialSignature? = null,
            val issuer: String? = null,
            val subject: String? = null,
            val format: String,
            val mso: MobileSecurityObject?
        )

        override val descriptor: SerialDescriptor = MdocsCredentialDataTransferObject.serializer().descriptor

        override fun serialize(encoder: Encoder, value: MdocsCredential) {
            val mso = msoExtractionTestHook?.invoke(value) ?: runCatching {
                value.parseToDocument().issuerSigned.decodeMobileSecurityObject()
            }.getOrNull()

            val dataTransferObject = MdocsCredentialDataTransferObject(
                credentialData = value.credentialData,
                signed = value.signed,
                docType = value.docType,
                signature = value.signature,
                issuer = value.issuer,
                subject = value.subject,
                format = value.format,
                mso = mso
            )

            encoder.encodeSerializableValue(MdocsCredentialDataTransferObject.serializer(), dataTransferObject)
        }

        override fun deserialize(decoder: Decoder): MdocsCredential {
            val dataTransferObject = decoder.decodeSerializableValue(MdocsCredentialDataTransferObject.serializer())

            return MdocsCredential(
                credentialData = dataTransferObject.credentialData,
                signed = dataTransferObject.signed,
                docType = dataTransferObject.docType,
                signature = dataTransferObject.signature,
                issuer = dataTransferObject.issuer,
                subject = dataTransferObject.subject
            )
        }
    }
}
