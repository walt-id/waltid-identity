@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.credentials

import id.walt.cose.coseCompliantCbor
import id.walt.credentials.CredentialDetectorTypes.CredentialDetectionResult
import id.walt.credentials.CredentialDetectorTypes.CredentialPrimaryDataType
import id.walt.credentials.CredentialDetectorTypes.SDJWTVCSubType
import id.walt.credentials.CredentialDetectorTypes.SignaturePrimaryType
import id.walt.credentials.CredentialDetectorTypes.W3CSubType
import id.walt.credentials.formats.*
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.credentials.signatures.DataIntegrityProofCredentialSignature
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.credentials.signatures.SdJwtCredentialSignature
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.utils.JwtUtils
import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.credentials.utils.SdJwtUtils.dropDollarPrefix
import id.walt.credentials.utils.SdJwtUtils.getSdArrays
import id.walt.credentials.utils.SdJwtUtils.parseDisclosureString
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object CredentialParser {

    private val log = KotlinLogging.logger { }

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

    fun JsonElement?.getString(key: String): String? = this?.jsonObject[key]?.jsonPrimitive?.contentOrNull
    operator fun JsonElement?.get(key: String): JsonElement? = this?.jsonObject[key]

    fun JsonElement?.getItAsStringOrId() = when (this) {
        null -> null
        is JsonNull -> null
        is JsonObject -> this.getString("id")
        is JsonPrimitive -> this.content
        else -> throw UnsupportedOperationException("Unsupported JSON type: $this")
    }

    fun getCredentialDataIssuer(data: JsonObject) = data["issuer"].getItAsStringOrId() ?: data["vc"]["issuer"].getItAsStringOrId()
    fun getJwtHeaderOrDataIssuer(data: JsonObject) = data.getString("iss") ?: getCredentialDataIssuer(data)

    fun getCredentialDataSubject(data: JsonObject) =
        data["credentialSubject"].getItAsStringOrId() ?: data["vc"]["credentialSubject"].getItAsStringOrId()

    fun getJwtHeaderOrDataSubject(data: JsonObject) = data.getString("sub") ?: getCredentialDataSubject(data)

    suspend fun handleMdocs(credential: String, base64: Boolean = false): Pair<CredentialDetectionResult, MdocsCredential> {
        log.trace { "Handle mdocs, string: $credential" }

        // --- New mdocs handling ---

        // vvvv
        val deviceResponseBytes = if (base64) credential.decodeFromBase64Url() else credential.hexToByteArray()
        // ^^^^

        // Parse DeviceResponse or Document into Document

        // vvvv
        //val document = MdocParser.parseToDocument(credential)
        // ^^^^

        val document = runCatching {
            val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(deviceResponseBytes)
            val document =
                deviceResponse.documents?.firstOrNull() ?: throw IllegalArgumentException("Mdoc document not found in DeviceResponse")
            log.trace { "Mdoc parsed (from device response)" }
            document
        }.recoverCatching {
            log.trace { "Mdoc could not be parsed as device response, trying as document" }
            val document = coseCompliantCbor.decodeFromByteArray<Document>(deviceResponseBytes)
            log.trace { "Mdoc parsed (from document)" }
            document
        }.getOrThrow()


        // Build a JSON object that includes the docType for the DcqlMatcher
        val credentialData = buildJsonObject {
            put("docType", JsonPrimitive(document.docType))

            document.issuerSigned.namespaces?.forEach { (namespace, issuerSignedList) ->
                putJsonObject(namespace) {
                    val x = issuerSignedList.entries.map { it.value }
                    x.forEach { item: IssuerSignedItem ->
                        log.trace { "$namespace - ${item.elementIdentifier} -> ${item.elementValue} (${item.elementValue::class.simpleName})" }

                        val serialized: JsonElement = MdocsCborSerializer.lookupSerializer(namespace, item.elementIdentifier)
                            ?.runCatching {
                                Json.encodeToJsonElement(this as KSerializer<Any?>, item.elementValue)
                            }?.getOrElse { log.warn { "Error encoding with custom serializer: ${it.stackTraceToString()}" }; null }
                            ?: item.elementValue.toJsonElement()

                        log.trace { "as JsonElement: $serialized" }
                        put(item.elementIdentifier, serialized)
                    }

                }
            }
            //put(namespace, Json.encodeToJsonElement(issuerSignedList))
        }

        // TODO: Issuer currently issues incorrect Mdocs, so old mdocs lib is used.
        // TODO: When issuer is updated, remove the old mdocs lib usage below.

        val hasSd = !document.issuerSigned.namespaces.isNullOrEmpty()

        // --- Old mdocs handling ---


//        val mapElement =
//            if (base64) Cbor.decodeFromByteArray<MapElement>(base64Url.decode(credential)) else Cbor.decodeFromHexString<MapElement>(
//                credential
//            )
//        // detect if this is the issuer-signed structure or the full mdoc
//        if (!mapElement.value.keys.containsAll(listOf(MapKey("docType"), MapKey("issuerSigned"))))
//            throw NotImplementedError("Invalid mdoc structure: $credential, only full mdocs are currently supported. If this is an issuer signed structure, like returned by an OpenID4VCI issuer, the doc type is additionally required to restore the full mdoc.")
//        val mdoc = MDoc.fromMapElement(mapElement)
//        val hasSd = !(mdoc.issuerSigned.nameSpaces?.values?.flatten().isNullOrEmpty())
//
//
        // --- Return ---

        val parsedIssuerAuth = document.issuerSigned.getParsedIssuerAuth()
        val x5CList = X5CList(parsedIssuerAuth.x5c.map { X5CCertificateString(it) })

        return CredentialDetectionResult(
            credentialPrimaryType = CredentialPrimaryDataType.MDOCS,
            credentialSubType = CredentialDetectorTypes.MdocsSubType.mdocs,
            signaturePrimary = SignaturePrimaryType.COSE,
            containsDisclosables = hasSd, providesDisclosures = hasSd
        ) to MdocsCredential(
            signature = CoseCredentialSignature(
                x5cList = x5CList,
                signerKey = DirectSerializedKey(parsedIssuerAuth.signerKey)
            ),
            //credentialData = credentialData,
            //credentialDataOld = mdoc.issuerSigned.toUIJson(),
            credentialData = credentialData /*JsonObject(credentialData
                mdoc.issuerSigned.toUIJson().toMutableMap().apply {
                    put("docType", JsonPrimitive(document.docType))
                }
            )*/,
            signed = credential,
            docType = document.docType
        )
    }

    private fun parseSdJwt(
        credential: String,
        header: JsonObject,
        payload: JsonObject,
        signature: String,
    ): Pair<CredentialDetectionResult, DigitalCredential> {
        val containedDisclosables = payload.getSdArrays()
        val containedDisclosablesSaveable = containedDisclosables.dropDollarPrefix()

        val containsDisclosures = containedDisclosables.count() >= 1

        fun detectedSdjwtSigned(
            primary: CredentialPrimaryDataType, sub: CredentialDetectorTypes.CredentialSubDataType
        ) = CredentialDetectionResult(
            credentialPrimaryType = primary,
            credentialSubType = sub,
            signaturePrimary = SignaturePrimaryType.SDJWT,
            containsDisclosables = containsDisclosures,
            providesDisclosures = true
        )

        val signedCredentialWithoutDisclosures = credential.substringBefore("~")
        val plainSignature = signedCredentialWithoutDisclosures.substringAfterLast(".")

        var availableDisclosures = parseDisclosureString(signature.substringAfter("~", ""))

        if (availableDisclosures?.isNotEmpty() == true) {
            log.trace { "${"=== MAPPING ==="}" }
            // Map disclosures to disclosable locations
            val mappedDisclosures = ArrayList<SdJwtSelectiveDisclosure>()

            fun findForHash(hash: String) =
                availableDisclosures!!.firstOrNull { it.asHashed() == hash || it.asHashed2() == hash || it.asHashed3() == hash }

            containedDisclosables.entries.forEach { (sdLocation, disclosureHashes) ->
                log.trace { "Trying sd location: $sdLocation\n" }
                val unsuffixedLocation = sdLocation.removeSuffix("_sd")
                disclosureHashes.forEach { hash ->
                    log.trace { "Trying hash: $hash" }
                    log.trace {
                        "Available disclosures: ${availableDisclosures?.joinToString("\n") { "Available h: ${it.asHashed()}  –  ${it.asHashed2()} –  ${it.asHashed3()}" }}"
                    }
                    findForHash(hash)?.let { matchingDisclosure ->
                        mappedDisclosures.add(matchingDisclosure.copy(location = "$unsuffixedLocation${matchingDisclosure.name}"))
                        log.trace { "Found hash for ${matchingDisclosure.name}: ${matchingDisclosure.asHashed()}" }
                    }
                }
            }

            log.trace {
                mappedDisclosures.mapIndexed { idx, it ->
                    "$idx: ${it.location} -> $it"
                }.joinToString("\n")
            }

            check(availableDisclosures.size == mappedDisclosures.size) { "Invalid disclosures: Different size after mapping disclosures (${availableDisclosures.size}) to mappable disclosable (${mappedDisclosures.size}), for credential: $credential" }
            availableDisclosures = mappedDisclosures
        }

        val fullCredentialData =
            if (availableDisclosures?.isNotEmpty() == true || header.getValue("typ").jsonPrimitive.content == "vc+sd-jwt") {
                SDJwt.parse(credential).fullPayload
            } else payload

        return when {
            payload.contains("@context") && payload.contains("vct") -> {
                val issuer = getJwtHeaderOrDataIssuer(payload)
                val subject = getCredentialDataSubject(payload)
                detectedSdjwtSigned(CredentialPrimaryDataType.SDJWTVC, SDJWTVCSubType.sdjwtvcdm) to
                        SdJwtCredential(
                            dmtype = SDJWTVCSubType.sdjwtvcdm,
                            disclosables = containedDisclosablesSaveable,
                            disclosures = availableDisclosures,
                            signature = SdJwtCredentialSignature(plainSignature, header, availableDisclosures),
                            signed = signedCredentialWithoutDisclosures,
                            signedWithDisclosures = credential,
                            credentialData = fullCredentialData,
                            originalCredentialData = payload,

                            issuer = issuer,
                            subject = subject
                        )
            }

            payload.contains("@context") || payload.contains("type") -> {
                val w3cModelVersion = detectW3CDataModelVersion(payload)
                val credential = when (w3cModelVersion) {
                    W3CSubType.W3C_1_1 -> W3C11(
                        disclosables = containedDisclosablesSaveable,
                        disclosures = availableDisclosures,
                        signature = SdJwtCredentialSignature(plainSignature, header, availableDisclosures),
                        signed = signedCredentialWithoutDisclosures,
                        signedWithDisclosures = credential,
                        credentialData = fullCredentialData,
                        originalCredentialData = payload,

                        issuer = getCredentialDataIssuer(payload),
                        subject = getCredentialDataSubject(payload)
                    )

                    W3CSubType.W3C_2 -> W3C2(
                        disclosables = containedDisclosablesSaveable,
                        disclosures = availableDisclosures,
                        signature = SdJwtCredentialSignature(plainSignature, header, availableDisclosures),
                        signed = signedCredentialWithoutDisclosures,
                        signedWithDisclosures = credential,
                        credentialData = fullCredentialData,
                        originalCredentialData = payload,

                        issuer = getCredentialDataIssuer(payload),
                        subject = getCredentialDataSubject(payload)
                    )
                }
                detectedSdjwtSigned(CredentialPrimaryDataType.W3C, w3cModelVersion) to credential
            }

            payload.contains("vct") && !payload.contains("@context")
                -> {
                detectedSdjwtSigned(CredentialPrimaryDataType.SDJWTVC, SDJWTVCSubType.sdjwtvc) to
                        SdJwtCredential(
                            dmtype = SDJWTVCSubType.sdjwtvcdm,
                            disclosables = containedDisclosablesSaveable,
                            disclosures = availableDisclosures,
                            signature = SdJwtCredentialSignature(plainSignature, header, availableDisclosures),
                            signed = signedCredentialWithoutDisclosures,
                            signedWithDisclosures = credential,
                            credentialData = fullCredentialData,
                            originalCredentialData = payload,

                            issuer = getJwtHeaderOrDataIssuer(payload),
                            subject = getCredentialDataSubject(payload)
                        )
            }

            payload.contains("vc") -> {
                parseSdJwt(credential, header, payload["vc"]!!.jsonObject, signature)
            }

            else -> {
                throw NotImplementedError("Unknown SD-JWT-signed credential: $credential")
            }
        }
    }

    suspend fun parseOnly(rawCredential: String) = detectAndParse(rawCredential).second

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun detectAndParse(rawCredential: String): Pair<CredentialDetectionResult, DigitalCredential> {
        val credential = rawCredential.trim()

        return when {
            credential.startsWith("{") -> {
                val parsedJson = Json.decodeFromString<JsonObject>(credential)

                val containedDisclosables = parsedJson.getSdArrays()
                val containedDisclosablesSaveable = containedDisclosables.dropDollarPrefix()
                val containsDisclosures = parsedJson.contains("_sd")


                fun detectedUnsigned(
                    primary: CredentialPrimaryDataType, sub: CredentialDetectorTypes.CredentialSubDataType
                ) = CredentialDetectionResult(
                    credentialPrimaryType = primary,
                    credentialSubType = sub,
                    signaturePrimary = SignaturePrimaryType.UNSIGNED,
                    containsDisclosables = containsDisclosures
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
                                disclosables = containedDisclosablesSaveable,
                                disclosures = null,
                                signature = DataIntegrityProofCredentialSignature(proofElement),
                                signed = credential,
                                signedWithDisclosures = null,
                                credentialData = parsedJson,

                                issuer = getCredentialDataIssuer(parsedJson),
                                subject = getCredentialDataSubject(parsedJson)
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
                        dmtype = SDJWTVCSubType.sdjwtvcdm,
                        disclosables = containedDisclosablesSaveable,
                        disclosures = null,
                        signature = null,
                        signed = null,
                        signedWithDisclosures = null,
                        credentialData = parsedJson,

                        issuer = getCredentialDataIssuer(parsedJson),
                        subject = getCredentialDataSubject(parsedJson)
                    )
                    // TODO: signed version?
                    parsedJson.contains("vct") -> detectedUnsigned(
                        CredentialPrimaryDataType.SDJWTVC, SDJWTVCSubType.sdjwtvc
                    ) to SdJwtCredential(
                        dmtype = SDJWTVCSubType.sdjwtvc,
                        disclosables = containedDisclosablesSaveable,
                        disclosures = null,
                        signature = null,
                        signed = null,
                        signedWithDisclosures = null,
                        credentialData = parsedJson,

                        issuer = getCredentialDataIssuer(parsedJson),
                        subject = getCredentialDataSubject(parsedJson)
                    )

                    parsedJson.contains("@context") && parsedJson.contains("type") -> {
                        val w3cModelVersion = detectW3CDataModelVersion(parsedJson)

                        val credential = when (w3cModelVersion) {
                            W3CSubType.W3C_1_1 -> W3C11(
                                disclosables = containedDisclosablesSaveable,
                                disclosures = null,
                                signature = null,
                                signed = null,
                                signedWithDisclosures = null,
                                credentialData = parsedJson,

                                issuer = getCredentialDataIssuer(parsedJson),
                                subject = getCredentialDataSubject(parsedJson)
                            )

                            W3CSubType.W3C_2 -> W3C2(
                                disclosables = containedDisclosablesSaveable,
                                disclosures = null,
                                signature = null,
                                signed = null,
                                signedWithDisclosures = null,
                                credentialData = parsedJson,

                                issuer = getCredentialDataIssuer(parsedJson),
                                subject = getCredentialDataSubject(parsedJson)
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

                when {
                    // SD-JWT disclosures
                    credential.contains("~") -> parseSdJwt(credential, header, payload, signature)

                    // JOSE signature
                    else -> {
                        val unsignedCredential = payload["vc"]?.toString() ?: payload.toString()
                        val unsignedCredentialDetection = detectAndParse(unsignedCredential)

                        val parsedCred = unsignedCredentialDetection.second

                        val signedCredential = when (parsedCred) {
                            is W3C2 -> parsedCred.copy(signed = credential, signature = JwtCredentialSignature(signature, header))
                            is W3C11 -> parsedCred.copy(signed = credential, signature = JwtCredentialSignature(signature, header))
                            is SdJwtCredential -> parsedCred.copy(
                                signed = credential,
                                signature = JwtCredentialSignature(signature, header)
                            )

                            else -> throw NotImplementedError("unknown credential with JOSE signature: $parsedCred")
                        }

                        unsignedCredentialDetection.copy(
                            first = unsignedCredentialDetection.first.copy(
                                signaturePrimary = SignaturePrimaryType.JWT
                            ),
                            second = signedCredential.apply {
                                issuer = getJwtHeaderOrDataIssuer(payload)
                                subject = getJwtHeaderOrDataSubject(payload)
                            }
                        )
                    }
                }
            }

            // TODO: W3C could also has COSE signature
            credential.matchesHex() -> handleMdocs(credential, base64 = false)
            credential.matchesBase64Url() -> handleMdocs(credential, base64 = true)

            else -> throw NotImplementedError("CredentialParser - Unknown credential, cannot parse: $credential")
        }
    }
}
