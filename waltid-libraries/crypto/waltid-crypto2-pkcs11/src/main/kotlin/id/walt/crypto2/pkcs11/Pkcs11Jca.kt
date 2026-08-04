package id.walt.crypto2.pkcs11

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.*
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide, reference-counted PKCS#11 sessions, one per token.
 *
 * Three properties of PKCS#11 force this shape, and they hold for every vendor library, not just SoftHSM:
 *
 * 1. A `SunPKCS11` instance cannot be terminated, and `Provider.configure` returns a *new* instance with its own
 *    token handle and session pool each time. Configuring per operation leaked a token plus its open sessions on
 *    every signature and repeated `C_Login`, which real tokens answer with `CKR_SESSION_COUNT` or
 *    `CKR_DEVICE_MEMORY`.
 * 2. Login state is per application (process) per slot, not per provider instance. `C_Logout` from any instance
 *    deauthenticates every other instance's sessions for that slot, which surfaces as
 *    `CKR_OPERATION_NOT_INITIALIZED` on the next operation. So logout may only happen when the last user is done.
 * 3. For the same reason, `KeyStore.load(null, pin)` cannot be relied on to validate a PIN: once the token is
 *    logged in, `C_Login` returns `CKR_USER_ALREADY_LOGGED_IN` and a *wrong* PIN is accepted. Verified empirically.
 *    A wrong PIN is therefore only detected on the first login to a token within a process.
 *
 * Sharing one logged-in session per token across provider instances is what makes all three safe.
 */
internal object Pkcs11Sessions {
    private val lock = Mutex()
    private val entries = mutableMapOf<SessionKey, Entry>()

    /**
     * A token is addressed twice at most: once with the hardening attribute template and once without it, for tokens
     * that reject templates outright (tpm2-pkcs11 does, for any attribute at all).
     */
    private data class SessionKey(val tokenId: TokenId, val hardened: Boolean)

    private class Entry(val session: Pkcs11Session, var holders: Int)

    suspend fun acquire(
        options: Pkcs11Options,
        pinResolver: Pkcs11PinResolver,
        hardened: Boolean = true,
    ): Pkcs11Session = lock.withLock {
        val key = SessionKey(options.tokenId, hardened)
        entries.getOrPut(key) { Entry(open(options, pinResolver, hardened), 0) }
            .also { it.holders++ }
            .session
    }

    /** Returns an already-open session for the token, preferring the hardened one. */
    suspend fun existing(tokenId: TokenId, hardened: Boolean): Pkcs11Session? = lock.withLock {
        entries[SessionKey(tokenId, hardened)]?.session
    }

    /**
     * Discards every session for a token so the next use opens a fresh one.
     *
     * Needed because a failed operation can leave the vendor library's context unusable: on tpm2-pkcs11 a single
     * rejected key generation makes every later operation fail with `CKR_GENERAL_ERROR`
     * (`Esys_Sign: Function called in the wrong order`), including operations on unrelated, working keys. With one
     * long-lived shared session that turns one bad request into a permanent outage, so the session is rebuilt
     * instead.
     */
    suspend fun invalidate(tokenId: TokenId) = lock.withLock {
        entries.keys.filter { it.tokenId == tokenId }.forEach { key ->
            entries.remove(key)?.let { entry ->
                runCatching { (entry.session.provider as? AuthProvider)?.logout() }
            }
        }
    }

    suspend fun release(tokenId: TokenId, hardened: Boolean) = lock.withLock {
        val key = SessionKey(tokenId, hardened)
        val entry = entries[key] ?: return@withLock
        entry.holders--
        if (entry.holders > 0) return@withLock
        entries.remove(key)
        // Logout is per application per slot, so it must only happen once nothing else in this process is using the
        // token - including the other template variant of the same token. Logging out while a second variant was in
        // use deauthenticated it, which surfaced as CKR_USER_NOT_LOGGED_IN on its next operation.
        if (entries.keys.none { it.tokenId == tokenId }) {
            runCatching { (entry.session.provider as? AuthProvider)?.logout() }
        }
    }

