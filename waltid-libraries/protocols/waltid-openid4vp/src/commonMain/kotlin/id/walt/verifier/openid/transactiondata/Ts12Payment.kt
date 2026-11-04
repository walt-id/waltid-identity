package id.walt.verifier.openid.transactiondata

import io.github.optimumcode.json.schema.FormatBehavior
import io.github.optimumcode.json.schema.JsonSchemaLoader
import io.github.optimumcode.json.schema.SchemaOption
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

/** A rule and payload-relative JSON pointer; errors never include transaction values. */
class Ts12PaymentValidationException(
    val path: String,
    val rule: String,
    val unsupported: Boolean = false,
) : IllegalArgumentException("Invalid TS12 payment at payload$path: $rule")

enum class Ts12PaymentFrequency { INDA, DAIL, WEEK, TOWK, TWMN, MNTH, TOMN, QUTR, FOMN, SEMI, YEAR, TYEA }

sealed interface Ts12PaymentSchedule {
    data object Unscheduled : Ts12PaymentSchedule
    class Scheduled internal constructor(val executionDate: Instant) : Ts12PaymentSchedule
    class Recurring internal constructor(val frequency: Ts12PaymentFrequency) : Ts12PaymentSchedule
}

/** Validated display values only; never serialize these instead of the received transaction entry. */
class Ts12Payment internal constructor(
    val schedule: Ts12PaymentSchedule,
    fields: Map<List<String>, String>,
) {
    val fields: Map<List<String>, String> = fields.toMap()

    fun requireCurrent(now: Instant = Clock.System.now()) {
        if (schedule is Ts12PaymentSchedule.Scheduled && schedule.executionDate < now) {
            invalid("/execution_date", "must not lie in the past")
        }
    }

    companion object {
        const val TYPE: String = "urn:eudi:sca:payment:1"
        val schema: JsonObject get() = Ts12PaymentSchema.value
        private val validator by lazy {
            JsonSchemaLoader.create()
                .withSchemaOption(SchemaOption.FORMAT_BEHAVIOR_OPTION, FormatBehavior.ANNOTATION_AND_ASSERTION)
                .fromJsonElement(schema)
        }

        fun validate(payload: JsonObject, now: Instant = Clock.System.now()): Ts12Payment {
            // Reject unsupported extensions before schema errors, retaining the wallet's error distinction.
            rejectUnknown(payload, schema, "")
            checkNumericResources(payload, "")
            val requestedCurrency = (payload["currency"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (requestedCurrency != null && paymentCurrencyMinorUnits(requestedCurrency) == null) {
                invalid("/currency", "unsupported ISO 4217 currency", unsupported = true)
            }
            validator.validate(payload) { error ->
                invalid(error.objectPath.toString(), "must satisfy " + error.schemaPath.toString().substringAfterLast('/'))
            }
            if ("execution_date" in payload && "recurrence" in payload) {
                invalid("/execution_date", "must be absent when recurrence is present")
            }
            val currency = payload.getValue("currency").jsonPrimitive.content
            val minorUnits = paymentCurrencyMinorUnits(currency)
                ?: invalid("/currency", "unsupported ISO 4217 currency", unsupported = true)
            val fields = linkedMapOf<List<String>, String>()
            fun leaves(value: JsonObject, prefix: List<String>) {
                value.forEach { (name, element) ->
                    val path = prefix + name
                    if (element is JsonObject) leaves(element, path) else {
                        val primitive = element.jsonPrimitive
                        val pointer = "/" + path.joinToString("/")
                        val text = primitive.content
                        fields[path] = when {
                            primitive.isString -> text.also {
                                if (it.isBlank()) invalid(pointer, "must not be blank")
                            }
                            primitive.booleanOrNull != null -> text
                            name in MONEY_FIELDS -> exactPaymentNumber(text, minorUnits, pointer)
                            name == "number" || name == "initial_amount_number" -> exactPaymentNumber(text, 0, pointer)
                            else -> exactPaymentNumber(text, null, pointer)
                        }
                    }
                }
            }
            leaves(payload, emptyList())
            val schedule = when {
                "execution_date" in payload -> Ts12PaymentSchedule.Scheduled(
                    Instant.parse(payload.getValue("execution_date").jsonPrimitive.content))
                "recurrence" in payload -> Ts12PaymentSchedule.Recurring(Ts12PaymentFrequency.valueOf(
                    payload.getValue("recurrence").jsonObject.getValue("frequency").jsonPrimitive.content))
                else -> Ts12PaymentSchedule.Unscheduled
            }
            return Ts12Payment(schedule, fields).also { it.requireCurrent(now) }
        }
    }
}

/** Called by both request validation modes, and again before releasing a payment proof. */
fun validateTs12PaymentEntries(entries: List<DecodedTransactionData>, now: Instant = Clock.System.now()) {
    entries.filter { it.transactionData.type == Ts12Payment.TYPE }.forEach { entry ->
        if (entry.details.keys != setOf("payload")) invalid("", "requires only the payload extension", unsupported = true)
        val payload = entry.details["payload"] as? JsonObject ?: invalid("", "must be an object")
        Ts12Payment.validate(payload, now)
    }
}

private fun rejectUnknown(value: JsonObject, schema: JsonObject, path: String) {
    val properties = schema.getValue("properties").jsonObject
    value.forEach { (name, element) ->
        val childPath = "$path/${name.replace("~", "~0").replace("/", "~1")}"
        val definition = properties[name] as? JsonObject
            ?: invalid(childPath, "unknown property", unsupported = true)
        if (element is JsonObject && definition["type"] == JsonPrimitive("object")) {
            rejectUnknown(element, definition, childPath)
        }
    }
}

private val MONEY_FIELDS = setOf("amount", "min_amount", "max_amount", "total_amount", "initial_amount")
private val JSON_NUMBER = Regex("(-?)(0|[1-9][0-9]*)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?")

/** Expands decimal exponents without floating point, preserving the existing bounded resource policy. */
private fun exactPaymentNumber(text: String, minorUnits: Int?, path: String): String {
    if (text.length > 128) invalid(path, "numeric token exceeds 128 characters", unsupported = true)
    val match = JSON_NUMBER.matchEntire(text) ?: invalid(path, "must be a JSON number")
    val exponentText = match.groupValues[4]
    val exponent = if (exponentText.isEmpty()) 0 else exponentText.toIntOrNull()
        ?: invalid(path, "unsupported numeric exponent", unsupported = true)
    if (exponent !in -128..128) invalid(path, "numeric exponent must be between -128 and 128", unsupported = true)
    val digits = match.groupValues[2] + match.groupValues[3]
    val point = match.groupValues[2].length + exponent
    val whole = when {
        point <= 0 -> "0"
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point)
    }.trimStart('0').ifEmpty { "0" }
    val fraction = when {
        point <= 0 -> "0".repeat(-point) + digits
        point >= digits.length -> ""
        else -> digits.substring(point)
    }.trimEnd('0')
    if (minorUnits != null && fraction.length > minorUnits) invalid(path, "exceeds permitted decimal precision")
    return match.groupValues[1] + whole + if (fraction.isEmpty()) "" else ".$fraction"
}

private fun invalid(path: String, rule: String, unsupported: Boolean = false): Nothing =
    throw Ts12PaymentValidationException(path, rule, unsupported)

private fun checkNumericResources(value: JsonObject, path: String) {
    value.forEach { (name, element) ->
        val child = "$path/$name"
        if (element is JsonObject) checkNumericResources(element, child)
        else if (element is JsonPrimitive && !element.isString && element.booleanOrNull == null && element != JsonNull) {
            exactPaymentNumber(element.content, null, child)
        }
    }
}
