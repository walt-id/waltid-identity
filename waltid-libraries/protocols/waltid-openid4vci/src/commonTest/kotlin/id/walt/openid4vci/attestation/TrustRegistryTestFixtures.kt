package id.walt.openid4vci.attestation

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.decision.TrustDecisionRequest
import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntity
import id.walt.trust.model.TrustedEntityType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

private val testRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

internal suspend fun generateTestP256Key(id: String): Key = testRuntime.generateSoftwareKey(
    GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
)

/** A wallet provider CA and the attester leaf it issued. The trust decision itself comes from a test evaluator. */
internal class TestAttesterPki(val leafKey: Key, val chainBase64: List<String>) {
    /** The x5c header value, leaf first. */
    val x5c: JsonArray get() = JsonArray(chainBase64.map(::JsonPrimitive))

    companion object {
        suspend fun create(): TestAttesterPki {
            val signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
            val caKey = generateTestP256Key("wallet-provider-ca")
            val leafKey = generateTestP256Key("wallet-provider-attester")
            val ca = X509CertificateUtil.createSelfSignedCertificate(caKey, signatureAlgorithm) {
                subjectDn = "CN=Wallet Provider CA"
                extensionBasicConstraints { cA = true }
                extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
            }
            val leaf = X509CertificateUtil.createCertificate(caKey, ca, signatureAlgorithm) {
                subjectDn = "CN=Wallet Provider Attester"
                subjectPublicKey(leafKey)
                extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
            }
            return TestAttesterPki(leafKey, listOf(leaf, ca).map { Base64.encode(it.encodedDer.toByteArray()) })
        }
    }
}

/** Records every request before answering it, so a failing answer is still recorded. */
internal class RecordingTrustEvaluator(
    private val answer: suspend (TrustDecisionRequest) -> TrustDecision,
) : TrustDecisionEvaluator {
    val requests = mutableListOf<TrustDecisionRequest>()

    override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision {
        requests += request
        return answer(request)
    }
}

/** A registry decision as the trust registry reports it for a listed entity. */
internal fun registryDecision(
    code: TrustDecisionCode = TrustDecisionCode.TRUSTED,
    freshness: FreshnessState = FreshnessState.FRESH,
    authenticity: AuthenticityState = AuthenticityState.AUTHENTICATED,
    entityType: TrustedEntityType = TrustedEntityType.WALLET_PROVIDER,
) = TrustDecision(
    decision = code,
    sourceFreshness = freshness,
    sourceAssurance = SourceAssurance(authenticityState = authenticity, accepted = true),
    matchedEntity = TrustedEntity("wallet-provider", "wallet-provider-lote", entityType, "Wallet Provider"),
)

/** What `HttpTrustDecisionEvaluator` answers when the resolver is unreachable or gives no decision. */
internal val unavailableDecision = TrustDecision(TrustDecisionCode.UNKNOWN, warnings = listOf("Trust resolver is unavailable"))
