@file:OptIn(ExperimentalTime::class)

package id.walt.verifier.oidc.models.presentedcredentials

import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.cose.CoseHeaders
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.encodeToHexString
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.json.toJsonElement
import id.walt.mdoc.dataelement.json.toUIJson
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.mdoc.mso.MSO
import id.walt.mdoc.mso.ValidityInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse as Mdoc2DeviceResponse
import id.walt.mdoc.objects.document.Document as Mdoc2Document
import id.walt.mdoc.objects.mso.MobileSecurityObject as Mdoc2MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo as Mdoc2ValidityInfo
import id.walt.mdoc.parser.MdocParser
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonObject
import org.cose.java.OneKey
import kotlin.time.ExperimentalTime

object PresentedMsoMdocViewModeFormatter {

    private suspend fun parseDeviceKeyFromMso(
        mso: MSO,
    ) = JWKKey.importRawPublicKey(
        type = KeyType.secp256r1,
        rawPublicKey = OneKey(
            CBORObject.DecodeFromBytes(
                mso.deviceKeyInfo.deviceKey.toCBOR()
            )
        ).AsPublicKey().encoded,
    )

    private suspend fun parseDeviceKeyJsonFromMso(
        mso: MSO,
    ) = parseDeviceKeyFromMso(mso).exportJWKObject()

    private fun parseX5cFromIssuerSigned(
        issuerSigned: IssuerSigned,
    ) = issuerSigned.issuerAuth!!.x5Chain!!.map {
        X509CertUtils.parse(it).let {
            X509CertUtils.toPEMString(it)
        }
    }

    private fun mapTransformValidityInfo(
        validityInfo: ValidityInfo,
    ) = mapOf(
        *listOfNotNull(
            "signed" to validityInfo.signed.value.toString().toJsonElement(),
            "validFrom" to validityInfo.validFrom.value.toString().toJsonElement(),
            "validUntil" to validityInfo.validUntil.value.toString().toJsonElement(),
            validityInfo.expectedUpdate?.let {
                "expectedUpdate" to it.value.toString().toJsonElement()
            }
        ).toTypedArray()
    )
    
    private fun mapTransformValidityInfoNew(
        validityInfo: Mdoc2ValidityInfo,
    ): Map<String, JsonElement> = buildMap {
        put("signed", validityInfo.signed.toString().toJsonElement())
        put("validFrom", validityInfo.validFrom.toString().toJsonElement())
        put("validUntil", validityInfo.validUntil.toString().toJsonElement())
        validityInfo.expectedUpdate?.let {
            put("expectedUpdate", it.toString().toJsonElement())
        }
    }

    private fun transformMdocNamespaces(
        nameSpaces: Map<String, List<EncodedCBORElement>>,
    ) = nameSpaces.mapValues { entry ->
        entry.value.associate { encodedCBORElement ->
            val decodedMapElement = encodedCBORElement.decode<MapElement>()
            decodedMapElement.value[MapKey("elementIdentifier")]!!.internalValue.toString() to
                    decodedMapElement.value[MapKey("elementValue")]!!.toJsonElement()
        }
    }

    private fun transformDeviceSignedNamespaces(
        nameSpaces: EncodedCBORElement,
    ) = nameSpaces.decode<MapElement>().toUIJson().jsonObject.toMap()

