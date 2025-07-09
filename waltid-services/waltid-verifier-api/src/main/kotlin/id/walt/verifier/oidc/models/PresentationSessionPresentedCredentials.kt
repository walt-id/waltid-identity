@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier.oidc.models

import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.mdoc.dataelement.toUIJson
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.sdjwt.SDJwtVC
import id.walt.w3c.utils.VCFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.cose.java.OneKey
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Serializable
@ConsistentCopyVisibility
data class PresentationSessionPresentedCredentials private constructor(
    val credentialsByFormat: Map<VCFormat, JsonArray>,
) {

    fun toJSON() = Json.encodeToJsonElement(this)

    fun toJSONObject() = this.toJSON().jsonObject

    companion object {

        private fun transformSdJwtVc(
            rawCredential: String,
        ) = buildJsonObject {
            put("raw", rawCredential.toJsonElement())
            put("decoded", buildJsonObject {
                val sdJwtVc = SDJwtVC.parse(rawCredential)
                put("header", sdJwtVc.header)
                put("payload", sdJwtVc.fullPayload)
                sdJwtVc.sdPayload.digestedDisclosures.takeIf { it.isNotEmpty() }
                    ?.let { digestedDisclosuresMap ->
                        put("disclosures", buildJsonObject {
                            digestedDisclosuresMap.forEach { entry ->
                                put(entry.key, buildJsonObject {
                                    put("disclosure", entry.value.disclosure.toJsonElement())
                                    put("salt", entry.value.salt.toJsonElement())
                                    put("key", entry.value.key.toJsonElement())
                                    put("value", entry.value.value.toJsonElement())
                                })
                            }
                        })
                    }
            })
        }

        private fun transformMdoc(
            base64UrlEncodedDeviceResponse: String,
        ) = buildJsonObject {
            put("raw", base64UrlEncodedDeviceResponse.toJsonElement())
            put("decoded", buildJsonObject {
                val deviceResponse =
                    DeviceResponse.fromCBORBase64URL(base64UrlEncodedDeviceResponse)
                put("version", deviceResponse.version.value.toJsonElement())
                put("status", deviceResponse.status.value.toJsonElement())
                put("documents", buildJsonArray {
                    deviceResponse.documents.forEach { mDoc ->
                        addJsonObject {
                            put("docType", mDoc.docType.value.toJsonElement())
                            put("issuerSigned", buildJsonObject {
                                mDoc.nameSpaces.takeIf { it.isNotEmpty() }?.let {
                                    put("nameSpaces", buildJsonObject {
                                        mDoc.nameSpaces.forEach { nameSpace ->
                                            put(nameSpace, buildJsonArray {
                                                mDoc.getIssuerSignedItems(nameSpace).forEach {
                                                    add(it.toMapElement().toUIJson())
                                                }
                                            })
                                        }
                                    })
                                }
                                put("issuerAuth", buildJsonObject {
                                    put("x5c", CertificateFactory
                                        .getInstance("X509")
                                        .generateCertificates(ByteArrayInputStream(mDoc.issuerSigned.issuerAuth!!.x5Chain))
                                        .map {
                                            X509CertUtils.toPEMString((it as X509Certificate))
                                        }.toJsonElement()
                                    )
                                    put("mso", mDoc.MSO!!.let { mso ->
                                        buildJsonObject {
                                            put("docType", mso.docType.value.toJsonElement())
                                            put("version", mso.version.value.toJsonElement())
                                            put("digestAlgorithm", mso.digestAlgorithm.value.toJsonElement())
                                            put("valueDigests", mso.valueDigests.toUIJson())
                                            put("validityInfo", mso.validityInfo.toMapElement().toUIJson())
                                            put("deviceKeyInfo", buildJsonObject {
                                                val deviceOneKey =
                                                    OneKey(CBORObject.DecodeFromBytes(mso.deviceKeyInfo.deviceKey.toCBOR()))
                                                val deviceKeyJson = runBlocking {
                                                    JWKKey.importRawPublicKey(
                                                        type = KeyType.secp256r1,
                                                        rawPublicKey = deviceOneKey.AsPublicKey().encoded,
                                                    ).exportJWKObject()
                                                }
                                                put("deviceKey", deviceKeyJson)
                                                mso.deviceKeyInfo.keyInfo?.let {
                                                    put("keyInfo", it.toUIJson())
                                                }
                                                mso.deviceKeyInfo.keyAuthorizations?.let {
                                                    put("keyAuthorizations", it.toUIJson())
                                                }
                                            })
                                            mso.status?.let {
                                                put("status", it.toMapElement().toUIJson())
                                            }
                                        }
                                    })
                                })
                            })
                            mDoc.errors?.let {
                                put("errors", it.toUIJson())
                            }
                        }
                    }
                })
                deviceResponse.documentErrors?.let { documentErrors ->
                    put("documentErrors", buildJsonArray {
                        documentErrors.forEach { documentError ->
                            documentError.toMapElement().toUIJson()
                        }
                    })
                }

            })
        }

        private fun transformW3cVp(
            rawCredential: String,
        ) = buildJsonObject {
            put("raw", rawCredential.toJsonElement())
            put("decoded", buildJsonObject {
                val decodedJwtVp = rawCredential.decodeJws()
                val parsedCredentialsArray =
                    (((decodedJwtVp.payload["vp"] as JsonObject)["verifiableCredential"] as JsonArray).map { credential ->
                        val vc = SDJwtVC.parse(credential.jsonPrimitive.content)
                        buildJsonObject {
                            put("raw", credential)
                            put("decoded", buildJsonObject {
                                put("header", vc.header)
                                put("payload", vc.fullPayload)
                                vc.sdPayload.digestedDisclosures.takeIf { it.isNotEmpty() }
                                    ?.let { digestedDisclosuresMap ->
                                        put("disclosures", buildJsonObject {
                                            digestedDisclosuresMap.forEach {
                                                put(it.key, buildJsonObject {
                                                    put(
                                                        "disclosure",
                                                        it.value.disclosure.toJsonElement()
                                                    )
                                                    put("salt", it.value.salt)
                                                    put("key", it.value.key)
                                                    put("value", it.value.value)
                                                })
                                            }
                                        })
                                    }
                            })
                        }

                    })
                val updatedVp = (decodedJwtVp.payload["vp"] as JsonObject).toMutableMap().apply {
                    this["verifiableCredential"] = JsonArray(parsedCredentialsArray)
                }.toJsonObject()
                val updatedPayload = decodedJwtVp.payload.toMutableMap().apply {
                    this["vp"] = updatedVp
                }.toJsonObject()
                put("header", decodedJwtVp.header)
                put("payload", updatedPayload)
            })
        }

        fun fromVpTokenStringsByFormat(
            vpTokenStringsByFormat: Map<VCFormat, List<String>>
        ) = PresentationSessionPresentedCredentials(
            credentialsByFormat = vpTokenStringsByFormat.mapValues { entry ->
                when (entry.key) {
                    VCFormat.sd_jwt_vc -> {
                        entry.value.map {
                            transformSdJwtVc(it)
                        }.let {
                            JsonArray(it)
                        }
                    }

                    VCFormat.mso_mdoc -> {
                        entry.value.map {
                            transformMdoc(it)
                        }
                    }

                    VCFormat.jwt_vc_json -> {
                        entry.value.map {
                            transformW3cVp(it)
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("VC format ${entry.key} not supported")
                    }
                }.let {
                    JsonArray(it)
                }
            }
        )
    }
}