@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseHmacKey
import id.walt.cose.CoseMac0
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSignDetached
import id.walt.cose.selectCoseSignatureAlgorithm
import id.walt.cose.toEncodedJwk
import id.walt.cose.toCoseKey
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.crypto.MdocKdf
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.EncryptedDocuments
import id.walt.mdoc.objects.deviceretrieval.EncryptedDocumentsPlaintext
import id.walt.mdoc.objects.deviceretrieval.EncryptionParameters
import id.walt.mdoc.objects.deviceretrieval.ZkDocument
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.IssuerSignedList
import org.kotlincrypto.hash.sha2.SHA256

sealed interface MdocAuthenticationMethod {
    data class Signature(val acceptedAlgorithms: Set<Int>? = null) : MdocAuthenticationMethod
    data class Mac(val eReaderKey: id.walt.cose.CoseKey) : MdocAuthenticationMethod
}

data class MdocDocumentPresentation(
    val source: Document,
    val holderKey: Key,
    val selectedIssuerElements: Set<ElementReference>,
    val deviceNameSpaces: DeviceNameSpaces = DeviceNameSpaces(emptyMap()),
    val elementErrors: Map<String, Map<String, Long>> = emptyMap(),
    val authentication: MdocAuthenticationMethod,
) {
    init {
        require(elementErrors.keys.all { it.isNotBlank() })
        require(elementErrors.values.all { errors ->
            errors.isNotEmpty() && errors.keys.all { it.isNotBlank() }
        })
    }
}

data class MdocDocumentError(val docType: String, val code: Long) {
    init { require(docType.isNotBlank()) }
}