    private suspend fun open(
        options: Pkcs11Options,
        pinResolver: Pkcs11PinResolver,
        hardened: Boolean,
    ): Pkcs11Session {
        require(Files.isRegularFile(Path.of(options.libraryPath))) {
            "PKCS11 library does not exist: ${options.libraryPath}"
        }
        val sunPkcs11 = requireNotNull(Security.getProvider("SunPKCS11")) { "SunPKCS11 provider is unavailable" }
        val config = Files.createTempFile("waltid-pkcs11-", ".cfg")
        val provider = try {
            Files.writeString(config, options.sunPkcs11Configuration(hardened))
            sunPkcs11.configure(config.toString())
        } catch (cause: ProviderException) {
            // Provider.configure() is where C_Initialize runs and the slot is resolved, so this is what fails when the
            // token is not reachable at all - not when a key operation is wrong. Left untranslated it surfaced as a
            // bare `CKR_SLOT_ID_INVALID` or SunPKCS11's `slotListIndex is 0 but token only has 0 slots`, which reads
            // like a numbering mistake even when the real cause is that the vendor client cannot see the token from
            // this process. That has cost real debugging time, so say what it actually means.
            //
            // Deliberately only ProviderException, which is what SunPKCS11 raises for initialisation and slot
            // failures. A malformed configuration or an IO error is a different problem and must keep its own message.
            throw IllegalStateException(options.tokenUnreachableMessage(), cause)
        } finally {
            Files.deleteIfExists(config)
        }
        val pin = pinResolver.resolve(options.pinReference).copy()
        val keyStore = try {
            KeyStore.getInstance("PKCS11", provider).apply { load(null, pin) }
        } catch (cause: Exception) {
            // A vendor library can be left globally unusable by an earlier failure, in which case even a brand new
            // SunPKCS11 instance cannot log in. tpm2-pkcs11 does this: one rejected operation poisons its ESAPI
            // context for the lifetime of the process ("Esys_Load: Function called in the wrong order"), and it
            // cannot be repaired in-process. Say so, rather than surfacing "load failed".
            throw IllegalStateException(
                "Could not open a PKCS11 session on ${options.tokenDescription()}. If an earlier operation on this " +
                        "token failed, some libraries - tpm2-pkcs11 in particular - are left unusable for the lifetime " +
                        "of the process and the service has to be restarted.",
                cause,
            )
        } finally {
            pin.fill('\u0000')
        }
        return Pkcs11Session(provider, keyStore, hardened)
    }
}

/**
 * Holds one reference per token for the lifetime of a [Pkcs11KeyProvider], and remembers whether that token's session
 * had to be opened without the generate attribute template.
 *
 * Exactly one session per token matters for more than efficiency: a `KeyStore` snapshots its entries at `load`, so a
 * key created through one provider instance is not visible through another instance's already-loaded `KeyStore`. Using
 * two variants of the same token made a freshly generated key unfindable on restore.
 */
internal class Pkcs11SessionFactory(
    private val pinResolver: Pkcs11PinResolver,
) {
    private val held = ConcurrentHashMap<TokenId, Boolean>()

    suspend fun session(options: Pkcs11Options): Pkcs11Session {
        held[options.tokenId]?.let { hardened ->
            Pkcs11Sessions.existing(options.tokenId, hardened)?.let { return it }
        }
        val session = Pkcs11Sessions.acquire(options, pinResolver, hardened = true)
        if (held.putIfAbsent(options.tokenId, true) != null) {
            Pkcs11Sessions.release(options.tokenId, true)
        }
        return session
    }

    /**
     * Replaces this token's session with one configured without the attribute template, for a token that rejects it.
     * Subsequent operations use the replacement, so generation and lookup always share a `KeyStore`.
     */
    suspend fun downgradeToPlainSession(options: Pkcs11Options): Pkcs11Session {
        val session = Pkcs11Sessions.acquire(options, pinResolver, hardened = false)
        held.put(options.tokenId, false)?.let { previous ->
            if (previous) Pkcs11Sessions.release(options.tokenId, true)
        }
        return session
    }

    /** @see Pkcs11Sessions.invalidate */
    suspend fun invalidate(options: Pkcs11Options) {
        held.remove(options.tokenId)
        Pkcs11Sessions.invalidate(options.tokenId)
    }

    suspend fun close() {
        held.keys.toList().forEach { tokenId ->
            held.remove(tokenId)?.let { hardened -> Pkcs11Sessions.release(tokenId, hardened) }
        }
    }
}

/**
 * True when a failure came from the token or its library, rather than from validation in this module. Such a failure
 * may have left the library's context unusable, so the session is rebuilt before the next use.
 */
internal fun Throwable.indicatesTokenFailure(): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        cause is ProviderException || cause::class.qualifiedName?.endsWith("PKCS11Exception") == true
    }

/** How this token is addressed, for error messages. Shared so both failure paths name the token the same way. */
internal fun Pkcs11Options.tokenDescription(): String =
    "$libraryPath (${slotId?.let { "slot $it" } ?: "slot-list index $slotListIndex"})"

