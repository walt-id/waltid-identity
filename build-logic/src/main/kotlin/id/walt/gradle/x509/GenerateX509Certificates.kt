package id.walt.gradle.x509

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * Writes the [X509OutputFile]s of every certificate to the paths in [outputFiles].
 *
 * Deliberately not cacheable: private keys must not end up in a shared build cache. Keys are
 * regenerated only when the configuration changes; any change regenerates every certificate so
 * chains stay consistent.
 */
abstract class GenerateX509Certificates : DefaultTask() {
    @get:Input
    abstract val certificates: ListProperty<X509CertificateRequest>

    /** Keyed by [outputKey]. */
    @get:OutputFiles
    abstract val outputFiles: MapProperty<String, File>

    /** Outputs are out of date once a certificate expires within this many days. */
    @get:Internal
    abstract val renewBeforeDays: Property<Int>

    init {
        outputs.upToDateWhen { certificatesOutlive(Duration.ofDays(renewBeforeDays.get().toLong())) }
    }

    private fun certificatesOutlive(margin: Duration): Boolean {
        val deadline = Date.from(Instant.now().plus(margin))
        val factory = CertificateFactory.getInstance("X.509")
        return certificates.get().all { request ->
            val file = outputFiles.get().getValue(outputKey(request.name, X509OutputFile.CERTIFICATE))
            file.isFile && (file.inputStream().use(factory::generateCertificate) as X509Certificate).notAfter.after(deadline)
        }
    }

    @TaskAction
    fun generate() {
        val files = outputFiles.get()
        files.entries.groupBy({ it.value.canonicalFile }, { it.key }).filterValues { it.size > 1 }.forEach { (file, keys) ->
            throw InvalidUserDataException("x509 outputs $keys are all configured to write $file")
        }
        X509CertificateGenerator.generate(certificates.get()).forEach { (name, generated) ->
            fun write(kind: X509OutputFile, content: String) =
                files.getValue(outputKey(name, kind)).apply { parentFile.mkdirs() }.writeText(content)

            write(X509OutputFile.CERTIFICATE, pem(listOf(generated.certificate)))
            write(X509OutputFile.CHAIN, pem(generated.chain))
            write(X509OutputFile.PRIVATE_JWK, toJson(jwk(generated, includePrivate = true)))
            write(X509OutputFile.PUBLIC_JWK, toJson(jwk(generated, includePrivate = false)))
        }
    }

    companion object {
        fun outputKey(certificate: String, kind: X509OutputFile) = "$certificate/${kind.name}"
    }
}
