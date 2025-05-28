package id.walt.policies.policies.status.entry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


//todo: maybe use waltid-credential-status domain?

@Serializable
data class W3CEntry(
    val id: String,
    val type: String,
    val statusPurpose: String,
    val statusListIndex: String,
    val statusSize: Int,
    val statusListCredential: String,
    val statusMessage: List<Status>? = null,
    val statusReference: String? = null,
) {
    @Serializable
    data class Status(
        @SerialName("status")
        val type: String,
        val message: String,
    )
}

@Serializable
data class IETFEntry(
    @SerialName("status_list")
    val statusList: StatusListField,
) {

    @Serializable
    data class StatusListField(
        @SerialName("idx")
        val index: Int,
        val uri: String,
    )
}