/**
 * Why initialising a token failed, in terms an operator can act on.
 *
 * `Provider.configure()` covers `C_Initialize` and slot resolution, so its failures are almost never about the key
 * being requested. The vendor's own wording actively misleads here: an unreachable token reports
 * `CKR_SLOT_ID_INVALID`, and an empty slot list reports `slotListIndex is 0 but token only has 0 slots`. Both read as
 * a wrong slot number, when the usual cause is that the vendor client cannot see the token from *this process* - a
 * missing or mis-scoped configuration environment variable, or configuration holding paths relative to a working
 * directory the service does not run in.
 */
internal fun Pkcs11Options.tokenUnreachableMessage(): String =
    "Could not initialise the PKCS11 token ${tokenDescription()}. The library loaded, so this is not a wrong " +
            "library path. Either the slot does not exist, or the vendor client cannot see the token from this " +
            "process - check that the library's configuration environment variable is set for the service process " +
            "itself, and that any paths inside that configuration are absolute rather than relative to the client " +
            "install directory. Verify with a vendor tool run as the service user from an unrelated working " +
            "directory before changing the slot."

internal data class Pkcs11Session(
    val provider: Provider,
    val keyStore: KeyStore,
    /** Whether this session was configured with the generated-key hardening attribute template. */
    val hardened: Boolean,
) {
    /**
     * `Signature` services this provider registers for the token.
     *
     * SunPKCS11 derives its service list from the token's `C_GetMechanismList`, which makes it a far better capability
     * signal than a hard-coded table - the previous table over-advertised on any token lacking RSA-PSS or a given
     * digest. It is not infallible, though: SoftHSMv2 registers `SHA256withECDSA` and
     * `SHA256withECDSAinP1363Format` and both fail at `C_SignUpdate` with `CKR_OPERATION_NOT_INITIALIZED`. Combined
     * ECDSA mechanisms are therefore never used - see [supportsEcdsa].
     */
    val signatureAlgorithmNames: Set<String> by lazy {
        provider.services
            .filter { it.type == "Signature" }
            .mapTo(mutableSetOf()) { it.algorithm.uppercase() }
    }

    /** Key types this token can generate, probed the same way. */
    val keyPairGeneratorNames: Set<String> by lazy {
        provider.services
            .filter { it.type == "KeyPairGenerator" }
            .mapTo(mutableSetOf()) { it.algorithm.uppercase() }
    }

    fun supportsSignature(name: String): Boolean = name.uppercase() in signatureAlgorithmNames

    fun supportsKeyPairGeneration(name: String): Boolean = name.uppercase() in keyPairGeneratorNames
}

/**
 * SunPKCS11's `KeyStore` only exposes a private key that has a certificate, so a generated key needs one before it
 * can be addressed by alias. It is a placeholder for addressing, never a trust statement.
 */
internal fun createPkcs11Certificate(
    alias: String,
    spec: KeySpec,
    keyPair: KeyPair,
    session: Pkcs11Session,
): X509Certificate {
    val now = Instant.now()
    // X500NameBuilder rather than string concatenation: an alias containing '+', '"', '<', ';' or a leading space
    // would otherwise produce a different DN than intended, or fail to parse.
    val name = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, alias).build()
    val builder = JcaX509v3CertificateBuilder(
        name,
        // BigInteger(numBits, Random) is already non-negative but may be zero, which RFC 5280 forbids.
        BigInteger(160, SecureRandom()).max(BigInteger.ONE),
        Date.from(now.minus(1, ChronoUnit.MINUTES)),
        Date.from(now.plus(3650, ChronoUnit.DAYS)),
        name,
        keyPair.public,
    )
    return JcaX509CertificateConverter()
        .setProvider(bouncyCastle)
        .getCertificate(builder.build(Pkcs11ContentSigner(spec, keyPair, session)))
}

/**
 * Buffers the to-be-signed bytes and performs `initSign`/`update`/`sign` as one uninterrupted sequence.
 *
 * This matters on PKCS#11 specifically. BouncyCastle's own `ContentSigner` initialises the `Signature` when it is
 * built and only writes the data afterwards; across that gap SunPKCS11 can return the session to its pool and cancel
 * the initialised operation, so the later `update` fails with `CKR_OPERATION_NOT_INITIALIZED`. Buffering removes the
 * gap and is vendor-neutral.
 */
