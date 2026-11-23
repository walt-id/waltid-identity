package id.walt.credentials.issuance

import id.walt.crypto.utils.UuidUtils.randomUUID
import id.walt.crypto2.keys.Key
import id.walt.did.dids.Crypto2DidService
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private val webDataFetcher = WebDataFetcher(WebDataFetcherId.DATA_FUNCTIONS)

class InstantClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

class IssuanceClock(val clock: Clock) : AbstractCoroutineContextElement(IssuanceClock) {
    constructor(instant: Instant) : this(InstantClock(instant))

    companion object Key : CoroutineContext.Key<IssuanceClock>
}

/** The verified holder key selected for one credential. Mappings may request a DID for this key. */
class IssuanceHolderKey(private val holderKey: Key) : AbstractCoroutineContextElement(IssuanceHolderKey) {
    companion object : CoroutineContext.Key<IssuanceHolderKey>

    private val mutex = Mutex()
    private var derivedDid: JsonPrimitive? = null

    internal suspend fun subjectDid(context: Map<String, JsonElement>): JsonElement =
        context["subjectDid"] ?: mutex.withLock {
            derivedDid ?: try {
                val did = Crypto2DidService.registerByKey(method = "jwk", key = holderKey).did
                JsonPrimitive(did).also { derivedDid = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Cannot find in context: subjectDid", e)
            }
        }
}

suspend fun currentIssuanceClock(): Clock =
    currentCoroutineContext()[IssuanceClock]?.clock ?: Clock.System

/**
 * The data functions for the issuance under way: every timestamp function reads the issuance clock set with
 * [IssuanceClock], falling back to the system clock. Use this rather than [dataFunctions], whose clock is always the
 * system clock. A scoped [IssuanceHolderKey] also supplies a fallback for a requested `subjectDid`.
 */
suspend fun issuanceDataFunctions(): Map<String, suspend (call: CredentialDataMergeUtils.FunctionCall) -> JsonElement> =
    dataFunctionsFor(currentIssuanceClock(), currentCoroutineContext()[IssuanceHolderKey])

fun dataFunctionsFor(clock: Clock) = dataFunctionsFor(clock, holderKey = null)

@OptIn(ExperimentalJsExport::class)
private fun dataFunctionsFor(
    clock: Clock,
    holderKey: IssuanceHolderKey?,
): Map<String, suspend (call: CredentialDataMergeUtils.FunctionCall) -> JsonElement> =
    mapOf(
        "subjectDid" to { holderKey?.subjectDid(it.context) ?: it.fromContext() },
        "issuerDid" to { it.fromContext() },
        "issuerId" to { it.fromContext() },
        "context" to {
            if (it.args == "subjectDid" && holderKey != null) holderKey.subjectDid(it.context)
            else it.context[it.args!!]!!
        },
        "display" to {
            val context = it.context
            val displayList =
                context["display"]?.jsonArray ?: throw IllegalArgumentException("No display available for this credential")
            val displayJsonArray = JsonArray(
                displayList.map { entry ->
                    val display = entry.jsonObject
                    JsonObject(
                        buildMap {
                            put("name", display["name"]!!)
                            display["description"]?.let { put("description", it) }
                            display["locale"]?.let { put("locale", it) }
                            display["logo"]?.jsonObject?.let { logo ->
                                put(
                                    "logo", buildJsonObject {
                                        // LogoProperties (id.walt.oid4vc) uses "url"; CredentialDisplayLogo uses "uri"
                                        put("url", (logo["url"] ?: logo["uri"])!!)
                                        logo["alt_text"]?.let { put("altText", it) }
                                    }
                                )
                            }
                            display["background_color"]?.let { put("backgroundColor", it) }
                            display["text_color"]?.let { put("textColor", it) }
                            display["background_image"]?.jsonObject?.let { bgImage ->
                                put(
                                    "backgroundImage", buildJsonObject {
                                        // LogoProperties uses "url"; CredentialDisplayBackgroundImage uses "uri"
                                        put("url", (bgImage["url"] ?: bgImage["uri"])!!)
                                        bgImage["alt_text"]?.let { put("altText", it) }
                                    }
                                )
                            }
                            display["customParameters"]?.jsonObject?.get("secondary_image")?.jsonObject?.let { secImage ->
                                put(
                                    "secondaryImage", JsonObject(
                                        mapOf(
                                            "url" to secImage["url"]!!,
                                            "altText" to secImage["alt_text"]!!,
                                        )
                                    )
                                )
                            }
                        }
                    )
                }
            )
            displayJsonArray
        },
        "timestamp-ebsi" to { JsonPrimitive(clock.now().toIso8681WithoutSubSecondPrecision()) },
        "timestamp-ebsi-in" to { JsonPrimitive((clock.now() + Duration.parse(it.args!!)).toIso8681WithoutSubSecondPrecision()) },

        "timestamp" to { JsonPrimitive(clock.now().toString()) },

        "timestamp-seconds" to { JsonPrimitive(clock.now().epochSeconds) },

        "timestamp-in" to { JsonPrimitive((clock.now() + Duration.parse(it.args!!)).toString()) },
        "timestamp-in-seconds" to { JsonPrimitive((clock.now() + Duration.parse(it.args!!)).epochSeconds) },

        "timestamp-before" to { JsonPrimitive((clock.now() - Duration.parse(it.args!!)).toString()) },
        "timestamp-before-seconds" to { JsonPrimitive((clock.now() - Duration.parse(it.args!!)).epochSeconds) },

        // Date-only functions (YYYY-MM-DD) for ISO 18013-5 full-date fields such as
        // issue_date and expiry_date. <timestamp> produces Instant strings that LocalDate.parse rejects.
        "date" to { JsonPrimitive(clock.now().toLocalDateTime(TimeZone.UTC).date.toString()) },
        "date-in" to { JsonPrimitive((clock.now() + Duration.parse(it.args!!)).toLocalDateTime(TimeZone.UTC).date.toString()) },
        "date-before" to { JsonPrimitive((clock.now() - Duration.parse(it.args!!)).toLocalDateTime(TimeZone.UTC).date.toString()) },

        "uuid" to { JsonPrimitive("urn:uuid:${randomUUID()}") },
        "webhook" to { JsonPrimitive(webDataFetcher.fetch<String>(it.args!!).body) },
        "webhook-json" to { webDataFetcher.fetch<JsonElement>(it.args!!).body },

        "last" to {
            it.history?.get(it.args!!)
                ?: throw IllegalArgumentException("No such function in history or no history: ${it.args}")
        }
    )

@OptIn(ExperimentalJsExport::class)
@JsExport
val dataFunctions = dataFunctionsFor(Clock.System)

private fun Instant.toIso8681WithoutSubSecondPrecision(): String =
    toString().substringBefore(".") + "Z"
