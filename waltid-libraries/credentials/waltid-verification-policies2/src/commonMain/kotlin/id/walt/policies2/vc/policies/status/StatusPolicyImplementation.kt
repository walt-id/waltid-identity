package id.walt.policies2.vc.policies.status

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.policies2.vc.policies.status.Values
import id.walt.policies2.vc.policies.status.bit.BitValueReaderFactory
import id.walt.policies2.vc.policies.status.content.CwtParser
import id.walt.policies2.vc.policies.status.content.JsonElementParser
import id.walt.policies2.vc.policies.status.content.JwtParser
import id.walt.policies2.vc.policies.status.entry.IETFEntryExtractor
import id.walt.policies2.vc.policies.status.entry.MDocEntryExtractor
import id.walt.policies2.vc.policies.status.entry.W3CEntryExtractor
import id.walt.policies2.vc.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies2.vc.policies.status.expansion.W3cStatusListExpansionAlgorithmFactory
import id.walt.policies2.vc.policies.status.model.*
import id.walt.policies2.vc.policies.status.reader.IETFCwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.IETFJwtStatusValueReader
import id.walt.policies2.vc.policies.status.reader.W3CStatusValueReader
import id.walt.policies2.vc.policies.status.reader.format.CwtFormatMatcher
import id.walt.policies2.vc.policies.status.reader.format.JwtFormatMatcher
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.policies2.vc.policies.status.validator.IETFStatusValidator
import id.walt.policies2.vc.policies.status.validator.W3CStatusValidator
import id.walt.webdatafetching.ssrf.installPrivateNetworkGuard
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

