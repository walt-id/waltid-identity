package id.walt.policies.policies

import id.walt.policies.policies.status.*
import id.walt.policies.policies.status.entry.IETFEntry
import id.walt.policies.policies.status.entry.W3CEntry
import id.walt.policies.policies.status.parser.JsonElementParser
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class CredentialStatusPolicy : CredentialStatusPolicyMp() {
    //todo: inject through constructor (not allowed by interface atm - needs serializable)
    @Transient
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    @Transient
    private val credentialFetcher = CredentialFetcher(httpClient)
    //endtodo

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        requireNotNull(args) { "args can't be null" }
        require(args is CredentialStatusPolicyArguments) { "args must be a CredentialStatusPolicyArgument" }
        val statusElement = data.getStatusEntryElement(args)
        //todo: [RevocationPolicy] passes when no entry found - should this follow the same pattern?
        requireNotNull(statusElement) { "Corresponding status entry not found" }
        val parser = getStatusEntryParser(args)
        val entry = parser.parse(statusElement)
        /*
        todo: if w3c list arguments provided,
        1. check the arguments list is less or equal to entry list
        2. match each argument with its corresponding entry
         */
        TODO("Not yet implemented")
    }

    private fun getStatusEntryParser(args: CredentialStatusPolicyArguments) = when (args) {
        is IETFStatusPolicyArguments -> JsonElementParser(serializer<IETFEntry>())
        is W3CStatusPolicyArguments -> JsonElementParser(serializer<W3CEntry>())
        is W3CStatusPolicyListArguments -> JsonElementParser(serializer<List<W3CEntry>>())
    }

    private fun JsonObject.getStatusEntryElement(args: CredentialStatusPolicyArguments) = when (args) {
        is IETFStatusPolicyArguments -> this["status"]
        is W3CStatusPolicyArguments, is W3CStatusPolicyListArguments -> this["vc"]?.jsonObject?.get("credentialStatus")
    }
}