    private fun createPresentedMsoMdocSimpleViewMode(
        base64UrlDeviceResponse: String,
    ) = runBlocking {
        // Try parsing with new library first, fallback to old library for backward compatibility
        val parsedWithNewLibrary = runCatching {
            // Try parsing with MdocParser (handles both DeviceResponse and Document)
            val parsedDocument = MdocParser.parseToDocument(base64UrlDeviceResponse)
            // Construct a DeviceResponse from the document for the view mode
            val deviceResponse = Mdoc2DeviceResponse(
                version = "1.0",
                documents = arrayOf(parsedDocument),
                status = 0u
            )
            
            val document = deviceResponse.documents?.firstOrNull() 
                ?: throw IllegalArgumentException("No documents in DeviceResponse")
            val mso = document.issuerSigned.decodeMobileSecurityObject()
            
            PresentedMsoMdocSimpleViewMode(
                version = deviceResponse.version,
                status = deviceResponse.status.toInt(),
                documents = deviceResponse.documents?.map { doc ->
                    val docMso = doc.issuerSigned.decodeMobileSecurityObject()
                    MdocSimpleViewMode(
                        docType = doc.docType,
                        nameSpaces = doc.issuerSigned.namespacesToJson().jsonObject.toMap().mapValues { (_, v) ->
                            (v as JsonObject).toMap()
                        },
                        certificateChain = extractX5cFromNewLibrary(doc),
                        validityInfo = mapTransformValidityInfoNew(docMso.validityInfo),
                        deviceKey = extractDeviceKeyFromNewLibrary(docMso),
                    )
                } ?: emptyList(),
            )
        }
        
        parsedWithNewLibrary.getOrElse {
            // Fallback to old library parser for backward compatibility
            DeviceResponse.fromCBORBase64URL(base64UrlDeviceResponse).let { deviceResponse ->
        PresentedMsoMdocSimpleViewMode(
            version = deviceResponse.version.value,
            status = deviceResponse.status.value.toInt(),
            documents = deviceResponse.documents.map { mDoc ->
                MdocSimpleViewMode(
                    docType = mDoc.docType.value,
                    nameSpaces = transformMdocNamespaces(mDoc.issuerSigned.nameSpaces ?: emptyMap()),
                    certificateChain = parseX5cFromIssuerSigned(mDoc.issuerSigned),
                    validityInfo = mapTransformValidityInfo(mDoc.MSO!!.validityInfo),
                            deviceKey = parseDeviceKeyJsonFromMso(mDoc.MSO!!),
                        )
                    },
                )
            }
        }
    }
    
    private suspend fun extractX5cFromNewLibrary(document: Mdoc2Document): List<String> {
        val issuerAuth = document.issuerSigned.issuerAuth
        return issuerAuth.unprotected.x5chain?.map { cert ->
            X509CertUtils.parse(cert.rawBytes).let {
                X509CertUtils.toPEMString(it)
            }
        } ?: emptyList()
    }
    
    private suspend fun extractDeviceKeyFromNewLibrary(mso: Mdoc2MobileSecurityObject): JsonObject {
        val deviceKeyJwk = mso.deviceKeyInfo.deviceKey.toJWK()
        val deviceKey = JWKKey.importJWK(Json.encodeToString(deviceKeyJwk)).getOrThrow()
        return deviceKey.exportJWKObject()
    }

