package id.walt.wallet2.consent

import id.walt.sdjwt.metadata.type.MetadataIntegrity
import id.walt.sdjwt.metadata.type.TypeMetadataException
import id.walt.sdjwt.metadata.type.TypeMetadataFailure
import id.walt.webdatafetching.BoundedDocumentSession
import id.walt.webdatafetching.config.RequestConfiguration
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** One immutable retrieval snapshot per consent review. */
internal class PaymentMetadataFetchSession(
    httpClient: HttpClient,
    requestConfiguration: RequestConfiguration? = null,
    requireUrlAllowed: (String) -> Unit = {},
) : AutoCloseable {
    private val documents = BoundedDocumentSession(httpClient, requestConfiguration, requireUrlAllowed)

    suspend fun readBytes(uri: String): ByteArray = try {
        documents.read(uri)
    } catch (cause: CancellationException) { throw cause
    } catch (_: Exception) { consentFailure(PaymentConsentFailure.METADATA_UNAVAILABLE) }

    suspend fun read(uri: String, integrity: List<String> = emptyList()): JsonElement {
        val bytes = readBytes(uri)
        integrity.forEach { verifyMetadataIntegrity(bytes, it) }
        return try { Json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (_: Exception) { consentFailure(PaymentConsentFailure.INVALID_METADATA) }
    }

    override fun close() = documents.close()
}

internal fun verifyMetadataIntegrity(bytes: ByteArray, reference: String) {
    try { MetadataIntegrity.verify(bytes, reference)
    } catch (cause: TypeMetadataException) {
        consentFailure(if (cause.reason == TypeMetadataFailure.INTEGRITY_MISMATCH)
            PaymentConsentFailure.INTEGRITY_MISMATCH else PaymentConsentFailure.INVALID_METADATA)
    }
}
