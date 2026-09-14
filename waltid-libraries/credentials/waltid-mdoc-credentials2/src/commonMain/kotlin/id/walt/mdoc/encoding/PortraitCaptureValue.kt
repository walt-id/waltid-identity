package id.walt.mdoc.encoding

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborString
import kotlin.time.Instant

/** Preserves source precision until a caller explicitly chooses a timestamp or legacy date. */
internal sealed interface PortraitCaptureValue {
    data class DateOnly(val date: LocalDate) : PortraitCaptureValue
    data class Timestamp(val instant: Instant) : PortraitCaptureValue

    fun toInstant(): Instant = when (this) {
        is DateOnly -> date.atStartOfDayIn(TimeZone.UTC)
        is Timestamp -> instant
    }

    fun toTimestampString(): String = toInstant().toMdocTDateString()

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
    fun toCborString(): CborString = CborString(toTimestampString(), 0u)

    fun toLocalDate(): LocalDate = when (this) {
        is DateOnly -> date
        is Timestamp -> instant.toLocalDateTime(TimeZone.UTC).date.also {
            require(it.atStartOfDayIn(TimeZone.UTC) == instant) {
                "The legacy LocalDate model cannot retain a capture time; use namespace data for full timestamps"
            }
        }
    }

    /** Retains the existing value types exposed by the dynamic namespace serializer registry. */
    fun toLegacyValue(): Any = when (this) {
        is DateOnly -> date
        is Timestamp -> instant
    }

    companion object {
        fun parse(value: String): PortraitCaptureValue =
            if (value.length == 10) DateOnly(LocalDate.parse(value)) else Timestamp(Instant.parse(value))

        /** Runtime validation is confined to the existing Any-valued registry boundary. */
        fun fromLegacyValue(value: Any): PortraitCaptureValue = when (value) {
            is LocalDate -> DateOnly(value)
            is Instant -> Timestamp(value)
            else -> throw IllegalArgumentException("Expected a portrait capture date or timestamp")
        }
    }
}
