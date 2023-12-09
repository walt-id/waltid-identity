package id.walt.web.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
sealed class AccountRequest {
    abstract val name: String?
}

@Serializable
@SerialName("email")
data class EmailAccountRequest(override val name: String? = null, val email: String, val password: String) : AccountRequest()

@Serializable
@SerialName("address")
data class AddressAccountRequest(override val name: String? = null, val address: String, val ecosystem: String) : AccountRequest()

val module = SerializersModule {
    polymorphic(AccountRequest::class) {
        EmailAccountRequest::class
        AddressAccountRequest::class
    }
}

val LoginRequestJson = Json {
    serializersModule = module
    ignoreUnknownKeys = true
}
