import id.walt.gradle.x509.GenerateX509Certificates
import id.walt.gradle.x509.X509CertificatesExtension
import id.walt.gradle.x509.X509OutputFile

// Generates X.509 test/dev certificates (PEM) with JWK keys; see X509CertificatesExtension for the DSL.
val x509 = extensions.create<X509CertificatesExtension>("x509")
x509.outputDirectory.convention(layout.buildDirectory.dir("generated/x509"))
x509.renewBeforeDays.convention(30)
x509.certificates.configureEach {
    X509OutputFile.entries.forEach { kind ->
        outputFile(kind).convention(x509.outputDirectory.file("$name/${kind.defaultFileName}"))
    }
}

tasks.register<GenerateX509Certificates>("generateX509Certificates") {
    group = "walt.id"
    description = "Generates the X.509 certificates and JWK keys declared in the x509 extension."
    certificates.set(provider { x509.certificates.map { it.toRequest() } })
    renewBeforeDays.set(x509.renewBeforeDays)
    outputFiles.set(provider {
        x509.certificates.flatMap { spec ->
            X509OutputFile.entries.map { kind ->
                GenerateX509Certificates.outputKey(spec.name, kind) to spec.outputFile(kind).get().asFile
            }
        }.toMap()
    })
}
