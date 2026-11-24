package id.walt.crypto2.kms.csc

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.CURVE_SECP256R1_OID
import id.walt.certificate.x509.X509SigningAlgorithmInfo.Companion.KEY_ALG_OID_EC
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.PublicKeyExporter
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.digest
import id.walt.crypto2.kms.requireHttpEndpoint
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Signs through a remote signing service (RSSP) that implements the CSC API v2.2, for example a QTSP's qualified seal.
 *
 * The RSSP provisions the credential, so [generate] is rejected: attach an existing credential with
 * [storedKeyForExisting]. Supported scope: `specs` 2.2.0.0, OAuth 2.0 client-credentials service authorization
 * (`oauth2client`), `explicit` credential authorization with static `Password` objects combined only with `AND`,
 * ECDSA P-256 with SHA-256, synchronous `signatures/signHash`. Every returned signature is verified locally against
 * the leaf certificate's key.
 */
class CscKeyProvider(
    client: HttpClient,
    private val credentialResolver: CscCredentialResolver,
    private val now: () -> Instant = { Clock.System.now() },
) : ManagedKeyProvider {
    override val id: ProviderId = ID

    private val csc = CscClient(client)
    private val tokens = CscAccessTokens(client, now)
    private val publicKeys = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey =
        throw UnsupportedOperationException(
            "The remote signing service provisions CSC credentials; attach one with storedKeyForExisting"
        )

    /**
     * Reads the credential from the RSSP, checks that this provider can use it, and returns its descriptor. The
     * descriptor pins the leaf certificate's public key and keeps the certificate chain.
     */
    suspend fun storedKeyForExisting(
        id: KeyId,
        options: CscOptions,
        credentialID: String,
        usages: Set<KeyUsage> = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        metadata: Map<String, String> = emptyMap(),
    ): StoredKey.Managed {
        require(credentialID.isNotBlank()) { "CSC credential ID cannot be blank" }
        requireSigningUsages(usages)

        // RSSP answers that this provider cannot use are KmsProviderException; caller input and configuration are
        // IllegalArgumentException.
        val info = csc.info(options.serviceUrl)
        requireRssp(info.specs in SUPPORTED_SPECS) { "Unsupported CSC API version: ${info.specs}" }
        requireRssp(CLIENT_CREDENTIALS in info.authTypes) {
            "The CSC service does not offer OAuth 2.0 client credentials ($CLIENT_CREDENTIALS)"
        }
        val tokenEndpoint = options.tokenEndpoint ?: derivedTokenEndpoint(options.serviceUrl, info.oauth2)

        val credentials = credentialResolver.resolve(options.credentialReference)
        val token = tokens.accessToken(tokenEndpoint, options.credentialReference, credentials)
        val credential = try {
            csc.credentialInfo(options.serviceUrl, token, credentialID)
        } catch (cause: KmsProviderException) {
            dropRejectedToken(cause, tokenEndpoint, options.credentialReference, token)
            throw cause
        }
        requireRssp(credential.keyStatus == "enabled") { "CSC credential key is ${credential.keyStatus}" }
        requireRssp(CscClient.ECDSA_SHA256_OID in credential.keyAlgorithms) {
            "CSC credential does not support ECDSA with SHA-256"
        }
        requireRssp(credential.keyCurve == CscClient.P256_OID) { "CSC credential key is not on curve P-256" }
        credential.certificateStatus?.let { requireRssp(it == "valid") { "CSC credential certificate is $it" } }
        requireRssp(credential.authMode == CscClient.EXPLICIT) {
            "Unsupported CSC authorization mode: ${credential.authMode}"
        }
        val authObjects = requiredAuthObjects(credential.authExpression, credential.authObjects)
        authObjects.forEach {
            requireRssp(it.type == "Password" && it.generator == null) {
                "CSC authentication object ${it.id} needs user interaction, which is not supported"
            }
            require(it.id in credentials.authData) { "No stored authorization data for CSC authentication object ${it.id}" }
        }

        val leaf = parseCertificate(credential.certificates.first()) { cause ->
            KmsProviderException("CSC returned an invalid leaf certificate", cause)
        }
        requireRssp(leaf.data.validity.covers(now())) { "CSC leaf certificate is not valid now" }
        val spki = leaf.data.subjectPublicKeyInfo
        requireRssp(spki.algorithmOid == KEY_ALG_OID_EC && spki.ellipticCurveOid == CURVE_SECP256R1_OID) {
            "CSC leaf certificate does not hold a P-256 public key"
        }
        try {
            verifierFor(leaf)
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            throw KmsProviderException("CSC leaf certificate does not hold a usable P-256 public key", cause)
        }
        val publicKey = EncodedKey.SpkiDer(BinaryData(spki.encodedDer.toByteArray()))

        return StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = id,
            spec = SPEC,
            usages = usages,
            provider = ID,
            providerSchemaVersion = PROVIDER_SCHEMA_VERSION,
            providerData = CscStoredKeyData(
                options = options,
                credentialID = credentialID,
                tokenEndpoint = tokenEndpoint,
                authObjectIds = authObjects.map { it.id },
                certificateChain = credential.certificates.map(Base64.Default::encode),
            ).encode(),
            publicKey = publicKey,
            metadata = metadata,
        )
    }

    /** Restores the key from its descriptor without contacting the RSSP. */
    override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
        require(stored.provider == id) { "Stored key belongs to a different provider" }
        require(stored.providerSchemaVersion == PROVIDER_SCHEMA_VERSION) {
            "Unsupported CSC provider schema: ${stored.providerSchemaVersion}"
        }
        require(stored.spec == SPEC) { "CSC keys must be EC P-256" }
        requireSigningUsages(stored.usages)
        val publicKey = stored.publicKey as? EncodedKey.SpkiDer
            ?: throw IllegalArgumentException("Stored CSC key is missing its SPKI public key")
        val data = CscStoredKeyData.decode(stored.providerData)
        val leaf = parseCertificate(data.certificates().first()) { cause ->
            IllegalArgumentException("Stored CSC leaf certificate is invalid", cause)
        }
        require(leaf.data.subjectPublicKeyInfo.encodedDer == ByteString(publicKey.data.toByteArray())) {
            "Stored CSC leaf certificate does not hold the stored public key"
        }
        return CscKey(stored, data, leaf.data.validity, verifierFor(leaf))
    }

    private suspend fun verifierFor(leaf: X509Certificate): Verifier =
        requireNotNull(leaf.restoreSubjectPublicKey(publicKeys).capabilities.verifier) {
            "CSC public key cannot verify signatures"
        }

    /** CSC answers a bad or expired access token with HTTP 401; the next call then fetches a new token. */
    private suspend fun dropRejectedToken(
        failure: KmsProviderException,
        tokenEndpoint: String,
        reference: CredentialReference,
        token: String,
    ) {
        if (failure.httpStatus == HttpStatusCode.Unauthorized.value) tokens.invalidate(tokenEndpoint, reference, token)
    }

    private inner class CscKey(
        override val storedKey: StoredKey.Managed,
        private val data: CscStoredKeyData,
        private val validity: X509Certificate.Validity,
        private val publicKeyVerifier: Verifier,
    ) : ManagedKey {
        override val capabilities = KeyCapabilities(
            signer = KeyUsage.SIGN.takeIf(storedKey.usages::contains)?.let {
                Signer { message, algorithm -> sign(message, algorithm) }
            },
            verifier = KeyUsage.VERIFY.takeIf(storedKey.usages::contains)?.let {
                Verifier { message, signature, algorithm ->
                    require(algorithm in SIGNATURE_ALGORITHMS) { "Unsupported CSC signature algorithm" }
                    publicKeyVerifier.verify(message, signature, algorithm)
                }
            },
            publicKeyExporter = PublicKeyExporter { requireNotNull(storedKey.publicKey) },
            signatureAlgorithms = SIGNATURE_ALGORITHMS,
        )

        private suspend fun sign(message: ByteArray, algorithm: SignatureAlgorithm): ByteArray {
            require(algorithm in SIGNATURE_ALGORITHMS) { "Unsupported CSC signature algorithm" }
            val hash = digest(message, DigestAlgorithm.SHA_256).value.toByteArray()
            // An expired certificate must cost no SAD and no billed signature.
            check(validity.covers(now())) { "CSC leaf certificate is not valid now" }

            val reference = data.options.credentialReference
            val credentials = credentialResolver.resolve(reference)
            val authData = data.authObjectIds.associateWith { objectId ->
                credentials.authData[objectId]
                    ?: throw IllegalStateException("No stored authorization data for CSC authentication object $objectId")
            }
            val token = tokens.accessToken(data.tokenEndpoint, reference, credentials)
            val signature = try {
                val sad = csc.authorize(data.options.serviceUrl, token, data.credentialID, hash, authData)
                csc.signHash(data.options.serviceUrl, token, data.credentialID, sad, hash)
            } catch (cause: KmsProviderException) {
                // Never retry the signature here: RSSPs bill per signature.
                dropRejectedToken(cause, data.tokenEndpoint, reference, token)
                throw cause
            }

            // CSC does not define the ECDSA encoding: accept raw r||s or DER, whichever verifies.
            val p1363 = p1363Candidates(signature).firstOrNull { publicKeyVerifier.verify(message, it, P1363) }
                ?: throw KmsProviderException("CSC signature does not verify against the credential's public key")
            return when ((algorithm as SignatureAlgorithm.Ecdsa).encoding) {
                EcdsaSignatureEncoding.IEEE_P1363 -> p1363
                EcdsaSignatureEncoding.DER -> EcdsaSignatureCodec.p1363ToDer(p1363, P256_COMPONENT_BYTES)
            }
        }
    }

    companion object {
        val ID = ProviderId("csc-v2")

        /** The certificate chain read when the credential was attached, leaf first, as DER. Offline. */
        fun certificateChain(stored: StoredKey.Managed): List<BinaryData> {
            require(stored.provider == ID) { "Stored key belongs to a different provider" }
            return CscStoredKeyData.decode(stored.providerData).certificates().map(::BinaryData)
        }

        private const val PROVIDER_SCHEMA_VERSION = 1
        private const val CLIENT_CREDENTIALS = "oauth2client"
        private const val P256_COMPONENT_BYTES = 32
        private val SUPPORTED_SPECS = setOf("2.2.0.0")
        private val SPEC = KeySpec.Ec(EcCurve.P256)
        private val P1363 = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)
        private val SIGNATURE_ALGORITHMS: Set<SignatureAlgorithm> = EcdsaSignatureEncoding.entries
            .mapTo(mutableSetOf()) { SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, it) }

        private inline fun requireRssp(condition: Boolean, message: () -> String) {
            if (!condition) throw KmsProviderException(message())
        }

        /**
         * The authentication objects that every authorization sends: all of them without `auth/expression`, otherwise
         * those it names. Only conjunctions are supported. With `OR` or `XOR` the client must choose, and sending
         * every value can fail where the service expects exactly one.
         */
        private fun requiredAuthObjects(expression: String?, objects: List<CscAuthObject>): List<CscAuthObject> {
            if (expression == null) return objects
            val unsupported = { "Unsupported CSC authorization expression: $expression" }
            val byId = objects.associateBy { it.id }
            val required = mutableListOf<CscAuthObject>()
            var depth = 0
            var operandNext = true
            AUTH_EXPRESSION_TOKEN.findAll(expression).forEach { match ->
                val token = match.value
                when (token.uppercase()) {
                    "(" -> {
                        requireRssp(operandNext, unsupported)
                        depth++
                    }
                    ")" -> {
                        requireRssp(!operandNext && depth > 0, unsupported)
                        depth--
                    }
                    "AND" -> {
                        requireRssp(!operandNext, unsupported)
                        operandNext = true
                    }
                    "OR", "XOR" -> throw KmsProviderException(
                        "CSC authorization expression $expression needs a choice of authentication objects, " +
                            "which is not supported"
                    )
                    else -> {
                        requireRssp(operandNext, unsupported)
                        required += byId[token]
                            ?: throw KmsProviderException("CSC authorization expression names unknown object $token")
                        operandNext = false
                    }
                }
            }
            requireRssp(depth == 0 && !operandNext, unsupported)
            return required.distinct()
        }

        private val AUTH_EXPRESSION_TOKEN = Regex("""[()]|[^\s()]+""")

        private inline fun parseCertificate(der: ByteArray, failure: (Exception) -> Exception): X509Certificate =
            try {
                X509CertificateUtil.parseCertificateDerEncoded(ByteString(der))
            } catch (cause: Exception) {
                throw failure(cause)
            }

        /** RFC 5280 4.1.2.5: both ends of the validity period are inclusive. */
        private fun X509Certificate.Validity.covers(instant: Instant) = instant in notBefore..notAfter

        /**
         * `{oauth2}/oauth2/token`, but only on the service's own HTTPS origin: the client secret must not follow an
         * unauthenticated `info` answer to another host.
         */
        private fun derivedTokenEndpoint(serviceUrl: String, oauth2: String?): String {
            requireNotNull(oauth2) { "The CSC service names no oauth2 base URI; configure tokenEndpoint" }
            val service = Url(serviceUrl)
            val authorization = runCatching { Url(oauth2) }.getOrNull()
            require(
                authorization != null &&
                    service.protocol == URLProtocol.HTTPS &&
                    authorization.protocol == URLProtocol.HTTPS &&
                    authorization.host.equals(service.host, ignoreCase = true) &&
                    authorization.port == service.port
            ) { "The CSC service's oauth2 base URI is not on its HTTPS origin; configure tokenEndpoint" }
            return "${oauth2.trimEnd('/')}/oauth2/token".also { requireHttpEndpoint(it, "CSC token endpoint") }
        }

        private fun requireSigningUsages(usages: Set<KeyUsage>) =
            require(usages.isNotEmpty() && usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY }) {
                "CSC keys support only signing and verification"
            }

        private fun p1363Candidates(signature: ByteArray): List<ByteArray> = buildList {
            if (signature.size == 2 * P256_COMPONENT_BYTES) add(signature)
            try {
                add(EcdsaSignatureCodec.derToP1363(signature, P256_COMPONENT_BYTES))
            } catch (_: IllegalArgumentException) {
                // Not DER.
            }
        }
    }
}

@Serializable
private data class CscStoredKeyData(
    val options: CscOptions,
    val credentialID: String,
    val tokenEndpoint: String,
    val authObjectIds: List<String>,
    val certificateChain: List<String>,
) {
    init {
        require(credentialID.isNotBlank()) { "Stored CSC credential ID cannot be blank" }
        requireHttpEndpoint(tokenEndpoint, "CSC token endpoint")
        require(certificateChain.isNotEmpty()) { "Stored CSC certificate chain cannot be empty" }
    }

    fun certificates(): List<ByteArray> = certificateChain.map(Base64.Default::decode)

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    companion object {
        private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        fun decode(data: BinaryData): CscStoredKeyData = json.decodeFromString(data.toByteArray().decodeToString())
    }
}
