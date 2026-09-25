package id.walt.wallet2.consent

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.time.Clock

/** Independently configured issuer key, never learned from a credential header or its type metadata. */
class PaymentCredentialIssuer(
    val issuer: String,
    val publicJwkJson: String,
    val algorithm: JwsAlgorithm = JwsAlgorithm.ES256,
) {
    init {
        require(issuer.isNotBlank())
        val jwk = Json.parseToJsonElement(publicJwkJson).jsonObject
        require(jwk.keys.none { it in setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k") }) {
            "Payment issuer trust requires a public asymmetric verification key"
        }
    }
}

/** Authenticates the selected attestation before following its type reference. No persistent cache. */
class PaymentConsentResolver(
    trustedIssuers: List<PaymentCredentialIssuer>,
    httpClient: HttpClient? = null,
) {
    private val httpClient = httpClient ?: defaultPaymentMetadataFetcher.httpClient
    private val trustedIssuers = trustedIssuers.toList()
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    suspend fun resolve(
        credential: SdJwtCredential,
        payload: JsonObject,
        preferredLocales: List<String>,
    ): PaymentConsent {
        val authenticated = authenticate(credential)
        if ("transaction_data_types['$TS12_PAYMENT_TYPE'].schema_uri#integrity" in authenticated) {
            consentFailure(PaymentConsentFailure.UNSUPPORTED_SCHEMA)
        }
        val vct = authenticated.string("vct", PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
        val values = paymentValues(payload)
        PaymentMetadataFetchSession(httpClient, requireUrlAllowed = { url ->
            defaultPaymentMetadataFetcher.dataFetcherConfiguration.url?.requireUrlAllowed(url)
        }).use { session ->
            val metadata = session.read(vct, authenticated.integrity("vct#integrity")) as? JsonObject
                ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            if (metadata["vct"] != JsonPrimitive(vct)) consentFailure(PaymentConsentFailure.INVALID_METADATA)
            if (metadata["category"] != JsonPrimitive(SCA_ATTESTATION_CATEGORY)) {
                consentFailure(PaymentConsentFailure.INVALID_METADATA)
            }
            // Inheritance and top-level schema constraints need a separate, complete implementation.
            if (metadata.keys.any { it in setOf("extends", "extends#integrity", "schema", "schema_uri", "schema_uri#integrity") }) {
                consentFailure(PaymentConsentFailure.UNSUPPORTED_SCHEMA)
            }
            val types = metadata["transaction_data_types"] as? JsonObject
                ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            val type = types[TS12_PAYMENT_TYPE] as? JsonObject
                ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            val sources = paymentMetadataSources(type)
            suspend fun resolveSource(source: PaymentMetadataSource, name: String): JsonElement {
                val credentialIntegrity = authenticated.integrity("transaction_data_types['$TS12_PAYMENT_TYPE'].${name}_uri#integrity")
                return when (source) {
                    is PaymentMetadataSource.Inline -> {
                        if (credentialIntegrity.isNotEmpty()) consentFailure(PaymentConsentFailure.INVALID_METADATA)
                        source.value
                    }
                    is PaymentMetadataSource.Reference -> session.read(
                        source.uri, credentialIntegrity + listOfNotNull(source.integrity),
                    )
                }
            }
            return localizePaymentConsent(
                resolveSource(sources.claims, "claims"), resolveSource(sources.catalogue, "ui_labels"), values, preferredLocales,
            )
        }
    }

    private suspend fun authenticate(stored: SdJwtCredential): JsonObject = try {
        val wire = stored.signedWithDisclosures ?: consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
        val jwt = wire.substringBefore('~')
        val decoded = CompactJws.decodeUnverified(jwt)
        val untrusted = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
        val issuer = untrusted.string("iss", PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
        val candidates = trustedIssuers.filter { it.issuer == issuer && decoded.protectedHeader["alg"] == JsonPrimitive(it.algorithm.identifier) }
        var verified = false
        for (candidate in candidates) {
            val key = runtime.restore(EncodedKey.Jwk(
                data = BinaryData(candidate.publicJwkJson.encodeToByteArray()), privateMaterial = false,
            ).toStoredSoftwareKey(KeyId("payment-issuer"), setOf(KeyUsage.VERIFY)))
            try {
                CompactJws.verify(jwt, key, candidate.algorithm)
                verified = true
                break
            } catch (cause: CancellationException) { throw cause } catch (_: Exception) { /* Try independently configured rollover keys. */ }
        }
        if (!verified) consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
        // Reparse the verified wire representation to validate disclosures and bind stored matching data.
        val parsed = CredentialParser.detectAndParse(wire).second as? SdJwtCredential
            ?: consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
        if (parsed.credentialData != stored.credentialData || parsed.signed != stored.signed) {
            consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
        }
        val now = Clock.System.now().epochSeconds
        val claims = parsed.credentialData
        for (name in listOf("exp", "nbf")) if (name in claims) {
            val number = claims[name] as? JsonPrimitive ?: consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
            val seconds = number.takeUnless { it.isString }?.longOrNull ?: consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
            if ((name == "exp" && now >= seconds) || (name == "nbf" && now < seconds)) {
                consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL)
            }
        }
        claims
    } catch (cause: CancellationException) { throw cause
    } catch (cause: PaymentConsentException) { throw cause
    } catch (_: Exception) { consentFailure(PaymentConsentFailure.UNTRUSTED_CREDENTIAL) }
}

private fun JsonObject.integrity(name: String): List<String> =
    if (name in this) listOf(string(name, PaymentConsentFailure.INVALID_METADATA)) else emptyList()

private val defaultPaymentMetadataFetcher by lazy {
    WebDataFetcher(WebDataFetcherId.WALLET2_PAYMENT_METADATA)
}
