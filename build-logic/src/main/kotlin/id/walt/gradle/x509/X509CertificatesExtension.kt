package id.walt.gradle.x509

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.Serializable
import javax.inject.Inject

/** Certificate profile; decides the extensions, the allowed issuer and the default validity. */
enum class X509Profile(val defaultValidityDays: Int) {
    /** Root (self-signed) or intermediate certificate authority. */
    CA(3650),

    /** End-entity certificate, e.g. TLS server/client or a generic signing key. */
    LEAF(365),

    /** ISO/IEC 18013-5 IACA root (Annex B); always self-signed, EC keys only. */
    IACA(9 * 365),

    /** ISO/IEC 18013-5 Document Signer; must be issued by an [IACA], EC keys only. */
    DOCUMENT_SIGNER(365),
}

/**
 * `x509 { ... }` extension added by the `waltid.x509` plugin.
 *
 * ```
 * x509 {
 *     ca("root-ca") { subject = "CN=Test Root CA,O=walt.id,C=AT" }
 *     leaf("server") {
 *         issuer = "root-ca"
 *         subject = "CN=localhost"
 *         dnsNames.add("localhost")
 *         extendedKeyUsages.add("serverAuth")
 *     }
 *     iaca("iaca") {
 *         subject = "C=US,ST=NY,CN=Test IACA"
 *         issuerAlternativeNameUri = "https://iaca.example.com"
 *     }
 *     documentSigner("ds") {
 *         issuer = "iaca"
 *         subject = "C=US,ST=NY,CN=Test Document Signer"
 *         crlDistributionPointUri = "https://iaca.example.com/crl"
 *         certificateFile = layout.projectDirectory.file("src/test/resources/ds.pem")
 *         privateJwkFile = layout.projectDirectory.file("src/test/resources/ds.jwk.json")
 *     }
 * }
 * ```
 */
abstract class X509CertificatesExtension @Inject constructor(objects: ObjectFactory) {
    val certificates: NamedDomainObjectContainer<X509CertificateSpec> =
        objects.domainObjectContainer(X509CertificateSpec::class.java)

    /** Each certificate is written to `<outputDirectory>/<name>/`. */
    abstract val outputDirectory: DirectoryProperty

    /** Regenerate all certificates once any generated one expires within this many days (default 30). */
    abstract val renewBeforeDays: Property<Int>

    fun certificate(name: String, action: Action<X509CertificateSpec>): NamedDomainObjectProvider<X509CertificateSpec> =
        certificates.register(name, action)

    fun ca(name: String, action: Action<X509CertificateSpec>) = register(name, X509Profile.CA, action)
    fun leaf(name: String, action: Action<X509CertificateSpec>) = register(name, X509Profile.LEAF, action)
    fun iaca(name: String, action: Action<X509CertificateSpec>) = register(name, X509Profile.IACA, action)
    fun documentSigner(name: String, action: Action<X509CertificateSpec>) =
        register(name, X509Profile.DOCUMENT_SIGNER, action)

    private fun register(name: String, profile: X509Profile, action: Action<X509CertificateSpec>) =
        certificates.register(name) {
            this.profile.set(profile)
            action.execute(this)
        }
}

abstract class X509CertificateSpec @Inject constructor(private val name: String) : Named {
    override fun getName() = name

    abstract val profile: Property<X509Profile>

    /** Subject distinguished name in RFC 4514 form, e.g. `CN=Test,O=walt.id,C=AT`. */
    abstract val subject: Property<String>

    /** Name of the issuing certificate in this extension; absent means self-signed. */
    abstract val issuer: Property<String>

    /** One of `P-256` (default), `P-384`, `P-521`, `RSA-2048`, `RSA-3072`, `RSA-4096`, `Ed25519`. */
    abstract val keyAlgorithm: Property<String>

    abstract val validityDays: Property<Int>

    /** BasicConstraints path length for [X509Profile.CA]; absent means unlimited. */
    abstract val pathLength: Property<Int>