/** Builds exact selectively-disclosed documents while keeping key access in the caller-provided handle. */
class MdocResponseBuilder {
    suspend fun buildDocument(
        presentation: MdocDocumentPresentation,
        transcript: SessionTranscript,
    ): Document {
        val source = presentation.source
        val mso = source.issuerSigned.decodeMobileSecurityObject()
        require(source.docType == mso.docType) { "Document and MSO docType do not match" }
        requireHolderKeyMatchesMso(presentation.holderKey, mso.deviceKeyInfo.deviceKey)
        val selectedIssuer = selectIssuerSigned(source.issuerSigned, presentation.selectedIssuerElements)
        requireDeviceNamespacesAuthorized(presentation.deviceNameSpaces, mso.deviceKeyInfo.keyAuthorizations)
        val encodedNamespaces = id.walt.cose.coseCompliantCbor.encodeToByteArray(
            DeviceNameSpaces.serializer(),
            presentation.deviceNameSpaces,
        )
        val wrappedNamespaces = ByteStringWrapper(presentation.deviceNameSpaces, encodedNamespaces)
        val deviceAuthentication = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            transcript,
            source.docType,
            wrappedNamespaces,
        )
        val auth = when (val method = presentation.authentication) {
            is MdocAuthenticationMethod.Signature -> {
                val algorithm = presentation.holderKey.selectCoseSignatureAlgorithm(method.acceptedAlgorithms)
                DeviceAuth(
                    deviceSignature = CoseSign1.createAndSignDetached(
                        protectedHeaders = CoseHeaders(algorithm = algorithm),
                        detachedPayload = deviceAuthentication,
                        key = presentation.holderKey,
                    )
                )
            }
            is MdocAuthenticationMethod.Mac -> DeviceAuth(
                deviceMac = createMac(presentation.holderKey, method.eReaderKey, transcript, deviceAuthentication)
            )
        }
        return Document(
            docType = source.docType,
            issuerSigned = selectedIssuer,
            deviceSigned = DeviceSigned(wrappedNamespaces, auth),
            errors = presentation.elementErrors.takeIf { it.isNotEmpty() },
            extensions = source.extensions,
        )
    }

    suspend fun buildResponse(
        presentations: List<MdocDocumentPresentation> = emptyList(),
        transcript: SessionTranscript,
        zkDocuments: List<ZkDocument> = emptyList(),
        encryptedDocuments: List<EncryptedDocuments> = emptyList(),
        documentErrors: List<MdocDocumentError> = emptyList(),
        status: UInt = 0u,
    ): DeviceResponse {
        return DeviceResponse(
            version = "1.0",
            documents = presentations.map { buildDocument(it, transcript) }.takeIf { it.isNotEmpty() },
            zkDocuments = zkDocuments.toList().takeIf { it.isNotEmpty() },
            encryptedDocuments = encryptedDocuments.toList().takeIf { it.isNotEmpty() },
            documentErrors = documentErrors.map { mapOf(it.docType to it.code) }.takeIf { it.isNotEmpty() },
            status = status,
        )
    }

    /** Builds and HPKE-seals the response to one DocRequest using the exact requested parameters. */
    suspend fun buildEncryptedDocuments(
        docRequestId: UInt,
        presentations: List<MdocDocumentPresentation> = emptyList(),
        zkDocuments: List<ZkDocument> = emptyList(),
        transcript: SessionTranscript,
        encryptionParameters: ByteStringWrapper<EncryptionParameters>,
    ): EncryptedDocuments {
        require(presentations.isNotEmpty() || zkDocuments.isNotEmpty()) {
            "An encrypted document response must contain a document"
        }
        val exactParameters = encryptionParameters.serialized.takeIf { it.isNotEmpty() }
            ?: id.walt.cose.coseCompliantCbor.encodeToByteArray(
                EncryptionParameters.serializer(),
                encryptionParameters.value,
            )
        val encryptionTranscript = transcript.withDocumentEncryptionParameters(exactParameters)
        val plaintext = EncryptedDocumentsPlaintext(
            documents = presentations.map { buildDocument(it, encryptionTranscript) }.takeIf { it.isNotEmpty() },
            zkDocuments = zkDocuments.toList().takeIf { it.isNotEmpty() },
        )
        val sealed = Hpke.sealBase(
            recipientPublicKey = encryptionParameters.value.recipientPublicKey.toEncodedJwk(),
            plaintext = id.walt.cose.coseCompliantCbor.encodeToByteArray(
                EncryptedDocumentsPlaintext.serializer(),
                plaintext,
            ),
            info = id.walt.cose.coseCompliantCbor.encodeToByteArray(
                SessionTranscript.serializer(),
                encryptionTranscript,
            ),
        )
        require(sealed.suite == Hpke.P256_HKDF_SHA256_AES_128_GCM) {
            "Document response encryption requires the ISO HPKE suite"
        }
        return EncryptedDocuments(
            enc = sealed.encapsulatedKey.toByteArray(),
            cipherText = sealed.ciphertext.toByteArray(),
            docRequestID = docRequestId,
        )
    }

    private fun selectIssuerSigned(
        issuerSigned: IssuerSigned,
        selected: Set<ElementReference>,
    ): IssuerSigned {
        val available = issuerSigned.namespaces.orEmpty()
        val selectedByNamespace = selected.groupBy(ElementReference::namespace)
        val namespaces = selectedByNamespace.mapValues { (namespace, references) ->
            val source = available[namespace]
                ?: throw IllegalArgumentException("Selected issuer namespace is unavailable: $namespace")
            val identifiers = references.map(ElementReference::elementIdentifier).toSet()
            val entries = identifiers.map { identifier ->
                source.entries.singleOrNull { it.value.elementIdentifier == identifier }
                    ?: throw IllegalArgumentException("Selected issuer element is unavailable: $namespace.$identifier")
            }
            IssuerSignedList(entries)
        }
        return IssuerSigned.fromIssuerSignedLists(
            namespaces = namespaces,
            issuerAuth = issuerSigned.issuerAuth,
            extensions = issuerSigned.extensions,
        )
    }

    private suspend fun requireHolderKeyMatchesMso(holderKey: Key, msoKey: id.walt.cose.CoseKey) {
        val holderPublic = requireNotNull(holderKey.capabilities.publicKeyExporter) {
            "Selected holder key cannot export public material"
        }.exportPublicKey() as? EncodedKey.Jwk
            ?: throw IllegalArgumentException("Selected holder key cannot export a public JWK")
        val holderCose = holderPublic.toCoseKey()
        require(
            holderCose.kty == msoKey.kty && holderCose.crv == msoKey.crv &&
                holderCose.x.contentEquals(msoKey.x) && holderCose.y.contentEquals(msoKey.y)
        ) {
            "Selected holder key does not match the mdoc MSO device key"
        }
    }

    private fun requireDeviceNamespacesAuthorized(
        namespaces: DeviceNameSpaces,
        authorizations: id.walt.mdoc.objects.mso.KeyAuthorization?,
    ) {
        if (namespaces.entries.isEmpty()) return
        val policy = requireNotNull(authorizations) { "Device-signed elements require MSO keyAuthorizations" }
        namespaces.entries.forEach { (namespace, elements) ->
            val namespaceAuthorized = namespace in policy.namespaces.orEmpty()
            val authorizedElements = policy.dataElements?.get(namespace).orEmpty().toSet()
            require(namespace !in policy.namespaces.orEmpty() || namespace !in policy.dataElements.orEmpty()) {
                "MSO keyAuthorizations cannot grant the same namespace twice"
            }
            require(namespaceAuthorized || elements.entries.all { it.key in authorizedElements }) {
                "Device key is not authorized for all elements in $namespace"
            }
        }
    }

    private suspend fun createMac(
        holderKey: Key,
        eReaderKey: id.walt.cose.CoseKey,
        transcript: SessionTranscript,
        deviceAuthentication: ByteArray,
    ): CoseMac0 {
        val keyAgreementAlgorithm = MdocSessionKeyValidator.agreementAlgorithm(holderKey)
        val agreement = requireNotNull(holderKey.capabilities.keyAgreement)
        val readerPublic = MdocSessionKeyValidator.requireCompatiblePeerKey(
            holderKey,
            eReaderKey.toEncodedJwk(),
            dev.whyoleg.cryptography.CryptographyProvider.Default,
        )
        val sharedSecret = agreement.generateSharedSecret(readerPublic, keyAgreementAlgorithm).toByteArray()
        val transcriptBytes = MdocCryptoHelper.buildSessionTranscriptBytes(transcript)
        val salt = SHA256().digest(transcriptBytes)
        val eMacKey = try {
            MdocKdf.deriveSha256(sharedSecret, salt, "EMacKey".encodeToByteArray(), 32)
        } finally {
            sharedSecret.fill(0)
            salt.fill(0)
        }
        return try {
            CoseMac0.createAndMacDetached(
                protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.HMAC_256),
                detachedPayload = deviceAuthentication,
                creator = CoseHmacKey(eMacKey).toCoseMacCreator(Cose.Algorithm.HMAC_256),
            )
        } finally {
            eMacKey.fill(0)
        }
    }
}