    private fun createPresentedMsoMdocVerboseViewMode(
        base64UrlDeviceResponse: String,
    ) = runBlocking {
        // Try parsing with new library first, fallback to old library for backward compatibility
        val parsedWithNewLibrary = runCatching {
            // Try parsing with MdocParser (handles both DeviceResponse and Document)
            val document = MdocParser.parseToDocument(base64UrlDeviceResponse)
            // Construct a DeviceResponse from the document for the view mode
            val deviceResponse = Mdoc2DeviceResponse(
                version = "1.0",
                documents = arrayOf(document),
                status = 0u
            )
            
            PresentedMsoMdocVerboseViewMode(
                raw = base64UrlDeviceResponse,
                version = deviceResponse.version,
                status = deviceResponse.status.toInt(),
                documents = deviceResponse.documents?.map { doc ->
                    val mso = doc.issuerSigned.decodeMobileSecurityObject()
                    val issuerAuth = doc.issuerSigned.issuerAuth
                    // Parse protected headers - if empty, use default headers
                    val protectedHeaders = if (issuerAuth.protected.isEmpty()) {
                        CoseHeaders()
                    } else {
                        // Use the same approach as MdocParser - decode using coseCompliantCbor
                        // Since we can't directly access Cbor, we'll parse it through the Document's issuerAuth
                        // For now, create empty headers and extract algorithm from issuerAuth if needed
                        CoseHeaders() // TODO: Parse protected headers properly when Cbor is accessible
                    }
                    
                    val issuerSignedNamespacesJson = doc.issuerSigned.namespacesToJson()
                    
                    MdocVerboseViewMode(
                        docType = doc.docType,
                        issuerSigned = IssuerSignedParsed(
                            nameSpaces = issuerSignedNamespacesJson.jsonObject.toMap().mapValues { (_, v) ->
                                (v as JsonObject).toMap()
                            },
                            issuerAuth = IssuerAuthParsed(
                                x5c = extractX5cFromNewLibrary(doc),
                                algorithm = protectedHeaders.algorithm ?: -1,
                                protectedHeader = buildMap<String, JsonElement> {
                                    protectedHeaders.algorithm?.let { put("alg", it.toJsonElement()) }
                                    protectedHeaders.kid?.let { put("kid", it.decodeToString().toJsonElement()) }
                                },
                                payload = MsoParsed(
                                    docType = mso.docType,
                                    version = mso.version,
                                    digestAlgorithm = mso.digestAlgorithm,
                                    valueDigests = buildMap<String, JsonElement> {
                                        mso.valueDigests.forEach { (namespace, digests) ->
                                            put(namespace, buildMap<String, JsonElement> {
                                                digests.entries.forEach { digest ->
                                                    put(digest.key.toString(), JsonPrimitive(digest.value.joinToString("") { "%02x".format(it) }))
                                                }
                                            }.let { map -> buildJsonObject { map.forEach { (k, v) -> put(k, v) } } })
                                        }
                                    },
                                    validityInfo = mapTransformValidityInfoNew(mso.validityInfo),
                                    deviceKeyInfo = buildMap<String, JsonElement> {
                                        put("deviceKey", extractDeviceKeyFromNewLibrary(mso))
                                        mso.deviceKeyInfo.keyInfo?.let {
                                            put("keyInfo", buildJsonObject {
                                                it.forEach { (k, v) -> put(k.toString(), JsonPrimitive(v)) }
                                            })
                                        }
                                        mso.deviceKeyInfo.keyAuthorizations?.let {
                                            put("keyAuthorizations", Json.encodeToJsonElement(
                                                id.walt.mdoc.objects.mso.KeyAuthorization.serializer(),
                                                it
                                            ))
                                        }
                                    },
                                    status = mso.status?.let { 
                                        Json.encodeToJsonElement(
                                            id.walt.mdoc.objects.mso.Status.serializer(),
                                            it
                                        )
                                    },
                                ),
                            ),
                        ),
                        deviceSigned = doc.deviceSigned?.let { deviceSigned ->
                            DeviceSignedParsed(
                                nameSpaces = deviceSigned.namespaces.value.namespacesToJson().jsonObject.toMap(),
                                deviceAuth = buildMap<String, JsonElement> {
                                    deviceSigned.deviceAuth.deviceSignature?.let {
                                        put("deviceSignature", JsonPrimitive(it.serialize().joinToString("") { "%02x".format(it) }))
                                    }
                                    deviceSigned.deviceAuth.deviceMac?.let {
                                        put("deviceMac", JsonPrimitive(it.toTagged().joinToString("") { "%02x".format(it) }))
                                    }
                                },
                            )
                        },
                        errors = doc.errors?.let { 
                            buildJsonObject {
                                it.forEach { (namespace, errors) ->
                                    putJsonObject(namespace) {
                                        errors.forEach { (elementId, errorCode) ->
                                            put(elementId, JsonPrimitive(errorCode))
                                        }
                                    }
                                }
                            }
                        },
                    )
                } ?: emptyList(),
            )
        }
        
        parsedWithNewLibrary.getOrElse {
            // Fallback to old library parser for backward compatibility
            DeviceResponse.fromCBORBase64URL(base64UrlDeviceResponse).let { deviceResponse ->
        PresentedMsoMdocVerboseViewMode(
            raw = base64UrlDeviceResponse,
            version = deviceResponse.version.value,
            status = deviceResponse.status.value.toInt(),
            documents = deviceResponse.documents.map { mDoc ->
                MdocVerboseViewMode(
                    docType = mDoc.docType.value,
                    issuerSigned = IssuerSignedParsed(
                        nameSpaces = transformMdocNamespaces(mDoc.issuerSigned.nameSpaces ?: emptyMap()),
                        issuerAuth = IssuerAuthParsed(
                            x5c = parseX5cFromIssuerSigned(mDoc.issuerSigned),
                            algorithm = mDoc.issuerSigned.issuerAuth!!.algorithm,
                            protectedHeader = mDoc.issuerSigned.issuerAuth!!.protectedHeader.let { protectedHeaderSerialized ->
                                val decodedProtectedHeader = DataElement.fromCBOR<MapElement>(protectedHeaderSerialized)
                                decodedProtectedHeader.toUIJson().jsonObject.toMap()
                            },
                            payload = mDoc.MSO!!.let { mso ->
                                MsoParsed(
                                    docType = mso.docType.value,
                                    version = mso.version.value,
                                    digestAlgorithm = mso.digestAlgorithm.value,
                                    valueDigests = mso.valueDigests.toUIJson().jsonObject.toMap(),
                                    validityInfo = mapTransformValidityInfo(mso.validityInfo),
                                    deviceKeyInfo = buildMap {
                                                put("deviceKey", parseDeviceKeyJsonFromMso(mso).toJsonElement())
                                        mso.deviceKeyInfo.keyInfo?.let {
                                            put("keyInfo", it.toUIJson())
                                        }
                                        mso.deviceKeyInfo.keyAuthorizations?.let {
                                            put("keyAuthorizations", it.toUIJson())
                                        }
                                    }.toMap(),
                                    status = mso.status?.toMapElement().toJsonElement(),
                                )
                            },
                        ),
                    ),
                    deviceSigned = mDoc.deviceSigned?.let { deviceSigned ->
                        DeviceSignedParsed(
                            nameSpaces = transformDeviceSignedNamespaces(deviceSigned.nameSpaces),
                            deviceAuth = deviceSigned.deviceAuth.toMapElement().toUIJson().jsonObject,
                        )
                    },
                    errors = mDoc.errors?.toJsonElement(),
                )
            },
        )
            }
        }
    }

