package id.walt.cli.util

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto2.jose.defaultJwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.did.dids.DidService
import id.walt.policies2.vc.policies.AllowedIssuerPolicy
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.CredentialVerificationPolicy2
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.policies2.vc.policies.JsonSchemaPolicy
import id.walt.policies2.vc.policies.NotBeforePolicy
import id.walt.policies2.vc.policies.RevocationPolicy
import id.walt.policies2.vc.policies.WebhookPolicy
import id.walt.w3c.issuance.Issuer.mergingJwtIssue
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CredentialPolicyRun(
    val policy: CredentialVerificationPolicy2,
    val result: Result<JsonElement>,
)

object VCUtil {
    init {
        runBlocking { DidService.minimalInit() }
    }

    suspend fun sign(key: Key, issuerDid: String, subjectDid: String, payload: String): String {
        val vc = W3CVC(Json.decodeFromString<Map<String, JsonElement>>(payload))
        return vc.mergingJwtIssue(
            issuerKey = key,
            algorithm = key.spec.defaultJwsAlgorithm(),
            issuerId = issuerDid,
            subjectDid = subjectDid,
            mappings = JsonObject(emptyMap()),
            additionalJwtHeader = emptyMap(),
            additionalJwtOptions = emptyMap(),
        )
    }

    suspend fun parse(rawCredential: String): DigitalCredential = CredentialParser.parseOnly(rawCredential)

    suspend fun verify(
        rawCredential: String,
        policies: List<CredentialVerificationPolicy2>,
    ): List<CredentialPolicyRun> = verify(parse(rawCredential), policies)

    suspend fun verify(
        credential: DigitalCredential,
        policies: List<CredentialVerificationPolicy2>,
    ): List<CredentialPolicyRun> = policies.map { policy -> CredentialPolicyRun(policy, policy.verify(credential)) }

    fun policies(
        names: List<String>,
        arguments: Map<String, List<String>>,
    ): List<CredentialVerificationPolicy2> = (listOf("signature") + names.ifEmpty { listOf("expired", "not-before") })
        .distinct()
        .map { name ->
            when (name) {
                "signature" -> CredentialSignaturePolicy()
                "expired", "expiration" -> ExpirationDatePolicy()
                "not-before" -> NotBeforePolicy()
                "revoked-status-list" -> RevocationPolicy()
                "schema" -> JsonSchemaPolicy(
                    schema = Json.parseToJsonElement(requiredArgument(arguments, "schema", name)) as? JsonObject
                        ?: throw IllegalArgumentException("Schema must be a JSON object"),
                )
                "allowed-issuer" -> AllowedIssuerPolicy(
                    JsonArray(requiredArguments(arguments, "issuer", name).map(::JsonPrimitive)),
                )
                "webhook" -> WebhookPolicy(requiredArgument(arguments, "url", name))
                else -> throw IllegalArgumentException("Unknown credential policy: $name")
            }
        }

    private fun requiredArgument(arguments: Map<String, List<String>>, key: String, policy: String): String =
        requiredArguments(arguments, key, policy).singleOrNull()
            ?: throw IllegalArgumentException("Policy $policy requires exactly one --arg=$key=value")

    private fun requiredArguments(arguments: Map<String, List<String>>, key: String, policy: String): List<String> =
        arguments[key]?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)
            ?: throw IllegalArgumentException("Policy $policy requires --arg=$key=value")
}
