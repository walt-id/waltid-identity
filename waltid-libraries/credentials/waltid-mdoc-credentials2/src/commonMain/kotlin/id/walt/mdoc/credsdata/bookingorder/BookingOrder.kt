@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata

import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for the IATA Booking Order mDOC as per IATA specifications.
 * DocType: org.iata.bookingorder.1
 * Namespace: org.iata.bookingorder.1
 */
@Serializable
data class BookingOrder(
    /** Credential Subject Given Name */
    @SerialName("givenName")
    val givenName: String,

    /** Order/Booking Identifier (max 14 characters) */
    @SerialName("orderIdentifier")
    val orderIdentifier: String,

    /** Credential Subject Surname */
    @SerialName("surname")
    val surname: String,

    /** Credential Subject Title Name */
    @SerialName("titleName")
    val titleName: String,

    /** Barcode string representation */
    @SerialName("barcode_string")
    val barcodeString: String,

    /** Passenger name */
    @SerialName("pax_name")
    val paxName: String,

    /** Ticket indicator */
    @SerialName("ticket_indicator")
    val ticketIndicator: String,

    /** IATA location code for the arrival station (3 characters) */
    @SerialName("destStationIATALocationCode")
    val destStationIATALocationCode: String,

    /** Date of the scheduled aircraft departure from the station of origin in ISO 8601 format (YYYY-MM-DD) */
    @SerialName("flightIdentifierDate")
    val flightIdentifierDate: LocalDate,

    /** The IATA Airline code or ICAO Airline code of the operating airline */
    @SerialName("operatingCarrierAirlineDesigCode")
    val operatingCarrierAirlineDesigCode: String,

    /** The numerical designation of a flight as assigned by the operating carrier */
    @SerialName("operatingCarrierFlightNumber")
    val operatingCarrierFlightNumber: String,

    /** IATA location code for the departure station (3 characters) */
    @SerialName("originStationIATALocationCode")
    val originStationIATALocationCode: String,

    /** Collection of passenger and booking statuses related to an airline's customer ordering and passenger fulfillment processes */
    @SerialName("bookingStatusCode")
    val bookingStatusCode: String,

    /** Local time the flight is due to arrive at the destination airport in ISO 8601 format (HH:MM:SS) */
    @SerialName("scheduledArrivalTime")
    val scheduledArrivalTime: String,

    /** Local time the flight is due to leave the origin airport in ISO 8601 format (HH:MM:SS) */
    @SerialName("scheduledDepartureTime")
    val scheduledDepartureTime: String

) : MdocData {

    companion object : MdocCompanion {
        const val DOC_TYPE = "org.iata.bookingorder.1"
        const val NAMESPACE = "org.iata.bookingorder.1"

        override fun registerSerializationTypes() {
            val localDate = LocalDate.serializer()

            MdocsCborSerializer.register(
                mapOf(
                    "flightIdentifierDate" to localDate
                ),
                NAMESPACE
            )
        }
    }
}