object StatusPolicyImplementation {
    private val httpClient = HttpClient {
        installPrivateNetworkGuard()
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

    private val cwtParser = CwtParser()

    private val jwtFormatMatcher = JwtFormatMatcher()

    private val cwtFormatMatcher = CwtFormatMatcher()

    private val w3cStatusReader = W3CStatusValueReader(jwtFormatMatcher, jwtParser)

    private val ietfJwtStatusReader = IETFJwtStatusValueReader(jwtFormatMatcher, jwtParser)

    private val ietfCwtStatusReader = IETFCwtStatusValueReader(cwtFormatMatcher, cwtParser)

    private val bitValueReaderFactory = BitValueReaderFactory()
    private val statusListSignatureVerifier = StatusListSignatureVerifier()

    private val base64UrlHandler = Base64UrlHandler()

    private val statusListExpansionAlgorithmFactory =
        W3cStatusListExpansionAlgorithmFactory(base64UrlHandler)

    private val w3cStatusValidator = W3CStatusValidator(
        credentialFetcher,
        w3cStatusReader,
        bitValueReaderFactory,
        statusListExpansionAlgorithmFactory,
        statusListSignatureVerifier,
    )

    private val tokenStatusListExpansionAlgorithm =
        TokenStatusListExpansionAlgorithm(base64UrlHandler)

    private val ietfStatusValidator = IETFStatusValidator(
        fetcher = credentialFetcher,
        bitValueReaderFactory = bitValueReaderFactory,
        expansionAlgorithm = tokenStatusListExpansionAlgorithm,
        ietfJwtStatusReader,
        ietfCwtStatusReader,
        signatureVerifier = statusListSignatureVerifier,
    )

    suspend fun verifyWithAttributes(
        data: DigitalCredential,
        attributes: StatusPolicyArgument,
        signerAuthorizer: StatusListSignerAuthorizer? = null,
    ): Result<JsonElement> {
        val statusEntry = try {
            when (attributes) {
                is AutoStatusPolicyAttribute -> return runStatusCheck { verifyAutomatically(data, attributes, signerAuthorizer) }
                    .map { available -> JsonObject(mapOf("policy_available" to JsonPrimitive(available))) }
                is IETFStatusPolicyAttribute -> ietfEntryExtractor.extract(data)
                is W3CStatusPolicyAttribute, is W3CStatusPolicyListArguments -> w3cEntryExtractor.extract(data)
            }
        } catch (error: IllegalArgumentException) {
            return Result.failure(error)
        }
        return statusEntry?.let {
            processStatusEntry(it, attributes, data, signerAuthorizer)
        } ?: Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))
    }

    private suspend fun processStatusEntry(
        data: JsonElement,
        args: StatusPolicyArgument,
        credential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ) =
        when (args) {
            is AutoStatusPolicyAttribute -> error("Automatic status checking resolves arguments before validation")
            is IETFStatusPolicyAttribute -> processIETF(data, args, credential, signerAuthorizer)
            is W3CStatusPolicyAttribute -> processW3C(data, args, credential, signerAuthorizer)
            is W3CStatusPolicyListArguments -> processListW3C(data, args, credential, signerAuthorizer)
        }.fold(onSuccess = {
            Result.success(JsonObject(emptyMap()))
        }, onFailure = {
            Result.failure(it)
        })

    private suspend fun processW3C(
        data: JsonElement,
        attribute: W3CStatusPolicyAttribute,
        credential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ): Result<Unit> = runStatusCheck {
        val statusEntry = w3cEntryContentParser.parse(data)
        w3cStatusValidator.validate(statusEntry, attribute, credential, signerAuthorizer).getOrThrow()
    }

    private suspend fun processListW3C(
        data: JsonElement,
        attribute: W3CStatusPolicyListArguments,
        credential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ): Result<Unit> =
        runStatusCheck {
            val statusEntries = w3cListEntryContentParser.parse(data)
            //check the arguments list is less or equal to entry list
            require(statusEntries.size >= attribute.list.size) { "Arguments list size mismatch: ${statusEntries.size} expecting ${attribute.list.size}" }
            //match each argument with its corresponding entry
            val sortedEntries = statusEntries.sortedBy { it.purpose }
            val sortedAttributes = attribute.list.sortedBy { it.purpose }
            val validationResults: List<Result<Unit>> = sortedEntries.zip(sortedAttributes) { entry, attr ->
                w3cStatusValidator.validate(entry, attr, credential, signerAuthorizer)
            }
            require(validationResults.isNotEmpty()) { emptyResultMessage(attribute) }
            require(validationResults.none { it.isFailure }) { failResultMessage(validationResults) }
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
        attribute: IETFStatusPolicyAttribute,
        credential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ): Result<Unit> = runStatusCheck {
        val statusEntry = ietfEntryContentParser.parse(data)
        ietfStatusValidator.validate(statusEntry, attribute, credential, signerAuthorizer).getOrThrow()
    }

    private suspend fun verifyAutomatically(
        credential: DigitalCredential,
        args: AutoStatusPolicyAttribute,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ): Boolean {
        val ietf = ietfEntryExtractor.extract(credential)
        val w3c = w3cEntryExtractor.extract(credential)
        ietf?.let {
            val declarations = if (credential is MdocsCredential) {
                credential.documentMso.status?.let { status -> Json.encodeToJsonElement(status).jsonObject }
            } else it as? JsonObject
            val mechanisms = declarations?.keys.orEmpty()
            require(mechanisms == setOf("status_list")) {
                "Unsupported credential status mechanisms: ${mechanisms - "status_list"}"
            }
            processIETF(it, IETFStatusPolicyAttribute(args.value, args.values), credential, signerAuthorizer).getOrThrow()
        }
        w3c?.let { status ->
            val entries = if (status is JsonArray) status.toList() else listOf(status)
            require(entries.isNotEmpty()) { "credentialStatus must not be empty" }
            for (entry in entries) {
                val parsed = w3cEntryContentParser.parse(entry)
                val type = when (parsed.type) {
                    Values.BITSTRING_STATUS_LIST_ENTRY -> Values.BITSTRING_STATUS_LIST
                    Values.STATUS_LIST_2021_ENTRY -> Values.STATUS_LIST_2021
                    else -> error("Unsupported credential status type: ${parsed.type}")
                }
                val purposes = when (type) {
                    Values.BITSTRING_STATUS_LIST -> setOf("revocation", "suspension", "refresh", "message")
                    else -> setOf("revocation", "suspension")
                }
                require(parsed.purpose in purposes) {
                    "Unsupported credential status purpose: ${parsed.purpose}"
                }
                w3cStatusValidator.validate(parsed,
                    W3CStatusPolicyAttribute(args.value, args.values, parsed.purpose, type),
                    credential, signerAuthorizer).getOrThrow()
            }
        }
        return ietf != null || w3c != null
    }
}