    /** Overrides the profile's key usages (CA/LEAF only), e.g. `digitalSignature`, `keyEncipherment`. */
    abstract val keyUsages: SetProperty<String>

    /** Names like `serverAuth`, `clientAuth`, `codeSigning` or dotted OIDs. */
    abstract val extendedKeyUsages: ListProperty<String>

    abstract val dnsNames: ListProperty<String>
    abstract val ipAddresses: ListProperty<String>
    abstract val uris: ListProperty<String>
    abstract val emails: ListProperty<String>

    /** IssuerAlternativeName; one of these is required for IACA, Document Signers inherit the IACA's. */
    abstract val issuerAlternativeNameUri: Property<String>
    abstract val issuerAlternativeNameEmail: Property<String>

    /** CRLDistributionPoints URI; required for Document Signers. */
    abstract val crlDistributionPointUri: Property<String>

    /** Output files; default to `<outputDirectory>/<name>/<file name of [X509OutputFile]>`. */
    abstract val certificateFile: RegularFileProperty
    abstract val chainFile: RegularFileProperty
    abstract val privateJwkFile: RegularFileProperty
    abstract val publicJwkFile: RegularFileProperty

    internal fun outputFile(kind: X509OutputFile) = when (kind) {
        X509OutputFile.CERTIFICATE -> certificateFile
        X509OutputFile.CHAIN -> chainFile
        X509OutputFile.PRIVATE_JWK -> privateJwkFile
        X509OutputFile.PUBLIC_JWK -> publicJwkFile
    }

    init {
        profile.convention(X509Profile.LEAF)
        keyAlgorithm.convention("P-256")
        validityDays.convention(profile.map { it.defaultValidityDays })
    }

    internal fun toRequest() = X509CertificateRequest(
        name = name,
        profile = profile.get(),
        subject = subject.orNull ?: error("x509 certificate '$name' needs a subject"),
        issuer = issuer.orNull,
        keyAlgorithm = keyAlgorithm.get(),
        validityDays = validityDays.get(),
        pathLength = pathLength.orNull,
        keyUsages = keyUsages.get().toSortedSet().toList(),
        extendedKeyUsages = extendedKeyUsages.get(),
        dnsNames = dnsNames.get(),
        ipAddresses = ipAddresses.get(),
        uris = uris.get(),
        emails = emails.get(),
        issuerAlternativeNameUri = issuerAlternativeNameUri.orNull,
        issuerAlternativeNameEmail = issuerAlternativeNameEmail.orNull,
        crlDistributionPointUri = crlDistributionPointUri.orNull,
    )
}

/** Files written per certificate, with their default name below `<outputDirectory>/<name>/`. */
enum class X509OutputFile(val defaultFileName: String) {
    /** The certificate alone, PEM. */
    CERTIFICATE("certificate.pem"),

    /** The certificate followed by its issuers up to the root, PEM. */
    CHAIN("chain.pem"),

    /** Private JWK with `kid` and `x5c`. */
    PRIVATE_JWK("private.jwk.json"),

    /** Public JWK with `kid` and `x5c`. */
    PUBLIC_JWK("public.jwk.json"),
}

/** Immutable, configuration-cache friendly snapshot of an [X509CertificateSpec]. */
data class X509CertificateRequest(
    val name: String,
    val profile: X509Profile,
    val subject: String,
    val issuer: String? = null,
    val keyAlgorithm: String = "P-256",
    val validityDays: Int = profile.defaultValidityDays,
    val pathLength: Int? = null,
    val keyUsages: List<String> = emptyList(),
    val extendedKeyUsages: List<String> = emptyList(),
    val dnsNames: List<String> = emptyList(),
    val ipAddresses: List<String> = emptyList(),
    val uris: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val issuerAlternativeNameUri: String? = null,
    val issuerAlternativeNameEmail: String? = null,
    val crlDistributionPointUri: String? = null,
) : Serializable
