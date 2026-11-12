package id.walt.credentials.status

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.Crypto2VerificationAlgorithms
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.bit.BitValueReaderFactory
import id.walt.policies2.vc.policies.status.content.CwtParser
import id.walt.policies2.vc.policies.status.content.JwtParser
import id.walt.policies2.vc.policies.status.entry.IETFEntryExtractor
import id.walt.policies2.vc.policies.status.entry.MDocEntryExtractor
import id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithmFactory
import id.walt.policies2.vc.policies.status.model.*
import id.walt.policies2.vc.policies.status.reader.*
import id.walt.policies2.vc.policies.status.reader.format.CwtFormatMatcher
import id.walt.policies2.vc.policies.status.reader.format.JwtFormatMatcher
import id.walt.policies2.vc.policies.status.signature.*
import id.walt.policies2.vc.policies.status.validator.IETFStatusValidator
import id.walt.policies2.vc.policies.status.validator.W3CStatusValidator
import id.walt.statuslist.codec.*
import id.walt.statuslist.compression.StatusListSizeLimitException
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Extra issuer authorization can be supplied without changing the status protocol implementation. */
fun interface CredentialStatusReferenceAuthorizer {
    suspend fun authorize(credential: DigitalCredential): Boolean
}

/** Metadata plus the storage restriction of the response that produced it. */
data class CredentialStatusResolution(
    val metadata: CredentialStatusMetadata,
    val nonPersistentReferenceIds: Set<String> = emptySet(),
)

/**
 * Authenticated status evaluation shared with the verifier policy. Creation and [discover] are local;
 * only [resolve] performs network I/O. Use a separate instance per wallet/trust configuration.
 */
