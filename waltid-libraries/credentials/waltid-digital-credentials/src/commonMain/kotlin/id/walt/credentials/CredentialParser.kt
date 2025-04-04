package id.walt.credentials

import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.MdocsSubType
import id.walt.credentials.CredentialDetectorTypes.SDJWTVCSubType
import id.walt.credentials.CredentialDetectorTypes.SignaturePrimaryType
import id.walt.credentials.CredentialDetectorTypes.W3CSubType
import id.walt.credentials.formats.VerifiableCredential
import id.walt.credentials.formats.mdocs.MdocsCredential
import id.walt.credentials.formats.sdjwtvc.SdJwtCredential
import id.walt.credentials.formats.w3c.W3C11
import id.walt.credentials.formats.w3c.W3C2
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.DataIntegrityProofCredentialSignature
import id.walt.credentials.signatures.SdJwtCredentialSignature
import id.walt.credentials.utils.Base64Utils.base64Url
import id.walt.credentials.utils.Base64Utils.matchesBase64Url
import id.walt.credentials.utils.HexUtils.matchesHex
import id.walt.credentials.utils.JwtUtils
import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.mdoc.doc.MDoc
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
object CredentialParser {

    val DM_1_1_CONTEXT_INDICATORS = listOf(
        "https://www.w3.org/2018/credentials/v1", "https://w3id.org/credentials/v1"
    )

    val DM_2_0_CONTEXT_INDICATORS = listOf(
        "https://www.w3.org/ns/credentials/v2",
        "https://w3id.org/vc/status-list/2021/v1",
        "https://w3id.org/security/data-integrity/v1",
        "https://w3id.org/vc-revocation-list-2020/v1"
    )

    fun detectW3CDataModelVersion(data: JsonObject): W3CSubType {
        val contextField = data["@context"]?.jsonArray?.map { it.jsonPrimitive.content } ?: error("Missing context from W3C: $data")

        return when {
            DM_2_0_CONTEXT_INDICATORS.any { contextField.contains(it) } -> W3CSubType.W3C_2
            DM_1_1_CONTEXT_INDICATORS.any { contextField.contains(it) } -> W3CSubType.W3C_1_1
            else -> error("Unknown W3C type: $data")
        }
    }

