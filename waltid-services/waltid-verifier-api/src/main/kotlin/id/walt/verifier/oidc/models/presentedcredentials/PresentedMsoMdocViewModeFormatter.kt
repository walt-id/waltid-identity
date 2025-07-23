package id.walt.verifier.oidc.models.presentedcredentials

import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.toJsonElement
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.mdoc.mso.MSO
import id.walt.mdoc.mso.ValidityInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.cose.java.OneKey
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object PresentedMsoMdocViewModeFormatter {

    private suspend fun parseDeviceKeyFromMso(
        mso: MSO,
    ) = JWKKey.Companion.importRawPublicKey(
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
    ) = CertificateFactory
        .getInstance("X509")
        .generateCertificates(ByteArrayInputStream(issuerSigned.issuerAuth!!.x5Chain))
        .map {
            X509CertUtils.toPEMString((it as X509Certificate))
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
    ) = DeviceResponse.fromCBORBase64URL(base64UrlDeviceResponse).let { deviceResponse ->
        PresentedMsoMdocSimpleViewMode(
            version = deviceResponse.version.value,
            status = deviceResponse.status.value.toInt(),
            documents = deviceResponse.documents.map { mDoc ->
                MdocSimpleViewMode(
                    docType = mDoc.docType.value,
                    nameSpaces = transformMdocNamespaces(mDoc.issuerSigned.nameSpaces ?: emptyMap()),
                    certificateChain = parseX5cFromIssuerSigned(mDoc.issuerSigned),
                    validityInfo = mapTransformValidityInfo(mDoc.MSO!!.validityInfo),
                    deviceKey = runBlocking {
                        parseDeviceKeyJsonFromMso(mDoc.MSO!!)
                    },
                )
            },
        )

    }

    private fun createPresentedMsoMdocVerboseViewMode(
        base64UrlDeviceResponse: String,
    ) = DeviceResponse.fromCBORBase64URL(base64UrlDeviceResponse).let { deviceResponse ->
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
                                        put("deviceKey", runBlocking {
                                            parseDeviceKeyJsonFromMso(mso)
                                        }.toJsonElement())
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