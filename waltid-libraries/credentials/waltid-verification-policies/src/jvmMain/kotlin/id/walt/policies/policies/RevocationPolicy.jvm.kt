package id.walt.policies.policies

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.W3CStatusPolicyArguments
import id.walt.policies.policies.status.entry.EntryParser
import id.walt.policies.policies.status.entry.W3CEntry
import id.walt.policies.policies.status.parser.JwtParser
import id.walt.policies.policies.status.reader.W3CStatusValueReader
import id.walt.policies.policies.status.validator.W3CStatusValidator
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import java.util.*


@Serializable
actual class RevocationPolicy : RevocationPolicyMp() {
    //todo: inject through constructor (not allowed by interface atm - needs serializable)
    @Transient
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    @Transient
    private val jsonModule = Json { ignoreUnknownKeys = true }
    @Transient
    private val credentialFetcher = CredentialFetcher(httpClient)
    @Transient
    private val statusReader = W3CStatusValueReader(JwtParser())
    @Transient
    private val validator = W3CStatusValidator(credentialFetcher, statusReader)
    //endtodo

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        // extract status entry from holder credential
        val credentialStatus = data["vc"]?.jsonObject?.get("credentialStatus") ?: return Result.success(
            JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
        )
        // parse status entry
        val entry = EntryParser.objectParser(jsonModule, serializer<W3CEntry>()).parse(credentialStatus)
        val arguments = (args as? W3CStatusPolicyArguments) ?: W3CStatusPolicyArguments(
            purpose = "revocation",
            type = "StatusList2021",
            value = 0u
        )
        return validator.validate(entry, arguments)
    }
}

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
    fun urlDecode(base64Url: String): ByteArray = Base64.getUrlDecoder().decode(base64Url)
}