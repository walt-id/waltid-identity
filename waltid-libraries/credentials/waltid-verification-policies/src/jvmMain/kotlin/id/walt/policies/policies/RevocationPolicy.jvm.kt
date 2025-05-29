package id.walt.policies.policies

import id.walt.policies.policies.status.BitValueReader
import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.expansion.StatusList2021ExpansionAlgorithm
import id.walt.policies.policies.status.parser.JwtParser
import id.walt.policies.policies.status.reader.W3CStatusValueReader
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import java.util.*


@Serializable
actual class RevocationPolicy : RevocationPolicyMp() {
    @Transient
    private val logger = KotlinLogging.logger {}
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
    private val bitValueReader = BitValueReader(StatusList2021ExpansionAlgorithm())
    @Transient
    private val statusReader = W3CStatusValueReader(JwtParser(), bitValueReader)
    //endtodo

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val credentialStatus = data["vc"]?.jsonObject?.get("credentialStatus")
            ?: return Result.success(
                JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
            )

        logger.debug { "Credential status: $credentialStatus" }
        val statusListIndex = credentialStatus.jsonObject["statusListIndex"]?.jsonPrimitive?.content?.toULong()!!
        val statusListCredentialUrl = credentialStatus.jsonObject["statusListCredential"]?.jsonPrimitive?.content
        logger.debug { "Status list index: $statusListIndex" }
        logger.debug { "Credential URL: $statusListCredentialUrl" }
        val response = credentialFetcher.fetch(statusListCredentialUrl!!).getOrThrow()
        // response is a jwt
        val bitValue = statusReader.read(response, statusListIndex).getOrElse {
            return Result.failure(Throwable(it.cause))
        }

        checkStatus(bitValue).getOrElse {
            return Result.failure(Throwable("Credential has been revoked"))
        }
        return Result.success(statusListCredentialUrl)
    }

    private fun checkStatus(it: List<Char>) = runCatching {
        require(binToInt(it.joinToString("")) == 0)
    }

    fun binToInt(bin: String) = bin.toInt(2)
}

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
    fun urlDecode(base64Url: String): ByteArray = Base64.getUrlDecoder().decode(base64Url)
}