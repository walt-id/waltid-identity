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
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.StatusEntry
import id.walt.policies2.vc.policies.status.model.W3CEntry
import id.walt.policies2.vc.policies.status.model.W3CStatusContent
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.reader.IETFCwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.IETFJwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.W3CStatusValueReader
import id.walt.policies2.vc.policies.status.reader.format.CwtFormatMatcher
import id.walt.policies2.vc.policies.status.reader.format.JwtFormatMatcher
import id.walt.policies2.vc.policies.status.signature.KeyResolutionFailedException
import id.walt.policies2.vc.policies.status.signature.SignatureInvalidException
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.signature.referencedCredentialCertificateChain
import id.walt.policies2.vc.policies.status.validator.IETFStatusValidator
import id.walt.policies2.vc.policies.status.validator.W3CStatusValidator
import id.walt.statuslist.codec.BitstringStatusListCodec
import id.walt.statuslist.codec.StatusList2021Codec
import id.walt.statuslist.codec.StatusListCodec
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.codec.decodeBounded
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import id.walt.policies2.vc.policies.status.reader.StatusValueReader
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import kotlinx.serialization.json.doubleOrNull
import id.walt.webdatafetching.ResponseBodyTooLargeException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlin.io.encoding.Base64
import kotlin.math.floor
import kotlin.time.Duration

/** Extra issuer authorization can be supplied without changing the status protocol implementation. */
fun interface CredentialStatusReferenceAuthorizer {
    suspend fun authorize(credential: DigitalCredential): Boolean
}

