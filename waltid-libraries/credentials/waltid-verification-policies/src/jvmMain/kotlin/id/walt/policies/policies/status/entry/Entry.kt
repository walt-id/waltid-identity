package id.walt.policies.policies.status.entry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


//todo: maybe use waltid-credential-status domain?

@Serializable
data class W3CEntry(
    val id: String? = null,
    val type: String,
    @SerialName("statusPurpose")
    val purpose: String,
    @SerialName("statusListIndex")
    val index: ULong,
    @SerialName("statusSize")
    val size: Int,
    @SerialName("statusListCredential")
    val uri: String,
    @SerialName("statusMessage")
    val statuses: List<Status>? = null,
    @SerialName("statusReference")
    val reference: String? = null,
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
        val index: ULong,
        val uri: String,
    )
}