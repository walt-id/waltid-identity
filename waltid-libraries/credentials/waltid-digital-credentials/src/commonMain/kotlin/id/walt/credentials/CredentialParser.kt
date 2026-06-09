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
import id.walt.credentials.utils.SdJwtUtils.appendArrayIndex
import id.walt.credentials.utils.SdJwtUtils.appendClaimName
import id.walt.credentials.utils.SdJwtUtils.parseDisclosureString
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.crypto.utils.JsonUtils.toSerializedJsonElement
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

/** Thrown when an SD-JWT contains disclosures that cannot be reached from any payload digest hash (RFC 9901 §7.1 step 5) */
class UnreachableDisclosuresException(message: String) : IllegalArgumentException(message)

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
        // @context may be a JSON array (standard) or a single string (non-conformant but seen in practice)
        val rawContext = data["@context"] ?: error("Missing context from W3C: $data")
        val contextField = when (rawContext) {
            is JsonArray -> rawContext.map { it.jsonPrimitive.content }
            is JsonPrimitive -> listOf(rawContext.content)
            else -> error("Unexpected @context type in W3C credential: $data")
        }

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

    fun getCredentialDataIssuer(data: JsonObject) =
        data["issuer"].getItAsStringOrId() ?: data["vc"]?.jsonObject?.get("issuer")?.getItAsStringOrId()

    fun getJwtHeaderOrDataIssuer(data: JsonObject) = data.getString("iss") ?: getCredentialDataIssuer(data)

    fun getCredentialDataSubject(data: JsonObject) =
        data["credentialSubject"].getItAsStringOrId() ?: data["vc"]?.jsonObject?.get("credentialSubject")?.getItAsStringOrId()

    fun getJwtHeaderOrDataSubject(data: JsonObject) = data.getString("sub") ?: getCredentialDataSubject(data)

    /**
     * Converts an SD-JWT (VC) encoded using the JWS JSON Serialization (RFC 9901 §8) (either the
     * Flattened or the General form) into the SD-JWT Compact Serialization, or returns `null` if
     * [credential] is not a JWS JSON serialized SD-JWT.
     *
     * Per RFC 9901 §8.1, the compact form is built by concatenating the `protected` header, the
     * `payload`, and the `signature` of the JWS with `.`, followed by each Disclosure from the
     * `disclosures` member of the unprotected header (each prefixed with `~`), and finally the
     * `kb_jwt` (if present) prefixed with `~`.
     *
     * Supporting this format is OPTIONAL per the spec, but issuers MAY use it (SD-JWT VC §2.2), so a
     * conformant verifier should accept it.
     *
     * Flattened: `{ "payload", "protected", "header": { "disclosures": [...], "kb_jwt"? }, "signature" }`
     * General:   `{ "payload", "signatures": [ { "protected", "header"?, "signature" } ] }`
     */
    private fun jwsJsonToCompactSdJwtOrNull(credential: String): String? {
        if (!credential.startsWith("{")) return null

        val json = runCatching { Json.decodeFromString<JsonObject>(credential) }.getOrNull() ?: return null
        val payload = (json["payload"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null

        // Resolve the signature object: Flattened has protected/signature at top level; General nests
        // them in signatures[0]. The disclosures (and optional kb_jwt) live in the unprotected header.
        val (protectedHeader, signature, unprotectedHeader) = when {
            json.containsKey("signatures") -> {
                val sig = (json["signatures"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
                Triple(
                    (sig["protected"] as? JsonPrimitive)?.content,
                    (sig["signature"] as? JsonPrimitive)?.content,
                    sig["header"] as? JsonObject
                )
            }

            json.containsKey("protected") || json.containsKey("signature") -> Triple(
                (json["protected"] as? JsonPrimitive)?.content,
                (json["signature"] as? JsonPrimitive)?.content,
                json["header"] as? JsonObject
            )

            else -> return null
        }
        if (protectedHeader == null || signature == null) return null

        val disclosures = (unprotectedHeader?.get("disclosures") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            ?: emptyList()
        val kbJwt = (unprotectedHeader?.get("kb_jwt") as? JsonPrimitive)?.content

        return buildString {
            append(protectedHeader).append('.').append(payload).append('.').append(signature)
            disclosures.forEach { append('~').append(it) }
            // RFC 9901 §4: a presentation without a KB-JWT ends with a trailing '~'.
            if (kbJwt != null) append('~').append(kbJwt) else if (disclosures.isNotEmpty()) append('~')
        }
    }

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
        }.recoverCatching {
            // OID4VCI §mso_mdoc: the credential response contains a bare IssuerSigned structure
            // (not a full Document). Reconstruct the Document using the docType from the MSO
            // embedded inside the issuerAuth COSE structure.
            log.trace { "Mdoc could not be parsed as document, trying as IssuerSigned (OID4VCI credential response format)" }
            val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(deviceResponseBytes)
            val docType = issuerSigned.decodeMobileSecurityObject().docType
            log.trace { "Mdoc parsed (from IssuerSigned), docType=$docType" }
            Document(docType = docType, issuerSigned = issuerSigned)
        }.getOrThrow()


        // Build a JSON object that includes the docType for the DcqlMatcher
        val credentialData = buildJsonObject {
            put("docType", JsonPrimitive(document.docType))

            document.issuerSigned.namespaces?.forEach { (namespace, issuerSignedList) ->
                putJsonObject(namespace) {
                    val issuerSignedItems = issuerSignedList.entries.map { it.value }
                    issuerSignedItems.forEach { item: IssuerSignedItem ->
                        log.trace { "$namespace - ${item.elementIdentifier} -> ${item.elementValue} (${item.elementValue::class.simpleName})" }

                        val serialized = runCatching {
                            item.elementValue.toSerializedJsonElement()
                        }.getOrElse {
                            throw IllegalArgumentException(
                                "Could not serialize element ${item.digestId} in $namespace: ${item.elementIdentifier} (value ${item.elementValue}), due to: ${it.message}",
                                it
                            )
                        }

                        /* Previous serialization:
                        val serialized: JsonElement = MdocsCborSerializer.lookupSerializer(namespace, item.elementIdentifier)
                            ?.runCatching {
                                Json.encodeToJsonElement(this as KSerializer<Any?>, item.elementValue)
                            }?.getOrElse { log.warn { "Error encoding with custom serializer: ${it.stackTraceToString()}" }; null }
                            ?: item.elementValue.toJsonElement()
                        */

                        log.trace { "-> as JsonElement: $serialized" }
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

        // Disclosures follow the first "~" in the full credential string.
        // `signature` is the bare JWT signature bytes (no tilde); disclosures must be
        // extracted from `credential` itself per RFC 9901 §4 compact serialisation format.
        val disclosuresPart = credential.substringAfter("~", "")
        log.trace { "Parsing disclosures: $disclosuresPart" }
        var availableDisclosures = parseDisclosureString(disclosuresPart)

        if (availableDisclosures?.isNotEmpty() == true) {
            log.trace { "=== MAPPING ===" }
            // Use a Mutable Set to track what we have successfully mapped
            val mappedDisclosures = HashSet<SdJwtSelectiveDisclosure>()

            // A queue of hashes we expect to find, paired with the Claim Path of their containing
            // object (per SD-JWT VC §4.6.1; relative to the credential root).
            val hashesToVerify = ArrayDeque<Pair<List<JsonElement>, String>>()

            fun findForHash(hash: String) =
                /* asHashedFromEncoded() hashes the exact original base64url wire bytes (RFC 9901 §4.2)
                 - the most reliable match as it is independent of any JSON re-serialization.
                 asHashed() is SHA-256(base64url(json_array)) recomputed from salt/name/value.
                 asHashed2() is an alternative base64 encoding fallback for some non-standard issuers.
                 asHashed3() (double-base64url) is a non-standard malformed disclosure */
                availableDisclosures!!.firstOrNull {
                    it.asHashedFromEncoded() == hash || it.asHashed() == hash || it.asHashed2() == hash || it.asHashed3() == hash
                }

            // Helper to recursively scan a JSON element for more SD-JWT hashes.
            // currentPath is the Claim Path (§4.6.1) of [element] relative to the credential root.
            fun scanForHashes(element: JsonElement, currentPath: List<JsonElement>) {
                when (element) {
                    is JsonObject -> {
                        // Check for _sd array in this object
                        element["_sd"]?.jsonArray?.forEach {
                            if (it is JsonPrimitive && it.isString) {
                                hashesToVerify.add(currentPath to it.content)
                            }
                        }
                        // Recurse into properties
                        element.forEach { (key, value) ->
                            if (key != "_sd") scanForHashes(value, currentPath.appendClaimName(key))
                        }
                    }

                    is JsonArray -> {
                        element.forEachIndexed { index, item ->
                            // Check for Array Disclosure format: { "...": "hash" }
                            if (item is JsonObject && item.size == 1 && item.containsKey("...")) {
                                val hash = item["..."]?.jsonPrimitive?.content
                                if (hash != null) {
                                    hashesToVerify.add(currentPath.appendArrayIndex(index) to hash)
                                }
                            } else {
                                // Recurse
                                scanForHashes(item, currentPath.appendArrayIndex(index))
                            }
                        }
                    }

                    else -> {} // Primitives contain no hashes
                }
            }

            // 1. Initial population from the credential-root payload, using the
            // identification logic in scanForHashes (RFC 9901 §7.1 step 3.b). For W3C JWT-VCs that
            // embed the credential under a `vc` claim, scan that wrapper's content so Claim Paths
            // stay relative to the credential root (mirrors parseSdLocationToClaimPath's vc handling).
            val credentialRoot = (payload["vc"] as? JsonObject) ?: payload
            scanForHashes(credentialRoot, emptyList())

            // 2. Process the queue until all nested hashes are resolved
            while (hashesToVerify.isNotEmpty()) {
                val (basePath, hash) = hashesToVerify.removeFirst()
                log.trace { "Processing hash: $hash at $basePath" }

                val matchingDisclosure = findForHash(hash)
                if (matchingDisclosure != null) {
                    // Construct the Claim Path for the *content* of this disclosure.
                    // Object-property disclosures append their claim name; array-element
                    // disclosures ([salt, value], name == null) keep the containing path.
                    val newPath = if (matchingDisclosure.name != null)
                        basePath.appendClaimName(matchingDisclosure.name)
                    else basePath

                    // Create a copy of the disclosure with the populated location
                    val updatedDisclosure = matchingDisclosure.copy(location = newPath)

                    // Only process if we haven't mapped this specific disclosure instance yet
                    // (SD-JWT spec says digest MUST NOT appear more than once, but safety check)
                    // Add the UPDATED disclosure to the set, not the raw one
                    if (mappedDisclosures.add(updatedDisclosure)) {
                        log.trace { "Found hash for ${updatedDisclosure.name ?: "array_element"}" }

                        // 3. Recursive Step: Scan the *value* of the disclosure for new hashes
                        scanForHashes(updatedDisclosure.value, newPath)
                    }
                } else {
                    log.trace { "Hash $hash not found in available disclosures (might be a decoy)" }
                }
            }

            log.trace {
                mappedDisclosures.mapIndexed { idx, it -> "$idx: ${it.name} -> $it" }.joinToString("\n")
            }

            // Per RFC 9901 §7.1 step 5: "If any Disclosure was not referenced by digest value
            // in the Issuer-signed JWT (directly or recursively via other Disclosures), the
            // SD-JWT MUST be rejected." Throw a distinct exception so callers can return INVALID.
            if (availableDisclosures.size != mappedDisclosures.size) {
                throw UnreachableDisclosuresException(
                    "Invalid disclosures: ${availableDisclosures.size} disclosures provided but " +
                    "only ${mappedDisclosures.size} are reachable from the payload hashes. " +
                    "Per RFC 9901 §7.1 step 5 this SD-JWT MUST be rejected."
                )
            }
            availableDisclosures = mappedDisclosures.toList()
        }

        val fullCredentialData =
            if (availableDisclosures?.isNotEmpty() == true ||
                header.getValue("typ").jsonPrimitive.content.let { it == "vc+sd-jwt" || it == "dc+sd-jwt" }
            ) {
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

            payload.contains("@context") || payload.contains("type") || (payload.containsKey("vc") && (payload["vc"]?.jsonObject?.contains("@context") == true || payload["vc"]?.jsonObject?.contains(
                "type"
            ) == true)) -> {
                val w3cPayload = if (payload.containsKey("vc")) payload["vc"]!!.jsonObject else payload
                val w3cModelVersion = detectW3CDataModelVersion(w3cPayload)
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
                val subPayload = payload["vc"]!!.jsonObject
                if (subPayload.contains("_sd")) {
                    parseSdJwt(credential, header, subPayload, signature)
                } else {
                    // W3C credential wrapped in vc claim with SD-JWT envelope but no selective disclosures.
                    // @context may be absent from vc top-level (e.g. nested inside credentialSubject),
                    // so fall back to validFrom (DM2) / issuanceDate (DM1.1) for version detection.
                    val w3cModelVersion = when {
                        subPayload.contains("@context") -> detectW3CDataModelVersion(subPayload)
                        subPayload.contains("validFrom") -> W3CSubType.W3C_2
                        subPayload.contains("issuanceDate") -> W3CSubType.W3C_1_1
                        else -> null
                    }
                    if (w3cModelVersion != null) {
                        val w3cCredential = when (w3cModelVersion) {
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
                        detectedSdjwtSigned(CredentialPrimaryDataType.W3C, w3cModelVersion) to w3cCredential
                    } else {
                        throw NotImplementedError("Unknown SD-JWT-signed credential: $credential")
                    }
                }
            }

            else -> {
                throw NotImplementedError("Unknown SD-JWT-signed credential: $credential")
            }
        }
    }

    suspend fun parseOnly(rawCredential: String) = detectAndParse(rawCredential).second

    suspend fun detectAndParse(rawCredential: String): Pair<CredentialDetectionResult, DigitalCredential> {
        val credential = rawCredential.trim()

        // SD-JWT VC may be encoded using the JWS JSON Serialization (RFC 9901 §8), which is a valid
        // but OPTIONAL format (SD-JWT VC draft §2.2). Detect it and convert to the compact form
        // (header.payload.signature~disclosure...~kb_jwt) so the rest of the pipeline can parse it.
        jwsJsonToCompactSdJwtOrNull(credential)?.let { compact ->
            return detectAndParse(compact)
        }

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
                            "DataIntegrityProof" -> {
                                val dipVersion = detectW3CDataModelVersion(parsedJson)
                                val signature = DataIntegrityProofCredentialSignature(proofElement)
                                CredentialDetectionResult(
                                    CredentialPrimaryDataType.W3C, dipVersion,
                                    SignaturePrimaryType.DATA_INTEGRITY_PROOF
                                ) to when (dipVersion) {
                                    W3CSubType.W3C_2 -> W3C2(
                                        disclosables = containedDisclosablesSaveable,
                                        disclosures = null,
                                        signature = signature,
                                        signed = credential,
                                        signedWithDisclosures = null,
                                        credentialData = parsedJson,
                                        issuer = getCredentialDataIssuer(parsedJson),
                                        subject = getCredentialDataSubject(parsedJson)
                                    )

                                    W3CSubType.W3C_1_1 -> W3C11(
                                        disclosables = containedDisclosablesSaveable,
                                        disclosures = null,
                                        signature = signature,
                                        signed = credential,
                                        signedWithDisclosures = null,
                                        credentialData = parsedJson,
                                        issuer = getCredentialDataIssuer(parsedJson),
                                        subject = getCredentialDataSubject(parsedJson)
                                    )
                                }
                            }

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

                    (parsedJson.contains("@context") && parsedJson.contains("type")) ||
                            (parsedJson.contains("credentialSubject") &&
                                    parsedJson["credentialSubject"]?.jsonObject?.contains("@context") == true &&
                                    parsedJson["credentialSubject"]?.jsonObject?.contains("type") == true) -> {
                        val vcJson = if (parsedJson.contains("@context") && parsedJson.contains("type")) parsedJson
                        else parsedJson["credentialSubject"]!!.jsonObject
                        val w3cModelVersion = detectW3CDataModelVersion(vcJson)

                        val credential = when (w3cModelVersion) {
                            W3CSubType.W3C_1_1 -> W3C11(
                                disclosables = containedDisclosablesSaveable,
                                disclosures = null,
                                signature = null,
                                signed = null,
                                signedWithDisclosures = null,
                                credentialData = vcJson,

                                issuer = getCredentialDataIssuer(vcJson),
                                subject = getCredentialDataSubject(vcJson)
                            )

                            W3CSubType.W3C_2 -> W3C2(
                                disclosables = containedDisclosablesSaveable,
                                disclosures = null,
                                signature = null,
                                signed = null,
                                signedWithDisclosures = null,
                                credentialData = vcJson,

                                issuer = getCredentialDataIssuer(vcJson),
                                subject = getCredentialDataSubject(vcJson)
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
                // Per RFC 9901 §4 ABNF: SD-JWT-KB = SD-JWT KB-JWT where SD-JWT = JWT "~" *(DISCLOSURE "~").
                // The issuer-signed JWT is always the part before the first "~". We must parse only that
                // part as a JWT — the full string may contain extra dots from a KB-JWT appended after "~".
                val issuerJwt = credential.substringBefore("~")
                val (header, payload, signature) = JwtUtils.parseJwt(issuerJwt)

                when {
                    // SD-JWT disclosures
                    credential.contains("~") -> parseSdJwt(credential, header, payload, signature)

                    // JOSE signature
                    else -> {
                        val unsignedCredential = payload["vc"]?.toString() ?: payload.toString()
                        val unsignedCredentialDetection = detectAndParse(unsignedCredential)

                        val signedCredential = when (val parsedCred = unsignedCredentialDetection.second) {
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
