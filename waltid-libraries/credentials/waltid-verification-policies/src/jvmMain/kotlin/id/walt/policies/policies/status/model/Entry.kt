package id.walt.policies.policies.status.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


//todo: maybe use waltid-credential-status domain?
sealed class StatusEntry {
    abstract val index: ULong
    abstract val uri: String
}

@Serializable
data class W3CEntry(
    val id: String? = null,
    val type: String,
    @SerialName("statusPurpose")
    val purpose: String,
    @SerialName("statusListIndex")
    override val index: ULong,
    @SerialName("statusSize")
    val size: Int,
    @SerialName("statusListCredential")
    override val uri: String,
    @SerialName("statusMessage")
    val statuses: List<Status>? = null,
    @SerialName("statusReference")
    val reference: String? = null,
) : StatusEntry() {
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
) : StatusEntry() {
    override val index: ULong = statusList.index
    override val uri: String = statusList.uri

    @Serializable
    data class StatusListField(
        @SerialName("idx")
        val index: ULong,
        val uri: String,
    )
}