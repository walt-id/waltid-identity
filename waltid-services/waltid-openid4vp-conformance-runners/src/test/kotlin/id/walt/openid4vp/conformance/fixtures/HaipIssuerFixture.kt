package id.walt.openid4vp.conformance.fixtures

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/** Opt-in, per-run issuer configuration. Never use these public test keys in a deployment. */
internal class HaipIssuerFixture(baseUrl: String) {
    val service: Config
    val profiles: Config
    val pki: IssuerTestPki

    init {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.path.orEmpty() in setOf("", "/")) {
            "HAIP fixture requires an HTTPS service base URL without a path or credentials"
        }
        val base = baseUrl.trimEnd('/')
        service = ConfigFactory.parseResources("issuer2/issuer-service-ci.conf.template").resolve()
            .withValue("baseUrl", ConfigValueFactory.fromAnyRef(base))
            .withValue("enforcePushedAuthorizationRequests", ConfigValueFactory.fromAnyRef(true))
            .withValue("requirePkce", ConfigValueFactory.fromAnyRef(true))

        val original = ConfigFactory.parseResources("issuer2/issuer2-profiles-ci.conf").resolve()
        pki = IssuerTestPki("$base/conformance/issuer.crl",
            original.getConfig("defaultHaipIssuerKey.jwk").root().render(jsonOptions), countryCode = "AT")
        val sdJwtPki = IssuerTestPki("$base/conformance/issuer.crl",
            original.getConfig("defaultHaipIssuerKey.jwk").root().render(jsonOptions), countryCode = "AT",
            documentSigner = false)
        val signer = CertificateFactory.getInstance("X.509")
            .generateCertificate(pki.signerCertificate.inputStream()) as X509Certificate
        val validUntil = minOf(Instant.now().plus(365, ChronoUnit.DAYS), signer.notAfter.toInstant().minusSeconds(60))
            .truncatedTo(ChronoUnit.DAYS)
        // Resolve each inherited profile first, then replace every HAIP consumer explicitly.
        var updated = original
        for (entry in original.getConfig("profiles").root().keys) {
            val path = "profiles.$entry"
            if (!original.getString("$path.credentialConfigurationId").contains("haip")) continue
            val isMdoc = original.hasPath("$path.credentialData.\"org.iso.18013.5.1\"")
            val certificate = if (isMdoc) pki.signerCertificate else sdJwtPki.signerCertificate
            val leafBase64 = Base64.getEncoder().encodeToString(certificate)
            val leafPem = "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(certificate) +
                "\n-----END CERTIFICATE-----"
            updated = updated.withValue("$path.x5Chain", ConfigValueFactory.fromAnyRef(listOf(leafPem)))
                .withValue("$path.issuerKey.jwk.x5c", ConfigValueFactory.fromAnyRef(listOf(leafBase64)))
            if (!isMdoc) continue
            val namespace = "$path.credentialData.\"org.iso.18013.5.1\""
            val birthDate = LocalDate.parse(original.getString("$namespace.birth_date"))
            val age = Period.between(birthDate, LocalDate.now(ZoneOffset.UTC)).years
            updated = updated.withValue("$namespace.nationality", ConfigValueFactory.fromAnyRef("AT"))
                .withValue("$namespace.age_in_years", ConfigValueFactory.fromAnyRef(age))
                .withValue("$path.msoData.validUntil", ConfigValueFactory.fromAnyRef(validUntil.toString()))
            for ((name, value) in original.getConfig(namespace).root()) {
                if (name.startsWith("biometric_template_") && value.unwrapped() == null) {
                    updated = updated.withoutPath("$namespace.$name")
                }
                if (name.startsWith("age_over_")) {
                    val threshold = name.removePrefix("age_over_").toInt()
                    updated = updated.withValue("$namespace.$name", ConfigValueFactory.fromAnyRef(age >= threshold))
                }
            }
        }
        profiles = updated
    }

    fun writeTo(directory: Path) {
        Files.createDirectories(directory)
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        fun write(name: String, bytes: ByteArray) {
            val file = directory.resolve(name)
            Files.write(file, bytes)
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
        }
        write("issuer-service.conf", service.root().render(jsonOptions).toByteArray())
        write("issuer2-profiles.conf", profiles.root().render(jsonOptions).toByteArray())
        write("credential-trust-anchor.pem", pki.trustAnchorPem.toByteArray())
        write("issuer.crl", pki.crl)
    }

    companion object {
        private val jsonOptions = ConfigRenderOptions.concise().setJson(true)

        @JvmStatic
        fun main(args: Array<String>) {
            require(args.size == 2) { "Usage: HaipIssuerFixture <output-directory> <https-service-base-url>" }
            HaipIssuerFixture(args[1]).writeTo(Path.of(args[0]))
            println("Generated strict HAIP issuer test configuration and public PKI artifacts")
        }
    }
}