private class Pkcs11ContentSigner(
    private val spec: KeySpec,
    private val keyPair: KeyPair,
    private val session: Pkcs11Session,
) : ContentSigner {
    private val algorithmName = spec.certificateSignatureAlgorithm(session)
    private val buffer = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier() = DefaultSignatureAlgorithmIdentifierFinder().find(algorithmName)

    override fun getOutputStream(): OutputStream = buffer

    override fun getSignature(): ByteArray {
        val toBeSigned = buffer.toByteArray()
        // ECDSA always digests here and signs with raw CKM_ECDSA: it is the one ECDSA mechanism every PKCS#11 token
        // implements, and the combined ones are unreliable even when registered (see Pkcs11Session).
        val rawEcdsa = spec is KeySpec.Ec
        val payload = if (rawEcdsa) {
            MessageDigest.getInstance(spec.certificateDigest()).digest(toBeSigned)
        } else toBeSigned
        return Signature.getInstance(if (rawEcdsa) "NONEwithECDSA" else algorithmName, session.provider).apply {
            initSign(keyPair.private)
            update(payload)
        }.sign()
    }
}

private fun Pkcs11Options.sunPkcs11Configuration(hardened: Boolean): String = buildString {
    appendLine("name = Waltid${tokenId.hashCode().toUInt()}${if (hardened) "" else "Plain"}")
    appendLine("library = $libraryPath")
    // Slot ID and slot-list index are both native SunPKCS11 directives; Pkcs11Options guarantees exactly one is set.
    slotId?.let { appendLine("slot = $it") }
    slotListIndex?.let { appendLine("slotListIndex = $it") }
    // Generated private keys must be persistent, non-extractable, and usable for signing only. CKA_DECRYPT,
    // CKA_UNWRAP, CKA_SIGN_RECOVER and CKA_DERIVE are set false explicitly: without them the token's own defaults
    // apply, and SoftHSM was measured to hand back an RSA private key that IS decrypt-capable, i.e. an
    // RSAES-PKCS1-v1_5 oracle for any other PKCS#11 client holding the PIN.
    //
    // The template is nonetheless best-effort: tpm2-pkcs11 rejects a generate template containing *any* attribute,
    // so a token that refuses it is retried without one and the resulting key is then checked behaviourally instead
    // (see Pkcs11KeyProvider.verifyGeneratedKeySafety). Config alone was never proof that the template applied.
    if (!hardened) {
        providerConfigurationLines.forEach(::appendLine)
        return@buildString
    }
    appendLine("attributes(generate, CKO_PRIVATE_KEY, *) = {")
    appendLine("  CKA_TOKEN = true")
    appendLine("  CKA_PRIVATE = true")
    appendLine("  CKA_SENSITIVE = true")
    appendLine("  CKA_EXTRACTABLE = false")
    appendLine("  CKA_SIGN = true")
    appendLine("  CKA_DECRYPT = false")
    appendLine("  CKA_UNWRAP = false")
    appendLine("  CKA_SIGN_RECOVER = false")
    appendLine("  CKA_DERIVE = false")
    appendLine("}")
    // Public keys are deliberately session objects. SunPKCS11 reads the public key from the certificate, so a token
    // public-key object serves no purpose - and P11KeyStore.deleteEntry destroys only the private key and the
    // certificate, so a persistent one is orphaned on the token by every generate/delete cycle.
    appendLine("attributes(generate, CKO_PUBLIC_KEY, *) = {")
    appendLine("  CKA_TOKEN = false")
    appendLine("  CKA_VERIFY = true")
    appendLine("}")
    providerConfigurationLines.forEach(::appendLine)
}

private fun KeySpec.certificateSignatureAlgorithm(session: Pkcs11Session): String = when (this) {
    KeySpec.Ec(EcCurve.P256) -> "SHA256withECDSA"
    KeySpec.Ec(EcCurve.P384) -> "SHA384withECDSA"
    KeySpec.Ec(EcCurve.P521) -> "SHA512withECDSA"
    is KeySpec.Rsa -> rsaCertificateSignatureAlgorithm(session)
    else -> throw IllegalArgumentException("Unsupported PKCS11 certificate key specification: $this")
}

/** Not every token implements SHA-256 with RSA; fall back through the digests the token does register. */
private fun rsaCertificateSignatureAlgorithm(session: Pkcs11Session): String =
    listOf("SHA256withRSA", "SHA384withRSA", "SHA512withRSA")
        .firstOrNull(session::supportsSignature)
        ?: throw IllegalArgumentException("PKCS11 token implements no supported RSA certificate signature algorithm")

private fun KeySpec.Ec.certificateDigest(): String = when (curve) {
    EcCurve.P256 -> "SHA-256"
    EcCurve.P384 -> "SHA-384"
    EcCurve.P521 -> "SHA-512"
    else -> throw IllegalArgumentException("Unsupported PKCS11 EC curve: ${curve.name}")
}

private val bouncyCastle = BouncyCastleProvider()