/** Metadata plus the storage restriction of the response that produced it. */
data class CredentialStatusResolution(
    val metadata: CredentialStatus,
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
    private data class Reference(val status: CredentialStatusEntry, val entry: StatusEntry) {
        val id get() = status.referenceId
        val protocol get() = requireNotNull(status.protocol)
        val purpose get() = requireNotNull(status.purposeName)
    }
    private data class Discovery(val metadata: CredentialStatus, val references: List<Reference>)

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
                    else -> fail(CheckFailure.Reason.UNSUPPORTED_PROOF)
                },
                minimumBits = 131_072 * entry.size, cacheAllowed = cacheAllowed,
            )
        },
        signatureVerifier,
    )

    // Preserve the released single-purpose helper model while projecting an authenticated list's
    // one-or-more purposes onto this exact reference. Debug status messages never become UI labels.
    private fun w3cReader(entry: W3CEntry) = object : StatusValueReader<W3CStatusContent> {
        private val base = W3CStatusValueReader(JwtFormatMatcher(), JwtParser())
        override fun canHandle(content: StatusListContent): Boolean = base.canHandle(content)
        override fun read(content: StatusListContent): Result<W3CStatusContent> = try {
            val payload = JwtParser().parse((content as? StatusListContent.Text)?.content ?: fail(CheckFailure.Reason.UNSUPPORTED_PROOF))
            val vc = payload["vc"] as? JsonObject ?: payload
            val subject = vc["credentialSubject"] as? JsonObject ?: fail(CheckFailure.Reason.INVALID_LIST)
            val type = if (entry.type == "BitstringStatusListEntry") "BitstringStatusList" else "StatusList2021"
            if (type !in stringValues(subject["type"]) || entry.purpose !in stringValues(subject["statusPurpose"])) fail(CheckFailure.Reason.INVALID_LIST)
            subject["statusSize"]?.let {
                val size = it as? JsonPrimitive ?: fail(CheckFailure.Reason.INVALID_LIST)
                if (size.isString || size.intOrNull != entry.size) fail(CheckFailure.Reason.INVALID_LIST)
            }
            val encoded = subject["encodedList"] as? JsonPrimitive ?: fail(CheckFailure.Reason.INVALID_LIST)
            if (!encoded.isString || encoded.content.isBlank()) fail(CheckFailure.Reason.INVALID_LIST)
            Result.success(W3CStatusContent(type, entry.purpose, encoded.content))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (cause: Throwable) { Result.failure(cause) }
    }

    private fun stringValues(value: JsonElement?): List<String> {
        val values = if (value is JsonArray) value else listOfNotNull(value)
        if (values.isEmpty()) fail(CheckFailure.Reason.INVALID_LIST)
        return values.map {
            val string = it as? JsonPrimitive ?: fail(CheckFailure.Reason.INVALID_LIST)
            if (!string.isString || string.content.isBlank()) fail(CheckFailure.Reason.INVALID_LIST)
            string.content
        }
    }

    /** Canonical local discovery, without signature verification, issuer requests or wallet mutation. */
    suspend fun discover(credential: DigitalCredential): CredentialStatus = try {
        discovery(canonicalCredential(credential)).metadata
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        CredentialStatus.DiscoveryFailed(DiscoveryProblem.MALFORMED_CONTAINER)
    }

    private suspend fun canonicalCredential(credential: DigitalCredential): DigitalCredential =
        credential.signed?.let { CredentialParser.detectAndParse(it).second } ?: credential

    suspend fun resolve(credential: DigitalCredential, force: Boolean = false, session: CredentialStatusRefreshSession? = null): CredentialStatusResolution {
        session?.enter(transport)
        return try {
            withTimeoutOrNull(limits.resolutionTimeout) {
                resolutionPermits.withPermit { resolveWithinBudget(credential, force, session) }
            } ?: CredentialStatusResolution(failed(discover(credential), CheckFailure.Reason.TIMEOUT))
        } finally { withContext(NonCancellable) { session?.leave() } }
    }

    private suspend fun resolveWithinBudget(credential: DigitalCredential, force: Boolean, session: CredentialStatusRefreshSession?): CredentialStatusResolution {
        // Even absence/unsupported discovery must come from the signed source, not a mutable
        // stored projection which could hide a real reference or introduce another locator.
        val canonical = try {
            canonicalCredential(credential)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Throwable) {
            val projected = discovery(credential).metadata
            return CredentialStatusResolution(if (projected is CredentialStatus.References && projected.entries.isEmpty())
                CredentialStatus.DiscoveryFailed(DiscoveryProblem.MALFORMED_CONTAINER)
            else failed(projected, failure(cause, CheckFailure.Reason.REFERENCE_UNAUTHENTICATED)))
        }
        val initial = discovery(canonical)
        if (initial.references.isEmpty()) return CredentialStatusResolution(initial.metadata)
        val authenticated = try {
            if (canonical.signed == null) fail(CheckFailure.Reason.UNSUPPORTED_PROOF)
            authenticate(canonical)
            canonical
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            return CredentialStatusResolution(failed(initial.metadata, failure(cause, CheckFailure.Reason.REFERENCE_UNAUTHENTICATED)))
        }
        val discovery = discovery(authenticated)
        if (discovery.metadata is CredentialStatus.DiscoveryFailed) return CredentialStatusResolution(discovery.metadata)
        val results = discovery.metadata.entries.associateByTo(linkedMapOf(), CredentialStatusEntry::referenceId)
        val notPersistent = mutableSetOf<String>()
        for (reference in discovery.references) {
            var resource: StatusListResource? = null
            results[reference.id] = try {
                resource = transport.fetch(reference.entry.uri, force, session)
                if (!resource.storageAllowed) notPersistent += reference.id
                val entry = evaluate(authenticated, reference, resource)
                transport.constrain(reference.entry.uri, resource.bodyId, requireNotNull(entry.lastObservation()).freshUntil)
                entry
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                val reason = failure(cause, if (resource == null) CheckFailure.Reason.TRANSPORT else CheckFailure.Reason.INVALID_LIST)
                if (reason != CheckFailure.Reason.TRANSPORT && reason != CheckFailure.Reason.TIMEOUT && reason !is CheckFailure.RateLimited) {
                    resource?.let { transport.invalidate(reference.entry.uri, it.bodyId) }
                }
                reference.status.withFailure(reason, now())
            }
        }
        return CredentialStatusResolution(CredentialStatus.references(results.values.toList()), notPersistent)
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
        val references = linkedMapOf<String, Reference>()
        val entries = linkedMapOf<String, CredentialStatusEntry>()
        fun unsupported(method: String, identity: String = method) {
            val id = referenceId(credential, identity)
            entries[id] = CredentialStatusEntry.Unsupported(id, method.take(64))
        }
        fun add(reference: Reference) {
            val id = reference.id
            if (entries[id] is CredentialStatusEntry.Malformed) return
            val previous = references[id]
            if (previous != null && previous.entry != reference.entry) {
                references.remove(id)
                entries[id] = CredentialStatusEntry.Malformed(id, ReferenceProblem.CONFLICTING_REFERENCE)
            } else {
                references[id] = reference
                entries[id] = reference.status
            }
        }
        fun identified(identity: String, operation: () -> Unit) {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (cause: Exception) {
                val id = referenceId(credential, identity)
                entries[id] = CredentialStatusEntry.Malformed(id,
                    if ((cause as? CredentialStatusException)?.reason == CheckFailure.Reason.RESOURCE_LIMIT)
                        ReferenceProblem.RESOURCE_LIMIT else ReferenceProblem.MALFORMED)
            }
        }
        try {
            val status = credential.credentialData["status"]
            if (credential is MdocsCredential) {
                credential.documentMso.status?.let { msoStatus ->
                    if (msoStatus.statusList != null) identified("mso-status-list") {
                        val entry = json.decodeFromJsonElement<IETFEntry>(requireNotNull(MDocEntryExtractor().extract(credential)))
                        add(reference(credential, entry, StatusProtocol.ISO_MSO_STATUS_LIST))
                    }
                    if (msoStatus.identifierList != null) unsupported("identifier_list")
                    if (msoStatus.type != null || msoStatus.purpose != null || msoStatus.index != null || msoStatus.uri != null) unsupported("ETSI mdoc status")
                    if (msoStatus.statusList == null && msoStatus.identifierList == null) unsupported("mdoc status method")
                }
            } else if (status != null) {
                val objectValue = status as? JsonObject ?: throw IllegalArgumentException("Malformed status container")
                require(objectValue.isNotEmpty())
                if (objectValue.size > limits.maxReferencesPerCredential) fail(CheckFailure.Reason.RESOURCE_LIMIT)
                if ("status_list" in objectValue) identified("token-status-list") {
                    val entry = json.decodeFromJsonElement<IETFEntry>(requireNotNull(IETFEntryExtractor(MDocEntryExtractor()).extract(credential)))
                    add(reference(credential, entry, StatusProtocol.TOKEN_STATUS_LIST))
                }
                objectValue.keys.filter { it != "status_list" }.forEach { method ->
                    identified("status-method:$method") { unsupported(method, "status-method:$method") }
                }
            }
            credential.credentialData["credentialStatus"]?.let { raw ->
                val list = when (raw) {
                    is JsonArray -> raw
                    is JsonObject -> listOf(raw)
                    else -> throw IllegalArgumentException("Malformed status container")
                }
                require(list.isNotEmpty())
                if (list.size > limits.maxReferencesPerCredential) fail(CheckFailure.Reason.RESOURCE_LIMIT)
                list.forEachIndexed { index, element -> identified("credentialStatus[$index]") {
                    val value = element as? JsonObject ?: throw IllegalArgumentException("Malformed status entry")
                    val type = (value["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    require(!type.isNullOrBlank())
                    val protocol = when (type) {
                        "BitstringStatusListEntry" -> StatusProtocol.BITSTRING_STATUS_LIST
                        "StatusList2021Entry" -> StatusProtocol.STATUS_LIST_2021
                        else -> { unsupported(type, "credentialStatus:$value"); return@identified }
                    }
                    val entryIndex = value["statusListIndex"] as? JsonPrimitive ?: throw IllegalArgumentException("Missing status index")
                    require(entryIndex.isString && entryIndex.content.isNotEmpty() && entryIndex.content.all { it in '0'..'9' })
                    if (entryIndex.content.toULongOrNull() == null) fail(CheckFailure.Reason.RESOURCE_LIMIT)
                    value["statusSize"]?.let {
                        val size = it as? JsonPrimitive ?: throw IllegalArgumentException("Malformed status size")
                        require(!size.isString && size.intOrNull != null && size.int >= 1)
                        if (size.int > 8) fail(CheckFailure.Reason.RESOURCE_LIMIT)
                    }
                    val normalized = if (value["statusReference"] is JsonArray) JsonObject(value - "statusReference") else value
                    val entry = json.decodeFromJsonElement<W3CEntry>(normalized)
                    if (protocol == StatusProtocol.STATUS_LIST_2021) require(entry.size == 1)
                    else {
                        require(entry.size in 1..8)
                        if (entry.size > 1 || entry.statuses != null) require(entry.statuses?.size == 1.shl(entry.size))
                    }
                    add(reference(credential, entry, protocol))
                } }
            }
            if (entries.size > limits.maxReferencesPerCredential) fail(CheckFailure.Reason.RESOURCE_LIMIT)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (cause: Exception) {
            val problem = if ((cause as? CredentialStatusException)?.reason == CheckFailure.Reason.RESOURCE_LIMIT)
                DiscoveryProblem.RESOURCE_LIMIT else DiscoveryProblem.MALFORMED_CONTAINER
            return Discovery(CredentialStatus.DiscoveryFailed(problem), emptyList())
        }
        return Discovery(CredentialStatus.references(entries.values.toList()), references.values.toList())
    }

    private fun reference(credential: DigitalCredential, entry: StatusEntry, protocol: StatusProtocol): Reference {
        requireStatusEndpoint(entry.uri)
        if (entry.index > Int.MAX_VALUE.toULong()) fail(CheckFailure.Reason.RESOURCE_LIMIT)
        val purpose = when (protocol) {
            StatusProtocol.TOKEN_STATUS_LIST -> "token"
            StatusProtocol.ISO_MSO_STATUS_LIST -> "revocation"
            else -> (entry as W3CEntry).purpose.also { require(it.isNotBlank()) }
        }
        val id = referenceId(credential, "$protocol|$purpose|${entry.uri}|${entry.index}")
        val status = when (protocol) {
            StatusProtocol.TOKEN_STATUS_LIST -> CredentialStatusEntry.Token(id, Check.NotChecked)
            StatusProtocol.ISO_MSO_STATUS_LIST -> CredentialStatusEntry.Mso(id, Check.NotChecked)
            StatusProtocol.BITSTRING_STATUS_LIST -> CredentialStatusEntry.Bitstring(id, BitstringPurpose.fromValue(purpose), Check.NotChecked)
            StatusProtocol.STATUS_LIST_2021 -> CredentialStatusEntry.Legacy(id, LegacyPurpose.fromValue(purpose), Check.NotChecked)
        }
        return Reference(status, entry)
    }

    private fun referenceId(credential: DigitalCredential, reference: String): String =
        fingerprint("${credential.format}|${credential.signed}|$reference".encodeToByteArray())

    private suspend fun authenticate(credential: DigitalCredential) {
        val key = credential.getSignerCrypto2Key() ?: fail(CheckFailure.Reason.REFERENCE_UNAUTHENTICATED)
        credential.verifyCrypto2(key, Crypto2VerificationAlgorithms(
            jws = JwsAlgorithm.entries.toSet(), cose = setOf(-7, -35, -36, -8, -37, -38, -39, -257, -258, -259),
        )).getOrThrow()
        if (referenceAuthorizer?.authorize(credential) == false) fail(CheckFailure.Reason.REFERENCE_UNAUTHENTICATED)
        val at = now()
        certificateDeadline(referencedCredentialCertificateChain(credential), at)
        if (credential is MdocsCredential) {
            val validity = credential.documentMso.validityInfo
            if (at < validity.validFrom) fail(CheckFailure.NotYetValid(validity.validFrom))
            if (at >= validity.validUntil) fail(CheckFailure.Reason.CREDENTIAL_EXPIRED)
        } else {
            val jwt = credential.signed?.substringBefore('~')?.let { JwtParser().parse(it) }
                ?: fail(CheckFailure.Reason.UNSUPPORTED_PROOF)
            val data = jwt["vc"] as? JsonObject ?: jwt
            val expiry = listOfNotNull(jwt.instantClaim("exp"), data.dateClaim("validUntil"), data.dateClaim("expirationDate")).minOrNull()
            val beginning = listOfNotNull(jwt.instantClaim("nbf"), data.dateClaim("validFrom"), data.dateClaim("issuanceDate")).maxOrNull()
            if (beginning != null && at < beginning) fail(CheckFailure.NotYetValid(beginning))
            if (expiry != null && at >= expiry) fail(CheckFailure.Reason.CREDENTIAL_EXPIRED)
        }
    }

    private suspend fun evaluate(credential: DigitalCredential, reference: Reference, resource: StatusListResource): CredentialStatusEntry {
        val at = now()
        val content = resource.content
        val evaluated = when (val entry = reference.entry) {
            is IETFEntry -> {
                if (credential is SdJwtCredential && content !is StatusListContent.Text) fail(CheckFailure.Reason.UNSUPPORTED_PROOF)
                if (reference.protocol == StatusProtocol.ISO_MSO_STATUS_LIST) requireMsoProfile(content)
                val statusList = when (content) {
                    is StatusListContent.Text -> jwtReader.read(content).getOrThrow()
                    is StatusListContent.Binary -> cwtReader.read(content).getOrThrow()
                }
                if (statusList.size !in setOf(1, 2, 4, 8)) fail(CheckFailure.Reason.INVALID_LIST)
                if (reference.protocol == StatusProtocol.ISO_MSO_STATUS_LIST && statusList.size != 1) fail(CheckFailure.Reason.INVALID_LIST)
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
            certificateDeadline(listOf(Base64.encode(it)), at)
        }
        val validUntil = listOfNotNull(listValidUntil, credentialExpiry, evaluated.authorizationValidUntil,
            credentialCertificateDeadline, explicitStatusAnchorDeadline).minOrNull()
        val freshUntil = listOfNotNull(resource.freshUntil, validUntil,
            ttl?.let { resource.retrievedAt + it }, resource.retrievedAt + limits.maximumFreshness).min()
        require(evaluated.value <= 255u)
        val code = evaluated.value.toUByte()
        return when (val owner = reference.status) {
            is CredentialStatusEntry.Token -> CredentialStatusEntry.Token(owner.referenceId,
                Check.Observed(Observation(TokenStatus.fromCode(code), resource.retrievedAt, freshUntil, validUntil)))
            is CredentialStatusEntry.Mso -> {
                require(evaluated.value <= 1u)
                CredentialStatusEntry.Mso(owner.referenceId, Check.Observed(Observation(
                    if (evaluated.value == 0u) MsoStatus.CLEAR else MsoStatus.REVOKED, resource.retrievedAt, freshUntil, validUntil)))
            }
            is CredentialStatusEntry.Bitstring -> CredentialStatusEntry.Bitstring(owner.referenceId, owner.purpose,
                Check.Observed(Observation(code, resource.retrievedAt, freshUntil, validUntil)))
            is CredentialStatusEntry.Legacy -> {
                require(evaluated.value <= 1u)
                CredentialStatusEntry.Legacy(owner.referenceId, owner.purpose,
                    Check.Observed(Observation(evaluated.value != 0u, resource.retrievedAt, freshUntil, validUntil)))
            }
            else -> error("Unsupported reference cannot be evaluated")
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun requireMsoProfile(content: StatusListContent) {
        if (content !is StatusListContent.Binary) fail(CheckFailure.Reason.UNSUPPORTED_PROOF)
        val sign1 = CoseSign1.fromTagged(content.content)
        val headers = coseCompliantCbor.decodeFromByteArray<CoseHeaders>(sign1.protected)
        if (headers.x5chain.isNullOrEmpty()) fail(CheckFailure.Reason.INVALID_SIGNATURE)
        val claims = coseCompliantCbor.decodeFromByteArray<CwtLifetime>(requireNotNull(sign1.payload))
        if (claims.exp == null) fail(CheckFailure.Reason.INVALID_VALIDITY)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun lifetime(content: StatusListContent, reference: Reference): Pair<Instant?, Duration?> = when (content) {
        is StatusListContent.Binary -> {
            val payload = requireNotNull(CoseSign1.fromTagged(content.content).payload)
            val claims = coseCompliantCbor.decodeFromByteArray<CwtLifetime>(payload)
            claims.exp?.let(Instant::fromEpochSeconds) to claims.ttl?.also { if (it < 0) fail(CheckFailure.Reason.INVALID_LIST) }?.seconds
        }
        is StatusListContent.Text -> {
            val jwt = JwtParser().parse(content.content)
            if (reference.protocol == StatusProtocol.TOKEN_STATUS_LIST) {
                val expiry = jwt.instantClaim("exp")
                val beginning = jwt.instantClaim("nbf")
                if (expiry != null && now() >= expiry || beginning != null && now() < beginning) fail(CheckFailure.Reason.INVALID_VALIDITY)
                expiry to jwt.nonnegativeLongClaim("ttl")?.seconds
            } else {
                val vc = jwt["vc"] as? JsonObject ?: jwt
                val from = listOfNotNull(jwt.instantClaim("nbf"), vc.dateClaim("validFrom"), vc.dateClaim("issuanceDate")).maxOrNull()
                val expiry = listOfNotNull(jwt.instantClaim("exp"), vc.dateClaim("validUntil"), vc.dateClaim("expirationDate")).minOrNull()
                if (from != null && now() < from || expiry != null && now() >= expiry) fail(CheckFailure.Reason.INVALID_VALIDITY)
                if (vc["id"] != null && vc["id"]?.jsonPrimitive?.contentOrNull != reference.entry.uri) fail(CheckFailure.Reason.INVALID_LIST)
                val types = vc["type"]?.let { if (it is JsonArray) it else listOf(it) }.orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                val expected = if (reference.protocol == StatusProtocol.BITSTRING_STATUS_LIST) "BitstringStatusListCredential" else "StatusList2021Credential"
                if (expected !in types) fail(CheckFailure.Reason.INVALID_LIST)
                val subject = vc["credentialSubject"] as? JsonObject ?: fail(CheckFailure.Reason.INVALID_LIST)
                expiry to if (reference.protocol == StatusProtocol.BITSTRING_STATUS_LIST) subject.nonnegativeLongClaim("ttl")?.milliseconds else null
            }
        }
    }

    private fun boundedAlgorithm(codec: StatusListCodec, minimumBits: Int = 0, cacheAllowed: Boolean = true) = object : StatusListExpansionAlgorithm {
        override suspend fun invoke(bitstring: String): ByteArray {
            val id = fingerprint("${codec.format}|$bitstring".encodeToByteArray())
            return decodedMutex.withLock {
                val cached = if (cacheAllowed) decoded[id] else null
                val bytes = cached ?: codec.decodeBounded(bitstring, limits.maxExpandedBytes)
                if (bytes.size.toLong() * 8 < minimumBits) fail(CheckFailure.Reason.INVALID_LIST)
                if (cached != null || !cacheAllowed) return@withLock bytes
                decoded[id] = bytes
                while (decoded.values.sumOf { it.size.toLong() } > limits.maxCachedBytes || decoded.size > limits.maxCachedLists) {
                    decoded.remove(decoded.keys.first())
                }
                bytes
            }
        }
    }

    private fun failed(metadata: CredentialStatus, reason: CheckFailure): CredentialStatus = when (metadata) {
        is CredentialStatus.References -> CredentialStatus.references(metadata.entries.map { it.withFailure(reason, now()) })
        is CredentialStatus.DiscoveryFailed -> metadata
    }

    private fun failure(cause: Throwable, fallback: CheckFailure): CheckFailure = when (cause) {
        is CredentialStatusException -> cause.reason
        is StatusListSizeLimitException, is ResponseBodyTooLargeException -> CheckFailure.Reason.RESOURCE_LIMIT
        is HttpRequestTimeoutException -> CheckFailure.Reason.TIMEOUT
        is SignatureInvalidException, is KeyResolutionFailedException -> CheckFailure.Reason.INVALID_SIGNATURE
        is UnsupportedOperationException -> CheckFailure.Reason.UNSUPPORTED_PROOF
        else -> fallback
    }

    private fun JsonObject.instantClaim(name: String): Instant? = this[name]?.let {
        val value = it as? JsonPrimitive ?: fail(CheckFailure.Reason.INVALID_VALIDITY)
        if (value.isString) fail(CheckFailure.Reason.INVALID_VALIDITY)
        value.longOrNull?.let { epoch -> Instant.fromEpochSeconds(epoch) }
            ?: value.doubleOrNull?.takeIf { number -> number.isFinite() }?.let { number ->
                val seconds = floor(number)
                Instant.fromEpochSeconds(seconds.toLong(), ((number - seconds) * 1_000_000_000).toLong())
            } ?: fail(CheckFailure.Reason.INVALID_VALIDITY)
    }

    private fun JsonObject.nonnegativeLongClaim(name: String): Long? = this[name]?.let {
        val number = it as? JsonPrimitive ?: fail(CheckFailure.Reason.INVALID_LIST)
        if (number.isString) fail(CheckFailure.Reason.INVALID_LIST)
        number.longOrNull?.takeIf { value -> value >= 0 } ?: fail(CheckFailure.Reason.INVALID_LIST)
    }

    private fun JsonObject.dateClaim(name: String): Instant? = this[name]?.let {
        Instant.parse((it as? JsonPrimitive)?.contentOrNull ?: fail(CheckFailure.Reason.INVALID_VALIDITY))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class CwtLifetime(@CborLabel(4) val exp: Long? = null, @CborLabel(65534) val ttl: Long? = null)
}
