package id.walt.policies.policies

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.Values
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.content.JsonElementParser
import id.walt.policies.policies.status.content.JwtParser
import id.walt.policies.policies.status.entry.W3CEntryExtractor
import id.walt.policies.policies.status.model.W3CEntry
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
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
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


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
    private val w3cEntryExtractor = W3CEntryExtractor()
    @Transient
    private val entryContentParser = JsonElementParser(kotlinx.serialization.serializer<W3CEntry>())
    @Transient
    private val validator = W3CStatusValidator(
        fetcher = CredentialFetcher(
            client = httpClient
        ),
        reader = W3CStatusValueReader(
            parser = JwtParser()
        ),
        bitValueReaderFactory = BitValueReaderFactory()
    )
    //endtodo

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val statusElement = w3cEntryExtractor.extract(data)
            ?: return Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))
        val attribute = W3CStatusPolicyAttribute(
            value = 0u,
            purpose = "revocation",
            type = Values.STATUS_LIST_2021
        )
        val statusEntry = entryContentParser.parse(statusElement)
        return validator.validate(statusEntry, attribute)
    }
}