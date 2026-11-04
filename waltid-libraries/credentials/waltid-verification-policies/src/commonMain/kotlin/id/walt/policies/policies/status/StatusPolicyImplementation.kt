package id.walt.policies.policies.status

import id.walt.policies.policies.Base64UrlHandler
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.content.CwtParser
import id.walt.policies.policies.status.content.JsonElementParser
import id.walt.policies.policies.status.content.JwtParser
import id.walt.policies.policies.status.entry.IETFEntryExtractor
import id.walt.policies.policies.status.entry.MDocEntryExtractor
import id.walt.policies.policies.status.entry.W3CEntryExtractor
import id.walt.policies.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.W3cStatusListExpansionAlgorithmFactory
import id.walt.policies.policies.status.model.*
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.mdoc.doc.MDoc
import id.walt.policies.policies.status.reader.IETFCwtStatusValueReader
import id.walt.policies.policies.status.reader.IETFJwtStatusValueReader
import id.walt.policies.policies.status.reader.W3CStatusValueReader
import id.walt.policies.policies.status.signature.RAW_CREDENTIAL_CONTEXT_KEY
import id.walt.policies.policies.status.signature.STATUS_LIST_SIGNER_AUTHORIZER_CONTEXT_KEY
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.policies.policies.status.reader.format.CwtFormatMatcher
import id.walt.policies.policies.status.reader.format.JwtFormatMatcher
import id.walt.policies.policies.status.validator.IETFStatusValidator
import id.walt.policies.policies.status.validator.W3CStatusValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

object StatusPolicyImplementation {
    private val logger = KotlinLogging.logger {}

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val credentialFetcher = CredentialFetcher(httpClient)

    private val w3cEntryExtractor = W3CEntryExtractor()

    private val mdocEntryExtractor = MDocEntryExtractor()

    private val ietfEntryExtractor = IETFEntryExtractor(mdocExtractor = mdocEntryExtractor)

    private val w3cEntryContentParser = JsonElementParser(serializer<W3CEntry>())

    private val w3cListEntryContentParser = JsonElementParser(serializer<List<W3CEntry>>())

    private val ietfEntryContentParser = JsonElementParser(serializer<IETFEntry>())

    private val cwtParser = CwtParser()

    private val jwtParser = JwtParser()

    private val jwtFormatMatcher = JwtFormatMatcher()

    private val cwtFormatMatcher = CwtFormatMatcher()

    private val w3cStatusReader = W3CStatusValueReader(jwtFormatMatcher, jwtParser)

    private val ietfJwtStatusReader = IETFJwtStatusValueReader(jwtFormatMatcher, jwtParser)

    private val ietfCwtStatusReader = IETFCwtStatusValueReader(cwtFormatMatcher, cwtParser)

    private val bitValueReaderFactory = BitValueReaderFactory()

    private val base64UrlHandler = Base64UrlHandler()

    private val statusListExpansionAlgorithmFactory = W3cStatusListExpansionAlgorithmFactory(base64UrlHandler)

    private val w3cStatusValidator = W3CStatusValidator(
        credentialFetcher, w3cStatusReader, bitValueReaderFactory, statusListExpansionAlgorithmFactory
    )

    private val tokenStatusListExpansionAlgorithm = TokenStatusListExpansionAlgorithm()

    private val ietfStatusValidator = IETFStatusValidator(
        fetcher = credentialFetcher,
        bitValueReaderFactory = bitValueReaderFactory,
        expansionAlgorithm = tokenStatusListExpansionAlgorithm,
        ietfJwtStatusReader,
        ietfCwtStatusReader
    )

    suspend fun verifyWithAttributes(
        data: JsonObject,
        attributes: StatusPolicyArgument,
        context: Map<String, Any> = emptyMap(),
    ): Result<Any> =
        getStatusEntryElementExtractor(attributes).extract(data)?.let { processStatusEntry(it, attributes, data, context) }
            ?: Result.success(JsonObject(mapOf("policy_available" to JsonPrimitive(false))))

