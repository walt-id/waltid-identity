package id.walt.policies2.vc.policies.status

import id.walt.policies2.vc.policies.status.bit.BitValueReaderFactory
import id.walt.policies2.vc.policies.status.content.JsonElementParser
import id.walt.policies2.vc.policies.status.content.JwtParser
import id.walt.policies2.vc.policies.status.entry.IETFEntryExtractor
import id.walt.policies2.vc.policies.status.entry.MDocEntryExtractor
import id.walt.policies2.vc.policies.status.entry.W3CEntryExtractor
import id.walt.policies2.vc.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies2.vc.policies.status.expansion.W3cStatusListExpansionAlgorithmFactory
import id.walt.policies2.vc.policies.status.model.*
import id.walt.policies2.vc.policies.status.reader.IETFJwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.W3CStatusValueReader
import id.walt.policies2.vc.policies.status.validator.IETFStatusValidator
import id.walt.policies2.vc.policies.status.validator.W3CStatusValidator
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

object StatusPolicyImplementation {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val credentialFetcher = CredentialFetcher(httpClient)

    private val w3cEntryExtractor = W3CEntryExtractor()

    private val mdocEntryExtractor = MDocEntryExtractor()

    private val ietfEntryExtractor = IETFEntryExtractor(mdocExtractor = mdocEntryExtractor)

    private val w3cEntryContentParser =
        JsonElementParser(serializer<W3CEntry>())

    private val w3cListEntryContentParser =
        JsonElementParser(serializer<List<W3CEntry>>())

    private val ietfEntryContentParser =
        JsonElementParser(serializer<IETFEntry>())

    private val jwtParser = JwtParser()

    private val w3cStatusReader = W3CStatusValueReader(jwtParser)

    private val ietfJwtStatusReader = IETFJwtStatusValueReader(jwtParser)

    private val bitValueReaderFactory = BitValueReaderFactory()

    private val base64UrlHandler = Base64UrlHandler()

    private val statusListExpansionAlgorithmFactory =
        W3cStatusListExpansionAlgorithmFactory(base64UrlHandler)

    private val w3cStatusValidator = W3CStatusValidator(
        credentialFetcher, w3cStatusReader, bitValueReaderFactory, statusListExpansionAlgorithmFactory
    )

    private val tokenStatusListExpansionAlgorithm =
        TokenStatusListExpansionAlgorithm(base64UrlHandler)

    private val ietfStatusValidator = IETFStatusValidator(
        credentialFetcher, ietfJwtStatusReader, bitValueReaderFactory, tokenStatusListExpansionAlgorithm
    )

    suspend fun verifyWithAttributes(
        data: JsonObject,
        attributes: StatusPolicyArgument
    ): Result<JsonElement> =
        getStatusEntryElementExtractor(attributes).extract(data)?.let { processStatusEntry(it, attributes) }
            ?: Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

    private suspend fun processStatusEntry(data: JsonElement, args: StatusPolicyArgument) =
        when (args) {
            is IETFStatusPolicyAttribute -> processIETF(data, args)
            is W3CStatusPolicyAttribute -> processW3C(data, args)
            is W3CStatusPolicyListArguments -> processListW3C(data, args)
        }.fold(onSuccess = {
            Result.success(JsonObject(emptyMap()))
        }, onFailure = {
            Result.failure(it)
        })

    private suspend fun processW3C(
        data: JsonElement,
        attribute: W3CStatusPolicyAttribute
    ): Result<Unit> {
        val statusEntry = w3cEntryContentParser.parse(data)
        return w3cStatusValidator.validate(statusEntry, attribute)
    }

    private suspend fun processListW3C(
        data: JsonElement,
        attribute: W3CStatusPolicyListArguments
    ): Result<Unit> =
        runCatching {
            val statusEntries = w3cListEntryContentParser.parse(data)
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
        validationResults.filter { it.isFailure }.map { it.exceptionOrNull()?.message ?: "Unknown validation error" }
            .let {
                "Verification failed: ${it.joinToString("\n")}"
            }

    private fun emptyResultMessage(attribute: W3CStatusPolicyListArguments) =
        if (attribute.list.isEmpty()) {
            "Verification failed: Attribute list is empty."
        } else {
            "Verification failed: No matching status entries found for attributes, or status entries list is empty/shorter."
        }

    private suspend fun processIETF(
        data: JsonElement,
        attribute: IETFStatusPolicyAttribute
    ): Result<Unit> {
        val statusEntry = ietfEntryContentParser.parse(data)
        return ietfStatusValidator.validate(statusEntry, attribute)
    }

    private fun getStatusEntryElementExtractor(args: StatusPolicyArgument) = when (args) {
        is IETFStatusPolicyAttribute -> ietfEntryExtractor
        is W3CStatusPolicyAttribute, is W3CStatusPolicyListArguments -> w3cEntryExtractor
    }
}
