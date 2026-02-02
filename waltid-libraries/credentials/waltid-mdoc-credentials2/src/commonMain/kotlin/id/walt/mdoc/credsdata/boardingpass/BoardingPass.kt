@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.credsdata

import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Data model for the IATA Boarding Pass mDOC as per IATA specifications.
 * DocType: org.iata.boardingpass.1
 * Namespace: org.iata.boardingpass.1
 */
@Serializable
data class BoardingPass(
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
    val scheduledDepartureTime: String,

    /** Array of flight segments for this boarding pass */
    @SerialName("pax_segments")
    val paxSegments: List<BoardingPassSegment>

) : MdocData {

    companion object : MdocCompanion {
        const val DOC_TYPE = "org.iata.boardingpass.1"
        const val NAMESPACE = "org.iata.boardingpass.1"

        override fun registerSerializationTypes() {
            val localDate = LocalDate.serializer()
            val segmentList = ListSerializer(BoardingPassSegment.serializer())

            MdocsCborSerializer.register(
                mapOf(
                    "flightIdentifierDate" to localDate,
                    "pax_segments" to segmentList
                ),
                NAMESPACE
            )
        }
    }
}

/**
 * Represents a single flight segment within a boarding pass.
 */
@Serializable
data class BoardingPassSegment(
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
)
