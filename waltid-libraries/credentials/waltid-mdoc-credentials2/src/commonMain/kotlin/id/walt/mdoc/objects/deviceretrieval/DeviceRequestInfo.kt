package id.walt.mdoc.objects.deviceretrieval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the DeviceRequestInfo CDDL structure.
 * CDDL: DeviceRequestInfo = { ? "useCases" : [+UseCase], * tstr => any }
 */
@Serializable
data class DeviceRequestInfo(
    @SerialName("useCases")
    val useCases: List<UseCase>? = null

    // Note: The spec allows extension fields (* tstr => any).
    // Proprietary extensions would be added here
)

/**
 * Represents the UseCase CDDL structure.
 * CDDL: UseCase = { "mandatory": boolean, ? "purposeHints": {+ Type => any}, "documentSets": [+ DocumentSet] }
 */
@Serializable
data class UseCase(
    @SerialName("mandatory")
    val mandatory: Boolean,

    // The spec defines the value as 'any'. In practice, these are usually strings or specific
    // structured data. We use String here, but it could be replaced with a custom @Serializable
    // class
    @SerialName("purposeHints")
    val purposeHints: Map<Int, String>? = null,

    // DocumentSet is defined as [+ DocRequestID] where DocRequestID = uint.
    // Thus, [+ DocumentSet] becomes a List of Lists of UInts.
    @SerialName("documentSets")
    val documentSets: List<List<UInt>>
)
