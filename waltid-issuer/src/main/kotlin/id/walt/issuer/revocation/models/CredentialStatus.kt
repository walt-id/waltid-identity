package id.walt.issuer.revocation.models

import com.sksamuel.hoplite.fp.valid
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@OptIn(ExperimentalSerializationApi::class)
@Polymorphic
@Serializable
@JsonClassDiscriminator("type")
abstract class CredentialStatus {
    abstract val type: String
    abstract val id: String

    enum class Types(val value: String) {
        StatusList2021Entry("StatusList2021Entry")
    }
}

@Serializable
@SerialName("StatusList2021Entry")
data class StatusList2021EntryCredentialStatus(
    override val type: String,
    override val id: String,
    val statusPurpose: String,
    val statusListIndex: String,
    val statusListCredential: String,
) : CredentialStatus()

val statusSerializerModule = SerializersModule {
    polymorphic(CredentialStatus::class) {
        subclass(StatusList2021EntryCredentialStatus::class)
    }
}