    fun fromDeviceResponseString(
        base64UrlEncodedDeviceResponse: String,
        viewMode: PresentedCredentialsViewMode = PresentedCredentialsViewMode.simple,
    ) = when (viewMode) {
        PresentedCredentialsViewMode.simple -> {
            createPresentedMsoMdocSimpleViewMode(base64UrlEncodedDeviceResponse)
        }

        PresentedCredentialsViewMode.verbose -> {
            createPresentedMsoMdocVerboseViewMode(base64UrlEncodedDeviceResponse)
        }
    }
}

@Serializable
@SerialName("mso_mdoc_view_simple")
data class PresentedMsoMdocSimpleViewMode(
    val version: String,
    val status: Int,
    val documents: List<MdocSimpleViewMode>,
) : PresentedCredentialView()

@Serializable
data class MdocSimpleViewMode(
    val docType: String,
    val nameSpaces: Map<String, Map<String, JsonElement>>,
    val certificateChain: List<String>,
    val validityInfo: Map<String, JsonElement>,
    val deviceKey: JsonObject,
)

@Serializable
@SerialName("mso_mdoc_view_verbose")
data class PresentedMsoMdocVerboseViewMode(
    val raw: String,
    val version: String,
    val status: Int,
    val documents: List<MdocVerboseViewMode>,
) : PresentedCredentialView()

@Serializable
data class MdocVerboseViewMode(
    val docType: String,
    val issuerSigned: IssuerSignedParsed,
    val deviceSigned: DeviceSignedParsed? = null,
    val errors: JsonElement? = null,
)

@Serializable
data class IssuerSignedParsed(
    val nameSpaces: Map<String, Map<String, JsonElement>>,
    val issuerAuth: IssuerAuthParsed,
)

@Serializable
data class IssuerAuthParsed(
    val x5c: List<String>,
    val algorithm: Int,
    val protectedHeader: Map<String, JsonElement>,
    val payload: MsoParsed,
)

@Serializable
data class MsoParsed(
    val docType: String,
    val version: String,
    val digestAlgorithm: String,
    val valueDigests: Map<String, JsonElement>,
    val validityInfo: Map<String, JsonElement>,
    val deviceKeyInfo: Map<String, JsonElement>,
    val status: JsonElement? = null,
)

@Serializable
data class DeviceSignedParsed(
    val nameSpaces: Map<String, JsonElement>,
    val deviceAuth: Map<String, JsonElement>,
)
