package id.walt.policies.policies

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.model.*
import id.walt.policies.policies.status.parser.JsonElementParser
import id.walt.policies.policies.status.parser.JwtParser
import id.walt.policies.policies.status.reader.IETFJwtStatusValueReader
import id.walt.policies.policies.status.reader.W3CStatusValueReader
import id.walt.policies.policies.status.validator.IETFStatusValidator
import id.walt.policies.policies.status.validator.W3CStatusValidator
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking


@Serializable
actual class StatusPolicy : StatusPolicyMp() {
    //todo: inject through constructor (not allowed by interface atm - needs serializable)
    @Transient
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    @Transient
    private val credentialFetcher = CredentialFetcher(httpClient)
    @Transient
    private val w3cEntryParser = JsonElementParser(serializer<W3CEntry>())
    @Transient
    private val w3cListEntryParser = JsonElementParser(serializer<List<W3CEntry>>())
    @Transient
    private val ietfEntryParser = JsonElementParser(serializer<IETFEntry>())
    @Transient
    private val jwtParser = JwtParser()
    @Transient
    private val w3cStatusReader = W3CStatusValueReader(jwtParser)
    @Transient
    private val ietfJwtStatusReader = IETFJwtStatusValueReader(jwtParser)
    @Transient
    private val bitValueReaderFactory = BitValueReaderFactory()
    @Transient
    private val w3cStatusValidator = W3CStatusValidator(credentialFetcher, w3cStatusReader, bitValueReaderFactory)
    @Transient
    private val ietfStatusValidator = IETFStatusValidator(credentialFetcher, ietfJwtStatusReader, bitValueReaderFactory)
    //endtodo

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        requireNotNull(args) { "args required" }
        require(args is StatusPolicyArgument) { "args must be a CredentialStatusPolicyArgument" }
        val statusElement = data.getStatusEntryElement(args)
        //todo: [RevocationPolicy] passes when no entry found - should this follow the same pattern?
        requireNotNull(statusElement) { "Corresponding status entry not found" }
        val result = processStatusEntry(statusElement, args)
        return result
    }

    private suspend fun processStatusEntry(data: JsonElement, args: StatusPolicyArgument) = when (args) {
        is IETFStatusPolicyAttribute -> processIETF(data, args)
        is W3CStatusPolicyAttribute -> processW3C(data, args)
        is W3CStatusPolicyListArguments -> processListW3C(data, args)
    }

    private suspend fun processW3C(data: JsonElement, attribute: W3CStatusPolicyAttribute): Result<Unit> {
        val statusEntry = w3cEntryParser.parse(data)
        return w3cStatusValidator.validate(statusEntry, attribute)
    }

    private suspend fun processListW3C(data: JsonElement, attribute: W3CStatusPolicyListArguments): Result<Unit> =
        runCatching {
            val statusEntries = w3cListEntryParser.parse(data)
            //check the arguments list is less or equal to entry list
            require(statusEntries.size >= attribute.list.size) { "Arguments list size mismatch: ${statusEntries.size} expecting ${attribute.list.size}" }
            //match each argument with its corresponding entry
            val sortedEntries = statusEntries.sortedBy { it.purpose }
            val sortedAttributes = attribute.list.sortedBy { it.purpose }
            val validationResults: List<Result<Unit>> = sortedEntries.zip(sortedAttributes) { entry, attr ->
                w3cStatusValidator.validate(entry, attr)
            }
            if (validationResults.isEmpty()) {
                throw IllegalArgumentException(emptyResultMessage(attribute))
            }
            if (validationResults.any { it.isFailure }) {
                throw IllegalArgumentException(failResultMessage(validationResults))
            }
        }

    private fun failResultMessage(validationResults: List<Result<Unit>>) =
        // Find the first failure to include its message in the error.
        validationResults.filter { it.isFailure }.map { it.exceptionOrNull()?.message ?: "Unknown validation error" }
            .let {
                "Verification failed: ${it.joinToString(System.lineSeparator())}"
            }

    private fun emptyResultMessage(attribute: W3CStatusPolicyListArguments) = if (attribute.list.isEmpty()) {
        "Verification failed: Attribute list is empty."
    } else {
        // This implies attribute.list was not empty, but statusEntries might have been empty or shorter.
        "Verification failed: No matching status entries found for attributes, or status entries list is empty/shorter."
    }

    private suspend fun processIETF(data: JsonElement, attribute: IETFStatusPolicyAttribute): Result<Unit> {
        val statusEntry = ietfEntryParser.parse(data)
        return ietfStatusValidator.validate(statusEntry, attribute)
    }

    private fun JsonObject.getStatusEntryElement(args: StatusPolicyArgument) = when (args) {
        is IETFStatusPolicyAttribute -> this["status"]
        is W3CStatusPolicyAttribute, is W3CStatusPolicyListArguments -> this["vc"]?.jsonObject?.get("credentialStatus")
    }
}