    private suspend fun processStatusEntry(
        data: JsonElement,
        args: StatusPolicyArgument,
        credential: JsonObject,
        context: Map<String, Any>,
    ) = when (args) {
        is IETFStatusPolicyAttribute -> processIETF(data, args, credential, context)
        is W3CStatusPolicyAttribute -> processW3C(data, args)
        is W3CStatusPolicyListArguments -> processListW3C(data, args)
    }

    private suspend fun processW3C(data: JsonElement, attribute: W3CStatusPolicyAttribute): Result<Unit> {
        val statusEntry = w3cEntryContentParser.parse(data)
        return w3cStatusValidator.validate(statusEntry, attribute)
    }

    private suspend fun processListW3C(data: JsonElement, attribute: W3CStatusPolicyListArguments): Result<Unit> =
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
            require(validationResults.isNotEmpty()) { emptyResultMessage(attribute) }
            require(validationResults.none { it.isFailure }) { failResultMessage(validationResults) }
        }

    private fun failResultMessage(validationResults: List<Result<Unit>>) =
        validationResults.filter { it.isFailure }.map { it.exceptionOrNull()?.message ?: "Unknown validation error" }
            .let {
                "Verification failed: ${it.joinToString("\n")}"
            }

    private fun emptyResultMessage(attribute: W3CStatusPolicyListArguments) = if (attribute.list.isEmpty()) {
        "Verification failed: Attribute list is empty."
    } else {
        "Verification failed: No matching status entries found for attributes, or status entries list is empty/shorter."
    }

    private suspend fun processIETF(
        data: JsonElement,
        attribute: IETFStatusPolicyAttribute,
        credential: JsonObject,
        context: Map<String, Any>,
    ): Result<Unit> = try {
        val statusEntry = ietfEntryContentParser.parse(data)
        ietfStatusValidator.validate(
            entry = statusEntry,
            attribute = attribute,
            referencedCredential = referencedCredential(credential, context),
            signerAuthorizer = context[STATUS_LIST_SIGNER_AUTHORIZER_CONTEXT_KEY] as? StatusListSignerAuthorizer,
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

    private suspend fun referencedCredential(credential: JsonObject, context: Map<String, Any>): DigitalCredential {
        val raw = context[RAW_CREDENTIAL_CONTEXT_KEY] as? String
        if (!raw.isNullOrBlank()) {
            try {
                return CredentialParser.parseOnly(raw)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                logger.debug { "Could not parse raw credential for status-list signer authorization: ${cause.message}" }
            }
        }
        credential["mdoc"]?.jsonPrimitive?.contentOrNull?.let { hex ->
            mdocReferencedCredential(hex)?.let { return it }
        }
        val issuer = referencedIssuer(credential)
            ?: throw StatusVerificationError("Referenced credential is required for status-list signer authorization")
        return W3C11(
            credentialData = credential,
            signature = null,
            signed = null,
            issuer = issuer,
        )
    }

    private fun mdocReferencedCredential(hex: String): DigitalCredential? {
        val chain = runCatching { MDoc.fromCBORHex(hex).issuerSigned.issuerAuth?.x5Chain }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return W3C11(
            credentialData = buildJsonObject { },
            signature = JwtCredentialSignature(
                signature = "mdoc-issuer-auth",
                jwtHeader = buildJsonObject {
                    put("x5c", JsonArray(chain.map { JsonPrimitive(it.encodeToBase64()) }))
                },
            ),
            signed = null,
            issuer = null,
        )
    }

    private fun referencedIssuer(credential: JsonObject): String? {
        credential["iss"]?.jsonPrimitive?.contentOrNull?.let { return it }
        credential["issuer"]?.let { issuer ->
            return when (issuer) {
                is JsonObject -> issuer["id"]?.jsonPrimitive?.contentOrNull
                else -> issuer.jsonPrimitive.contentOrNull
            }
        }
        return credential["vc"]?.jsonObject?.let(::referencedIssuer)
    }

    private fun getStatusEntryElementExtractor(args: StatusPolicyArgument) = when (args) {
        is IETFStatusPolicyAttribute -> ietfEntryExtractor
        is W3CStatusPolicyAttribute, is W3CStatusPolicyListArguments -> w3cEntryExtractor
    }
}