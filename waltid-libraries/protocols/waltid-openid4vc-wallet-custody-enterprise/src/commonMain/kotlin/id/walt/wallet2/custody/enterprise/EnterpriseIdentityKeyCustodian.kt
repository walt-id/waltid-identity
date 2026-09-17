package id.walt.wallet2.custody.enterprise

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.serialization.BinaryData
import id.walt.wallet2.mobile.identity.*
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*

/** Optional Enterprise KMS import integration. It gives the KMS private-key custody; it does not
 * preserve a portable recovery record, synchronize credentials, or configure remote signing.
 *
 * Supply an authenticated client without request/response body logging. This adapter disables
 * redirects and closes only its configured client when [close] is called.
 * @param httpClient Host-owned authentication, TLS and timeout configuration.
 * @param kmsResourceUrl HTTPS KMS resource URL, e.g. `https://enterprise.example/v1/org.kms`.
 * @property id Stable integration identifier.
 * @property displayName Destination label presented before explicit selection. */
public class EnterpriseIdentityKeyCustodian(
    httpClient: HttpClient,
    private val kmsResourceUrl: Url,
    override val id: String = "enterprise-kms",
    override val displayName: String = "Enterprise KMS",
) : IdentityKeyCustodian, AutoCloseable {
    init {
        require(id.isNotBlank() && displayName.isNotBlank())
        require(kmsResourceUrl.protocol == URLProtocol.HTTPS && kmsResourceUrl.user == null && kmsResourceUrl.password == null)
        require(kmsResourceUrl.parameters.isEmpty() && kmsResourceUrl.fragment.isEmpty())
        require(kmsResourceUrl.encodedPath.trim('/').isNotEmpty() && !kmsResourceUrl.encodedPath.endsWith('/'))
    }

    private val client = httpClient.config { followRedirects = false; expectSuccess = false }

    /** Imports under the stable logical key ID and verifies the destination public key.
     * @param identity Original identity whose signing key is copied.
     * @param privateKey Original P-256 private JWK; never included in receipts or error messages. */
    override suspend fun importKey(identity: SigningIdentity, privateKey: EncodedKey.Jwk): IdentityCustodyReceipt {
        require(identity.keyId.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "Unsupported KMS key resource identifier" }
        val resource = "$kmsResourceUrl.${identity.keyId}"
        val privateBytes = privateKey.data.toByteArray()
        try {
            return client.preparePost("$resource/kms-service-api/keys/import/jwk") {
                setBody(TextContent(privateBytes.decodeToString(), ContentType.Application.Json))
            }.execute { response ->
                when (response.status.value) {
                    200, 201 -> Unit
                    401 -> throw IdentityProviderException(IdentityProviderFailure.InteractionRequired)
                    403 -> throw IdentityProviderException(IdentityProviderFailure.Rejected)
                    409 -> throw IdentityProviderException(IdentityProviderFailure.Conflict)
                    429, in 500..599 -> throw IdentityProviderException(IdentityProviderFailure.TemporarilyUnavailable)
                    else -> throw IdentityProviderException(IdentityProviderFailure.Rejected)
                }
                val bytes = response.bodyAsChannel().readRemaining(65_537).readByteArray()
                try {
                    if (bytes.size > 65_536) throw IdentityProviderException(IdentityProviderFailure.Rejected)
                    val view = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
                    val jwk = view.getValue("key").jsonObject.getValue("jwk").jsonObject
                    // Enterprise local-JWK views can contain private material. Return only public members.
                    val publicJwk = buildJsonObject {
                        for (name in listOf("kty", "crv", "x", "y")) put(name, jwk.getValue(name))
                    }.toString()
                    val observed = EncodedKey.Jwk(BinaryData(publicJwk.encodeToByteArray()), false)
                    if (observed.toSpkiDer(KeySpec.Ec(EcCurve.P256)) != privateKey.toSpkiDer(KeySpec.Ec(EcCurve.P256)))
                        throw IdentityProviderException(IdentityProviderFailure.Conflict)
                    IdentityCustodyReceipt(resource, publicJwk)
                } catch (cause: IdentityProviderException) { throw cause }
                catch (_: Exception) { throw IdentityProviderException(IdentityProviderFailure.Rejected) }
                finally { bytes.fill(0) }
            }
        } catch (cause: CancellationException) { throw cause }
        catch (cause: IdentityProviderException) { throw cause }
        catch (_: Exception) { throw IdentityProviderException(IdentityProviderFailure.TemporarilyUnavailable) }
        finally { privateBytes.fill(0) }
    }

    /** Releases this adapter's configured HTTP client; the host retains ownership of its original client. */
    override fun close(): Unit = client.close()
}
