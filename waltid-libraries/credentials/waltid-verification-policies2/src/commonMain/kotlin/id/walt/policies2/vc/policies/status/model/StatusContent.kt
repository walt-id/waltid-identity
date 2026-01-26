package id.walt.policies2.vc.policies.status.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("status")
sealed class StatusContent {
    abstract val list: String
}

@Serializable
@SerialName("W3CStatusContent")
data class W3CStatusContent(
    val type: String,
    @SerialName("statusPurpose")
    val purpose: String? = "revocation",
    @SerialName("encodedList")
    override val list: String,
) : StatusContent()

@Serializable
@SerialName("IETFStatusContent")
data class IETFStatusContent(
    @SerialName("bits")
    val size: Int = 1,
    @SerialName("lst")
    override val list: String,
) : StatusContent()