    private fun handleMdocs(credential: String, base64: Boolean = false): Pair<CredentialDetectionResult, MdocsCredential> {
        val mdoc = if (base64) MDoc.fromCBOR(base64Url.decode(credential)) else MDoc.fromCBORHex(credential)

        return CredentialDetectionResult(CredentialPrimaryDataType.MDOCS, MdocsSubType.mdocs, SignaturePrimaryType.COSE) to
                MdocsCredential(signature = CoseCredentialSignature(), signed = credential, credentialData = JsonObject(emptyMap()))
        // TODO: ^^^ mdocs credentials
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun detectAndParse(rawCredential: String): Pair<CredentialDetectionResult, VerifiableCredential> {
        val credential = rawCredential.trim()

        return when {
            credential.startsWith("{") -> {
                val parsedJson = Json.decodeFromString<JsonObject>(credential)

                val containsDisclosures = parsedJson.contains("_sd")
                val disclosures = parsedJson["_sd"]?.jsonArray


                fun detectedUnsigned(
                    primary: CredentialPrimaryDataType, sub: CredentialDetectorTypes.CredentialSubDataType
                ) = CredentialDetectionResult(
                    credentialPrimaryType = primary,
                    credentialSubType = sub,
                    signaturePrimary = SignaturePrimaryType.UNSIGNED,
                    containsDisclosures = containsDisclosures
                )

                when {
                    parsedJson.contains("proof") -> { // Signed W3C
                        val proofElement = parsedJson["proof"]!!.jsonObject
                        val proofType = proofElement["type"]?.jsonPrimitive?.content ?: error("Invalid proof (no type)")
                        proofElement["cryptosuite"]?.jsonPrimitive?.content ?: error("Invalid proof (no cryptosuite)")

                        when (proofType) {
                            "DataIntegrityProof" -> CredentialDetectionResult(
                                CredentialPrimaryDataType.W3C, W3CSubType.W3C_2, // DataIntegrityProof was introduced with DM 2
                                SignaturePrimaryType.DATA_INTEGRITY_PROOF
                            ) to W3C2(
                                disclosableAttributes = disclosures,
                                disclosuresString = null,
                                signature = DataIntegrityProofCredentialSignature(proofElement),
                                signed = credential,
                                credentialData = parsedJson
                            )

                            else -> throw NotImplementedError("Unknown W3C proofType: $proofType")
                        }
                    }

                    // Unsigned
                    parsedJson.contains("@context") && parsedJson.contains("vct")
                        -> detectedUnsigned(
                        CredentialPrimaryDataType.SDJWTVC,
                        SDJWTVCSubType.sdjwtvcdm
                    ) to SdJwtCredential(
                        type = SDJWTVCSubType.sdjwtvcdm,
                        disclosableAttributes = disclosures,
                        disclosuresString = null,
                        signature = null,
                        signed = null,
                        credentialData = parsedJson
                    )
                    // TODO: signed version?
                    parsedJson.contains("vct") -> detectedUnsigned(
                        CredentialPrimaryDataType.SDJWTVC, SDJWTVCSubType.sdjwtvc
                    ) to SdJwtCredential(
                        type = SDJWTVCSubType.sdjwtvc,
                        disclosableAttributes = disclosures,
                        disclosuresString = null,
                        signature = null,
                        signed = null,
                        credentialData = parsedJson
                    )

                    parsedJson.contains("@context") && parsedJson.contains("type") -> {
                        val w3cModelVersion = detectW3CDataModelVersion(parsedJson)
                        val credential = when (w3cModelVersion) {
                            W3CSubType.W3C_1_1 -> W3C11(
                                disclosableAttributes = disclosures,
                                disclosuresString = null,
                                signature = null,
                                signed = null,
                                credentialData = parsedJson
                            )

                            W3CSubType.W3C_2 -> W3C2(
                                disclosableAttributes = disclosures,
                                disclosuresString = null,
                                signature = null,
                                signed = null,
                                credentialData = parsedJson
                            )
                        }

                        detectedUnsigned(
                            CredentialPrimaryDataType.W3C, w3cModelVersion
                        ) to credential
                    }

                    else -> throw NotImplementedError("unknown: $credential")
                }
            }

            credential.isJwt() -> {
                // TODO: also check if `typ` matches, and pass through `alg`
                val (header, payload, signature) = JwtUtils.parseJwt(credential)

                val containsDisclosures = payload.contains("_sd")
                val disclosures = payload["_sd"]?.jsonArray

                fun detectedSdjwtSigned(
                    primary: CredentialPrimaryDataType, sub: CredentialDetectorTypes.CredentialSubDataType
                ) = CredentialDetectionResult(
                    credentialPrimaryType = primary,
                    credentialSubType = sub,
                    signaturePrimary = SignaturePrimaryType.SDJWT,
                    containsDisclosures = containsDisclosures,
                    providesDisclosures = true
                )

                when {
                    // SD-JWT disclosures
                    credential.contains("~") -> {

                        val signedCredentialWithoutDisclosures = credential.substringBefore("~")
                        val availableDisclosures = signature.substringAfter("~")

                        when {
                            payload.contains("@context") && payload.contains("vct")
                                -> detectedSdjwtSigned(CredentialPrimaryDataType.SDJWTVC, SDJWTVCSubType.sdjwtvcdm) to
                                    SdJwtCredential(
                                        type = SDJWTVCSubType.sdjwtvcdm,
                                        disclosableAttributes = disclosures,
                                        disclosuresString = availableDisclosures,
                                        signature = SdJwtCredentialSignature(),
                                        signed = signedCredentialWithoutDisclosures,
                                        credentialData = payload
                                    )

                            payload.contains("@context") && ((payload["@context"]?.jsonArray?.map { it.jsonPrimitive.content }
                                ?.contains("https://www.w3.org/ns/credentials/v2") == true) || payload.contains("type"))
                                -> {
                                val w3cModelVersion = detectW3CDataModelVersion(payload)
                                val credential = when (w3cModelVersion) {
                                    W3CSubType.W3C_1_1 -> W3C11(
                                        disclosableAttributes = disclosures,
                                        disclosuresString = availableDisclosures,
                                        signature = SdJwtCredentialSignature(),
                                        signed = signedCredentialWithoutDisclosures,
                                        credentialData = payload
                                    )

                                    W3CSubType.W3C_2 -> W3C2(
                                        disclosableAttributes = disclosures,
                                        disclosuresString = availableDisclosures,
                                        signature = SdJwtCredentialSignature(),
                                        signed = signedCredentialWithoutDisclosures,
                                        credentialData = payload
                                    )
                                }
                                detectedSdjwtSigned(CredentialPrimaryDataType.W3C, w3cModelVersion) to credential
                            }

                            payload.contains("vct") && !payload.contains("@context")
                                -> detectedSdjwtSigned(CredentialPrimaryDataType.SDJWTVC, SDJWTVCSubType.sdjwtvc) to
                                    SdJwtCredential(
                                        type = SDJWTVCSubType.sdjwtvcdm,
                                        disclosableAttributes = disclosures,
                                        disclosuresString = availableDisclosures,
                                        signature = SdJwtCredentialSignature(),
                                        signed = signedCredentialWithoutDisclosures,
                                        credentialData = payload
                                    )

                            else -> throw NotImplementedError("Unknown SD-JWT-signed credential: $credential")
                        }
                    }

                    // JOSE signature
                    else -> {
                        val unsignedCredential = payload["vc"]?.toString() ?: payload.toString()
                        val unsignedCredentialDetection = detectAndParse(unsignedCredential)
                        unsignedCredentialDetection.copy(first = unsignedCredentialDetection.first.copy(signaturePrimary = SignaturePrimaryType.JWT))
                    }
                }
            }

            // TODO: W3C could also has COSE signature
            credential.matchesHex() -> handleMdocs(credential, false)
            credential.matchesBase64Url() -> handleMdocs(credential, true)

            else -> throw NotImplementedError("unknown: $credential")
        }
    }
}
