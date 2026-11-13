package id.walt.policies2.vc.policies

import io.ktor.client.HttpClient
import id.walt.credentials.formats.DigitalCredential
import id.walt.sdjwt.metadata.type.ResolvedTypeMetadata
import id.walt.sdjwt.metadata.type.TypeMetadataResolver
import id.walt.sdjwt.metadata.type.TypeMetadataReference
import id.walt.webdatafetching.BoundedDocumentSession
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Per-verification snapshot shared by DCQL and credential policies. Never a global trust cache. */
class CredentialTypeMetadataResolver(private val httpClient: HttpClient? = null) {
    private val mutex = Mutex()
    private val results = mutableMapOf<Pair<String, String?>, Result<ResolvedTypeMetadata>>()

    suspend fun resolve(credential: DigitalCredential): Result<ResolvedTypeMetadata> = mutex.withLock {
        val reference = try { TypeMetadataReference.fromCredential(credential.credentialData)
        } catch (cause: Exception) { return@withLock Result.failure(cause) }
        val vct = reference.vct
        val pin = reference.integrity
        val key = vct to pin
        results[key] ?: try {
            val transport = httpClient?.let {
                WebDataFetcher.wrapping(it, WebDataFetcherId.VCT_INTEGRITY_POLICY.name)
            } ?: fetcher
            val session = BoundedDocumentSession(transport)
            val result = session.use { documents ->
                Result.success(TypeMetadataResolver(documents::read).resolve(vct, pin))
            }
            results[key] = result
            result
        } catch (cause: CancellationException) { throw cause
        } catch (cause: Exception) { Result.failure<ResolvedTypeMetadata>(cause).also { results[key] = it } }
    }

    private companion object { val fetcher = WebDataFetcher(WebDataFetcherId.VCT_INTEGRITY_POLICY) }
}