class CredentialStatusResolver(
    client: HttpClient,
    scope: CoroutineScope,
    private val now: () -> Instant,
    private val limits: CredentialStatusLimits = CredentialStatusLimits(),
    private val signerAuthorizer: StatusListSignerAuthorizer? = null,
    private val referenceAuthorizer: CredentialStatusReferenceAuthorizer? = null,
) : AutoCloseable {
    private data class Reference(
        val id: String,
        val protocol: StatusProtocol,
        val purpose: String,
        val entry: StatusEntry,
    )
    private data class Discovery(val metadata: CredentialStatusMetadata, val references: List<Reference>)

    private val json = Json { ignoreUnknownKeys = true }
    private val transport = StatusListTransport(client, scope, now, limits)
    private val resolutionPermits = Semaphore(limits.concurrency)
    private val decodedMutex = Mutex()
    private val decoded = linkedMapOf<String, ByteArray>()
    private val legacyFetcher = CredentialFetcher(client, now)
    private val signatureVerifier = StatusListSignatureVerifier()
    private val jwtReader = IETFJwtStatusValueReader(JwtFormatMatcher(), JwtParser())
    private val cwtReader = IETFCwtStatusValueReader(CwtFormatMatcher(), CwtParser())
    private val ietfValidator = IETFStatusValidator(
        legacyFetcher, BitValueReaderFactory(), boundedAlgorithm(TokenStatusListCodec), jwtReader, cwtReader,
        signatureVerifier = signatureVerifier,
    )

    private fun createW3cValidator(cacheAllowed: Boolean, entry: W3CEntry) = W3CStatusValidator(
        legacyFetcher, w3cReader(entry), BitValueReaderFactory(),
        object : StatusListExpansionAlgorithmFactory<W3CStatusContent> {
            override fun create(content: W3CStatusContent): StatusListExpansionAlgorithm = boundedAlgorithm(
                when (content.type) {
                    "BitstringStatusList" -> BitstringStatusListCodec
                    "StatusList2021" -> StatusList2021Codec
                    else -> fail(StatusFailure.UNSUPPORTED_METHOD)
                },
                minimumBits = 131_072 * entry.size, cacheAllowed = cacheAllowed,
            )
        },
        signatureVerifier,
    )

    // Preserve the released single-purpose helper model while projecting an authenticated list's
    // one-or-more purposes onto this exact reference. Debug status messages never become UI labels.
    private fun w3cReader(entry: W3CEntry) = object : id.walt.policies2.vc.policies.status.reader.StatusValueReader<W3CStatusContent> {
        private val base = W3CStatusValueReader(JwtFormatMatcher(), JwtParser())
        override fun canHandle(content: StatusListContent): Boolean = base.canHandle(content)
        override fun read(content: StatusListContent): Result<W3CStatusContent> = try {
            val payload = JwtParser().parse((content as? StatusListContent.Text)?.content ?: fail(StatusFailure.UNSUPPORTED_PROOF))
            val vc = payload["vc"] as? JsonObject ?: payload
            val subject = vc["credentialSubject"] as? JsonObject ?: fail(StatusFailure.INVALID_LIST)
            val type = if (entry.type == "BitstringStatusListEntry") "BitstringStatusList" else "StatusList2021"
            if (type !in stringValues(subject["type"]) || entry.purpose !in stringValues(subject["statusPurpose"])) fail(StatusFailure.INVALID_LIST)
            subject["statusSize"]?.let {
                val size = it as? JsonPrimitive ?: fail(StatusFailure.INVALID_LIST)
                if (size.isString || size.intOrNull != entry.size) fail(StatusFailure.INVALID_LIST)
            }
            val encoded = subject["encodedList"] as? JsonPrimitive ?: fail(StatusFailure.INVALID_LIST)
            if (!encoded.isString || encoded.content.isBlank()) fail(StatusFailure.INVALID_LIST)
            Result.success(W3CStatusContent(type, entry.purpose, encoded.content))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (cause: Throwable) { Result.failure(cause) }
    }

    private fun stringValues(value: JsonElement?): List<String> {
        val values = if (value is JsonArray) value else listOfNotNull(value)
        if (values.isEmpty()) fail(StatusFailure.INVALID_LIST)
        return values.map {
            val string = it as? JsonPrimitive ?: fail(StatusFailure.INVALID_LIST)
            if (!string.isString || string.content.isBlank()) fail(StatusFailure.INVALID_LIST)
            string.content
        }
    }

    /** No signature/key resolution, issuer request or wallet mutation. */
    fun discover(credential: DigitalCredential): CredentialStatusMetadata = discovery(credential).metadata

    suspend fun resolve(credential: DigitalCredential, force: Boolean = false, session: CredentialStatusRefreshSession? = null): CredentialStatusResolution {
        session?.enter(transport)
        return try {
            withTimeoutOrNull(limits.resolutionTimeout) {
                resolutionPermits.withPermit { resolveWithinBudget(credential, force, session) }
            } ?: CredentialStatusResolution(failed(discover(credential), StatusFailure.TIMEOUT))
        } finally { withContext(NonCancellable) { session?.leave() } }
    }

    private suspend fun resolveWithinBudget(credential: DigitalCredential, force: Boolean, session: CredentialStatusRefreshSession?): CredentialStatusResolution {
        val initial = discovery(credential)
        if (initial.references.isEmpty()) return CredentialStatusResolution(initial.metadata)
        val authenticated = try {
            val raw = credential.signed ?: fail(StatusFailure.UNSUPPORTED_PROOF)
            // Rebuild from signed bytes rather than trusting mutable issuer/display fields in a stored projection.
            val canonical = CredentialParser.detectAndParse(raw).second
            authenticate(canonical)
            canonical
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            return CredentialStatusResolution(failed(initial.metadata, failure(cause, StatusFailure.REFERENCE_UNAUTHENTICATED)))
        }
        val discovery = discovery(authenticated)
        val results = discovery.metadata.entries.associateByTo(linkedMapOf(), CredentialStatusEntry::referenceId)
        val notPersistent = mutableSetOf<String>()
        for (reference in discovery.references) {
            var resource: StatusListResource? = null
            results[reference.id] = try {
                resource = transport.fetch(reference.entry.uri, force, session)
                if (!resource.storageAllowed) notPersistent += reference.id
                val observation = evaluate(authenticated, reference, resource)
                transport.constrain(reference.entry.uri, resource.bodyId, observation.freshUntil)
                CredentialStatusEntry.Observed(observation)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                val reason = failure(cause, if (resource == null) StatusFailure.TRANSPORT else StatusFailure.INVALID_LIST)
                if (reason != StatusFailure.TRANSPORT && reason != StatusFailure.TIMEOUT && reason != StatusFailure.RATE_LIMITED) {
                    resource?.let { transport.invalidate(reference.entry.uri, it.bodyId) }
                }
                CredentialStatusEntry.Unavailable(reference.id, reason, now())
            }
        }
        return CredentialStatusResolution(CredentialStatusMetadata(results.values.toList()), notPersistent)
    }

    /** A bounded batch revalidates each unique list once, even with an immediate response. */
    fun newRefreshSession(): CredentialStatusRefreshSession = transport.refreshSession()

    suspend fun clear() {
        transport.clear()
        decodedMutex.withLock { decoded.clear() }
    }

    /** Closes this resolver's derived HTTP client; the caller's client remains independently owned. */
    override fun close() { transport.close() }

    private fun discovery(credential: DigitalCredential): Discovery {
        val references = mutableListOf<Reference>()
        val entries = mutableListOf<CredentialStatusEntry>()
        fun unsupported(method: String) {
            entries += CredentialStatusEntry.Unsupported(referenceId(credential, method), method.take(64))
        }
        try {
            val status = credential.credentialData["status"]
            if (credential is MdocsCredential) {
                val msoStatus = credential.documentMso.status
                if (msoStatus != null) {
                    if (msoStatus.statusList != null) {
                        val entry = json.decodeFromJsonElement<IETFEntry>(MDocEntryExtractor().extract(credential)!!)
                        references += reference(credential, entry, StatusProtocol.ISO_MSO_STATUS_LIST, "revocation")
                    }
                    if (msoStatus.identifierList != null) unsupported("identifier_list")
                    if (msoStatus.type != null || msoStatus.purpose != null || msoStatus.index != null || msoStatus.uri != null) {
                        unsupported("ETSI mdoc status")
                    }
                    if (msoStatus.statusList == null && msoStatus.identifierList == null) unsupported("mdoc status method")
                }
            } else if (status != null) {
                val objectValue = status as? JsonObject ?: fail(StatusFailure.REFERENCE_MALFORMED)
                if ("status_list" in objectValue) {
                    val entry = json.decodeFromJsonElement<IETFEntry>(IETFEntryExtractor(MDocEntryExtractor()).extract(credential)!!)
                    references += reference(credential, entry, StatusProtocol.TOKEN_STATUS_LIST, "token")
                }
                objectValue.keys.filter { it != "status_list" }.forEach(::unsupported)
                if (objectValue.isEmpty()) fail(StatusFailure.REFERENCE_MALFORMED)
            }
            credential.credentialData["credentialStatus"]?.let { raw ->
                val list = if (raw is JsonArray) raw else listOf(raw)
                if (list.isEmpty()) fail(StatusFailure.REFERENCE_MALFORMED)
                if (list.size > limits.maxReferencesPerCredential) fail(StatusFailure.RESOURCE_LIMIT)
                for (element in list) {
                    val value = element as? JsonObject ?: fail(StatusFailure.REFERENCE_MALFORMED)
                    val type = value["type"]?.jsonPrimitive?.contentOrNull
                    val protocol = when (type) {
                        "BitstringStatusListEntry" -> StatusProtocol.BITSTRING_STATUS_LIST
                        "StatusList2021Entry" -> StatusProtocol.STATUS_LIST_2021
                        else -> { unsupported(type ?: "unknown credential status method"); continue }
                    }
                    val index = value["statusListIndex"] as? JsonPrimitive ?: fail(StatusFailure.REFERENCE_MALFORMED)
                    if (!index.isString || index.content.isEmpty() || index.content.any { it !in '0'..'9' }) fail(StatusFailure.REFERENCE_MALFORMED)
                    if (index.content.toULongOrNull() == null) fail(StatusFailure.RESOURCE_LIMIT)
                    value["statusSize"]?.let {
                        val size = it as? JsonPrimitive ?: fail(StatusFailure.REFERENCE_MALFORMED)
                        if (size.isString || size.intOrNull == null || size.int < 1) fail(StatusFailure.REFERENCE_MALFORMED)
                        if (size.int > 8) fail(StatusFailure.RESOURCE_LIMIT)
                    }
                    val normalized = if (value["statusReference"] is JsonArray) JsonObject(value - "statusReference") else value
                    val entry = json.decodeFromJsonElement<W3CEntry>(normalized)
                    require(entry.size in 1..8)
                    if (entry.size > 1 || entry.statuses != null) require(entry.statuses?.size == 1.shl(entry.size))
                    references += reference(credential, entry, protocol, entry.purpose)
                }
            }
            if (entries.size + references.size > limits.maxReferencesPerCredential) fail(StatusFailure.RESOURCE_LIMIT)
            entries += references.distinctBy(Reference::id).map {
                CredentialStatusEntry.NotChecked(it.id, it.protocol, it.purpose)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            references.clear()
            entries.clear()
            entries += CredentialStatusEntry.Unavailable(
                referenceId(credential, "malformed"), failure(cause, StatusFailure.REFERENCE_MALFORMED), now(),
            )
        }
        return Discovery(CredentialStatusMetadata(entries.distinctBy(CredentialStatusEntry::referenceId)), references.distinctBy(Reference::id))
    }

    private fun reference(credential: DigitalCredential, entry: StatusEntry, protocol: StatusProtocol, purpose: String): Reference {
        require(entry.uri.isNotBlank() && purpose.isNotBlank())
        if (entry.index > Int.MAX_VALUE.toULong()) fail(StatusFailure.RESOURCE_LIMIT)
        return Reference(referenceId(credential, "$protocol|$purpose|${entry.uri}|${entry.index}"), protocol, purpose, entry)
    }

    private fun referenceId(credential: DigitalCredential, reference: String): String =
        fingerprint("${credential.format}|${credential.signed}|$reference".encodeToByteArray())

    private suspend fun authenticate(credential: DigitalCredential) {
        val key = credential.getSignerCrypto2Key() ?: fail(StatusFailure.REFERENCE_UNAUTHENTICATED)
        credential.verifyCrypto2(key, Crypto2VerificationAlgorithms(
            jws = JwsAlgorithm.entries.toSet(), cose = setOf(-7, -35, -36, -8, -37, -38, -39, -257, -258, -259),
        )).getOrThrow()
        if (referenceAuthorizer?.authorize(credential) == false) fail(StatusFailure.REFERENCE_UNAUTHENTICATED)
        val at = now()
        certificateDeadline(referencedCredentialCertificateChain(credential), at)
        if (credential is MdocsCredential) {
            val validity = credential.documentMso.validityInfo
            if (at < validity.validFrom || at >= validity.validUntil) fail(StatusFailure.CREDENTIAL_EXPIRED)
        } else {
            val jwt = credential.signed?.substringBefore('~')?.let { JwtParser().parse(it) }
                ?: fail(StatusFailure.UNSUPPORTED_PROOF)
            val data = jwt["vc"] as? JsonObject ?: jwt
            val expiry = listOfNotNull(jwt.instantClaim("exp"), data.dateClaim("validUntil"), data.dateClaim("expirationDate")).minOrNull()
            val beginning = listOfNotNull(jwt.instantClaim("nbf"), data.dateClaim("validFrom"), data.dateClaim("issuanceDate")).maxOrNull()
            if (expiry != null && at >= expiry || beginning != null && at < beginning) fail(StatusFailure.CREDENTIAL_EXPIRED)
        }
    }

    private suspend fun evaluate(credential: DigitalCredential, reference: Reference, resource: StatusListResource): StatusObservation {
        val at = now()
        val content = resource.content
        val evaluated = when (val entry = reference.entry) {
            is IETFEntry -> {
                if (credential is SdJwtCredential && content !is StatusListContent.Text) fail(StatusFailure.UNSUPPORTED_PROOF)
                if (reference.protocol == StatusProtocol.ISO_MSO_STATUS_LIST) requireMsoProfile(content)
                val statusList = when (content) {
                    is StatusListContent.Text -> jwtReader.read(content).getOrThrow()
                    is StatusListContent.Binary -> cwtReader.read(content).getOrThrow()
                }
                if (statusList.size !in setOf(1, 2, 4, 8)) fail(StatusFailure.INVALID_LIST)
                if (reference.protocol == StatusProtocol.ISO_MSO_STATUS_LIST && statusList.size != 1) fail(StatusFailure.INVALID_LIST)
                val validator = if (resource.storageAllowed) ietfValidator else IETFStatusValidator(
                    legacyFetcher, BitValueReaderFactory(), boundedAlgorithm(TokenStatusListCodec, cacheAllowed = false),
                    jwtReader, cwtReader, signatureVerifier = signatureVerifier,
                )
                validator.evaluate(entry, IETFStatusPolicyAttribute(value = 0u), credential,
                    signerAuthorizer, content, at.epochSeconds, reference.protocol).getOrThrow()
            }
            is W3CEntry -> createW3cValidator(resource.storageAllowed, entry).evaluate(entry, W3CStatusPolicyAttribute(value = 0u,
                purpose = entry.purpose, type = if (reference.protocol == StatusProtocol.BITSTRING_STATUS_LIST) "BitstringStatusList" else "StatusList2021"),
                credential, signerAuthorizer, content, at.epochSeconds).getOrThrow()
        }
        val (listValidUntil, ttl) = lifetime(content, reference)
        val credentialExpiry = if (credential is MdocsCredential) credential.documentMso.validityInfo.validUntil else {
            val jwt = JwtParser().parse(requireNotNull(credential.signed).substringBefore('~'))
            val data = jwt["vc"] as? JsonObject ?: jwt
            listOfNotNull(jwt.instantClaim("exp"), data.dateClaim("validUntil"), data.dateClaim("expirationDate")).minOrNull()
        }
        val credentialCertificateDeadline = certificateDeadline(referencedCredentialCertificateChain(credential), at)
        val explicitStatusAnchorDeadline = (credential as? MdocsCredential)?.msoStatusTrustPoint()?.let {
            certificateDeadline(listOf(kotlin.io.encoding.Base64.encode(it)), at)
        }
        val validUntil = listOfNotNull(listValidUntil, credentialExpiry, evaluated.authorizationValidUntil,
            credentialCertificateDeadline, explicitStatusAnchorDeadline).minOrNull()
        val freshUntil = listOfNotNull(resource.freshUntil, validUntil,
            ttl?.let { resource.retrievedAt + it }, resource.retrievedAt + limits.maximumFreshness).min()
        return StatusObservation(reference.id, reference.protocol, reference.purpose, evaluated.value,
            resource.retrievedAt, freshUntil, validUntil)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun requireMsoProfile(content: StatusListContent) {
        if (content !is StatusListContent.Binary) fail(StatusFailure.UNSUPPORTED_PROOF)
        val sign1 = CoseSign1.fromTagged(content.content)
        val headers = coseCompliantCbor.decodeFromByteArray<CoseHeaders>(sign1.protected)
        if (headers.x5chain.isNullOrEmpty()) fail(StatusFailure.INVALID_SIGNATURE)
        val claims = coseCompliantCbor.decodeFromByteArray<CwtLifetime>(requireNotNull(sign1.payload))
        if (claims.exp == null) fail(StatusFailure.INVALID_VALIDITY)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun lifetime(content: StatusListContent, reference: Reference): Pair<Instant?, kotlin.time.Duration?> = when (content) {
        is StatusListContent.Binary -> {
            val payload = requireNotNull(CoseSign1.fromTagged(content.content).payload)
            val claims = coseCompliantCbor.decodeFromByteArray<CwtLifetime>(payload)
            claims.exp?.let(Instant::fromEpochSeconds) to claims.ttl?.also { if (it < 0) fail(StatusFailure.INVALID_LIST) }?.seconds
        }
        is StatusListContent.Text -> {
            val jwt = JwtParser().parse(content.content)
            if (reference.protocol == StatusProtocol.TOKEN_STATUS_LIST) {
                val expiry = jwt.instantClaim("exp")
                val beginning = jwt.instantClaim("nbf")
                if (expiry != null && now() >= expiry || beginning != null && now() < beginning) fail(StatusFailure.INVALID_VALIDITY)
                expiry to jwt.nonnegativeLongClaim("ttl")?.seconds
            } else {
                val vc = jwt["vc"] as? JsonObject ?: jwt
                val from = listOfNotNull(jwt.instantClaim("nbf"), vc.dateClaim("validFrom"), vc.dateClaim("issuanceDate")).maxOrNull()
                val expiry = listOfNotNull(jwt.instantClaim("exp"), vc.dateClaim("validUntil"), vc.dateClaim("expirationDate")).minOrNull()
                if (from != null && now() < from || expiry != null && now() >= expiry) fail(StatusFailure.INVALID_VALIDITY)
                if (vc["id"] != null && vc["id"]?.jsonPrimitive?.contentOrNull != reference.entry.uri) fail(StatusFailure.INVALID_LIST)
                val types = vc["type"]?.let { if (it is JsonArray) it else listOf(it) }.orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                val expected = if (reference.protocol == StatusProtocol.BITSTRING_STATUS_LIST) "BitstringStatusListCredential" else "StatusList2021Credential"
                if (expected !in types) fail(StatusFailure.INVALID_LIST)
                val subject = vc["credentialSubject"] as? JsonObject ?: fail(StatusFailure.INVALID_LIST)
                expiry to subject.nonnegativeLongClaim("ttl")?.milliseconds
            }
        }
    }

    private fun boundedAlgorithm(codec: StatusListCodec, minimumBits: Int = 0, cacheAllowed: Boolean = true) = object : StatusListExpansionAlgorithm {
        override suspend fun invoke(bitstring: String): ByteArray {
            val id = fingerprint("${codec.format}|$bitstring".encodeToByteArray())
            return decodedMutex.withLock {
                val cached = if (cacheAllowed) decoded[id] else null
                val bytes = cached ?: codec.decodeBounded(bitstring, limits.maxExpandedBytes)
                if (bytes.size.toLong() * 8 < minimumBits) fail(StatusFailure.INVALID_LIST)
                if (cached != null || !cacheAllowed) return@withLock bytes
                decoded[id] = bytes
                while (decoded.values.sumOf { it.size.toLong() } > limits.maxCachedBytes || decoded.size > limits.maxCachedLists) {
                    decoded.remove(decoded.keys.first())
                }
                bytes
            }
        }
    }

    private fun failed(metadata: CredentialStatusMetadata, reason: StatusFailure) = CredentialStatusMetadata(metadata.entries.map {
        if (it is CredentialStatusEntry.NotChecked) CredentialStatusEntry.Unavailable(it.referenceId, reason, now()) else it
    })

    private fun failure(cause: Throwable, fallback: StatusFailure): StatusFailure = when (cause) {
        is CredentialStatusException -> cause.reason
        is StatusListSizeLimitException, is id.walt.webdatafetching.ResponseBodyTooLargeException -> StatusFailure.RESOURCE_LIMIT
        is io.ktor.client.plugins.HttpRequestTimeoutException -> StatusFailure.TIMEOUT
        is SignatureInvalidException, is KeyResolutionFailedException -> StatusFailure.INVALID_SIGNATURE
        is UnsupportedOperationException -> StatusFailure.UNSUPPORTED_PROOF
        else -> fallback
    }

    private fun JsonObject.instantClaim(name: String): Instant? = this[name]?.let {
        val value = it as? JsonPrimitive ?: fail(StatusFailure.INVALID_VALIDITY)
        if (value.isString) fail(StatusFailure.INVALID_VALIDITY)
        value.longOrNull?.let { epoch -> Instant.fromEpochSeconds(epoch) }
            ?: value.doubleOrNull?.takeIf { number -> number.isFinite() }?.let { number ->
                val seconds = kotlin.math.floor(number)
                Instant.fromEpochSeconds(seconds.toLong(), ((number - seconds) * 1_000_000_000).toLong())
            } ?: fail(StatusFailure.INVALID_VALIDITY)
    }

    private fun JsonObject.nonnegativeLongClaim(name: String): Long? = this[name]?.let {
        val number = it as? JsonPrimitive ?: fail(StatusFailure.INVALID_LIST)
        if (number.isString) fail(StatusFailure.INVALID_LIST)
        number.longOrNull?.takeIf { value -> value >= 0 } ?: fail(StatusFailure.INVALID_LIST)
    }

    private fun JsonObject.dateClaim(name: String): Instant? = this[name]?.let {
        Instant.parse((it as? JsonPrimitive)?.contentOrNull ?: fail(StatusFailure.INVALID_VALIDITY))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class CwtLifetime(@CborLabel(4) val exp: Long? = null, @CborLabel(65534) val ttl: Long? = null)
}
